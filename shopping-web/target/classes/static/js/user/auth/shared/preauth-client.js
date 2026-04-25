(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingPreAuthClient = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const HEADER_PREAUTH_TOKEN = "X-Pre-Auth-Token"; // legacy, no longer used by default
  const HEADER_DEVICE_FINGERPRINT = "X-Device-Fingerprint";
  const HEADER_CSRF_TOKEN = "X-XSRF-TOKEN";
  const COOKIE_CSRF_TOKEN = "XSRF-TOKEN";

  const PREAUTH_BOOTSTRAP_URL = "/shopping/auth/preauth/bootstrap";

  const PREAUTH_REFRESHABLE_ERRORS = new Set([
    "PREAUTH_MISSING",
    "PREAUTH_EXPIRED",
    "PREAUTH_INVALID",
    "PREAUTH_FINGERPRINT_MISMATCH",
    "PREAUTH_UA_MISMATCH"
  ]);

  let bootstrapTask = null;
  let bootstrapped = false;

  function getNativeFetch() {
    if (typeof fetch !== "function") {
      throw new Error("fetch is not available");
    }
    return fetch.bind(globalThis);
  }

  function buildDeviceFingerprint() {
    const userAgent = typeof navigator !== "undefined" ? navigator.userAgent || "unknown" : "unknown";
    const language = typeof navigator !== "undefined" ? navigator.language || "unknown" : "unknown";
    const platform = typeof navigator !== "undefined" ? navigator.platform || "unknown" : "unknown";
    const screenWidth = typeof screen !== "undefined" ? String(screen.width || 0) : "0";
    const screenHeight = typeof screen !== "undefined" ? String(screen.height || 0) : "0";
    const timeZone = typeof Intl !== "undefined"
      ? Intl.DateTimeFormat().resolvedOptions().timeZone || "unknown"
      : "unknown";
    return [userAgent, language, platform, screenWidth, screenHeight, timeZone].join("|");
  }

  async function parseJsonSafely(response) {
    try {
      return await response.json();
    } catch (_) {
      return null;
    }
  }

  function cloneOptions(options) {
    const source = options || {};
    const cloned = { ...source };
    cloned.headers = new Headers(source.headers || {});
    if (typeof cloned.credentials === "undefined" || cloned.credentials === null) {
      cloned.credentials = "same-origin";
    }
    return cloned;
  }

  function readCookieValue(name) {
    if (typeof document === "undefined" || !document.cookie) {
      return "";
    }
    const target = `${name}=`;
    const entries = document.cookie.split(";");
    for (let index = 0; index < entries.length; index += 1) {
      const item = entries[index].trim();
      if (item.startsWith(target)) {
        return decodeURIComponent(item.substring(target.length));
      }
    }
    return "";
  }

  function applyCsrfHeader(headers) {
    if (!headers || typeof headers.set !== "function") {
      return;
    }
    if (headers.has(HEADER_CSRF_TOKEN)) {
      return;
    }
    const csrfToken = readCookieValue(COOKIE_CSRF_TOKEN);
    if (csrfToken) {
      headers.set(HEADER_CSRF_TOKEN, csrfToken);
    }
  }

  async function bootstrapPreAuthToken(force = false) {
    if (bootstrapTask) {
      return bootstrapTask;
    }

    if (!force && bootstrapped) {
      return {
        success: true,
        fromCache: true
      };
    }

    bootstrapTask = (async () => {
      const requestHeaders = new Headers({
        "Content-Type": "application/json",
        [HEADER_DEVICE_FINGERPRINT]: buildDeviceFingerprint()
      });
      applyCsrfHeader(requestHeaders);

      const response = await getNativeFetch()(PREAUTH_BOOTSTRAP_URL, {
        method: "POST",
        headers: requestHeaders,
        body: "{}",
        credentials: "same-origin"
      });
      if (!response.ok) {
        throw new Error(`preauth bootstrap failed: ${response.status}`);
      }

      const payload = await parseJsonSafely(response) || {};
      bootstrapped = true;
      return payload;
    })();

    try {
      return await bootstrapTask;
    } finally {
      bootstrapTask = null;
    }
  }

  function shouldRetryAfterRefresh(responseStatus, payload) {
    if (responseStatus !== 401) {
      return false;
    }
    const errorCode = payload && payload.error ? String(payload.error) : "";
    return PREAUTH_REFRESHABLE_ERRORS.has(errorCode);
  }

  async function fetchWithPreAuth(url, options = {}) {
    const requestOptions = cloneOptions(options);
    requestOptions.headers.set(HEADER_DEVICE_FINGERPRINT, buildDeviceFingerprint());
    applyCsrfHeader(requestOptions.headers);

    let response = await getNativeFetch()(url, requestOptions);
    if (response.status !== 401) {
      return response;
    }

    const errorPayload = await parseJsonSafely(response.clone());
    if (!shouldRetryAfterRefresh(response.status, errorPayload)) {
      return response;
    }

    await bootstrapPreAuthToken(true);
    const retryOptions = cloneOptions(options);
    retryOptions.headers.set(HEADER_DEVICE_FINGERPRINT, buildDeviceFingerprint());
    applyCsrfHeader(retryOptions.headers);
    return getNativeFetch()(url, retryOptions);
  }

  // keep compatibility with old callers
  function readStoredToken() {
    return "";
  }

  function writeStoredToken() {
    // no-op, token is now carried by HttpOnly cookie
  }

  return {
    HEADER_PREAUTH_TOKEN,
    HEADER_DEVICE_FINGERPRINT,
    buildDeviceFingerprint,
    bootstrapPreAuthToken,
    fetchWithPreAuth,
    readStoredToken,
    writeStoredToken
  };
});
