(function (root) {
  const CSRF_COOKIE = "XSRF-TOKEN";
  const CSRF_HEADER = "X-XSRF-TOKEN";
  const WEBRTC_IPS_HEADER = "X-WebRTC-IPs";
  const WEBRTC_STATUS_HEADER = "X-WebRTC-Status";
  const WEBRTC_SIGNAL_TTL_MILLIS = 60_000;
  const WEBRTC_SIGNAL_TIMEOUT_MILLIS = 8_000;
  const WEBRTC_REPORT_URL = "/shopping/admin/session/webrtc/report";
  const WEBRTC_STATE_URL = "/shopping/admin/session/webrtc/state";
  const NETWORK_CHECK_FAILED_PATH = "/shopping/auth/network-check-failed";
  const WAF_VERIFY_PATH = "/shopping/auth/waf/verify";
  const ADMIN_LOGIN_PATH = "/shopping/admin/login";
  const PASSWORD_CRYPTO_KEY_PATH = "/shopping/admin/password-crypto/key";
  const PASSWORD_CRYPTO_ERROR_MESSAGE = "Password encryption is unavailable, please refresh and try again.";
  const WEBRTC_ERROR_CODES = new Set(["WEBRTC_IP_MISMATCH", "WEBRTC_SIGNAL_REQUIRED", "WEBRTC_SIGNAL_UNVERIFIED"]);
  const ADMIN_WAF_REQUIRED_ERROR_CODE = "ADMIN_IP_CHANGED_WAF_REQUIRED";
  const NETWORK_DEBUG_STORAGE_KEY = "shopping:admin:network-debug";

  let webRtcSignalTask = null;
  let cachedWebRtcSignal = null;
  let cachedWebRtcSignalAt = 0;
  let webRtcReportTask = null;
  let lastReportedWebRtcSignature = "";
  let lastReportedWebRtcAt = 0;

  function readCookie(name) {
    if (!document.cookie || !name) {
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

  function isWebCryptoAvailable() {
    return !!window.crypto
      && !!window.crypto.subtle
      && typeof window.crypto.getRandomValues === "function"
      && typeof TextEncoder === "function";
  }

  function encodeBase64Url(bytes) {
    if (!bytes) {
      return "";
    }
    let binary = "";
    const chunkSize = 0x8000;
    for (let offset = 0; offset < bytes.length; offset += chunkSize) {
      const chunk = bytes.subarray(offset, offset + chunkSize);
      binary += String.fromCharCode.apply(null, chunk);
    }
    return btoa(binary)
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/g, "");
  }

  function buildPasswordCryptoNonce() {
    if (!isWebCryptoAvailable()) {
      throw new Error(PASSWORD_CRYPTO_ERROR_MESSAGE);
    }
    const nonceBytes = new Uint8Array(18);
    window.crypto.getRandomValues(nonceBytes);
    return encodeBase64Url(nonceBytes);
  }

  async function buildHeaders(extraHeaders = {}) {
    const headers = new Headers(extraHeaders);
    headers.set("Accept", "application/json");
    headers.set("X-Requested-With", "XMLHttpRequest");
    if (!headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    const csrfToken = readCookie(CSRF_COOKIE);
    if (csrfToken && !headers.has(CSRF_HEADER)) {
      headers.set(CSRF_HEADER, csrfToken);
    }
    applyWebRtcHeaders(headers);
    return headers;
  }

  function applyWebRtcHeaders(headers) {
    const signal = currentCachedWebRtcSignal();
    if (signal) {
      headers.set(WEBRTC_STATUS_HEADER, signal.status || "error");
      if (Array.isArray(signal.ips) && signal.ips.length > 0) {
        headers.set(WEBRTC_IPS_HEADER, signal.ips.join(","));
      }
    }
    scheduleWebRtcSignalReport();
  }

  function currentCachedWebRtcSignal() {
    const now = Date.now();
    if (cachedWebRtcSignal && now - cachedWebRtcSignalAt < WEBRTC_SIGNAL_TTL_MILLIS) {
      return cachedWebRtcSignal;
    }
    return null;
  }

  function scheduleWebRtcSignalReport() {
    if (!isBrowserRuntime()) {
      return;
    }
    resolveWebRtcSignal()
      .then((signal) => reportWebRtcSignal(signal))
      .catch(() => {
      });
  }

  function reportWebRtcSignal(signal) {
    const normalized = normalizeWebRtcSignal(signal);
    const signature = `${normalized.status || "error"}|${(normalized.ips || []).join(",")}`;
    const now = Date.now();
    if (signature === lastReportedWebRtcSignature && now - lastReportedWebRtcAt < WEBRTC_SIGNAL_TTL_MILLIS) {
      return;
    }
    if (webRtcReportTask) {
      return;
    }
    const headers = new Headers({
      "Accept": "application/json",
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest"
    });
    const csrfToken = readCookie(CSRF_COOKIE);
    if (csrfToken) {
      headers.set(CSRF_HEADER, csrfToken);
    }
    webRtcReportTask = fetch(WEBRTC_REPORT_URL, {
      method: "POST",
      headers,
      credentials: "same-origin",
      keepalive: true,
      body: JSON.stringify({
        webRtcIps: normalized.ips || [],
        webRtcStatus: normalized.status || "error",
        durationMillis: normalized.durationMillis == null ? null : normalized.durationMillis,
        diagnosticReason: normalized.diagnosticReason || ""
      })
    })
      .then((response) => {
        if (response.ok) {
          lastReportedWebRtcSignature = signature;
          lastReportedWebRtcAt = Date.now();
        }
      })
      .catch(() => {
      })
      .finally(() => {
        webRtcReportTask = null;
      });
  }

  async function resolveWebRtcSignal() {
    const now = Date.now();
    if (cachedWebRtcSignal && now - cachedWebRtcSignalAt < WEBRTC_SIGNAL_TTL_MILLIS) {
      return cachedWebRtcSignal;
    }
    if (webRtcSignalTask) {
      return webRtcSignalTask;
    }
    webRtcSignalTask = fetchReusableWebRtcStateSignal()
      .catch(() => null)
      .then((serverSignal) => serverSignal || detectWebRtcSignal())
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

  async function fetchReusableWebRtcStateSignal() {
    if (!isBrowserRuntime()) {
      return null;
    }
    const headers = new Headers({
      "Accept": "application/json",
      "X-Requested-With": "XMLHttpRequest"
    });
    const csrfToken = readCookie(CSRF_COOKIE);
    if (csrfToken) {
      headers.set(CSRF_HEADER, csrfToken);
    }
    const response = await fetch(WEBRTC_STATE_URL, {
      method: "GET",
      headers,
      credentials: "same-origin"
    });
    if (!response.ok) {
      return null;
    }
    const payload = await response.json().catch(() => null);
    if (!payload || payload.success !== true || payload.reusable !== true) {
      return null;
    }
    const ips = normalizeIpList(payload.webRtcIps);
    const status = payload.webRtcStatus ? String(payload.webRtcStatus).trim().toLowerCase() : "ok";
    if (status !== "ok" || ips.length === 0) {
      return null;
    }
    return {
      ip: ips[0],
      ips,
      status,
      durationMillis: 0,
      diagnosticReason: payload.reason || "server_state_reusable",
      serverReusable: true
    };
  }

  function detectWebRtcSignal() {
    return new Promise((resolve) => {
      const PeerConnection = window.RTCPeerConnection
        || window.webkitRTCPeerConnection
        || window.mozRTCPeerConnection;
      const diagnostics = createWebRtcDiagnostics(typeof PeerConnection === "function");
      if (typeof PeerConnection !== "function") {
        const unsupportedSignal = normalizeWebRtcSignal({
          ip: "",
          status: "unsupported",
          durationMillis: Date.now() - diagnostics.startedAt
        });
        unsupportedSignal.diagnosticReason = resolveWebRtcDiagnosticReason(unsupportedSignal, diagnostics);
        logWebRtcSignalDiagnostic(unsupportedSignal, diagnostics);
        resolve(unsupportedSignal);
        return;
      }

      let peer = null;
      let done = false;
      let lastPrivateCandidate = false;
      const publicIps = [];
      let timer = null;
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
        if (timer) {
          window.clearTimeout(timer);
        }
        const normalizedSignal = normalizeWebRtcSignal(signal);
        normalizedSignal.durationMillis = Date.now() - diagnostics.startedAt;
        normalizedSignal.diagnosticReason = resolveWebRtcDiagnosticReason(normalizedSignal, diagnostics);
        logWebRtcSignalDiagnostic(normalizedSignal, diagnostics);
        try {
          peer?.close?.();
        } catch (_) {
        }
        resolve(normalizedSignal);
      };
      timer = window.setTimeout(() => {
        diagnostics.timeoutReached = true;
        finish(buildCurrentSignal("timeout"));
      }, WEBRTC_SIGNAL_TIMEOUT_MILLIS);
      const finishOnce = (signal) => {
        window.clearTimeout(timer);
        finish(signal);
      };

      try {
        peer = new PeerConnection({
          iceServers: [
            {
              urls: [
                "stun:stun.cloudflare.com:3478",
                "stun:stun.nextcloud.com:3478",
                "stun:stun.ping0.cc:3478",
                "stun:stun.l.google.com:19302",
                "stun:global.stun.twilio.com:3478"
              ]
            }
          ],
          iceCandidatePoolSize: 0
        });
        peer.createDataChannel?.("admin-network-check");
        trackWebRtcState(diagnostics.iceGatheringStates, peer.iceGatheringState);
        trackWebRtcState(diagnostics.iceConnectionStates, peer.iceConnectionState);
        trackWebRtcState(diagnostics.connectionStates, peer.connectionState);
        peer.onicegatheringstatechange = () => {
          trackWebRtcState(diagnostics.iceGatheringStates, peer.iceGatheringState);
        };
        peer.oniceconnectionstatechange = () => {
          trackWebRtcState(diagnostics.iceConnectionStates, peer.iceConnectionState);
        };
        peer.onconnectionstatechange = () => {
          trackWebRtcState(diagnostics.connectionStates, peer.connectionState);
        };
        peer.onicecandidateerror = (event) => {
          recordWebRtcCandidateError(diagnostics, event);
        };
        peer.onicecandidate = (event) => {
          const candidate = event?.candidate?.candidate ? String(event.candidate.candidate) : "";
          if (!candidate) {
            diagnostics.emptyCandidateCount += 1;
            diagnostics.endOfCandidatesReached = true;
            finishOnce(buildCurrentSignal("timeout"));
            return;
          }
          updateWebRtcCandidateDiagnostics(diagnostics, candidate);
          const candidatePublicIps = extractPublicIpsFromCandidate(candidate);
          if (candidatePublicIps.length > 0) {
            candidatePublicIps.forEach(addPublicIp);
          }
          if (extractIpCandidates(candidate).length > 0) {
            lastPrivateCandidate = true;
          }
        };
        peer.createOffer()
          .then((offer) => peer.setLocalDescription(offer))
          .catch((error) => {
            diagnostics.offerError = error?.message || String(error || "create_offer_failed");
            finishOnce(buildCurrentSignal("error"));
          });
      } catch (error) {
        diagnostics.setupError = error?.message || String(error || "setup_failed");
        finishOnce(buildCurrentSignal("error"));
      }
    });
  }

  function createWebRtcDiagnostics(peerConnectionSupported) {
    return {
      peerConnectionSupported,
      startedAt: Date.now(),
      timeoutMillis: WEBRTC_SIGNAL_TIMEOUT_MILLIS,
      timeoutReached: false,
      endOfCandidatesReached: false,
      candidateCount: 0,
      emptyCandidateCount: 0,
      publicCandidateCount: 0,
      privateCandidateCount: 0,
      candidateTypes: { host: 0, srflx: 0, relay: 0, prflx: 0, unknown: 0 },
      candidateProtocols: { udp: 0, tcp: 0, unknown: 0 },
      iceCandidateErrors: [],
      iceGatheringStates: [],
      iceConnectionStates: [],
      connectionStates: [],
      setupError: "",
      offerError: ""
    };
  }

  function trackWebRtcState(states, state) {
    if (!Array.isArray(states) || !state) {
      return;
    }
    const normalized = String(state);
    if (states[states.length - 1] !== normalized) {
      states.push(normalized);
    }
  }

  function updateWebRtcCandidateDiagnostics(diagnostics, candidate) {
    diagnostics.candidateCount += 1;
    const type = readCandidateToken(candidate, "typ") || "unknown";
    const protocol = readCandidateProtocol(candidate);
    diagnostics.candidateTypes[type] = (diagnostics.candidateTypes[type] || 0) + 1;
    diagnostics.candidateProtocols[protocol] = (diagnostics.candidateProtocols[protocol] || 0) + 1;
    const publicIp = extractPublicIpFromCandidate(candidate);
    if (publicIp) {
      diagnostics.publicCandidateCount += 1;
      return;
    }
    if (extractIpCandidates(candidate).length > 0) {
      diagnostics.privateCandidateCount += 1;
    }
  }

  function readCandidateToken(candidate, tokenName) {
    const pattern = new RegExp(`\\b${tokenName}\\s+([a-z0-9_-]+)`, "i");
    const match = String(candidate || "").match(pattern);
    return match?.[1]?.toLowerCase() || "";
  }

  function readCandidateProtocol(candidate) {
    const match = String(candidate || "").match(/\b(udp|tcp)\b/i);
    return match?.[1]?.toLowerCase() || "unknown";
  }

  function recordWebRtcCandidateError(diagnostics, event) {
    const error = {
      url: event?.url || "",
      errorCode: event?.errorCode || "",
      errorText: event?.errorText || "",
      port: event?.port || ""
    };
    diagnostics.iceCandidateErrors.push(error);
    if (window.console?.info) {
      console.info("[admin-network-check] icecandidateerror", error);
    }
  }

  function resolveWebRtcDiagnosticReason(signal, diagnostics) {
    if (!diagnostics.peerConnectionSupported) {
      return "rtc_peer_connection_unsupported";
    }
    if (diagnostics.setupError) {
      return "rtc_setup_failed";
    }
    if (diagnostics.offerError) {
      return "rtc_offer_failed";
    }
    if (diagnostics.iceCandidateErrors.length > 0) {
      return "stun_or_ice_candidate_error";
    }
    if (signal.status === "timeout" && diagnostics.candidateCount === 0) {
      return "no_candidate_before_timeout";
    }
    if (signal.status === "timeout" && diagnostics.candidateCount > 0 && diagnostics.publicCandidateCount === 0) {
      return "candidates_without_public_ip";
    }
    if (signal.status === "private_only") {
      return "private_candidate_only";
    }
    if (signal.status === "ok") {
      return "public_candidate_found";
    }
    return signal.status || "unknown";
  }

  function logWebRtcSignalDiagnostic(signal, diagnostics) {
    const shouldLog = isNetworkDebugEnabled() || signal.status !== "ok";
    if (!shouldLog || !window.console?.info) {
      return;
    }
    console.info("[admin-network-check] webrtc-diagnostics", {
      status: signal.status,
      ips: signal.ips || [],
      reason: resolveWebRtcDiagnosticReason(signal, diagnostics),
      durationMillis: Date.now() - diagnostics.startedAt,
      timeoutMillis: diagnostics.timeoutMillis,
      timeoutReached: diagnostics.timeoutReached,
      endOfCandidatesReached: diagnostics.endOfCandidatesReached,
      peerConnectionSupported: diagnostics.peerConnectionSupported,
      candidateCount: diagnostics.candidateCount,
      emptyCandidateCount: diagnostics.emptyCandidateCount,
      publicCandidateCount: diagnostics.publicCandidateCount,
      privateCandidateCount: diagnostics.privateCandidateCount,
      candidateTypes: diagnostics.candidateTypes,
      candidateProtocols: diagnostics.candidateProtocols,
      iceGatheringStates: diagnostics.iceGatheringStates,
      iceConnectionStates: diagnostics.iceConnectionStates,
      connectionStates: diagnostics.connectionStates,
      iceCandidateErrors: diagnostics.iceCandidateErrors,
      setupError: diagnostics.setupError,
      offerError: diagnostics.offerError
    });
  }

  function isNetworkDebugEnabled() {
    try {
      return window.localStorage?.getItem(NETWORK_DEBUG_STORAGE_KEY) === "1";
    } catch (_) {
      return false;
    }
  }

  function logNetworkCheckDebug({ method, path, headers, response, body, phase }) {
    const errorCode = body && (body.error || body.code) ? String(body.error || body.code) : "";
    const shouldLog = isNetworkDebugEnabled()
      || WEBRTC_ERROR_CODES.has(errorCode)
      || response?.status === 403;
    if (!shouldLog || !window.console?.info) {
      return;
    }
    console.info("[admin-network-check]", {
      phase,
      method,
      path,
      httpStatus: response?.status || "",
      responseError: errorCode,
      responseMessage: body?.message || "",
      webRtcStatus: headers?.get?.(WEBRTC_STATUS_HEADER) || "",
      webRtcIps: headers?.get?.(WEBRTC_IPS_HEADER) || "",
      networkCheckUrl: body?.networkCheckUrl || ""
    });
  }

  function normalizeWebRtcSignal(signal) {
    const status = signal?.status ? String(signal.status).trim().toLowerCase() : "error";
    const ips = normalizeIpList(signal?.ips);
    const primaryIp = normalizeIpLiteral(signal?.ip ? String(signal.ip) : "");
    const durationMillis = Number.isFinite(Number(signal?.durationMillis))
      ? Math.max(0, Math.round(Number(signal.durationMillis)))
      : null;
    const diagnosticReason = signal?.diagnosticReason ? String(signal.diagnosticReason).trim() : "";
    const serverReusable = signal?.serverReusable === true;
    if (primaryIp && !ips.includes(primaryIp)) {
      ips.unshift(primaryIp);
    }
    const ip = primaryIp || ips[0] || "";
    if (!ip && status === "ok") {
      return { ip: "", ips: [], status: "private_only", durationMillis, diagnosticReason, serverReusable };
    }
    if (["ok", "timeout", "unsupported", "private_only", "error"].includes(status)) {
      return { ip, ips, status, durationMillis, diagnosticReason, serverReusable };
    }
    return { ip, ips, status: "error", durationMillis, diagnosticReason, serverReusable };
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

  function extractIpCandidates(candidate) {
    const source = String(candidate || "");
    return source.match(/(\b\d{1,3}(?:\.\d{1,3}){3}\b|(?:[0-9a-fA-F]{1,4}:){2,}[0-9a-fA-F:.]{1,})/g) || [];
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

  function isBrowserRuntime() {
    return typeof window !== "undefined" && window.location;
  }

  function safeSameOriginPath(value, fallback, allowedPrefixes = ["/shopping/"]) {
    const securityUrls = window.ShoppingSecurityUrls;
    if (securityUrls && typeof securityUrls.safeSameOriginPath === "function") {
      return securityUrls.safeSameOriginPath(value, fallback, allowedPrefixes);
    }
    const fallbackPath = fallback || ADMIN_LOGIN_PATH;
    const raw = String(value || "").trim();
    if (!raw || raw.includes("\\") || raw.startsWith("//")) {
      return fallbackPath;
    }
    try {
      const parsed = new URL(raw, window.location.origin);
      if (parsed.origin !== window.location.origin) {
        return fallbackPath;
      }
      const path = `${parsed.pathname}${parsed.search}${parsed.hash}`;
      return allowedPrefixes.some((prefix) => path.startsWith(prefix)) ? path : fallbackPath;
    } catch (_) {
      return fallbackPath;
    }
  }

  function isNetworkCheckFailure(body) {
    const errorCode = body && (body.error || body.code) ? String(body.error || body.code) : "";
    return WEBRTC_ERROR_CODES.has(errorCode);
  }

  function buildCurrentReturnPath() {
    if (!isBrowserRuntime()) {
      return ADMIN_LOGIN_PATH;
    }
    const path = safeSameOriginPath(`${window.location.pathname || "/"}${window.location.search || ""}`, ADMIN_LOGIN_PATH);
    if (path.startsWith(NETWORK_CHECK_FAILED_PATH)) {
      return ADMIN_LOGIN_PATH;
    }
    return path;
  }

  function buildNetworkCheckFailedUrl() {
    const path = buildCurrentReturnPath();
    return `${NETWORK_CHECK_FAILED_PATH}?scope=admin&path=${encodeURIComponent(path)}`;
  }

  function sanitizeNetworkCheckUrl(rawUrl) {
    const fallbackUrl = buildNetworkCheckFailedUrl();
    const value = String(rawUrl || "").trim();
    if (!value) {
      return fallbackUrl;
    }
    try {
      const path = safeSameOriginPath(value, fallbackUrl, [NETWORK_CHECK_FAILED_PATH]);
      return path.startsWith(NETWORK_CHECK_FAILED_PATH) ? path : fallbackUrl;
    } catch (_) {
      return fallbackUrl;
    }
  }

  function buildWafVerifyUrl() {
    return `${WAF_VERIFY_PATH}?return=${encodeURIComponent(buildCurrentReturnPath())}`;
  }

  function sanitizeWafVerifyUrl(rawUrl) {
    const fallbackUrl = buildWafVerifyUrl();
    const value = String(rawUrl || "").trim();
    if (!value) {
      return fallbackUrl;
    }
    try {
      const path = safeSameOriginPath(value, fallbackUrl, [WAF_VERIFY_PATH]);
      return path.startsWith(WAF_VERIFY_PATH) ? path : fallbackUrl;
    } catch (_) {
      return fallbackUrl;
    }
  }

  function scheduleNetworkCheckRedirect(response, body, headers) {
    if (!isBrowserRuntime() || window.location.pathname === NETWORK_CHECK_FAILED_PATH) {
      return;
    }
    const targetUrl = sanitizeNetworkCheckUrl(body?.networkCheckUrl);
    console.info("[admin-network-check] redirect-block-page", {
      httpStatus: response?.status || "",
      responseError: body?.error || body?.code || "",
      webRtcStatus: headers?.get?.(WEBRTC_STATUS_HEADER) || "",
      webRtcIps: headers?.get?.(WEBRTC_IPS_HEADER) || "",
      targetUrl
    });
    window.location.replace(targetUrl);
  }

  function handleNetworkCheckFailure(response, body, headers) {
    if ((response.status === 403 || response.status === 409) && isNetworkCheckFailure(body)) {
      scheduleNetworkCheckRedirect(response, body, headers);
    }
  }

  function handleAdminWafRequired(response, body) {
    const errorCode = body && (body.error || body.code) ? String(body.error || body.code) : "";
    if (response.status !== 409 || errorCode !== ADMIN_WAF_REQUIRED_ERROR_CODE || !isBrowserRuntime()) {
      return;
    }
    window.location.replace(sanitizeWafVerifyUrl(body?.verifyUrl));
  }

  async function requestWithMethod(method, path, payload) {
    const headers = await buildHeaders();
    const options = {
      method,
      headers,
      credentials: "same-origin"
    };
    if (payload !== undefined) {
      options.body = JSON.stringify(payload);
    }
    const response = await fetch(path, options);
    const body = await response.json().catch(() => null);
    logNetworkCheckDebug({ method, path, headers, response, body, phase: "response" });
    handleNetworkCheckFailure(response, body, headers);
    handleAdminWafRequired(response, body);
    if (!response.ok || !body || body.success !== true) {
      const message = body?.message || "请求失败，请稍后重试。";
      const error = new Error(message);
      error.payload = body;
      error.status = response.status;
      throw error;
    }
    return body;
  }

  async function request(path, payload) {
    return requestWithMethod("POST", path, payload);
  }

  async function get(path) {
    const headers = await buildHeaders({ "Content-Type": "application/json" });
    const response = await fetch(path, {
      method: "GET",
      headers,
      credentials: "same-origin"
    });
    const body = await response.json().catch(() => null);
    logNetworkCheckDebug({ method: "GET", path, headers, response, body, phase: "response" });
    handleNetworkCheckFailure(response, body, headers);
    handleAdminWafRequired(response, body);
    if (!response.ok || !body || body.success !== true) {
      const message = body?.message || "请求失败，请稍后重试。";
      const error = new Error(message);
      error.payload = body;
      error.status = response.status;
      throw error;
    }
    return body;
  }

  async function form(path, formData) {
    const headers = await buildHeaders();
    headers.delete("Content-Type");
    const response = await fetch(path, {
      method: "POST",
      headers,
      credentials: "same-origin",
      body: formData
    });
    const body = await response.json().catch(() => null);
    logNetworkCheckDebug({ method: "POST", path, headers, response, body, phase: "response" });
    handleNetworkCheckFailure(response, body, headers);
    handleAdminWafRequired(response, body);
    if (!response.ok || !body || body.success !== true) {
      const message = body?.message || "请求失败，请稍后重试。";
      const error = new Error(message);
      error.payload = body;
      error.status = response.status;
      throw error;
    }
    return body;
  }

  async function formWithProgress(path, formData, options = {}) {
    const headers = await buildHeaders();
    headers.delete("Content-Type");
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      let settled = false;
      const signal = options.signal;

      function cleanup() {
        signal?.removeEventListener?.("abort", abortRequest);
      }

      function finish(callback, value) {
        if (settled) {
          return;
        }
        settled = true;
        cleanup();
        callback(value);
      }

      function abortRequest() {
        xhr.abort();
        const error = new Error("Upload request was aborted.");
        error.name = "AbortError";
        finish(reject, error);
      }

      if (signal?.aborted) {
        abortRequest();
        return;
      }

      xhr.open("POST", path, true);
      xhr.withCredentials = true;
      headers.forEach((value, key) => {
        xhr.setRequestHeader(key, value);
      });
      signal?.addEventListener?.("abort", abortRequest, { once: true });

      xhr.upload.addEventListener("progress", (event) => {
        options.onUploadProgress?.({
          loaded: event.loaded || 0,
          total: event.total || 0,
          lengthComputable: Boolean(event.lengthComputable)
        });
      });
      xhr.upload.addEventListener("load", () => {
        options.onUploadDone?.();
        options.onProcessing?.();
      });
      xhr.addEventListener("error", () => {
        finish(reject, new Error("网络请求失败，请稍后重试。"));
      });
      xhr.addEventListener("timeout", () => {
        finish(reject, new Error("网络请求超时，请稍后重试。"));
      });
      xhr.addEventListener("load", () => {
        let body = null;
        try {
          body = xhr.responseText ? JSON.parse(xhr.responseText) : null;
        } catch (_) {
          body = null;
        }
        const response = {
          status: xhr.status,
          ok: xhr.status >= 200 && xhr.status < 300
        };
        logNetworkCheckDebug({ method: "POST", path, headers, response, body, phase: "response" });
        handleNetworkCheckFailure(response, body, headers);
        handleAdminWafRequired(response, body);
        if (!response.ok || !body || body.success !== true) {
          const message = body?.message || "请求失败，请稍后重试。";
          const error = new Error(message);
          error.payload = body;
          error.status = xhr.status;
          finish(reject, error);
          return;
        }
        finish(resolve, body);
      });
      xhr.send(formData);
    });
  }

  async function fetchPasswordCryptoKey() {
    const response = await request(PASSWORD_CRYPTO_KEY_PATH, {});
    const cryptoPayload = response?.data;
    const kid = cryptoPayload?.kid ? String(cryptoPayload.kid).trim() : "";
    const publicKeyJwk = cryptoPayload?.publicKeyJwk;
    if (!kid || !publicKeyJwk || typeof publicKeyJwk !== "object") {
      throw new Error(PASSWORD_CRYPTO_ERROR_MESSAGE);
    }
    return {
      kid,
      publicKeyJwk
    };
  }

  async function encryptPassword(rawPassword) {
    if (!isWebCryptoAvailable()) {
      throw new Error(PASSWORD_CRYPTO_ERROR_MESSAGE);
    }
    const passwordCryptoKey = await fetchPasswordCryptoKey();
    const cryptoKey = await window.crypto.subtle.importKey(
      "jwk",
      passwordCryptoKey.publicKeyJwk,
      {
        name: "RSA-OAEP",
        hash: "SHA-256"
      },
      false,
      ["encrypt"]
    );
    const rawBytes = new TextEncoder().encode(String(rawPassword || ""));
    const encryptedBuffer = await window.crypto.subtle.encrypt({ name: "RSA-OAEP" }, cryptoKey, rawBytes);
    return {
      kid: passwordCryptoKey.kid,
      passwordCipher: encodeBase64Url(new Uint8Array(encryptedBuffer)),
      nonce: buildPasswordCryptoNonce(),
      timestamp: Date.now()
    };
  }

  function setStatus(node, message, type = "") {
    if (!node) {
      return;
    }
    node.textContent = message || "";
    node.classList.toggle("is-error", type === "error");
    node.classList.toggle("is-ok", type === "ok");
  }

  root.AdminApi = {
    get,
    form,
    formWithProgress,
    request,
    requestWithMethod,
    encryptPassword,
    setStatus
  };
})(window);
