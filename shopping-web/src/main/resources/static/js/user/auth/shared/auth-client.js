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
  const CSRF_COOKIE_NAME = "XSRF-TOKEN";
  const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
  const ACCESS_EXPIRED_ERROR = "ACCESS_TOKEN_EXPIRED";

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

  function isPreAuthVerificationResponse(response) {
    return response?.status === 409;
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
      if (isPreAuthVerificationResponse(refreshResponse)) {
        return refreshResponse;
      }
      redirectToLogin();
      return firstResponse;
    }

    const retryOptions = cloneOptions(options);
    applyCsrf(retryOptions.headers);
    return guardedFetch(url, retryOptions);
  }

  async function logout() {
    const options = cloneOptions({ method: "POST" });
    applyCsrf(options.headers);
    const response = await guardedFetch(LOGOUT_PATH, options);
    if (!isPreAuthVerificationResponse(response)) {
      redirectToLogin();
    }
    return response;
  }

  async function logoutAll() {
    const options = cloneOptions({ method: "POST" });
    applyCsrf(options.headers);
    const response = await fetchWithAuth(LOGOUT_ALL_PATH, options);
    if (!isPreAuthVerificationResponse(response)) {
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
