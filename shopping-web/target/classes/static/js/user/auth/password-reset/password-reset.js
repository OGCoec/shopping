(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingPasswordReset = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const EMAIL_CODE_PATH = "/shopping/user/forgot-password/email-code";
  const VERIFY_CODE_PATH = "/shopping/user/forgot-password/verify-code";
  const CRYPTO_KEY_PATH = "/shopping/user/forgot-password/crypto-key";
  const RESET_BY_LINK_PATH = "/shopping/user/forgot-password/reset-by-link";
  const RESET_BY_CODE_PATH = "/shopping/user/forgot-password/reset-by-code";
  const WAF_PENDING_KEY = "shopping.password-reset.waf.pending";
  const WAF_RESUME_COOKIE = "PASSWORD_RESET_WAF_RESUME";
  const WAF_RESUME_HEADER = "X-Password-Reset-Waf-Resume";

  let initialized = false;
  let cooldownTimer = null;

  function initializePasswordResetFragment(options = {}) {
    if (initialized) {
      syncMode();
      return;
    }
    initialized = true;

    bindSendButton("btn-reset-code", options);
    bindVerifyCode(options);
    bindResetByLink(options);
    syncMode();
    resumeAfterWaf(options);
  }

  function syncMode() {
    const token = currentResetToken();
    const requestPanel = document.getElementById("password-reset-request-panel");
    const linkPanel = document.getElementById("password-reset-link-panel");
    if (requestPanel) requestPanel.style.display = token ? "none" : "";
    if (linkPanel) linkPanel.style.display = token ? "" : "none";
    if (token) {
      document.getElementById("reset-link-password")?.focus();
    }
  }

  function bindSendButton(id, options) {
    const button = document.getElementById(id);
    if (!button || button.dataset.passwordResetBound === "true") return;
    button.dataset.passwordResetBound = "true";
    button.addEventListener("click", async () => {
      const email = readEmail();
      if (!email) {
        showRequestMessage("\u8bf7\u8f93\u5165\u90ae\u7bb1\u3002", true);
        return;
      }
      await sendResetEmail(email, options, false);
    });
  }

  function bindVerifyCode(options) {
    const button = document.getElementById("btn-verify-reset-code");
    if (!button || button.dataset.passwordResetBound === "true") return;
    button.dataset.passwordResetBound = "true";
    button.addEventListener("click", async () => {
      const email = readEmail();
      const code = document.getElementById("reset-code")?.value?.trim() || "";
      if (!email || !code) {
        showRequestMessage("\u8bf7\u8f93\u5165\u90ae\u7bb1\u548c 6 \u4f4d\u9a8c\u8bc1\u7801\u3002", true);
        return;
      }
      try {
        const response = await fetchWithPreAuth(options)(VERIFY_CODE_PATH, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, code })
        });
        const payload = await parseJsonSafely(response);
        if (!response.ok || !payload?.success || !payload?.redirectPath) {
          showRequestMessage(payload?.message || "\u9a8c\u8bc1\u7801\u9519\u8bef\u6216\u5df2\u8fc7\u671f\u3002", true);
          return;
        }
        await options.shellApi?.navigateTo?.(payload.redirectPath);
      } catch (_) {
        showRequestMessage("\u9a8c\u8bc1\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
      }
    });
  }

  function bindResetByLink(options) {
    const button = document.getElementById("btn-reset-by-link");
    if (!button || button.dataset.passwordResetBound === "true") return;
    button.dataset.passwordResetBound = "true";
    button.addEventListener("click", async () => {
      const token = currentResetToken();
      const password = document.getElementById("reset-link-password")?.value || "";
      const confirmPassword = document.getElementById("reset-link-confirm")?.value || "";
      const resetPath = currentResetMode() === "code" ? RESET_BY_CODE_PATH : RESET_BY_LINK_PATH;
      await submitReset(resetPath, { token }, password, confirmPassword, showLinkMessage, options);
    });
  }

  async function sendResetEmail(email, options, wafResume) {
    try {
      const response = await fetchWithPreAuth(options)(EMAIL_CODE_PATH, {
        method: "POST",
        headers: buildJsonHeaders(wafResume),
        body: JSON.stringify({ email })
      });
      const payload = await parseJsonSafely(response);
      if (payload?.challengeType === "WAF_REQUIRED" && payload?.verifyUrl) {
        persistWafPending({ email });
        window.location.assign(payload.verifyUrl);
        return;
      }
      if (!response.ok || !payload?.success) {
        showRequestMessage(payload?.message || "\u53d1\u9001\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
        startCooldown(Number(payload?.retryAfterMs || 0));
        return;
      }
      showRequestMessage(payload.message || "\u5df2\u53d1\u9001\uff0c\u8bf7\u67e5\u770b\u90ae\u7bb1\u3002", false);
      startCooldown(Number(payload.retryAfterMs || 60000));
      const codePanel = document.getElementById("password-reset-code-panel");
      if (codePanel) codePanel.style.display = "";
      document.getElementById("reset-code")?.focus();
    } catch (_) {
      showRequestMessage("\u53d1\u9001\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
    }
  }

  async function resumeAfterWaf(options) {
    if (readCookie(WAF_RESUME_COOKIE) !== "1") return;
    clearCookie(WAF_RESUME_COOKIE);
    const pending = consumeWafPending();
    if (!pending?.email) return;
    const emailInput = document.getElementById("reset-email");
    if (emailInput) emailInput.value = pending.email;
    await sendResetEmail(pending.email, options, true);
  }

  async function submitReset(path, identity, password, confirmPassword, showMessage, options) {
    if (!password || password.length < 8) {
      showMessage("\u5bc6\u7801\u81f3\u5c11\u9700\u8981 8 \u4e2a\u5b57\u7b26\u3002", true);
      return;
    }
    if (password !== confirmPassword) {
      showMessage("\u4e24\u6b21\u8f93\u5165\u7684\u5bc6\u7801\u4e0d\u4e00\u81f4\u3002", true);
      return;
    }
    try {
      const encrypted = await encryptPasswordPayload({ password, confirmPassword }, options);
      const response = await fetchWithPreAuth(options)(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...identity, ...encrypted })
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        showMessage(payload?.message || "\u91cd\u7f6e\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
        return;
      }
      showMessage(payload.message || "\u5bc6\u7801\u5df2\u91cd\u7f6e\u3002", false);
      setTimeout(() => {
        options.shellApi?.navigateTo?.("/shopping/user/log-in", { replace: true });
      }, 800);
    } catch (_) {
      showMessage("\u91cd\u7f6e\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
    }
  }

  async function encryptPasswordPayload(payload, options) {
    const keyResponse = await fetchWithPreAuth(options)(CRYPTO_KEY_PATH, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}"
    });
    const keyPayload = await parseJsonSafely(keyResponse);
    const cryptoPayload = keyPayload?.passwordCrypto;
    if (!keyResponse.ok || !cryptoPayload?.kid || !cryptoPayload?.publicKeyJwk) {
      throw new Error("password reset crypto key unavailable");
    }
    const cryptoKey = await globalThis.crypto.subtle.importKey(
      "jwk",
      cryptoPayload.publicKeyJwk,
      { name: "RSA-OAEP", hash: "SHA-256" },
      false,
      ["encrypt"]
    );
    const rawBytes = new TextEncoder().encode(JSON.stringify(payload));
    const encryptedBuffer = await globalThis.crypto.subtle.encrypt({ name: "RSA-OAEP" }, cryptoKey, rawBytes);
    return {
      kid: cryptoPayload.kid,
      payloadCipher: encodeBase64Url(new Uint8Array(encryptedBuffer)),
      nonce: randomToken(24),
      timestamp: Date.now()
    };
  }

  function startCooldown(durationMs) {
    const effectiveMs = Math.max(0, Number(durationMs || 0));
    if (cooldownTimer) {
      clearInterval(cooldownTimer);
      cooldownTimer = null;
    }
    if (effectiveMs <= 0) {
      setSendButtonsDisabled(false);
      return;
    }
    const endsAt = Date.now() + effectiveMs;
    setSendButtonsDisabled(true, Math.ceil(effectiveMs / 1000));
    cooldownTimer = setInterval(() => {
      const remainingSeconds = Math.ceil(Math.max(0, endsAt - Date.now()) / 1000);
      if (remainingSeconds <= 0) {
        clearInterval(cooldownTimer);
        cooldownTimer = null;
        setSendButtonsDisabled(false);
        return;
      }
      setSendButtonsDisabled(true, remainingSeconds);
    }, 1000);
  }

  function setSendButtonsDisabled(disabled, remainingSeconds = 0) {
    setButtonState("btn-reset-code", disabled, disabled ? `${remainingSeconds}s` : "\u53d1\u9001\u9a8c\u8bc1\u7801");
  }

  function setButtonState(id, disabled, text) {
    const button = document.getElementById(id);
    if (!button) return;
    button.disabled = Boolean(disabled);
    button.querySelectorAll(".btn-text, .btn-hover-content span").forEach((node) => {
      node.textContent = text;
    });
  }

  function buildJsonHeaders(wafResume) {
    const headers = { "Content-Type": "application/json" };
    if (wafResume) {
      headers[WAF_RESUME_HEADER] = "1";
    }
    return headers;
  }

  function fetchWithPreAuth(options) {
    return options.preAuthClientApi?.fetchWithPreAuth || window.ShoppingPreAuthClient?.fetchWithPreAuth || fetch;
  }

  function readEmail() {
    return document.getElementById("reset-email")?.value?.trim() || "";
  }

  function currentResetToken() {
    try {
      return new URL(window.location.href).searchParams.get("token") || "";
    } catch (_) {
      return "";
    }
  }

  function currentResetMode() {
    try {
      const pathname = new URL(window.location.href).pathname;
      return pathname === "/shopping/user/reset-password-code" ? "code" : "url";
    } catch (_) {
      return "url";
    }
  }

  function showRequestMessage(message, isError) {
    showMessage("reset-request-msg", message, isError);
  }

  function showLinkMessage(message, isError) {
    showMessage("reset-link-msg", message, isError);
  }

  function showMessage(id, message, isError) {
    const node = document.getElementById(id);
    if (!node) return;
    node.textContent = message || "";
    node.style.display = message ? "block" : "none";
    node.style.color = isError ? "" : "#166534";
  }

  async function parseJsonSafely(response) {
    try {
      return await response.json();
    } catch (_) {
      return null;
    }
  }

  function persistWafPending(payload) {
    try {
      sessionStorage.setItem(WAF_PENDING_KEY, JSON.stringify(payload));
    } catch (_) {
    }
  }

  function consumeWafPending() {
    try {
      const raw = sessionStorage.getItem(WAF_PENDING_KEY);
      sessionStorage.removeItem(WAF_PENDING_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_) {
      return null;
    }
  }

  function readCookie(name) {
    const target = `${name}=`;
    return (document.cookie || "").split(";").map((item) => item.trim())
      .find((item) => item.startsWith(target))?.substring(target.length) || "";
  }

  function clearCookie(name) {
    document.cookie = `${name}=; Max-Age=0; Path=/; SameSite=Lax`;
  }

  function randomToken(length) {
    const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    const bytes = new Uint8Array(length);
    globalThis.crypto.getRandomValues(bytes);
    return Array.from(bytes, (value) => alphabet[value % alphabet.length]).join("");
  }

  function encodeBase64Url(bytes) {
    let binary = "";
    bytes.forEach((byte) => {
      binary += String.fromCharCode(byte);
    });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  }

  return {
    initializePasswordResetFragment
  };
});
