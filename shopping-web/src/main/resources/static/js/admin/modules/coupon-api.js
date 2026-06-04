(function (root) {
  const API_BASE = "/shopping/admin/api/coupons/templates";

  function adminApi() {
    if (!root.AdminApi) {
      throw new Error("AdminApi is not ready.");
    }
    return root.AdminApi;
  }

  function queryString(params) {
    return params instanceof URLSearchParams ? params.toString() : String(params || "");
  }

  function fetchTemplatePage(params) {
    const query = queryString(params);
    return adminApi().get(query ? `${API_BASE}?${query}` : API_BASE);
  }

  function getTemplateDetail(id) {
    return adminApi().get(`${API_BASE}/${encodeURIComponent(id)}`);
  }

  function fetchTemplateClaims(id, params) {
    const query = queryString(params);
    const path = `${API_BASE}/${encodeURIComponent(id)}/claims`;
    return adminApi().get(query ? `${path}?${query}` : path);
  }

  root.AdminCouponApi = {
    fetchTemplatePage,
    getTemplateDetail,
    fetchTemplateClaims
  };
})(window);
