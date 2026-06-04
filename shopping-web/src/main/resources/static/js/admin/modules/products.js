(function (root) {
  const api = root.AdminApi;
  const router = root.AdminRouter;
  const productApi = root.AdminProductApi;
  const imageUtils = root.AdminProductImageUtils;
  const CONSOLE_PRODUCTS_PATH = "/shopping/admin/console/products";
  const PRODUCT_IMAGE_CAROUSEL_MODULE_PATH = "/shopping/js/admin/modules/product-image-ripple-carousel.js?v=3";
  const HIGHLIGHT_START = "[[HL]]";
  const HIGHLIGHT_END = "[[/HL]]";
  const PAGE_SIZE_ERROR = "每页数量必须是大于 0 的整数。";
  const {
    escapeHtml,
    escapeAttribute,
    normalizeSearchText,
    formatDate
  } = imageUtils;
  let createController = null;
  let detailController = null;

  const state = {
    mounted: false,
    categoriesLoaded: false,
    leafCategories: [],
    page: 1,
    pageSize: 20,
    total: 0,
    currentRecords: [],
    selectedIds: new Set(),
    pageBusy: false
  };

  const el = {};

  function $(id) {
    return document.getElementById(id);
  }

  function readPageSize() {
    if (!el.pageSize) {
      return state.pageSize;
    }
    const rawValue = String(el.pageSize.value || "").trim();
    const pageSize = Number(rawValue);
    if (!rawValue || !Number.isInteger(pageSize) || pageSize <= 0) {
      api.setStatus(el.status, PAGE_SIZE_ERROR, "error");
      return null;
    }
    return pageSize;
  }

  function mount() {
    if (state.mounted) {
      return;
    }
    Object.assign(el, {
      create: $("admin-product-spu-create"),
      root: document.querySelector("[data-admin-panel='products'] .admin-product-spu-detail"),
      card: document.querySelector("[data-admin-panel='products'] .admin-product-spu-card"),
      tools: document.querySelector("[data-admin-panel='products'] .admin-product-spu-tools"),
      batchDisable: $("admin-product-spu-batch-disable"),
      batchDelete: $("admin-product-spu-batch-delete"),
      categoryBatchDisable: $("admin-product-spu-category-batch-disable"),
      categoryBatchDelete: $("admin-product-spu-category-batch-delete"),
      refresh: $("admin-product-spu-refresh"),
      filterForm: $("admin-product-spu-filter-form"),
      filterName: $("admin-product-spu-filter-name"),
      filterCategory: $("admin-product-spu-filter-category"),
      filterStatus: $("admin-product-spu-filter-status"),
      pageSize: $("admin-product-spu-page-size"),
      reset: $("admin-product-spu-filter-reset"),
      total: $("admin-product-spu-total"),
      pageLabel: $("admin-product-spu-page-label"),
      status: $("admin-product-spu-status"),
      summary: document.querySelector("[data-admin-panel='products'] .admin-product-spu-summary"),
      selectAll: $("admin-product-spu-select-all"),
      table: document.querySelector("[data-admin-panel='products'] .admin-product-spu-table"),
      list: $("admin-product-spu-list"),
      pagination: document.querySelector("[data-admin-panel='products'] .admin-risk-ip-pagination"),
      prev: $("admin-product-spu-prev"),
      next: $("admin-product-spu-next"),
      dialog: $("admin-product-spu-dialog"),
      dialogBackdrop: $("admin-product-spu-dialog-backdrop"),
      dialogClose: $("admin-product-spu-dialog-close"),
      form: $("admin-product-spu-form"),
      formStatus: $("admin-product-spu-form-status"),
      categorySearch: $("admin-product-spu-category-search"),
      category: $("admin-product-spu-category"),
      name: $("admin-product-spu-name"),
      subtitle: $("admin-product-spu-subtitle"),
      brand: $("admin-product-spu-brand"),
      editStatus: $("admin-product-spu-edit-status"),
      imagePick: $("admin-product-spu-image-pick"),
      imageRemove: $("admin-product-spu-image-remove"),
      imageInput: $("admin-product-spu-image-input"),
      imagePreview: $("admin-product-spu-image-preview"),
      imageEmpty: $("admin-product-spu-image-empty"),
      cancel: $("admin-product-spu-cancel"),
      save: $("admin-product-spu-save")
    });
    if (!el.list) {
      return;
    }
    state.mounted = true;
    detailController = root.AdminProductDetailController.create({
      el,
      carouselModulePath: PRODUCT_IMAGE_CAROUSEL_MODULE_PATH,
      showDetailView,
      loadLeafCategories,
      getLeafCategories: () => state.leafCategories,
      loadPage,
      navigateToProductDetail,
      navigateToProductImages,
      navigateToProductCarousel,
      navigateToProductSku,
      navigateToProductSkuCreate,
      navigateToProductHotSku,
      navigateToProductHotSkuDetail,
      navigateToProductList
    });
    createController = root.AdminProductCreateController.create({
      el,
      loadLeafCategories,
      renderDialogCategorySelect,
      getLeafCategories: () => state.leafCategories,
      navigateToProductDetail,
      loadPage,
      setPage: (page) => { state.page = page; },
      setPendingDetailStatus: (status) => detailController?.setPendingStatus(status)
    });
    createController.ensureEditors();
    bindEvents();
    router?.register?.("products", routeProducts);
  }

  function bindEvents() {
    createController?.bindEvents();
    el.batchDisable?.addEventListener("click", batchDisableSelected);
    el.batchDelete?.addEventListener("click", batchDeleteSelected);
    el.categoryBatchDisable?.addEventListener("click", batchDisableCurrentCategory);
    el.categoryBatchDelete?.addEventListener("click", batchDeleteCurrentCategory);
    el.selectAll?.addEventListener("change", toggleCurrentPageSelection);
    el.refresh?.addEventListener("click", () => loadPage());
    el.filterForm?.addEventListener("submit", (event) => {
      event.preventDefault();
      state.page = 1;
      loadPage();
    });
    el.reset?.addEventListener("click", () => {
      el.filterName.value = "";
      el.filterCategory.value = "";
      el.filterStatus.value = "";
      state.page = 1;
      loadPage();
    });
    el.pageSize?.addEventListener("change", () => {
      const pageSize = readPageSize();
      if (!pageSize) {
        return;
      }
      state.pageSize = pageSize;
      state.page = 1;
      loadPage();
    });
    el.filterCategory?.addEventListener("change", updateBulkActionState);
    el.prev?.addEventListener("click", () => {
      if (state.page > 1) {
        state.page -= 1;
        loadPage();
      }
    });
    el.next?.addEventListener("click", () => {
      if (state.page * state.pageSize < state.total) {
        state.page += 1;
        loadPage();
      }
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && detailController?.isOpen()) {
        detailController.closeAndNavigate(true);
        return;
      }
      if (event.key === "Escape" && createController?.isOpen()) {
        createController.close(true);
      }
    });
  }

  async function ensureDetailController() {
    if (detailController) {
      return;
    }
    if (!state.mounted) {
      const panel = router?.getLoadedPanel?.("products");
      if (panel) {
        mount();
      }
    }
  }

  async function routeProducts() {
    await ensureDetailController();
    const productRoute = productRouteFromLocation();
    if (productRoute.id) {
      if (!detailController) {
        return;
      }
      await detailController.open(productRoute.id, productRoute.mode, productRoute.skuId);
      return;
    }
    await detailController?.close(true);
    showListView();
    await loadPage();
  }

  function productRouteFromLocation() {
    const normalizedPath = String(window.location.pathname || "").replace(/\/+$/, "");
    const prefix = `${CONSOLE_PRODUCTS_PATH}/`;
    if (!normalizedPath.startsWith(prefix)) {
      return { id: "", mode: "detail" };
    }
    const parts = normalizedPath.slice(prefix.length).split("/");
    const id = decodeURIComponent(parts[0] || "").trim();
    if (parts[1] === "sku" && parts[2] === "hot") {
      const hotSkuId = decodeURIComponent(parts[3] || "").trim();
      if (hotSkuId) {
        return {
          id,
          mode: "hotSkuDetail",
          skuId: hotSkuId
        };
      }
      return {
        id,
        mode: "hotSku"
      };
    }
    if (parts[1] === "sku") {
      return {
        id,
        mode: "sku",
        skuId: decodeURIComponent(parts[2] || "").trim()
      };
    }
    return {
      id,
      mode: parts[1] === "images" ? "images" : parts[1] === "carousel" ? "carousel" : "detail"
    };
  }

  function navigateToProductDetail(productId) {
    const id = String(productId || "").trim();
    if (!id) {
      return;
    }
    if (window.history?.pushState) {
      const url = new URL(window.location.href);
      url.pathname = `${CONSOLE_PRODUCTS_PATH}/${encodeURIComponent(id)}`;
      url.search = "";
      window.history.pushState({ adminSection: "products", productId: id }, "", url.pathname + url.search + url.hash);
      routeProducts();
      return;
    }
    detailController?.open(id);
  }

  function navigateToProductImages(productId) {
    const id = String(productId || "").trim();
    if (!id) {
      return;
    }
    if (window.history?.pushState) {
      const url = new URL(window.location.href);
      url.pathname = `${CONSOLE_PRODUCTS_PATH}/${encodeURIComponent(id)}/images`;
      url.search = "";
      window.history.pushState({ adminSection: "products", productId: id, productImages: true }, "", url.pathname + url.search + url.hash);
      routeProducts();
      return;
    }
    detailController?.open(id, "images");
  }

  function navigateToProductCarousel(productId) {
    const id = String(productId || "").trim();
    if (!id) {
      return;
    }
    if (window.history?.pushState) {
      const url = new URL(window.location.href);
      url.pathname = `${CONSOLE_PRODUCTS_PATH}/${encodeURIComponent(id)}/carousel`;
      url.search = "";
      window.history.pushState({ adminSection: "products", productId: id, productCarousel: true }, "", url.pathname + url.search + url.hash);
      routeProducts();
      return;
    }
    detailController?.open(id, "carousel");
  }

  function navigateToProductSku(productId, skuId) {
    const id = String(productId || "").trim();
    const sku = String(skuId || "").trim();
    if (!id || !sku) {
      return;
    }
    if (window.history?.pushState) {
      const url = new URL(window.location.href);
      url.pathname = `${CONSOLE_PRODUCTS_PATH}/${encodeURIComponent(id)}/sku/${encodeURIComponent(sku)}`;
      url.search = "";
      window.history.pushState({ adminSection: "products", productId: id, skuId: sku }, "", url.pathname + url.search + url.hash);
      routeProducts();
      return;
    }
    detailController?.open(id, "sku", sku);
  }

  function navigateToProductSkuCreate(productId) {
    const id = String(productId || "").trim();
    if (!id) {
      return;
    }
    navigateToProductSku(id, "new");
  }

  function navigateToProductHotSku(productId) {
    const id = String(productId || "").trim();
    if (!id) {
      return;
    }
    if (window.history?.pushState) {
      const url = new URL(window.location.href);
      url.pathname = `${CONSOLE_PRODUCTS_PATH}/${encodeURIComponent(id)}/sku/hot`;
      url.search = "";
      window.history.pushState({ adminSection: "products", productId: id, productHotSku: true }, "", url.pathname + url.search + url.hash);
      routeProducts();
      return;
    }
    detailController?.open(id, "hotSku");
  }

  function navigateToProductHotSkuDetail(productId, skuId) {
    const id = String(productId || "").trim();
    const hotSkuId = String(skuId || "").trim();
    if (!id || !hotSkuId) {
      return;
    }
    if (window.history?.pushState) {
      const url = new URL(window.location.href);
      url.pathname = `${CONSOLE_PRODUCTS_PATH}/${encodeURIComponent(id)}/sku/hot/${encodeURIComponent(hotSkuId)}`;
      url.search = "";
      window.history.pushState({ adminSection: "products", productId: id, productHotSku: true, hotSkuId }, "", url.pathname + url.search + url.hash);
      routeProducts();
      return;
    }
    detailController?.open(id, "hotSkuDetail", hotSkuId);
  }

  function navigateToProductList() {
    if (router?.switchSection) {
      router.switchSection("products");
      return;
    }
    if (window.history?.pushState) {
      window.history.pushState({ adminSection: "products" }, "", CONSOLE_PRODUCTS_PATH);
    }
    routeProducts();
  }

  function showListView() {
    if (el.card) {
      el.card.hidden = false;
    }
    [el.tools, el.filterForm, el.summary, el.status, el.table, el.pagination].forEach((node) => {
      if (node) {
        node.hidden = false;
      }
    });
    detailController?.hidePage();
  }

  function showDetailView() {
    if (el.card) {
      el.card.hidden = true;
    }
    [el.tools, el.filterForm, el.summary, el.status, el.table, el.pagination].forEach((node) => {
      if (node) {
        node.hidden = true;
      }
    });
  }

  async function loadPage() {
    const pageSize = readPageSize();
    if (!pageSize) {
      return;
    }
    state.pageSize = pageSize;
    setPageBusy(true);
    api.setStatus(el.status, "正在加载商品列表。");
    try {
      await loadLeafCategories();
      const params = new URLSearchParams();
      params.set("page", String(state.page));
      params.set("pageSize", String(pageSize));
      const name = el.filterName?.value?.trim();
      const categoryId = el.filterCategory?.value;
      const status = el.filterStatus?.value;
      if (name) {
        params.set("name", name);
      }
      if (categoryId) {
        params.set("categoryId", categoryId);
      }
      if (status) {
        params.set("status", status);
      }
      const response = await productApi.fetchSpuPage(params);
      const data = response.data || {};
      state.total = Number(data.total || 0);
      state.page = Number(data.page || state.page);
      state.pageSize = Number(data.pageSize || state.pageSize);
      state.currentRecords = Array.isArray(data.records) ? data.records : [];
      pruneSelected();
      renderProducts(state.currentRecords);
      renderSummary();
      api.setStatus(el.status, "商品列表已刷新。", "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "商品列表加载失败。", "error");
      state.currentRecords = [];
      state.selectedIds.clear();
      renderProducts([]);
      renderSummary();
    } finally {
      setPageBusy(false);
    }
  }

  async function loadLeafCategories(force = false) {
    if (state.categoriesLoaded && !force) {
      return;
    }
    const response = await productApi.fetchCategoryTree();
    const tree = Array.isArray(response.data) ? response.data : [];
    state.leafCategories = [];
    walkCategories(tree, (node) => {
      if (node.status === "ACTIVE" && Number(node.childCount || 0) === 0) {
        state.leafCategories.push(node);
      }
    });
    state.categoriesLoaded = true;
    renderCategorySelects();
  }

  function renderCategorySelects() {
    renderCategorySelect(el.filterCategory, "全部分类", state.leafCategories);
    renderDialogCategorySelect();
    updateBulkActionState();
  }

  function renderDialogCategorySelect() {
    const query = normalizeSearchText(el.categorySearch?.value);
    const categories = query
      ? state.leafCategories.filter((category) => categoryMatchesSearch(category, query))
      : state.leafCategories;
    renderCategorySelect(el.category, query ? "请选择匹配的启用叶子分类" : "请选择启用叶子分类", categories);
  }

  function renderCategorySelect(select, placeholder, categories) {
    if (!select) {
      return;
    }
    const selected = select.value;
    select.replaceChildren();
    const empty = document.createElement("option");
    empty.value = "";
    empty.textContent = placeholder;
    select.appendChild(empty);
    (Array.isArray(categories) ? categories : []).forEach((category) => {
      const option = document.createElement("option");
      option.value = String(category.id);
      const code = category.code ? ` / ${category.code}` : "";
      option.textContent = `${category.name || category.id} / ${category.level || 1} 级${code}`;
      select.appendChild(option);
    });
    if (selected && Array.from(select.options).some((option) => option.value === selected)) {
      select.value = selected;
    }
  }

  function categoryMatchesSearch(category, query) {
    const fields = [
      category.name,
      category.code,
      category.id,
      category.parentId,
      `${category.level || 1}级`,
      `${category.level || 1} 级`
    ];
    return fields.some((field) => normalizeSearchText(field).includes(query));
  }

  function renderProducts(records) {
    el.list.replaceChildren();
    if (!records.length) {
      const empty = document.createElement("div");
      empty.className = "admin-product-spu-empty";
      empty.textContent = "暂无商品";
      el.list.appendChild(empty);
      updateBulkActionState();
      return;
    }
    records.forEach((product) => el.list.appendChild(renderProductRow(product)));
    updateBulkActionState();
  }

  function renderProductRow(product) {
    const productId = String(product.id || "");
    const row = document.createElement("div");
    row.className = "admin-product-spu-row";
    row.classList.toggle("is-clickable", Boolean(productId));
    row.classList.toggle("is-selected", state.selectedIds.has(productId));
    if (productId) {
      row.tabIndex = 0;
      row.setAttribute("role", "button");
      row.setAttribute("aria-label", `查看商品 ${product.name || product.id || ""}`);
      row.addEventListener("click", () => navigateToProductDetail(productId));
      row.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          navigateToProductDetail(productId);
        }
      });
    }

    const selectCell = document.createElement("label");
    selectCell.className = "admin-product-spu-select-cell";
    selectCell.addEventListener("click", (event) => event.stopPropagation());
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.value = productId;
    checkbox.checked = state.selectedIds.has(productId);
    checkbox.disabled = !productId;
    checkbox.setAttribute("aria-label", `选择商品 ${product.name || product.id || ""}`);
    checkbox.addEventListener("change", () => {
      if (checkbox.checked) {
        state.selectedIds.add(productId);
      } else {
        state.selectedIds.delete(productId);
      }
      row.classList.toggle("is-selected", state.selectedIds.has(productId));
      updateBulkActionState();
    });
    selectCell.appendChild(checkbox);
    row.appendChild(selectCell);

    const imageCell = document.createElement("div");
    imageCell.className = "admin-product-spu-image-cell";
    if (product.mainImageUrl) {
      const img = document.createElement("img");
      img.src = product.mainImageUrl;
      img.alt = product.name || "商品主图";
      imageCell.appendChild(img);
    } else {
      imageCell.textContent = "-";
    }
    row.appendChild(imageCell);

    const nameCell = document.createElement("div");
    nameCell.className = "admin-product-spu-name-cell";
    const name = document.createElement("strong");
    renderHighlightedName(name, product.nameHighlight || product.name || "-");
    const subtitle = document.createElement("small");
    subtitle.textContent = product.subtitle || `ID ${product.id || "-"}`;
    nameCell.append(name, subtitle);
    row.appendChild(nameCell);

    row.appendChild(textCell(product.brandName || "-"));
    row.appendChild(textCell(product.categoryName || product.categoryId || "-"));
    row.appendChild(statusCell(product.status));
    row.appendChild(textCell(formatDate(product.createdAt)));
    row.appendChild(actionCell(product));
    return row;
  }

  function textCell(text) {
    const cell = document.createElement("div");
    cell.className = "admin-product-spu-cell";
    cell.textContent = String(text ?? "-");
    return cell;
  }

  function renderHighlightedName(target, value) {
    const source = String(value || "-");
    let cursor = 0;
    while (cursor < source.length) {
      const start = source.indexOf(HIGHLIGHT_START, cursor);
      if (start < 0) {
        target.appendChild(document.createTextNode(source.slice(cursor)));
        return;
      }
      if (start > cursor) {
        target.appendChild(document.createTextNode(source.slice(cursor, start)));
      }
      const contentStart = start + HIGHLIGHT_START.length;
      const end = source.indexOf(HIGHLIGHT_END, contentStart);
      if (end < 0) {
        target.appendChild(document.createTextNode(source.slice(start)));
        return;
      }
      const mark = document.createElement("span");
      mark.className = "admin-product-search-highlight";
      mark.textContent = source.slice(contentStart, end);
      target.appendChild(mark);
      cursor = end + HIGHLIGHT_END.length;
    }
  }

  function statusCell(status) {
    const cell = document.createElement("div");
    const badge = document.createElement("span");
    badge.className = `admin-product-category-status-badge ${status === "ACTIVE" ? "is-active" : "is-disabled"}`;
    badge.textContent = status === "ACTIVE" ? "启用" : "禁用";
    cell.appendChild(badge);
    return cell;
  }

  function actionCell(product) {
    const cell = document.createElement("div");
    cell.className = "admin-product-spu-actions";
    cell.addEventListener("click", (event) => event.stopPropagation());
    const nextStatus = product.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    const button = document.createElement("button");
    button.className = `admin-risk-ip-action-btn admin-spring-button ${nextStatus === "ACTIVE" ? "is-remove" : "is-add"}`;
    button.type = "button";
    button.textContent = nextStatus === "ACTIVE" ? "启用" : "禁用";
    button.addEventListener("click", () => changeStatus(product, nextStatus));
    cell.appendChild(button);
    return cell;
  }

  function renderSummary() {
    setText(el.total, String(state.total || 0));
    setText(el.pageLabel, String(state.page || 1));
    if (el.prev) {
      el.prev.disabled = state.pageBusy || state.page <= 1;
    }
    if (el.next) {
      el.next.disabled = state.pageBusy || state.page * state.pageSize >= state.total;
    }
    updateBulkActionState();
  }


  async function changeStatus(product, status) {
    setPageBusy(true);
    try {
      await productApi.changeSpuStatus(product.id, status);
      await loadPage();
      api.setStatus(el.status, status === "ACTIVE" ? "商品已启用。" : "商品已禁用。", "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "商品状态更新失败。", "error");
    } finally {
      setPageBusy(false);
    }
  }

  async function batchDisableSelected() {
    const ids = selectedProductIds();
    if (!ids.length) {
      api.setStatus(el.status, "请选择需要批量禁用的商品。", "error");
      return;
    }
    if (!root.confirm(`确认禁用选中的 ${ids.length} 个商品？`)) {
      return;
    }
    setPageBusy(true);
    try {
      const response = await productApi.batchDisableSpu(ids);
      state.selectedIds.clear();
      await loadPage();
      const affected = Number(response.data?.affectedCount || 0);
      api.setStatus(el.status, `已批量禁用商品，更新 ${affected} 个。`, "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "商品批量禁用失败。", "error");
    } finally {
      setPageBusy(false);
    }
  }

  async function batchDeleteSelected() {
    const ids = selectedProductIds();
    if (!ids.length) {
      api.setStatus(el.status, "请选择需要批量删除的商品。", "error");
      return;
    }
    if (!root.confirm(`确认永久删除选中的 ${ids.length} 个商品？`)) {
      return;
    }
    setPageBusy(true);
    try {
      const response = await productApi.batchDeleteSpu(ids);
      state.selectedIds.clear();
      await loadPage();
      const deleted = Number(response.data?.deletedSpuCount || 0);
      api.setStatus(el.status, `已批量删除商品，删除 ${deleted} 个。`, "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "商品批量删除失败。", "error");
    } finally {
      setPageBusy(false);
    }
  }

  async function batchDisableCurrentCategory() {
    const categoryId = selectedLeafCategoryId();
    if (!categoryId) {
      api.setStatus(el.status, "请选择一个叶子分类。", "error");
      return;
    }
    if (!root.confirm("确认禁用当前叶子分类下的全部商品？")) {
      return;
    }
    setPageBusy(true);
    try {
      const response = await productApi.batchDisableCategorySpu(categoryId);
      state.selectedIds.clear();
      await loadPage();
      const affected = Number(response.data?.affectedCount || 0);
      api.setStatus(el.status, `已禁用当前分类商品，更新 ${affected} 个。`, "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "当前分类商品禁用失败。", "error");
    } finally {
      setPageBusy(false);
    }
  }

  async function batchDeleteCurrentCategory() {
    const categoryId = selectedLeafCategoryId();
    if (!categoryId) {
      api.setStatus(el.status, "请选择一个叶子分类。", "error");
      return;
    }
    if (!root.confirm("确认永久删除当前叶子分类下的全部商品？")) {
      return;
    }
    setPageBusy(true);
    try {
      const response = await productApi.batchDeleteCategorySpu(categoryId);
      state.selectedIds.clear();
      await loadPage();
      const deleted = Number(response.data?.deletedSpuCount || 0);
      api.setStatus(el.status, `已删除当前分类商品，删除 ${deleted} 个。`, "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "当前分类商品删除失败。", "error");
    } finally {
      setPageBusy(false);
    }
  }


  function selectedProductIds() {
    return Array.from(state.selectedIds)
      .filter((id) => id);
  }

  function visibleProductIds() {
    return state.currentRecords
      .map((product) => String(product.id || ""))
      .filter((id) => id);
  }

  function pruneSelected() {
    const visibleIds = new Set(visibleProductIds());
    Array.from(state.selectedIds).forEach((id) => {
      if (!visibleIds.has(id)) {
        state.selectedIds.delete(id);
      }
    });
  }

  function toggleCurrentPageSelection() {
    const visibleIds = visibleProductIds();
    if (!visibleIds.length) {
      state.selectedIds.clear();
      updateBulkActionState();
      return;
    }
    const shouldSelect = Boolean(el.selectAll?.checked);
    visibleIds.forEach((id) => {
      if (shouldSelect) {
        state.selectedIds.add(id);
      } else {
        state.selectedIds.delete(id);
      }
    });
    renderProducts(state.currentRecords);
  }

  function selectedLeafCategoryId() {
    const categoryId = String(el.filterCategory?.value || "");
    if (!categoryId) {
      return "";
    }
    return state.leafCategories.some((category) => String(category.id || "") === categoryId) ? categoryId : "";
  }

  function updateBulkActionState() {
    const visibleIds = visibleProductIds();
    const selectedVisibleCount = visibleIds.filter((id) => state.selectedIds.has(id)).length;
    const busy = Boolean(state.pageBusy);
    if (el.selectAll) {
      el.selectAll.disabled = busy || visibleIds.length === 0;
      el.selectAll.checked = visibleIds.length > 0 && selectedVisibleCount === visibleIds.length;
      el.selectAll.indeterminate = selectedVisibleCount > 0 && selectedVisibleCount < visibleIds.length;
    }
    if (el.batchDisable) {
      el.batchDisable.disabled = busy || selectedVisibleCount === 0;
    }
    if (el.batchDelete) {
      el.batchDelete.disabled = busy || selectedVisibleCount === 0;
    }
    const hasLeafCategory = Boolean(selectedLeafCategoryId());
    if (el.categoryBatchDisable) {
      el.categoryBatchDisable.disabled = busy || !hasLeafCategory;
    }
    if (el.categoryBatchDelete) {
      el.categoryBatchDelete.disabled = busy || !hasLeafCategory;
    }
  }

  function walkCategories(nodes, callback) {
    (Array.isArray(nodes) ? nodes : []).forEach((node) => {
      callback(node);
      walkCategories(node.children, callback);
    });
  }

  function setPageBusy(busy) {
    state.pageBusy = Boolean(busy);
    [el.create, el.refresh, el.filterName, el.filterCategory, el.filterStatus, el.pageSize, el.reset, el.prev, el.next].forEach((node) => {
      if (node) {
        node.disabled = busy;
      }
    });
    updateBulkActionState();
  }


  function setText(node, text) {
    if (node) {
      node.textContent = text;
    }
  }

  root.AdminProductsModule = { mount };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mount);
  } else {
    mount();
  }
})(window);
