(function (root) {
  const CSRF_COOKIE = "XSRF-TOKEN";
  const CSRF_HEADER = "X-XSRF-TOKEN";

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

  function buildHeaders(extraHeaders = {}) {
    const headers = new Headers(extraHeaders);
    headers.set("Accept", "application/json");
    if (!headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    const csrfToken = readCookie(CSRF_COOKIE);
    if (csrfToken && !headers.has(CSRF_HEADER)) {
      headers.set(CSRF_HEADER, csrfToken);
    }
    return headers;
  }

  async function request(path, payload) {
    const options = {
      method: "POST",
      headers: buildHeaders(),
      credentials: "same-origin"
    };
    if (payload !== undefined) {
      options.body = JSON.stringify(payload);
    }
    const response = await fetch(path, options);
    const body = await response.json().catch(() => null);
    if (!response.ok || !body || body.success !== true) {
      const message = body?.message || "请求失败，请稍后重试。";
      const error = new Error(message);
      error.payload = body;
      error.status = response.status;
      throw error;
    }
    return body;
  }

  async function get(path) {
    const response = await fetch(path, {
      method: "GET",
      headers: buildHeaders({ "Content-Type": "application/json" }),
      credentials: "same-origin"
    });
    const body = await response.json().catch(() => null);
    if (!response.ok || !body || body.success !== true) {
      const message = body?.message || "请求失败，请稍后重试。";
      const error = new Error(message);
      error.payload = body;
      error.status = response.status;
      throw error;
    }
    return body;
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
    request,
    setStatus
  };
})(window);
