(function () {
  const ME_PATH = "/shopping/user/auth/me";
  const PROFILE_PATH = "/shopping/user/profile";
  const CATEGORY_TREE_PATH = "/shopping/api/product-categories/tree";
  const CATEGORY_SEARCH_PATH = "/shopping/api/product-categories/search";
  const PRODUCT_SEARCH_PATH = "/shopping/api/products/search";
  const HIGHLIGHT_START = "[[HL]]";
  const HIGHLIGHT_END = "[[/HL]]";
  const PAGE_SIZE = 20;

  const authClient = window.ShoppingAuthClient;
  const avatarLink = document.getElementById("console-avatar-link");
  const avatarImage = document.getElementById("console-avatar-image");
  const avatarFallback = document.getElementById("console-avatar-fallback");

  const productSearchForm = document.getElementById("console-product-search-form");
  const productKeywordInput = document.getElementById("console-product-keyword");
  const productSearchClear = document.getElementById("console-product-search-clear");
  const categorySelectionClear = document.getElementById("console-category-selection-clear");
  const categorySearchForm = document.getElementById("console-category-search-form");
  const categoryKeywordInput = document.getElementById("console-category-keyword");
  const categorySearchClear = document.getElementById("console-category-search-clear");
  const selectedCategoryEl = document.getElementById("console-selected-category");
  const categoryStatusEl = document.getElementById("console-category-status");
  const categoryTreeEl = document.getElementById("console-category-tree");
  const productSummaryEl = document.getElementById("console-product-summary");
  const productStatusEl = document.getElementById("console-product-status");
  const productGridEl = document.getElementById("console-product-grid");
  const productPrevButton = document.getElementById("console-product-prev");
  const productNextButton = document.getElementById("console-product-next");
  const productPageEl = document.getElementById("console-product-page");

  const state = {
    categoryTree: [],
    expandedCategoryIds: new Set(),
    selectedCategoryId: "",
    selectedCategoryName: "",
    productKeyword: "",
    page: 1,
    pageSize: PAGE_SIZE,
    total: 0
  };

  let currentAvatarUrl = "";

  function normalizeAvatarUrl(value) {
    const normalized = value === null || value === undefined ? "" : String(value).trim();
    return /^https?:\/\//i.test(normalized) ? normalized : "";
  }

  function renderAvatar(user) {
    currentAvatarUrl = normalizeAvatarUrl(user?.avatarUrl);

    if (!currentAvatarUrl) {
      avatarImage.hidden = true;
      avatarImage.removeAttribute("src");
      avatarFallback.hidden = false;
      return;
    }

    avatarImage.hidden = true;
    avatarFallback.hidden = false;
    avatarImage.src = currentAvatarUrl;
  }

  avatarImage?.addEventListener("load", () => {
    if (!currentAvatarUrl) {
      return;
    }
    avatarImage.hidden = false;
    avatarFallback.hidden = true;
  });

  avatarImage?.addEventListener("error", () => {
    avatarImage.hidden = true;
    avatarFallback.hidden = false;
  });

  avatarLink?.addEventListener("click", (event) => {
    event.preventDefault();
    window.location.assign(PROFILE_PATH);
  });

  async function fetchJson(path, params) {
    if (!authClient?.fetchWithAuth) {
      window.location.assign("/shopping/user/log-in");
      throw new Error("Authentication client is unavailable.");
    }
    const url = new URL(path, window.location.origin);
    Object.entries(params || {}).forEach(([key, value]) => {
      if (value !== null && value !== undefined && String(value).trim() !== "") {
        url.searchParams.set(key, String(value));
      }
    });
    const response = await authClient.fetchWithAuth(url, {
      method: "GET",
      credentials: "same-origin",
      headers: {
        Accept: "application/json"
      }
    });
    if (!response.ok) {
      const text = await response.text().catch(() => "");
      const error = new Error(text || `HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    return response.json();
  }

  async function loadUser() {
    if (!authClient?.fetchWithAuth) {
      window.location.assign("/shopping/user/log-in");
      return;
    }

    try {
      const response = await authClient.fetchWithAuth(ME_PATH, { method: "GET" });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success || !payload?.user) {
        return;
      }
      renderAvatar(payload.user);
    } catch (_) {
      // Keep the fallback avatar visible when the user summary cannot be loaded.
    }
  }

  async function loadCategoryTree() {
    setCategoryStatus("正在加载分类");
    try {
      state.categoryTree = await fetchJson(CATEGORY_TREE_PATH);
      state.expandedCategoryIds = new Set();
      renderCategoryTree();
      setCategoryStatus(state.categoryTree.length ? "" : "暂无可用分类");
    } catch (_) {
      state.categoryTree = [];
      renderCategoryTree();
      setCategoryStatus("分类加载失败");
    }
  }

  async function searchCategories(keyword) {
    const value = String(keyword || "").trim();
    if (!value) {
      await loadCategoryTree();
      return;
    }
    setCategoryStatus("正在搜索分类");
    try {
      state.categoryTree = await fetchJson(CATEGORY_SEARCH_PATH, { keyword: value });
      state.expandedCategoryIds = collectCategoryIds(state.categoryTree);
      renderCategoryTree();
      setCategoryStatus(state.categoryTree.length ? "" : "没有匹配的分类");
    } catch (_) {
      state.categoryTree = [];
      renderCategoryTree();
      setCategoryStatus("分类搜索服务暂不可用");
    }
  }

  async function loadProducts(options) {
    if (options?.resetPage) {
      state.page = 1;
    }
    setProductStatus("正在加载商品");
    try {
      const payload = await fetchJson(PRODUCT_SEARCH_PATH, {
        keyword: state.productKeyword,
        categoryId: state.selectedCategoryId,
        page: state.page,
        pageSize: state.pageSize
      });
      state.total = Number(payload?.total || 0);
      state.page = Number(payload?.page || state.page || 1);
      state.pageSize = Number(payload?.pageSize || state.pageSize || PAGE_SIZE);
      renderProducts(Array.isArray(payload?.records) ? payload.records : []);
      setProductStatus("");
    } catch (_) {
      state.total = 0;
      renderProducts([]);
      setProductStatus("商品搜索服务暂不可用");
    }
  }

  function renderCategoryTree() {
    categoryTreeEl.replaceChildren();
    state.categoryTree.forEach((node) => {
      categoryTreeEl.appendChild(renderCategoryNode(node, 0));
    });
    renderSelectedCategory();
  }

  function renderCategoryNode(node, depth) {
    const wrapper = document.createElement("div");
    wrapper.className = "console-category-node";

    const children = Array.isArray(node.children) ? node.children : [];
    const hasChildren = children.length > 0;
    const id = String(node.id || "");
    const isExpanded = state.expandedCategoryIds.has(id);
    const isSelected = state.selectedCategoryId === id;

    const button = document.createElement("button");
    button.type = "button";
    button.className = "console-category-button";
    button.style.setProperty("--category-depth", String(Math.min(depth, 8)));
    button.setAttribute("aria-expanded", hasChildren ? String(isExpanded) : "false");
    if (isSelected) {
      button.classList.add("is-selected");
    }

    const marker = document.createElement("span");
    marker.className = "console-category-marker";
    marker.textContent = hasChildren ? (isExpanded ? "-" : "+") : "";
    button.appendChild(marker);

    const name = document.createElement("span");
    name.className = "console-category-name";
    appendHighlightedText(name, node.name, node.nameHighlight);
    button.appendChild(name);

    button.addEventListener("click", () => {
      if (hasChildren) {
        if (isExpanded) {
          state.expandedCategoryIds.delete(id);
        } else {
          state.expandedCategoryIds.add(id);
        }
        renderCategoryTree();
        return;
      }
      state.selectedCategoryId = id;
      state.selectedCategoryName = String(node.name || "");
      renderCategoryTree();
      loadProducts({ resetPage: true });
    });

    wrapper.appendChild(button);

    if (hasChildren && isExpanded) {
      const branch = document.createElement("div");
      branch.className = "console-category-branch";
      children.forEach((child) => {
        branch.appendChild(renderCategoryNode(child, depth + 1));
      });
      wrapper.appendChild(branch);
    }

    return wrapper;
  }

  function renderSelectedCategory() {
    if (!state.selectedCategoryId) {
      selectedCategoryEl.hidden = true;
      selectedCategoryEl.textContent = "";
      return;
    }
    selectedCategoryEl.hidden = false;
    selectedCategoryEl.textContent = `当前分类：${state.selectedCategoryName || state.selectedCategoryId}`;
  }

  function renderProducts(records) {
    productGridEl.replaceChildren();
    records.forEach((product) => {
      productGridEl.appendChild(renderProductCard(product));
    });
    if (!records.length) {
      const empty = document.createElement("div");
      empty.className = "console-empty-state";
      empty.textContent = "暂无商品";
      productGridEl.appendChild(empty);
    }
    renderProductSummary();
    renderPager();
  }

  function renderProductCard(product) {
    const card = document.createElement("a");
    card.className = "console-product-card";
    card.href = productDetailPath(product?.id);
    card.setAttribute("aria-label", `查看商品详情：${String(product?.name || "")}`);
    card.addEventListener("keydown", (event) => {
      if (event.key !== " ") {
        return;
      }
      event.preventDefault();
      window.location.assign(card.href);
    });

    const media = document.createElement("div");
    media.className = "console-product-media";
    const imageUrl = String(product?.mainImageUrl || "").trim();
    if (imageUrl) {
      const image = document.createElement("img");
      image.src = imageUrl;
      image.alt = String(product?.name || "商品图片");
      image.loading = "lazy";
      media.appendChild(image);
    } else {
      const fallback = document.createElement("span");
      fallback.textContent = "NO IMAGE";
      media.appendChild(fallback);
    }
    card.appendChild(media);

    const body = document.createElement("div");
    body.className = "console-product-body";

    const title = document.createElement("h2");
    appendHighlightedText(title, product?.name, product?.nameHighlight);
    body.appendChild(title);

    const subtitle = document.createElement("p");
    subtitle.textContent = String(product?.subtitle || "暂无副标题");
    body.appendChild(subtitle);

    const meta = document.createElement("div");
    meta.className = "console-product-meta";
    const brand = document.createElement("span");
    brand.textContent = String(product?.brandName || "未设置品牌");
    const category = document.createElement("span");
    category.textContent = String(product?.categoryName || "未设置分类");
    meta.append(brand, category);
    body.appendChild(meta);

    card.appendChild(body);
    return card;
  }

  function productDetailPath(productId) {
    const id = String(productId || "").trim();
    if (!id) {
      return "/shopping/user/console";
    }
    return `/shopping/user/products/${encodeURIComponent(id)}`;
  }

  function appendHighlightedText(container, fallback, highlight) {
    container.replaceChildren();
    const raw = String(highlight || fallback || "");
    if (!raw.includes(HIGHLIGHT_START)) {
      container.textContent = raw;
      return;
    }

    let active = false;
    raw.split(/(\[\[HL\]\]|\[\[\/HL\]\])/g).forEach((part) => {
      if (!part) {
        return;
      }
      if (part === HIGHLIGHT_START) {
        active = true;
        return;
      }
      if (part === HIGHLIGHT_END) {
        active = false;
        return;
      }
      if (!active) {
        container.appendChild(document.createTextNode(part));
        return;
      }
      const span = document.createElement("mark");
      span.className = "console-highlight";
      span.textContent = part;
      container.appendChild(span);
    });
  }

  function collectCategoryIds(nodes) {
    const ids = new Set();
    const stack = Array.isArray(nodes) ? [...nodes] : [];
    while (stack.length) {
      const node = stack.pop();
      if (!node) {
        continue;
      }
      if (node.id !== null && node.id !== undefined) {
        ids.add(String(node.id));
      }
      const children = Array.isArray(node.children) ? node.children : [];
      children.forEach((child) => stack.push(child));
    }
    return ids;
  }

  function renderProductSummary() {
    const totalPages = Math.max(1, Math.ceil(state.total / state.pageSize));
    const filters = [];
    if (state.productKeyword) {
      filters.push(`关键词：${state.productKeyword}`);
    }
    if (state.selectedCategoryId) {
      filters.push(`分类：${state.selectedCategoryName || state.selectedCategoryId}`);
    }
    const suffix = filters.length ? `，${filters.join("，")}` : "";
    productSummaryEl.textContent = `共 ${state.total} 个商品${suffix}`;
    productPageEl.textContent = `${Math.min(state.page, totalPages)} / ${totalPages}`;
  }

  function renderPager() {
    const totalPages = Math.max(1, Math.ceil(state.total / state.pageSize));
    productPrevButton.disabled = state.page <= 1;
    productNextButton.disabled = state.page >= totalPages;
  }

  function setCategoryStatus(message) {
    categoryStatusEl.textContent = message || "";
    categoryStatusEl.hidden = !message;
  }

  function setProductStatus(message) {
    productStatusEl.textContent = message || "";
    productStatusEl.hidden = !message;
  }

  productSearchForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    state.productKeyword = String(productKeywordInput.value || "").trim();
    loadProducts({ resetPage: true });
  });

  productSearchClear?.addEventListener("click", () => {
    productKeywordInput.value = "";
    state.productKeyword = "";
    loadProducts({ resetPage: true });
  });

  categorySelectionClear?.addEventListener("click", () => {
    state.selectedCategoryId = "";
    state.selectedCategoryName = "";
    renderCategoryTree();
    loadProducts({ resetPage: true });
  });

  categorySearchForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    searchCategories(categoryKeywordInput.value);
  });

  categorySearchClear?.addEventListener("click", () => {
    categoryKeywordInput.value = "";
    loadCategoryTree();
  });

  productPrevButton?.addEventListener("click", () => {
    if (state.page <= 1) {
      return;
    }
    state.page -= 1;
    loadProducts();
  });

  productNextButton?.addEventListener("click", () => {
    const totalPages = Math.max(1, Math.ceil(state.total / state.pageSize));
    if (state.page >= totalPages) {
      return;
    }
    state.page += 1;
    loadProducts();
  });

  async function startPage() {
    const pageGate = window.ShoppingPageAccessGate;
    if (pageGate?.ready) {
      const allowed = await pageGate.ready();
      if (!allowed) {
        return;
      }
    }
    loadUser();
    loadCategoryTree();
    loadProducts({ resetPage: true });
  }

  startPage();
})();
