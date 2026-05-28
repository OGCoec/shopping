(function (root) {
  const api = root.AdminApi;
  const productApi = root.AdminProductApi;
  const imageUtils = root.AdminProductImageUtils;
  const formUi = root.AdminProductFormUi;
  const detailModel = root.AdminProductDetailModel;
  const {
    escapeHtml
  } = imageUtils;
  const {
    pickImageFile,
    pickImageFiles
  } = formUi;

  function create(options) {
    const el = options.el;
    const state = {
      product: null,
      draft: null,
      editing: false,
      skuSelectedKeys: new Set(),
      page: null,
      pendingStatus: null,
      saving: false,
      nextSkuClientKey: 1,
      mode: "detail"
    };
    const carouselController = root.AdminProductCarouselController.create({
      modulePath: options.carouselModulePath,
      setStatus: (message, type) => api.setStatus(detailStatus(), message, type)
    });
    const uploadSession = root.AdminProductUploadSession.create({
      productApi,
      pickImageFile,
      pickImageFiles,
      setStatus: (message, type) => api.setStatus(detailStatus(), message, type),
      renderProgress: renderUploadProgress
    });
    const imageEditor = root.AdminProductImageEditor.create({
      imageUtils,
      formUi,
      uploadSession
    });
    const skuEditor = root.AdminProductSkuEditor.create({
      imageUtils,
      formUi,
      imageEditor
    });
    const detailViews = root.AdminProductDetailViews.create({
      imageUtils,
      formUi,
      imageEditor,
      skuEditor
    });

  async function open(productId, mode = "detail") {
    const id = String(productId || "");
    if (!id) {
      return;
    }
    carouselController.destroy();
    carouselController.cancelPrewarm();
    options.showDetailView();
    ensurePage();
    state.page.hidden = false;
    hideUploadProgress();
    await uploadSession.cleanup();
    state.product = null;
    state.draft = null;
    state.editing = false;
    state.skuSelectedKeys.clear();
    state.nextSkuClientKey = 1;
    state.mode = mode === "images" ? "images" : mode === "carousel" ? "carousel" : "detail";
    renderLoading();
    try {
      const response = await productApi.getSpuDetail(id);
      state.product = response.data || null;
      render();
      if (state.pendingStatus) {
        api.setStatus(detailStatus(), state.pendingStatus.message, state.pendingStatus.type);
        state.pendingStatus = null;
      }
    } catch (error) {
      renderError(error.message || "商品详情加载失败。");
    }
  }

  async function close(cleanup) {
    carouselController.destroy();
    carouselController.cancelPrewarm();
    if (cleanup) {
      await uploadSession.cleanup();
    }
    if (state.page) {
      state.page.hidden = true;
    }
    hideUploadProgress();
    state.product = null;
    state.draft = null;
    state.editing = false;
    state.skuSelectedKeys.clear();
    state.mode = "detail";
  }

  async function closeAndNavigate(cleanup) {
    await close(cleanup);
    options.navigateToProductList();
  }

  function isOpen() {
    return state.page && !state.page.hidden;
  }

  function hidePage() {
    carouselController.destroy();
    carouselController.cancelPrewarm();
    if (state.page) {
      state.page.hidden = true;
    }
    hideUploadProgress();
  }

  function setPendingStatus(status) {
    state.pendingStatus = status || null;
  }

  function ensurePage() {
    if (state.page) {
      return;
    }
    const page = document.createElement("div");
    page.className = "admin-oauth-config-card admin-product-detail-page";
    page.hidden = true;
    page.innerHTML = `
      <p class="admin-oauth-config-status" data-detail-status></p>
      <div class="admin-product-upload-progress" data-upload-progress hidden>
        <div class="admin-product-upload-progress-head">
          <strong data-upload-progress-title></strong>
          <span data-upload-progress-percent></span>
        </div>
        <div class="admin-product-upload-progress-bar">
          <span data-upload-progress-fill></span>
        </div>
        <div class="admin-product-upload-progress-meta">
          <span data-upload-progress-size></span>
          <span data-upload-progress-speed></span>
        </div>
      </div>
      <div class="admin-product-detail-body"></div>`;
    const parent = el.root || el.card?.parentNode || el.card;
    const before = parent && el.dialog?.parentNode === parent ? el.dialog : null;
    if (parent) {
      parent.insertBefore(page, before);
    }
    state.page = page;
  }

  function detailBody() {
    ensurePage();
    return state.page?.querySelector(".admin-product-detail-body");
  }

  function detailStatus() {
    ensurePage();
    return state.page?.querySelector("[data-detail-status]");
  }

  function uploadProgressNode() {
    ensurePage();
    return state.page?.querySelector("[data-upload-progress]");
  }

  function hideUploadProgress() {
    const node = uploadProgressNode();
    if (node) {
      node.hidden = true;
    }
  }

  function renderUploadProgress(view) {
    const node = uploadProgressNode();
    if (!node) {
      return;
    }
    const percent = Math.max(0, Math.min(100, Number(view.percent) || 0));
    node.hidden = false;
    node.dataset.phase = view.phase || "";
    node.querySelector("[data-upload-progress-title]").textContent = view.title || "";
    node.querySelector("[data-upload-progress-percent]").textContent = `${Math.round(percent)}%`;
    node.querySelector("[data-upload-progress-fill]").style.width = `${percent}%`;
    node.querySelector("[data-upload-progress-size]").textContent = `${formatUploadBytes(view.loadedBytes)} / ${formatUploadBytes(view.totalBytes)}`;
    node.querySelector("[data-upload-progress-speed]").textContent = view.speedBytesPerSecond > 0
      ? `${formatUploadBytes(view.speedBytesPerSecond)}/s`
      : view.speedLabel || "";
  }

  function formatUploadBytes(value) {
    const bytes = Math.max(0, Number(value) || 0);
    if (bytes < 1024) {
      return `${Math.round(bytes)} B`;
    }
    const units = ["KB", "MB", "GB"];
    let amount = bytes / 1024;
    let unitIndex = 0;
    while (amount >= 1024 && unitIndex < units.length - 1) {
      amount /= 1024;
      unitIndex += 1;
    }
    return `${amount >= 10 ? amount.toFixed(1) : amount.toFixed(2)} ${units[unitIndex]}`;
  }

  function renderLoading() {
    const body = detailBody();
    if (body) {
      body.innerHTML = `<div class="admin-product-detail-empty">正在加载商品详情</div>`;
    }
    hideUploadProgress();
    api.setStatus(detailStatus(), "");
  }

  function renderError(message) {
    const body = detailBody();
    if (body) {
      body.innerHTML = `<div class="admin-product-detail-empty">${escapeHtml(message)}</div>`;
    }
    hideUploadProgress();
    api.setStatus(detailStatus(), message, "error");
  }

    function render() {
      if (!state.product) {
        renderError("商品详情不存在。");
        return;
      }
      if (state.mode === "images") {
        renderImageManagement();
        return;
      }
      if (state.mode === "carousel") {
        renderCarouselPage();
        return;
      }
      if (state.editing) {
        renderEdit();
      } else {
        renderReadonly();
      }
    }

    function replaceDetailBody(node) {
      const body = detailBody();
      if (!body) {
        return false;
      }
      body.replaceChildren(node);
      return true;
    }

    function buildDraft(product) {
      return detailModel.buildDraft(product, allocateSkuClientKey);
    }

    function allocateSkuClientKey(sku = null) {
      const id = String(sku?.id || "");
      if (id) {
        return "sku:" + id;
      }
      const next = state.nextSkuClientKey;
      state.nextSkuClientKey += 1;
      return "new-sku:" + next;
    }

    function skuContext(rerender) {
      return {
        selectedKeys: state.skuSelectedKeys,
        allocateSkuClientKey,
        isSaving: () => state.saving,
        rerender,
        uploadImages: uploadSession.uploadImages,
        cancelSkuImages: uploadSession.cancelSkuImages
      };
    }

    function renderReadonly() {
      const product = state.product;
      api.setStatus(detailStatus(), "");
      const node = detailViews.readonly(product, {
        carouselController,
        navigateToProductCarousel: options.navigateToProductCarousel,
        navigateToProductImages: options.navigateToProductImages,
        enterEdit,
        closeAndNavigate
      });
      replaceDetailBody(node);
    }

    function renderEdit() {
      carouselController.destroy();
      carouselController.cancelPrewarm();
      const draft = state.draft || buildDraft(state.product);
      state.draft = draft;
      api.setStatus(detailStatus(), "");
      const node = detailViews.edit(draft, {
        saving: state.saving,
        getLeafCategories: options.getLeafCategories,
        cancelEdit,
        saveEdit,
        rerender: renderEdit,
        skuContext: skuContext(renderEdit)
      });
      replaceDetailBody(node);
    }

    function renderImageManagement() {
      carouselController.destroy();
      carouselController.cancelPrewarm();
      const draft = state.draft || buildDraft(state.product);
      state.draft = draft;
      api.setStatus(detailStatus(), "");
      const node = detailViews.imageManagement(draft, {
        saving: state.saving,
        saveImages,
        cancelImageManagement,
        returnToDetail,
        rerender: renderImageManagement
      });
      replaceDetailBody(node);
    }

    function renderCarouselPage() {
      carouselController.destroy();
      carouselController.cancelPrewarm();
      const product = state.product;
      api.setStatus(detailStatus(), "");
      const view = detailViews.carousel(product, {
        carouselController,
        returnToDetail,
        navigateToProductImages: options.navigateToProductImages,
        closeAndNavigate
      });
      if (!replaceDetailBody(view.node)) {
        return;
      }
      if (!view.stage) {
        api.setStatus(detailStatus(), view.emptyMessage, "error");
        return;
      }
      carouselController.mount(view.stage, product);
    }

  async function enterEdit(product) {
    try {
      api.setStatus(detailStatus(), "正在加载叶子分类。");
      await options.loadLeafCategories();
      state.editing = true;
      state.skuSelectedKeys.clear();
      state.nextSkuClientKey = 1;
      state.draft = detailModel.buildDraft(product, allocateSkuClientKey);
      render();
    } catch (error) {
      api.setStatus(detailStatus(), error.message || "叶子分类加载失败，暂时不能修改商品详情。", "error");
    }
  }

    async function cancelEdit() {
      await uploadSession.cleanup();
      state.editing = false;
      state.draft = null;
      state.skuSelectedKeys.clear();
      render();
    }

    async function cancelImageManagement() {
      await uploadSession.cleanup();
      state.draft = buildDraft(state.product);
      renderImageManagement();
      api.setStatus(detailStatus(), "已取消未保存的图片修改。", "ok");
    }

    async function returnToDetail() {
      const productId = state.product?.id || state.draft?.id;
      carouselController.destroy();
      carouselController.cancelPrewarm();
      await uploadSession.cleanup();
      state.draft = null;
      state.skuSelectedKeys.clear();
      state.mode = "detail";
      options.navigateToProductDetail(productId);
    }

    async function saveImages() {
      await saveDraft("商品图片已保存。", () => {
        state.mode = "images";
        state.draft = buildDraft(state.product);
        renderImageManagement();
      });
    }

    async function saveEdit() {
      if (state.saving || !state.draft?.id) {
        return;
      }
      await saveDraft("商品详情已保存。", () => {
        state.mode = "detail";
        state.editing = false;
        state.draft = null;
        render();
      });
    }

    async function saveDraft(successMessage, afterSuccess) {
      if (state.saving || !state.draft?.id) {
        return;
      }
      const draft = state.draft;
      const update = detailModel.buildUpdatePayload(draft, uploadSession.imageUploadSessions());
      if (!update.ok) {
        api.setStatus(detailStatus(), update.message, "error");
        return;
      }
      state.saving = true;
      if (state.mode === "images") {
        renderImageManagement();
      } else {
        renderEdit();
      }
      let saved = false;
      let saveError = null;
      try {
        const response = await productApi.updateSpuDetail(draft.id, update.payload);
        uploadSession.clearCommitted();
        state.product = response.data || state.product;
        state.draft = null;
        state.editing = false;
        state.skuSelectedKeys.clear();
        await options.loadPage();
        saved = true;
        state.saving = false;
        if (typeof afterSuccess === "function") {
          afterSuccess();
        } else {
          render();
        }
        api.setStatus(detailStatus(), successMessage, "ok");
      } catch (error) {
        saveError = error.message || "商品保存失败。";
      } finally {
        if (saved) {
          return;
        }
        state.saving = false;
        if (state.mode === "images" && state.draft) {
          renderImageManagement();
        } else if (state.editing) {
          renderEdit();
        }
        if (saveError) {
          api.setStatus(detailStatus(), saveError, "error");
        }
      }
    }

    return {
      open,
      close,
      closeAndNavigate,
      isOpen,
      hidePage,
      setPendingStatus
    };
  }

  root.AdminProductDetailController = { create };
})(window);
