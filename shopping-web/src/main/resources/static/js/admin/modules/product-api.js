(function (root) {
  const API_BASE = "/shopping/admin/api/products";
  const CATEGORY_API_BASE = "/shopping/admin/api/product-categories";

  function adminApi() {
    if (!root.AdminApi) {
      throw new Error("AdminApi is not ready.");
    }
    return root.AdminApi;
  }

  function requestWithMethod(method, path, payload) {
    const api = adminApi();
    if (typeof api.requestWithMethod === "function") {
      return api.requestWithMethod(method, path, payload);
    }
    if (method === "POST") {
      return api.request(path, payload);
    }
    throw new Error("当前管理端请求工具不支持该操作。");
  }

  function fetchSpuPage(params) {
    const query = params instanceof URLSearchParams ? params.toString() : String(params || "");
    return adminApi().get(`${API_BASE}/spu/page?${query}`);
  }

  function fetchCategoryTree() {
    return adminApi().get(`${CATEGORY_API_BASE}/tree`);
  }

  function preuploadImage(file, options = null) {
    const formData = new FormData();
    formData.append("file", file);
    const api = adminApi();
    if (options && typeof api.formWithProgress === "function") {
      return api.formWithProgress(`${API_BASE}/images/preupload`, formData, options);
    }
    return api.form(`${API_BASE}/images/preupload`, formData);
  }

  function cancelPreupload(tempImage) {
    return requestWithMethod("DELETE", `${API_BASE}/images/preupload`, {
      uploadSessionId: tempImage.uploadSessionId,
      tempUrl: tempImage.tempUrl
    });
  }

  function createSpu(payload) {
    return adminApi().request(`${API_BASE}/spu`, payload);
  }

  function getSpuDetail(id) {
    return adminApi().get(`${API_BASE}/spu/${encodeURIComponent(id)}`);
  }

  function getSkuDetail(spuId, skuId) {
    return adminApi().get(`${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/${encodeURIComponent(skuId)}`);
  }

  function updateSpuDetail(id, payload) {
    return requestWithMethod("PUT", `${API_BASE}/spu/${encodeURIComponent(id)}`, payload);
  }

  function createSku(spuId, payload) {
    return requestWithMethod("POST", `${API_BASE}/spu/${encodeURIComponent(spuId)}/sku`, payload);
  }

  function updateSku(spuId, skuId, payload) {
    return requestWithMethod("PUT", `${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/${encodeURIComponent(skuId)}`, payload);
  }

  function deleteSku(spuId, skuId) {
    return requestWithMethod("DELETE", `${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/${encodeURIComponent(skuId)}`);
  }

  function changeSkuStatus(spuId, skuId, status) {
    return requestWithMethod("PATCH", `${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/${encodeURIComponent(skuId)}/status`, { status });
  }

  function importSkuCardSecrets(spuId, skuId, formData) {
    return adminApi().form(`${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/${encodeURIComponent(skuId)}/card-secrets/import`, formData);
  }

  function batchChangeSkuStatus(spuId, ids, status) {
    return requestWithMethod("PATCH", `${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/batch-status`, { ids, status });
  }

  function batchDeleteSku(spuId, ids) {
    return requestWithMethod("DELETE", `${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/batch`, { ids });
  }

  function listHotSkus(spuId) {
    return adminApi().get(`${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/hot`);
  }

  function getHotSku(spuId, skuId) {
    return adminApi().get(`${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/hot/${encodeURIComponent(skuId)}`);
  }

  function batchEnableHotSkus(spuId, items) {
    return requestWithMethod("POST", `${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/hot/batch-enable`, { items });
  }

  function batchDeleteHotSkus(spuId, ids) {
    return requestWithMethod("DELETE", `${API_BASE}/spu/${encodeURIComponent(spuId)}/sku/hot/batch`, { ids });
  }

  function changeSpuStatus(id, status) {
    return requestWithMethod("PATCH", `${API_BASE}/spu/${encodeURIComponent(id)}/status`, { status });
  }

  function batchDisableSpu(ids) {
    return requestWithMethod("POST", `${API_BASE}/spu/batch-disable`, { ids });
  }

  function batchDeleteSpu(ids) {
    return requestWithMethod("DELETE", `${API_BASE}/spu/batch`, { ids });
  }

  function batchDisableCategorySpu(categoryId) {
    return requestWithMethod("POST", `${API_BASE}/spu/category/${encodeURIComponent(categoryId)}/batch-disable`);
  }

  function batchDeleteCategorySpu(categoryId) {
    return requestWithMethod("DELETE", `${API_BASE}/spu/category/${encodeURIComponent(categoryId)}/batch`);
  }

  root.AdminProductApi = {
    fetchSpuPage,
    fetchCategoryTree,
    preuploadImage,
    cancelPreupload,
    createSpu,
    getSpuDetail,
    getSkuDetail,
    updateSpuDetail,
    createSku,
    updateSku,
    deleteSku,
    changeSkuStatus,
    importSkuCardSecrets,
    batchChangeSkuStatus,
    batchDeleteSku,
    listHotSkus,
    getHotSku,
    batchEnableHotSkus,
    batchDeleteHotSkus,
    changeSpuStatus,
    batchDisableSpu,
    batchDeleteSpu,
    batchDisableCategorySpu,
    batchDeleteCategorySpu,
    requestWithMethod
  };
})(window);
