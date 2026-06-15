(function (root) {
  const DEFAULT_PATH_PREFIXES = ["/shopping/"];
  const BLOCKED_SCHEMES = new Set(["javascript:", "vbscript:", "file:"]);
  const SAFE_DATA_IMAGE_PATTERN = /^data:image\/(?:png|jpe?g|gif|webp|bmp);base64,[a-z0-9+/=\s]+$/i;

  function normalizeText(value) {
    return String(value ?? "").trim();
  }

  function origin() {
    return root.location?.origin || "http://localhost";
  }

  function normalizePrefixes(prefixes) {
    const source = Array.isArray(prefixes) && prefixes.length ? prefixes : DEFAULT_PATH_PREFIXES;
    return source.map(normalizeText).filter(Boolean);
  }

  function normalizeHosts(hosts) {
    if (!Array.isArray(hosts)) {
      return [];
    }
    return hosts.map((host) => {
      const value = normalizeText(host).toLowerCase();
      if (!value) {
        return "";
      }
      try {
        if (value.startsWith("http://") || value.startsWith("https://")) {
          return normalizeText(new URL(value).hostname).toLowerCase();
        }
      } catch (_) {
        return "";
      }
      return value.startsWith(".") ? value.slice(1) : value;
    }).filter(Boolean);
  }

  function hasUnsafeUrlSyntax(value) {
    const lower = value.toLowerCase();
    return !value
      || /[\u0000-\u001f\u007f]/.test(value)
      || value.includes("\\")
      || lower.includes("%5c")
      || value.startsWith("//");
  }

  function isAllowedPath(path, prefixes) {
    return normalizePrefixes(prefixes).some((prefix) => path.startsWith(prefix));
  }

  function safeFallbackPath(fallback, prefixes) {
    const value = normalizeText(fallback);
    if (!value) {
      return "";
    }
    if (hasUnsafeUrlSyntax(value)) {
      return DEFAULT_PATH_PREFIXES[0];
    }
    try {
      const parsed = new URL(value, origin());
      if (parsed.origin !== origin()) {
        return DEFAULT_PATH_PREFIXES[0];
      }
      const path = `${parsed.pathname}${parsed.search}${parsed.hash}`;
      return isAllowedPath(path, prefixes) ? path : DEFAULT_PATH_PREFIXES[0];
    } catch (_) {
      return DEFAULT_PATH_PREFIXES[0];
    }
  }

  function safeSameOriginPath(value, fallback = DEFAULT_PATH_PREFIXES[0], allowedPrefixes = DEFAULT_PATH_PREFIXES) {
    const fallbackPath = safeFallbackPath(fallback, allowedPrefixes);
    const raw = normalizeText(value);
    if (hasUnsafeUrlSyntax(raw)) {
      return fallbackPath;
    }
    try {
      const parsed = new URL(raw, origin());
      if (parsed.origin !== origin()) {
        return fallbackPath;
      }
      const path = `${parsed.pathname}${parsed.search}${parsed.hash}`;
      return isAllowedPath(path, allowedPrefixes) ? path : fallbackPath;
    } catch (_) {
      return fallbackPath;
    }
  }

  function isAllowedHost(host, allowedHosts) {
    const normalizedHost = normalizeText(host).toLowerCase();
    if (!normalizedHost) {
      return false;
    }
    return normalizeHosts(allowedHosts).some((allowedHost) => (
      normalizedHost === allowedHost || normalizedHost.endsWith(`.${allowedHost}`)
    ));
  }

  function isLocalHttp(parsed) {
    return parsed.protocol === "http:" && (parsed.hostname === "localhost" || parsed.hostname === "127.0.0.1");
  }

  function safeBlobUrl(value, fallback) {
    try {
      const inner = new URL(value.slice("blob:".length));
      return inner.origin === origin() ? value : fallback;
    } catch (_) {
      return fallback;
    }
  }

  function safeImageUrl(value, fallback = "", options = {}) {
    const raw = normalizeText(value);
    if (hasUnsafeUrlSyntax(raw)) {
      return fallback;
    }
    const lower = raw.toLowerCase();
    if (BLOCKED_SCHEMES.has(lower.slice(0, lower.indexOf(":") + 1))) {
      return fallback;
    }
    const allowData = options.allowData !== false;
    if (lower.startsWith("data:")) {
      return allowData && SAFE_DATA_IMAGE_PATTERN.test(raw) ? raw : fallback;
    }
    const allowBlob = options.allowBlob !== false;
    if (lower.startsWith("blob:")) {
      return allowBlob ? safeBlobUrl(raw, fallback) : fallback;
    }
    if (raw.startsWith("/")) {
      return safeSameOriginPath(raw, fallback, options.allowedPathPrefixes || DEFAULT_PATH_PREFIXES);
    }
    try {
      const parsed = new URL(raw, origin());
      if (parsed.origin === origin()) {
        return safeSameOriginPath(`${parsed.pathname}${parsed.search}${parsed.hash}`, fallback, options.allowedPathPrefixes || DEFAULT_PATH_PREFIXES);
      }
      if (parsed.protocol === "https:") {
        const allowedHosts = normalizeHosts(options.allowedHosts);
        if (options.allowAnyHttps !== false || isAllowedHost(parsed.hostname, allowedHosts)) {
          return parsed.href;
        }
      }
      if (options.allowLocalHttp !== false && isLocalHttp(parsed)) {
        return parsed.href;
      }
      return fallback;
    } catch (_) {
      return fallback;
    }
  }

  function safeExternalHttpsUrl(value, allowedHosts, fallback = "") {
    const raw = normalizeText(value);
    if (hasUnsafeUrlSyntax(raw)) {
      return fallback;
    }
    try {
      const parsed = new URL(raw);
      if (parsed.protocol !== "https:" || !isAllowedHost(parsed.hostname, allowedHosts)) {
        return fallback;
      }
      return parsed.href;
    } catch (_) {
      return fallback;
    }
  }

  root.ShoppingSecurityUrls = {
    safeSameOriginPath,
    safeImageUrl,
    safeExternalHttpsUrl
  };
})(typeof globalThis !== "undefined" ? globalThis : window);
