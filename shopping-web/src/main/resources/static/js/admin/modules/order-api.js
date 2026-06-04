(function (root) {
  const API_BASE = "/shopping/admin/api/orders";

  function adminApi() {
    if (!root.AdminApi) {
      throw new Error("AdminApi is not ready.");
    }
    return root.AdminApi;
  }

  function queryString(params) {
    return params instanceof URLSearchParams ? params.toString() : String(params || "");
  }

  function fetchOrderPage(params) {
    const query = queryString(params);
    return adminApi().get(query ? `${API_BASE}?${query}` : API_BASE);
  }

  function getOrderDetail(orderNo) {
    return adminApi().get(`${API_BASE}/${encodeURIComponent(String(orderNo || ""))}`);
  }

  root.AdminOrderApi = {
    fetchOrderPage,
    getOrderDetail
  };
})(window);
