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
  const WAF_REQUIRED_ERROR_CODE = "PREAUTH_IP_CHANGED_WAF_REQUIRED";
  const WAF_PENDING_REQUEST_KEY = "shopping.preauth.waf.pending-request";
  const WAF_REPLAY_EVENT_NAME = "shopping:preauth:waf-request-replayed";

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
  let lastBootstrapPayload = null;

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

  function isBrowserRuntime() {
    return typeof window !== "undefined" && typeof sessionStorage !== "undefined";
  }

  function toSerializableHeaders(headers) {
    const serialized = {};
    try {
      const normalized = new Headers(headers || {});
      normalized.forEach((value, key) => {
        serialized[key] = value;
      });
    } catch (_) {
      return {};
    }
    return serialized;
  }

  function toSerializableBody(body) {
    if (body == null) {
      return null;
    }
    if (typeof body === "string") {
      return body;
    }
    if (body instanceof URLSearchParams) {
      return body.toString();
    }
    return null;
  }

  function sanitizeReplayOptions(sourceOptions = {}) {
    const replay = {};
    const method = sourceOptions.method ? String(sourceOptions.method) : "GET";
    replay.method = method;
    replay.credentials = sourceOptions.credentials || "same-origin";
    replay.headers = sourceOptions.headers || {};
    replay.body = sourceOptions.body == null ? null : sourceOptions.body;
    return replay;
  }

  function persistWafPendingRequest(url, options = {}) {
    if (!isBrowserRuntime()) {
      return;
    }
    const serializedBody = toSerializableBody(options.body);
    if (options.body != null && serializedBody == null) {
      return;
    }
    const payload = {
      url: String(url || ""),
      options: {
        method: options.method || "GET",
        credentials: options.credentials || "same-origin",
        headers: toSerializableHeaders(options.headers),
        body: serializedBody
      },
      savedAt: Date.now()
    };
    sessionStorage.setItem(WAF_PENDING_REQUEST_KEY, JSON.stringify(payload));
  }

  function consumeWafPendingRequest() {
    if (!isBrowserRuntime()) {
      return null;
    }
    const raw = sessionStorage.getItem(WAF_PENDING_REQUEST_KEY);
    if (!raw) {
      return null;
    }
    sessionStorage.removeItem(WAF_PENDING_REQUEST_KEY);
    try {
      return JSON.parse(raw);
    } catch (_) {
      return null;
    }
  }

  function buildDefaultWafVerifyUrl() {
    if (!isBrowserRuntime()) {
      return "/shopping/auth/waf/verify";
    }
    const currentPath = `${window.location.pathname || "/"}${window.location.search || ""}`;
    return `/shopping/auth/waf/verify?return=${encodeURIComponent(currentPath)}`;
  }

  function buildWafReplayEventDetail(url, response, payload, errorMessage = "") {
    return {
      url,
      ok: Boolean(response?.ok),
      status: Number(response?.status || 0),
      payload: payload || null,
      error: errorMessage || ""
    };
  }

  function emitWafReplayEvent(detail) {
    if (!isBrowserRuntime() || typeof window.dispatchEvent !== "function") {
      return;
    }
    try {
      window.dispatchEvent(new CustomEvent(WAF_REPLAY_EVENT_NAME, { detail }));
    } catch (_) {
    }
  }

  function stripWafVerifiedQueryFlag() {
    if (!isBrowserRuntime() || !window.history || typeof window.history.replaceState !== "function") {
      return;
    }
    try {
      const current = new URL(window.location.href);
      if (!current.searchParams.has("waf_verified")) {
        return;
      }
      current.searchParams.delete("waf_verified");
      const nextPath = `${current.pathname}${current.search}${current.hash}`;
      window.history.replaceState(null, "", nextPath);
    } catch (_) {
    }
  }

  function shouldReplayPendingRequestAfterWaf() {
    if (!isBrowserRuntime()) {
      return false;
    }
    try {
      const current = new URL(window.location.href);
      return current.searchParams.get("waf_verified") === "1";
    } catch (_) {
      return false;
    }
  }

  async function replayPendingRequestAfterWaf() {
    if (!shouldReplayPendingRequestAfterWaf()) {
      return;
    }
    const pending = consumeWafPendingRequest();
    stripWafVerifiedQueryFlag();
    if (!pending || !pending.url) {
      return;
    }
    try {
      const replayOptions = sanitizeReplayOptions(pending.options || {});
      const response = await fetchWithPreAuth(pending.url, replayOptions);
      const payload = await parseJsonSafely(response.clone());
      emitWafReplayEvent(buildWafReplayEventDetail(pending.url, response, payload));
    } catch (error) {
      emitWafReplayEvent(buildWafReplayEventDetail(
        pending.url,
        null,
        null,
        error && error.message ? String(error.message) : "replay_failed"
      ));
    }
  }

  function redirectToWafVerify(verifyUrl, url, options) {
    if (!isBrowserRuntime()) {
      return;
    }
    persistWafPendingRequest(url, options || {});
    const finalUrl = (verifyUrl && String(verifyUrl).trim()) || buildDefaultWafVerifyUrl();
    window.location.assign(finalUrl);
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
      if (lastBootstrapPayload && typeof lastBootstrapPayload === "object") {
        return {
          ...lastBootstrapPayload,
          fromCache: true
        };
      }
      return { success: true, fromCache: true };
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
      if (response.status === 409) {
        const payload = await parseJsonSafely(response) || {};
        const errorCode = payload && payload.error ? String(payload.error) : "";
        if (errorCode === WAF_REQUIRED_ERROR_CODE) {
          if (isBrowserRuntime()) {
            const verifyUrl = payload && payload.verifyUrl ? String(payload.verifyUrl).trim() : "";
            window.location.assign(verifyUrl || buildDefaultWafVerifyUrl());
          }
          return payload;
        }
      }
      if (!response.ok) {
        throw new Error(`preauth bootstrap failed: ${response.status}`);
      }

      const payload = await parseJsonSafely(response) || {};
      bootstrapped = true;
      lastBootstrapPayload = payload;
      return payload;
    })();

    try {
      return await bootstrapTask;
    } finally {
      bootstrapTask = null;
    }
  }

  function resolvePasswordCryptoKey(payload) {
    const cryptoPayload = payload && typeof payload === "object" ? payload.passwordCrypto : null;
    if (!cryptoPayload || typeof cryptoPayload !== "object") {
      return null;
    }
    const kid = cryptoPayload.kid ? String(cryptoPayload.kid).trim() : "";
    const publicKeyJwk = cryptoPayload.publicKeyJwk;
    if (!kid || !publicKeyJwk || typeof publicKeyJwk !== "object") {
      return null;
    }
    return {
      kid,
      alg: cryptoPayload.alg ? String(cryptoPayload.alg) : "",
      publicKeyJwk,
      expiresAtEpochMillis: Number(cryptoPayload.expiresAtEpochMillis || 0)
    };
  }

  async function fetchRegisterPasswordCryptoKey(forceRefresh = true) {
    const payload = await bootstrapPreAuthToken(Boolean(forceRefresh));
    const key = resolvePasswordCryptoKey(payload);
    if (key) {
      return key;
    }
    if (!forceRefresh) {
      return fetchRegisterPasswordCryptoKey(true);
    }
    throw new Error("register password crypto key unavailable");
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
    if (!bootstrapped) {
      try {
        await bootstrapPreAuthToken(false);
      } catch (_) {
      }
      applyCsrfHeader(requestOptions.headers);
    }

    let response = await getNativeFetch()(url, requestOptions);
    if (response.status === 409) {
      const wafPayload = await parseJsonSafely(response.clone());
      const errorCode = wafPayload && wafPayload.error ? String(wafPayload.error) : "";
      if (errorCode === WAF_REQUIRED_ERROR_CODE) {
        redirectToWafVerify(wafPayload?.verifyUrl, url, options);
      }
      return response;
    }
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

  if (isBrowserRuntime()) {
    replayPendingRequestAfterWaf().catch(() => {
    });
  }

  return {
    HEADER_PREAUTH_TOKEN,
    HEADER_DEVICE_FINGERPRINT,
    WAF_REPLAY_EVENT_NAME,
    buildDeviceFingerprint,
    bootstrapPreAuthToken,
    fetchRegisterPasswordCryptoKey,
    fetchWithPreAuth,
    readStoredToken,
    writeStoredToken
  };
});
