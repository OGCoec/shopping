#!/usr/bin/env node
"use strict";

const { URL } = require("node:url");

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith("--")) {
      continue;
    }
    const key = arg.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith("--")) {
      args[key] = next;
      i += 1;
    } else {
      args[key] = "true";
    }
  }
  return args;
}

function loadPlaywright() {
  try {
    return require("playwright");
  } catch (error) {
    console.error("Playwright is required for DOM XSS smoke tests.");
    console.error("Install it with: npm i -D playwright && npx playwright install chromium");
    process.exit(2);
  }
}

function normalizeBaseUrl(value) {
  const base = new URL(value || "https://localhost:6655");
  base.pathname = base.pathname.replace(/\/+$/, "");
  base.search = "";
  base.hash = "";
  return base.toString().replace(/\/+$/, "");
}

function parseCookieHeader(cookieHeader, baseUrl) {
  const ignored = new Set(["path", "domain", "expires", "max-age", "secure", "httponly", "samesite"]);
  return String(cookieHeader || "")
    .split(";")
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => {
      const eq = part.indexOf("=");
      if (eq <= 0) {
        return null;
      }
      const name = part.slice(0, eq).trim();
      const value = part.slice(eq + 1).trim();
      if (!name || ignored.has(name.toLowerCase())) {
        return null;
      }
      return { name, value, url: baseUrl };
    })
    .filter(Boolean);
}

function buildUrl(baseUrl, path) {
  return new URL(path, `${baseUrl}/`).toString();
}

async function assertNoDangerousDom(page, label, baseOrigin) {
  const result = await page.evaluate(() => {
    const bad = [];
    const urlAttrs = new Set(["href", "src", "xlink:href", "action", "formaction"]);
    for (const element of document.querySelectorAll("*")) {
      for (const attr of element.getAttributeNames()) {
        const value = element.getAttribute(attr) || "";
        const attrLower = attr.toLowerCase();
        const lower = value.trim().toLowerCase();
        if (attrLower.startsWith("on") && /(alert\s*\(|javascript:|<script|onerror\s*=|onload\s*=)/i.test(value)) {
          bad.push(`${element.tagName.toLowerCase()}[${attr}="${value}"]`);
        }
        if (urlAttrs.has(attrLower) && (
          lower.startsWith("javascript:") ||
          lower.startsWith("data:text/html") ||
          lower.startsWith("//evil.com") ||
          lower.includes("evil.com/a.png")
        )) {
          bad.push(`${element.tagName.toLowerCase()}[${attr}="${value}"]`);
        }
      }
    }
    for (const script of document.scripts) {
      const src = script.getAttribute("src") || "";
      const text = script.textContent || "";
      if (src.trim().toLowerCase().startsWith("javascript:") || /alert\s*\(\s*1\s*\)/.test(text)) {
        bad.push(`script[src="${src}"]`);
      }
    }
    return {
      bad,
      href: window.location.href
    };
  });

  if (result.bad.length > 0) {
    throw new Error(`${label}: dangerous DOM sinks found: ${result.bad.join("; ")}`);
  }
  if (new URL(result.href).origin !== baseOrigin) {
    throw new Error(`${label}: page navigated outside same origin: ${result.href}`);
  }
}

async function assertSecurityUrlHelpers(page) {
  const checks = await page.evaluate(() => {
    const securityUrls = window.ShoppingSecurityUrls;
    if (!securityUrls) {
      return { available: false };
    }
    return {
      available: true,
      jsPath: securityUrls.safeSameOriginPath("javascript:alert(1)", "/shopping/user/console", ["/shopping/"]),
      protocolRelativePath: securityUrls.safeSameOriginPath("//evil.com/a", "/shopping/user/console", ["/shopping/"]),
      jsImage: securityUrls.safeImageUrl("javascript:alert(1)", "", { allowData: false, allowBlob: false }),
      htmlDataImage: securityUrls.safeImageUrl("data:text/html,<script>alert(1)</script>", "", { allowData: false, allowBlob: false }),
      evilExternal: securityUrls.safeExternalHttpsUrl("https://evil.com/a", [window.location.hostname], "")
    };
  });
  if (!checks.available) {
    throw new Error("ShoppingSecurityUrls is not available on the tested page.");
  }
  if (checks.jsPath !== "/shopping/user/console" || checks.protocolRelativePath !== "/shopping/user/console") {
    throw new Error(`safeSameOriginPath did not fall back safely: ${JSON.stringify(checks)}`);
  }
  if (checks.jsImage || checks.htmlDataImage || checks.evilExternal) {
    throw new Error(`URL helper accepted a dangerous URL: ${JSON.stringify(checks)}`);
  }
}

async function runPageCheck(context, options) {
  const { label, url, baseOrigin, expectAdmin, waitMs, requiredResponsePath, checkSecurityHelpers } = options;
  const page = await context.newPage();
  const failures = [];
  const requiredResponses = [];
  page.on("dialog", async (dialog) => {
    failures.push(`${label}: unexpected ${dialog.type()} dialog: ${dialog.message()}`);
    await dialog.dismiss().catch(() => {});
  });
  page.on("pageerror", (error) => {
    failures.push(`${label}: page error: ${error.message}`);
  });
  page.on("response", (response) => {
    if (!requiredResponsePath) {
      return;
    }
    try {
      if (new URL(response.url()).pathname === requiredResponsePath) {
        requiredResponses.push(response.status());
      }
    } catch (_) {
    }
  });

  const response = await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(waitMs);

  if (response && response.status() >= 500) {
    failures.push(`${label}: HTTP ${response.status()}`);
  }
  if (expectAdmin && /\/shopping\/admin\/login(?:$|[/?#])/.test(new URL(page.url()).pathname)) {
    failures.push(`${label}: admin cookie is missing or invalid; redirected to login.`);
  }
  if (requiredResponsePath) {
    if (requiredResponses.length === 0) {
      failures.push(`${label}: required API response was not observed: ${requiredResponsePath}`);
    } else if (requiredResponses.some((status) => status >= 400)) {
      failures.push(`${label}: required API returned HTTP ${requiredResponses.join(",")}: ${requiredResponsePath}`);
    }
  }

  if (checkSecurityHelpers) {
    await assertSecurityUrlHelpers(page);
  }
  await assertNoDangerousDom(page, label, baseOrigin);
  if (failures.length > 0) {
    throw new Error(failures.join("\n"));
  }
  await page.close();
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const baseUrl = normalizeBaseUrl(args["base-url"] || process.env.BASE_URL);
  const baseOrigin = new URL(baseUrl).origin;
  const spuId = args["spu-id"] || process.env.XSS_SPU_ID || process.env.SPU_ID;
  const adminCookie = args["admin-cookie"] || process.env.ADMIN_COOKIE || "";
  const userCookie = args["user-cookie"] || process.env.USER_COOKIE || "";
  const accessToken = args["access-token"] || process.env.ACCESS_TOKEN || "";
  const skipAdmin = args["skip-admin"] === "true" || process.env.SKIP_ADMIN === "true";
  const headless = args.headless !== "false" && process.env.HEADLESS !== "false";
  const waitMs = Number(args["wait-ms"] || process.env.WAIT_MS || 1500);

  if (!spuId) {
    throw new Error("Missing --spu-id or XSS_SPU_ID. Use the SPU id produced by the stored XSS JMeter run.");
  }
  if (!skipAdmin && !adminCookie) {
    throw new Error("Missing --admin-cookie or ADMIN_COOKIE. Pass --skip-admin only if you intentionally skip admin DOM coverage.");
  }

  const { chromium } = loadPlaywright();
  const browser = await chromium.launch({ headless });
  const contextOptions = {
    baseURL: baseUrl,
    ignoreHTTPSErrors: true
  };
  if (accessToken) {
    contextOptions.extraHTTPHeaders = {
      Authorization: `Bearer ${accessToken}`
    };
  }
  const context = await browser.newContext(contextOptions);

  const cookies = [
    ...parseCookieHeader(userCookie, baseUrl),
    ...parseCookieHeader(adminCookie, baseUrl)
  ];
  if (cookies.length > 0) {
    await context.addCookies(cookies);
  }

  const encodedSpuId = encodeURIComponent(String(spuId));
  const productApiPath = `/shopping/api/products/${encodedSpuId}`;
  const payload = "<img src=x onerror=alert(1)>";
  const encodedPayload = encodeURIComponent(payload);
  const checks = [
    {
      label: "user product detail",
      url: buildUrl(baseUrl, `/shopping/user/products/${encodedSpuId}`),
      baseOrigin,
      expectAdmin: false,
      waitMs,
      requiredResponsePath: productApiPath,
      checkSecurityHelpers: true
    },
    {
      label: "user product detail with keyword payload",
      url: buildUrl(baseUrl, `/shopping/user/products/${encodedSpuId}?keyword=${encodedPayload}`),
      baseOrigin,
      expectAdmin: false,
      waitMs,
      requiredResponsePath: productApiPath
    },
    {
      label: "waf returnTo payload",
      url: buildUrl(baseUrl, `/shopping/auth/waf/verify?returnTo=${encodeURIComponent("javascript:alert(1)")}`),
      baseOrigin,
      expectAdmin: false,
      waitMs
    },
    {
      label: "waf verifyUrl payload",
      url: buildUrl(baseUrl, `/shopping/auth/waf/verify?verifyUrl=${encodeURIComponent("javascript:alert(1)")}`),
      baseOrigin,
      expectAdmin: false,
      waitMs
    }
  ];

  if (!skipAdmin) {
    checks.push({
      label: "admin product detail",
      url: buildUrl(baseUrl, `/shopping/admin/console/products/${encodedSpuId}`),
      baseOrigin,
      expectAdmin: true,
      waitMs
    });
  }

  const failures = [];
  for (const check of checks) {
    try {
      await runPageCheck(context, check);
      console.log(`PASS ${check.label}`);
    } catch (error) {
      failures.push(error.message);
      console.error(`FAIL ${check.label}: ${error.message}`);
    }
  }

  await browser.close();
  if (failures.length > 0) {
    throw new Error(`DOM XSS smoke failed:\n${failures.join("\n")}`);
  }
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
