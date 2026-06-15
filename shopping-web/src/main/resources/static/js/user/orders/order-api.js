(function (root, factory) {
  root.ShoppingOrderApi = factory(root);
})(typeof globalThis !== "undefined" ? globalThis : this, function (root) {
  const API_BASE = "/shopping/user/api/orders";

  function authClient() {
    return root.ShoppingAuthClient || null;
  }

  function buildUrl(path, params) {
    const url = new URL(path, root.location?.origin || window.location.origin);
    Object.entries(params || {}).forEach(([key, value]) => {
      if (value !== null && value !== undefined && String(value).trim() !== "") {
        url.searchParams.set(key, String(value));
      }
    });
    return url;
  }

  async function parsePayload(response) {
    try {
      return await response.json();
    } catch (_) {
      return null;
    }
  }

  function toError(response, payload) {
    const message = payload?.message || payload?.code || payload?.error || `HTTP ${response.status}`;
    const error = new Error(message);
    error.status = response.status;
    error.code = payload?.code || payload?.error || "";
    error.payload = payload;
    return error;
  }

  async function request(path, options = {}) {
    const client = authClient();
    if (!client?.fetchWithAuth) {
      throw new Error("Authentication client is unavailable.");
    }

    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    if (options.body !== undefined && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }

    const response = await client.fetchWithAuth(buildUrl(path, options.params), {
      method: options.method || "GET",
      credentials: "same-origin",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
    const payload = await parsePayload(response);
    if (!response.ok || payload?.success === false) {
      throw toError(response, payload);
    }
    return payload && Object.prototype.hasOwnProperty.call(payload, "data") ? payload.data : payload;
  }

  function preview(body) {
    return request(`${API_BASE}/preview`, {
      method: "POST",
      body
    });
  }

  function create(body) {
    return request(API_BASE, {
      method: "POST",
      body
    });
  }

  function page(params) {
    return request(API_BASE, { params });
  }

  function detail(orderNo) {
    return request(`${API_BASE}/${encodeURIComponent(String(orderNo || ""))}`);
  }

  function cancel(orderNo, reason) {
    return request(`${API_BASE}/${encodeURIComponent(String(orderNo || ""))}/cancel`, {
      method: "POST",
      body: { reason: reason || "" }
    });
  }

  return {
    preview,
    create,
    page,
    detail,
    cancel
  };
});
