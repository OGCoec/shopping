(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingAuthClient = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const REFRESH_PATH = "/shopping/user/auth/refresh";
  const LOGOUT_PATH = "/shopping/user/auth/logout";
  const LOGOUT_ALL_PATH = "/shopping/user/auth/logout-all";
  const LOGIN_PATH = "/shopping/user/log-in";
  const PHONE_BINDING_PATH = "/shopping/user/security/phone";
  const CSRF_COOKIE_NAME = "XSRF-TOKEN";
  const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
  const ACCESS_EXPIRED_ERROR = "ACCESS_TOKEN_EXPIRED";
  const PHONE_BINDING_REQUIRED_ERROR = "PHONE_BINDING_REQUIRED";

  let refreshTask = null;

  function nativeFetch() {
    if (typeof fetch !== "function") {
      throw new Error("fetch is not available");
    }
    return fetch.bind(globalThis);
  }

  function preAuthClient() {
    return typeof globalThis !== "undefined" ? globalThis.ShoppingPreAuthClient : null;
  }

  async function guardedFetch(url, options) {
    const client = preAuthClient();
    if (client?.fetchWithPreAuth) {
      return client.fetchWithPreAuth(url, options);
    }
    return nativeFetch()(url, options);
  }

  function cloneOptions(options = {}) {
    const cloned = { ...options };
    cloned.headers = new Headers(options.headers || {});
    cloned.credentials = options.credentials || "same-origin";
    return cloned;
  }

  function readCookie(name) {
    if (typeof document === "undefined" || !document.cookie || !name) {
      return "";
    }
    const target = `${name}=`;
    const items = document.cookie.split(";");
    for (let index = 0; index < items.length; index += 1) {
      const item = items[index].trim();
      if (item.startsWith(target)) {
        return decodeURIComponent(item.substring(target.length));
      }
    }
    return "";
  }

  function applyCsrf(headers) {
    if (!headers.has(CSRF_HEADER_NAME)) {
      const token = readCookie(CSRF_COOKIE_NAME);
      if (token) {
        headers.set(CSRF_HEADER_NAME, token);
      }
    }
  }

  async function parseJson(response) {
    try {
      return await response.json();
    } catch (_) {
      return null;
    }
  }

  function redirectToLogin() {
    if (typeof window !== "undefined" && window.location) {
      window.location.assign(LOGIN_PATH);
    }
  }

  function safeSameOriginPath(value, fallback, allowedPrefixes = ["/shopping/"]) {
    const securityUrls = globalThis?.ShoppingSecurityUrls;
    if (securityUrls && typeof securityUrls.safeSameOriginPath === "function") {
      return securityUrls.safeSameOriginPath(value, fallback, allowedPrefixes);
    }
    const fallbackPath = fallback || "/shopping/";
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

  function redirectToPhoneBinding(payload) {
    if (typeof window === "undefined" || !window.location) {
      return;
    }
    const target = safeSameOriginPath(payload?.redirectPath, PHONE_BINDING_PATH);
    if (window.location.pathname === PHONE_BINDING_PATH) {
      return;
    }
    window.location.assign(target);
  }

  function isPreAuthVerificationResponse(response) {
    return response?.status === 409;
  }

  function handleNetworkCheckFailurePayload(payload) {
    const client = preAuthClient();
    if (client?.handleNetworkCheckFailurePayload) {
      return client.handleNetworkCheckFailurePayload(payload, "user");
    }
    return false;
  }

  async function handleNetworkCheckRequiredResponse(response) {
    if (response?.status !== 403 && response?.status !== 409) {
      return false;
    }
    const payload = await parseJson(response.clone());
    return handleNetworkCheckFailurePayload(payload);
  }

  async function handlePhoneBindingRequiredResponse(response) {
    if (response?.status !== 428) {
      return false;
    }
    const payload = await parseJson(response.clone());
    if (payload?.error !== PHONE_BINDING_REQUIRED_ERROR) {
      return false;
    }
    redirectToPhoneBinding(payload);
    return true;
  }

  async function refresh() {
    if (!refreshTask) {
      refreshTask = (async () => {
        const options = cloneOptions({ method: "POST" });
        applyCsrf(options.headers);
        return guardedFetch(REFRESH_PATH, options);
      })();
      refreshTask.finally(() => {
        refreshTask = null;
      });
    }
    return refreshTask;
  }

  async function fetchWithAuth(url, options = {}) {
    const firstOptions = cloneOptions(options);
    applyCsrf(firstOptions.headers);
    const firstResponse = await guardedFetch(url, firstOptions);
    if (await handleNetworkCheckRequiredResponse(firstResponse)) {
      return firstResponse;
    }
    if (await handlePhoneBindingRequiredResponse(firstResponse)) {
      return firstResponse;
    }
    if (firstResponse.status !== 401) {
      return firstResponse;
    }

    const errorPayload = await parseJson(firstResponse.clone());
    if (errorPayload?.error !== ACCESS_EXPIRED_ERROR) {
      redirectToLogin();
      return firstResponse;
    }

    const refreshResponse = await refresh();
    if (!refreshResponse.ok) {
      if (await handleNetworkCheckRequiredResponse(refreshResponse)) {
        return refreshResponse;
      }
      if (isPreAuthVerificationResponse(refreshResponse)) {
        return refreshResponse;
      }
      redirectToLogin();
      return firstResponse;
    }

    const retryOptions = cloneOptions(options);
    applyCsrf(retryOptions.headers);
    const retryResponse = await guardedFetch(url, retryOptions);
    if (await handleNetworkCheckRequiredResponse(retryResponse)) {
      return retryResponse;
    }
    await handlePhoneBindingRequiredResponse(retryResponse);
    return retryResponse;
  }

  async function logout() {
    const options = cloneOptions({ method: "POST" });
    applyCsrf(options.headers);
    const response = await guardedFetch(LOGOUT_PATH, options);
    if (!await handleNetworkCheckRequiredResponse(response) && !isPreAuthVerificationResponse(response)) {
      redirectToLogin();
    }
    return response;
  }

  async function logoutAll() {
    const options = cloneOptions({ method: "POST" });
    applyCsrf(options.headers);
    const response = await fetchWithAuth(LOGOUT_ALL_PATH, options);
    if (!await handleNetworkCheckRequiredResponse(response) && !isPreAuthVerificationResponse(response)) {
      redirectToLogin();
    }
    return response;
  }

  return {
    fetchWithAuth,
    refresh,
    logout,
    logoutAll
  };
});
