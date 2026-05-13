(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingPreAuthClient = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const HEADER_PREAUTH_TOKEN = "X-Pre-Auth-Token"; // legacy, no longer used by default
  const HEADER_DEVICE_FINGERPRINT = "X-Device-Fingerprint";
  const HEADER_WEBRTC_IP = "X-WebRTC-IP";
  const HEADER_WEBRTC_IPS = "X-WebRTC-IPs";
  const HEADER_WEBRTC_STATUS = "X-WebRTC-Status";
  const HEADER_CSRF_TOKEN = "X-XSRF-TOKEN";
  const COOKIE_CSRF_TOKEN = "XSRF-TOKEN";
  const WAF_REQUIRED_ERROR_CODE = "PREAUTH_IP_CHANGED_WAF_REQUIRED";
  const PHONE_BINDING_REQUIRED_ERROR_CODE = "PHONE_BINDING_REQUIRED";
  const PHONE_BINDING_PATH = "/shopping/user/security/phone";
  const NETWORK_CHECK_FAILED_PATH = "/shopping/auth/network-check-failed";
  const USER_LOGIN_PATH = "/shopping/user/log-in";
  const WEBRTC_ERROR_CODES = new Set(["WEBRTC_IP_MISMATCH", "WEBRTC_SIGNAL_REQUIRED"]);
  const WAF_PENDING_REQUEST_KEY = "shopping.preauth.waf.pending-request";
  const WAF_REPLAY_EVENT_NAME = "shopping:preauth:waf-request-replayed";
  const DEVICE_SEED_KEY = "shopping.preauth.device-seed";
  const WEBRTC_SIGNAL_TTL_MILLIS = 60_000;
  const WEBRTC_SIGNAL_TIMEOUT_MILLIS = 1_200;

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
  let inMemoryDeviceSeed = null;
  let webRtcSignalTask = null;
  let cachedWebRtcSignal = null;
  let cachedWebRtcSignalAt = 0;

  function getNativeFetch() {
    if (typeof fetch !== "function") {
      throw new Error("fetch is not available");
    }
    return fetch.bind(globalThis);
  }

  function buildDeviceFingerprint() {
    const nav = typeof navigator !== "undefined" ? navigator : null;
    const screenInfo = typeof screen !== "undefined" ? screen : null;
    const parts = [
      "v3",
      readOrCreateDeviceSeed(),
      buildLanguageFingerprint(nav),
      buildTimeZoneFingerprint(),
      String(screenInfo?.colorDepth || 0),
      String(nav?.hardwareConcurrency || 0),
      String(nav?.deviceMemory || "unknown"),
      buildWebglRendererFingerprint()
    ];
    return parts.map(encodeFingerprintPart).join("/");
  }

  function readOrCreateDeviceSeed() {
    if (inMemoryDeviceSeed) {
      return inMemoryDeviceSeed;
    }
    if (isBrowserRuntime() && typeof localStorage !== "undefined") {
      try {
        const existing = localStorage.getItem(DEVICE_SEED_KEY);
        if (existing) {
          inMemoryDeviceSeed = existing;
          return existing;
        }
        inMemoryDeviceSeed = createDeviceSeed();
        localStorage.setItem(DEVICE_SEED_KEY, inMemoryDeviceSeed);
        return inMemoryDeviceSeed;
      } catch (_) {
      }
    }
    inMemoryDeviceSeed = createDeviceSeed();
    return inMemoryDeviceSeed;
  }

  function createDeviceSeed() {
    const cryptoApi = globalThis?.crypto;
    if (cryptoApi && typeof cryptoApi.randomUUID === "function") {
      return cryptoApi.randomUUID();
    }
    if (cryptoApi && typeof cryptoApi.getRandomValues === "function") {
      const bytes = new Uint8Array(16);
      cryptoApi.getRandomValues(bytes);
      return Array.from(bytes, (item) => item.toString(16).padStart(2, "0")).join("");
    }
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  }

  function buildLanguageFingerprint(nav) {
    if (!nav) {
      return "unknown";
    }
    const primary = nav.language || "unknown";
    const languages = Array.isArray(nav.languages) && nav.languages.length > 0
      ? nav.languages.join(",")
      : primary;
    return `${primary};${languages}`;
  }

  function buildTimeZoneFingerprint() {
    try {
      return typeof Intl !== "undefined"
        ? Intl.DateTimeFormat().resolvedOptions().timeZone || "unknown"
        : "unknown";
    } catch (_) {
      return "unknown";
    }
  }

  function buildWebglRendererFingerprint() {
    if (typeof document === "undefined") {
      return "unknown";
    }
    try {
      const canvas = document.createElement("canvas");
      const gl = canvas.getContext("webgl") || canvas.getContext("experimental-webgl");
      if (!gl) {
        return "unknown";
      }
      const debugInfo = gl.getExtension("WEBGL_debug_renderer_info");
      if (debugInfo) {
        const unmaskedRenderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL);
        if (unmaskedRenderer) {
          return String(unmaskedRenderer);
        }
      }
      const renderer = gl.getParameter(gl.RENDERER);
      return renderer ? String(renderer) : "unknown";
    } catch (_) {
      return "unknown";
    }
  }

  function encodeFingerprintPart(value) {
    return encodeURIComponent(String(value ?? "unknown"));
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
      handlePhoneBindingRequiredPayload(payload);
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

  function redirectToPhoneBinding(payload) {
    if (!isBrowserRuntime()) {
      return;
    }
    const target = payload?.redirectPath || PHONE_BINDING_PATH;
    if (window.location.pathname === PHONE_BINDING_PATH) {
      return;
    }
    window.location.assign(target);
  }

  function handlePhoneBindingRequiredPayload(payload) {
    const errorCode = payload && payload.error ? String(payload.error) : "";
    if (errorCode !== PHONE_BINDING_REQUIRED_ERROR_CODE) {
      return false;
    }
    redirectToPhoneBinding(payload);
    return true;
  }

  function isNetworkCheckFailurePayload(payload) {
    const errorCode = payload && (payload.error || payload.code) ? String(payload.error || payload.code) : "";
    return WEBRTC_ERROR_CODES.has(errorCode);
  }

  function buildCurrentPath(fallbackPath) {
    if (!isBrowserRuntime()) {
      return fallbackPath;
    }
    const path = `${window.location.pathname || "/"}${window.location.search || ""}`;
    if (!path.startsWith("/") || path.startsWith("//") || path.startsWith(NETWORK_CHECK_FAILED_PATH)) {
      return fallbackPath;
    }
    return path;
  }

  function sanitizeNetworkCheckUrl(rawUrl, scope) {
    const fallbackPath = scope === "admin" ? "/shopping/admin/login" : USER_LOGIN_PATH;
    const currentPath = buildCurrentPath(fallbackPath);
    const fallbackUrl = `${NETWORK_CHECK_FAILED_PATH}?scope=${encodeURIComponent(scope)}&path=${encodeURIComponent(currentPath)}`;
    const value = String(rawUrl || "").trim();
    if (!value) {
      return fallbackUrl;
    }
    try {
      const parsed = new URL(value, window.location.origin);
      if (parsed.origin !== window.location.origin || parsed.pathname !== NETWORK_CHECK_FAILED_PATH) {
        return fallbackUrl;
      }
      return `${parsed.pathname}${parsed.search}${parsed.hash}`;
    } catch (_) {
      return fallbackUrl;
    }
  }

  function redirectToNetworkCheckFailed(payload, scope = "user") {
    if (!isBrowserRuntime() || window.location.pathname === NETWORK_CHECK_FAILED_PATH) {
      return;
    }
    window.location.replace(sanitizeNetworkCheckUrl(payload?.networkCheckUrl, scope));
  }

  function handleNetworkCheckFailurePayload(payload, scope = "user") {
    if (!isNetworkCheckFailurePayload(payload)) {
      return false;
    }
    redirectToNetworkCheckFailed(payload, scope);
    return true;
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

  async function applyWebRtcHeaders(headers) {
    if (!headers || typeof headers.set !== "function") {
      return;
    }
    const signal = await resolveWebRtcSignal();
    headers.set(HEADER_WEBRTC_STATUS, signal.status || "error");
    if (signal.ip) {
      headers.set(HEADER_WEBRTC_IP, signal.ip);
    }
    if (Array.isArray(signal.ips) && signal.ips.length > 0) {
      headers.set(HEADER_WEBRTC_IPS, signal.ips.join(","));
    }
  }

  async function resolveWebRtcSignal() {
    const now = Date.now();
    if (cachedWebRtcSignal && now - cachedWebRtcSignalAt < WEBRTC_SIGNAL_TTL_MILLIS) {
      return cachedWebRtcSignal;
    }
    if (webRtcSignalTask) {
      return webRtcSignalTask;
    }
    webRtcSignalTask = detectWebRtcSignal()
      .then((signal) => {
        cachedWebRtcSignal = normalizeWebRtcSignal(signal);
        cachedWebRtcSignalAt = Date.now();
        return cachedWebRtcSignal;
      })
      .catch(() => {
        cachedWebRtcSignal = { ip: "", ips: [], status: "error" };
        cachedWebRtcSignalAt = Date.now();
        return cachedWebRtcSignal;
      })
      .finally(() => {
        webRtcSignalTask = null;
      });
    return webRtcSignalTask;
  }

  function detectWebRtcSignal() {
    return new Promise((resolve) => {
      if (!isBrowserRuntime()) {
        resolve({ ip: "", status: "unsupported" });
        return;
      }

      const PeerConnection = window.RTCPeerConnection
        || window.webkitRTCPeerConnection
        || window.mozRTCPeerConnection;
      if (typeof PeerConnection !== "function") {
        resolve({ ip: "", status: "unsupported" });
        return;
      }

      let peer = null;
      let done = false;
      let lastPrivateCandidate = false;
      const publicIps = [];
      const addPublicIp = (ip) => {
        if (ip && !publicIps.includes(ip)) {
          publicIps.push(ip);
        }
      };
      const buildCurrentSignal = (fallbackStatus) => {
        if (publicIps.length > 0) {
          return { ip: publicIps[0], ips: publicIps.slice(), status: "ok" };
        }
        return { ip: "", ips: [], status: lastPrivateCandidate ? "private_only" : fallbackStatus };
      };
      const finish = (signal) => {
        if (done) {
          return;
        }
        done = true;
        try {
          if (peer && typeof peer.close === "function") {
            peer.close();
          }
        } catch (_) {
        }
        resolve(normalizeWebRtcSignal(signal));
      };
      const timer = window.setTimeout(() => {
        finish(buildCurrentSignal("timeout"));
      }, WEBRTC_SIGNAL_TIMEOUT_MILLIS);

      const finishOnce = (signal) => {
        window.clearTimeout(timer);
        finish(signal);
      };

      try {
        peer = new PeerConnection({
          iceServers: [
            { urls: ["stun:stun.l.google.com:19302", "stun:global.stun.twilio.com:3478"] }
          ],
          iceCandidatePoolSize: 0
        });
        if (typeof peer.createDataChannel === "function") {
          peer.createDataChannel("preauth");
        }
        peer.onicecandidate = (event) => {
          const candidate = event && event.candidate ? String(event.candidate.candidate || "") : "";
          if (!candidate) {
            finishOnce(buildCurrentSignal("timeout"));
            return;
          }
          const extracted = extractPublicIpsFromCandidate(candidate);
          if (extracted.length > 0) {
            extracted.forEach(addPublicIp);
          }
          if (extractAnyIpFromCandidate(candidate)) {
            lastPrivateCandidate = true;
          }
        };
        peer.createOffer()
          .then((offer) => peer.setLocalDescription(offer))
          .catch(() => finishOnce(buildCurrentSignal("error")));
      } catch (_) {
        finishOnce(buildCurrentSignal("error"));
      }
    });
  }

  function normalizeWebRtcSignal(signal) {
    const status = signal && signal.status ? String(signal.status).trim().toLowerCase() : "error";
    const ips = normalizeIpList(signal && signal.ips);
    const primaryIp = normalizeIpLiteral(signal && signal.ip ? String(signal.ip) : "");
    if (primaryIp && !ips.includes(primaryIp)) {
      ips.unshift(primaryIp);
    }
    const ip = primaryIp || ips[0] || "";
    if (!ip && status === "ok") {
      return { ip: "", ips: [], status: "private_only" };
    }
    if (["ok", "timeout", "unsupported", "private_only", "error"].includes(status)) {
      return { ip, ips, status };
    }
    return { ip, ips, status: "error" };
  }

  function normalizeIpList(rawIps) {
    const source = Array.isArray(rawIps) ? rawIps : String(rawIps || "").split(/[,\s]+/);
    const ips = [];
    for (let index = 0; index < source.length; index += 1) {
      const normalized = normalizeIpLiteral(source[index]);
      if (normalized && !ips.includes(normalized)) {
        ips.push(normalized);
      }
    }
    return ips;
  }

  function extractPublicIpFromCandidate(candidate) {
    return extractPublicIpsFromCandidate(candidate)[0] || "";
  }

  function extractPublicIpsFromCandidate(candidate) {
    const ips = extractIpCandidates(candidate);
    const publicIps = [];
    for (let index = 0; index < ips.length; index += 1) {
      const normalized = normalizeIpLiteral(ips[index]);
      if (normalized && !publicIps.includes(normalized)) {
        publicIps.push(normalized);
      }
    }
    return publicIps;
  }

  function extractAnyIpFromCandidate(candidate) {
    return extractIpCandidates(candidate).length > 0;
  }

  function extractIpCandidates(candidate) {
    const source = String(candidate || "");
    const matches = source.match(/(\b\d{1,3}(?:\.\d{1,3}){3}\b|(?:[0-9a-fA-F]{1,4}:){2,}[0-9a-fA-F:.]{1,})/g);
    return matches || [];
  }

  function normalizeIpLiteral(rawIp) {
    let value = String(rawIp || "").trim().toLowerCase();
    if (!value) {
      return "";
    }
    if (value.startsWith("[") && value.includes("]")) {
      value = value.substring(1, value.indexOf("]"));
    }
    if (/^\d{1,3}(?:\.\d{1,3}){3}:\d+$/.test(value)) {
      value = value.substring(0, value.lastIndexOf(":"));
    }
    if (value.startsWith("::ffff:")) {
      value = value.substring("::ffff:".length);
    }
    if (!isIpLiteral(value) || isPrivateOrLocalIp(value)) {
      return "";
    }
    return value;
  }

  function isIpLiteral(value) {
    return /^\d{1,3}(?:\.\d{1,3}){3}$/.test(value) || /^[0-9a-f:]+$/.test(value);
  }

  function isPrivateOrLocalIp(value) {
    if (/^\d{1,3}(?:\.\d{1,3}){3}$/.test(value)) {
      const parts = value.split(".").map((part) => Number(part));
      if (parts.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
        return true;
      }
      const first = parts[0];
      const second = parts[1];
      return first === 10
        || first === 127
        || first === 0
        || (first === 169 && second === 254)
        || (first === 172 && second >= 16 && second <= 31)
        || (first === 192 && second === 168);
    }
    return value === "::1"
      || value.startsWith("fc")
      || value.startsWith("fd")
      || value.startsWith("fe80:");
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
      await applyWebRtcHeaders(requestHeaders);

      const response = await getNativeFetch()(PREAUTH_BOOTSTRAP_URL, {
        method: "POST",
        headers: requestHeaders,
        body: "{}",
        credentials: "same-origin"
      });
      if (response.status === 403) {
        const payload = await parseJsonSafely(response.clone()) || {};
        if (handleNetworkCheckFailurePayload(payload)) {
          return payload;
        }
      }
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
    await applyWebRtcHeaders(requestOptions.headers);
    if (!bootstrapped) {
      try {
        await bootstrapPreAuthToken(false);
      } catch (_) {
      }
      applyCsrfHeader(requestOptions.headers);
    }

    let response = await getNativeFetch()(url, requestOptions);
    if (response.status === 403) {
      const networkPayload = await parseJsonSafely(response.clone());
      handleNetworkCheckFailurePayload(networkPayload);
      return response;
    }
    if (response.status === 409) {
      const wafPayload = await parseJsonSafely(response.clone());
      const errorCode = wafPayload && wafPayload.error ? String(wafPayload.error) : "";
      if (errorCode === WAF_REQUIRED_ERROR_CODE) {
        redirectToWafVerify(wafPayload?.verifyUrl, url, options);
      }
      return response;
    }
    if (response.status === 428) {
      const phoneBindingPayload = await parseJsonSafely(response.clone());
      handlePhoneBindingRequiredPayload(phoneBindingPayload);
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
    await applyWebRtcHeaders(retryOptions.headers);
    response = await getNativeFetch()(url, retryOptions);
    if (response.status === 403) {
      const networkPayload = await parseJsonSafely(response.clone());
      handleNetworkCheckFailurePayload(networkPayload);
    }
    return response;
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
    HEADER_WEBRTC_IP,
    HEADER_WEBRTC_IPS,
    HEADER_WEBRTC_STATUS,
    WAF_REPLAY_EVENT_NAME,
    buildDeviceFingerprint,
    bootstrapPreAuthToken,
    fetchRegisterPasswordCryptoKey,
    fetchWithPreAuth,
    readStoredToken,
    writeStoredToken
  };
});
