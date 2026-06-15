(function () {
  const PRODUCT_DETAIL_BASE_PATH = "/shopping/api/products";
  const CAROUSEL_MODULE_PATH = "/shopping/js/user/product-image-fromanother-carousel.js?v=1";

  const statusEl = document.getElementById("product-detail-status");
  const contentEl = document.getElementById("product-detail-content");
  let carousel = null;
  let carouselToken = 0;

  function authClient() {
    return window.ShoppingAuthClient || null;
  }

  function safeImageUrl(value) {
    return window.ShoppingSecurityUrls?.safeImageUrl?.(value, "", {
      allowData: false,
      allowBlob: false,
      allowAnyHttps: true,
      allowLocalHttp: true,
      allowedPathPrefixes: ["/shopping/"]
    }) || "";
  }

  async function fetchJson(path) {
    const client = authClient();
    if (!client?.fetchWithAuth) {
      throw new Error("Authentication client is unavailable.");
    }
    const response = await client.fetchWithAuth(path, {
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

  function readProductId() {
    const prefix = "/shopping/user/products/";
    const path = window.location.pathname || "";
    const raw = path.startsWith(prefix) ? path.slice(prefix.length) : path.split("/").pop();
    try {
      return decodeURIComponent(String(raw || "").split("/")[0]).trim();
    } catch (_) {
      return "";
    }
  }

  function showLoading() {
    destroyCarousel();
    statusEl.hidden = false;
    statusEl.classList.remove("is-error");
    statusEl.textContent = "正在加载商品详情";
    contentEl.hidden = true;
    contentEl.replaceChildren();
  }

  function showError(message) {
    destroyCarousel();
    statusEl.hidden = false;
    statusEl.classList.add("is-error");
    statusEl.textContent = message || "商品详情暂不可用";
    contentEl.hidden = true;
    contentEl.replaceChildren();
  }

  function showContent() {
    statusEl.hidden = true;
    statusEl.classList.remove("is-error");
    statusEl.textContent = "";
    contentEl.hidden = false;
  }

  async function loadProductDetail() {
    const productId = readProductId();
    if (!productId) {
      showError("商品地址无效");
      return;
    }
    showLoading();
    try {
      const detail = await fetchJson(`${PRODUCT_DETAIL_BASE_PATH}/${encodeURIComponent(productId)}`);
      renderProductDetail(detail || {});
    } catch (error) {
      showError(error?.status === 404 ? "商品已下架或不存在" : "商品详情暂不可用");
    }
  }

  function renderProductDetail(detail) {
    destroyCarousel();
    showContent();
    contentEl.replaceChildren();
    document.title = `${String(detail?.name || "商品详情")} - Shopping`;
    contentEl.append(
      renderHero(detail),
      renderImageSection("详情图片", detail?.detailImageUrls),
      renderKeyValueSection("商品参数", detail?.attributes),
      renderTextSection("商品说明", detail?.description || "暂无商品说明"),
      renderTextSection("售后说明", detail?.afterSale || "暂无售后说明"),
      renderSkuSection(detail?.skus)
    );
  }

  function renderHero(detail) {
    const hero = document.createElement("section");
    hero.className = "product-detail-hero";

    const media = document.createElement("section");
    media.className = "product-detail-section";
    const mediaTitle = document.createElement("h2");
    mediaTitle.textContent = "展示图片";
    const carouselHost = document.createElement("div");
    carouselHost.className = "product-detail-carousel-host";
    media.append(mediaTitle, carouselHost);

    const images = collectDisplayImages(detail);
    if (images.length) {
      carouselHost.appendChild(carouselPlaceholder("正在加载展示图片"));
      window.requestAnimationFrame(() => mountCarousel(carouselHost, images, detail?.name));
    } else {
      carouselHost.appendChild(carouselPlaceholder("暂无图片"));
    }

    const summary = document.createElement("section");
    summary.className = "product-detail-summary";
    const title = document.createElement("h1");
    title.textContent = String(detail?.name || "商品详情");
    const subtitle = document.createElement("p");
    subtitle.textContent = String(detail?.subtitle || "暂无副标题");
    const meta = document.createElement("div");
    meta.className = "product-detail-meta";
    meta.append(
      detailBadge(detail?.brandName || "未设置品牌"),
      detailBadge(detail?.categoryName || "未设置分类"),
      detailBadge(`${skuCount(detail?.skus)} 个规格`)
    );
    summary.append(title, subtitle, meta);

    hero.append(media, summary);
    return hero;
  }

  async function mountCarousel(host, images, title) {
    const token = ++carouselToken;
    try {
      const module = await import(CAROUSEL_MODULE_PATH);
      if (token !== carouselToken || !host.isConnected) {
        return;
      }
      host.replaceChildren();
      carousel = module.createProductImageFromAnotherCarousel(host, {
        images,
        initialIndex: 0,
        intervalMs: 5000,
        transitionMs: 1400,
        title: String(title || "商品展示图片"),
        inline: true
      });
      carousel.mount();
    } catch (_) {
      if (token === carouselToken && host.isConnected) {
        renderFallbackGallery(host, images);
      }
    }
  }

  function renderFallbackGallery(host, images) {
    const root = document.createElement("div");
    root.className = "product-detail-fallback-gallery";
    const main = document.createElement("div");
    main.className = "product-detail-fallback-main";
    const image = document.createElement("img");
    image.src = images[0];
    image.alt = "商品展示图片";
    main.appendChild(image);
    root.appendChild(main);
    host.replaceChildren(root);
  }

  function destroyCarousel() {
    carouselToken += 1;
    if (!carousel) {
      return;
    }
    try {
      carousel.destroy();
    } catch (_) {
    }
    carousel = null;
  }

  function renderImageSection(title, rawImages) {
    const section = detailSection(title);
    const images = normalizeImageList(rawImages);
    if (!images.length) {
      section.appendChild(detailEmpty("暂无图片"));
      return section;
    }
    const grid = document.createElement("div");
    grid.className = "product-detail-image-grid";
    images.forEach((imageUrl) => {
      const frame = document.createElement("div");
      frame.className = "product-detail-image-frame";
      const image = document.createElement("img");
      image.src = imageUrl;
      image.alt = title;
      image.loading = "lazy";
      frame.appendChild(image);
      grid.appendChild(frame);
    });
    section.appendChild(grid);
    return section;
  }

  function renderKeyValueSection(title, rawValue) {
    const section = detailSection(title);
    const entries = rawValue && typeof rawValue === "object" && !Array.isArray(rawValue)
      ? Object.entries(rawValue)
      : [];
    if (!entries.length) {
      section.appendChild(detailEmpty("暂无参数"));
      return section;
    }
    const list = document.createElement("dl");
    list.className = "product-detail-kv";
    entries.forEach(([key, value]) => {
      const term = document.createElement("dt");
      term.textContent = key;
      const desc = document.createElement("dd");
      desc.textContent = formatDetailValue(value);
      list.append(term, desc);
    });
    section.appendChild(list);
    return section;
  }

  function renderTextSection(title, text) {
    const section = detailSection(title);
    const paragraph = document.createElement("p");
    paragraph.className = "product-detail-text";
    paragraph.textContent = String(text || "");
    section.appendChild(paragraph);
    return section;
  }

  function renderSkuSection(rawSkus) {
    const section = detailSection("可选规格");
    const skus = Array.isArray(rawSkus) ? rawSkus : [];
    if (!skus.length) {
      section.appendChild(detailEmpty("暂无可选规格"));
      return section;
    }
    const list = document.createElement("div");
    list.className = "product-detail-sku-list";
    skus.forEach((sku) => {
      list.appendChild(renderSku(sku));
    });
    section.appendChild(list);
    return section;
  }

  function renderSku(sku) {
    const item = document.createElement("article");
    item.className = "product-detail-sku";
    item.appendChild(renderSkuImage(sku));

    const name = document.createElement("h3");
    name.textContent = String(sku?.skuName || "默认规格");
    const price = document.createElement("strong");
    price.textContent = formatPrice(sku?.priceYuan);
    const stock = document.createElement("span");
    stock.textContent = `库存 ${Number(sku?.stockQuantity || 0)}`;
    const buyLink = document.createElement("a");
    buyLink.className = "product-detail-buy-link";
    buyLink.href = checkoutPath(sku?.id);
    buyLink.dataset.action = "buy-sku";
    buyLink.dataset.skuId = String(sku?.id || "");
    buyLink.textContent = "立即购买";
    const spec = document.createElement("div");
    spec.className = "product-detail-sku-spec";
    spec.textContent = formatSpec(sku?.specJson);

    item.append(name, price, stock, buyLink, spec);
    return item;
  }

  function renderSkuImage(sku) {
    const frame = document.createElement("div");
    frame.className = "product-detail-sku-image";
    const imageUrl = normalizeImageList(sku?.skuImageUrls)[0];
    if (!imageUrl) {
      frame.textContent = "NO IMAGE";
      return frame;
    }
    const image = document.createElement("img");
    image.src = imageUrl;
    image.alt = String(sku?.skuName || "SKU 图片");
    image.loading = "lazy";
    frame.appendChild(image);
    return frame;
  }

  function detailSection(title) {
    const section = document.createElement("section");
    section.className = "product-detail-section";
    const heading = document.createElement("h2");
    heading.textContent = title;
    section.appendChild(heading);
    return section;
  }

  function detailBadge(text) {
    const badge = document.createElement("span");
    badge.textContent = String(text || "");
    return badge;
  }

  function detailEmpty(message) {
    const empty = document.createElement("div");
    empty.className = "product-detail-empty";
    empty.textContent = message;
    return empty;
  }

  function carouselPlaceholder(message) {
    const placeholder = document.createElement("div");
    placeholder.className = "product-detail-carousel-placeholder";
    placeholder.textContent = message;
    return placeholder;
  }

  function collectDisplayImages(detail) {
    const displayImages = normalizeImageList(detail?.imageUrls);
    if (displayImages.length) {
      return uniqueImages(displayImages);
    }
    const mainImageUrl = safeImageUrl(detail?.mainImageUrl);
    return mainImageUrl ? [mainImageUrl] : [];
  }

  function normalizeImageList(rawImages) {
    const values = Array.isArray(rawImages) ? rawImages : [];
    return values
      .map((item) => {
        if (typeof item === "string") {
          return safeImageUrl(item);
        }
        if (!item || typeof item !== "object") {
          return "";
        }
        return safeImageUrl(item.url || item.imageUrl || item.src);
      })
      .filter(Boolean);
  }

  function uniqueImages(images) {
    const seen = new Set();
    const result = [];
    images.forEach((imageUrl) => {
      if (!seen.has(imageUrl)) {
        seen.add(imageUrl);
        result.push(imageUrl);
      }
    });
    return result;
  }

  function skuCount(rawSkus) {
    return Array.isArray(rawSkus) ? rawSkus.length : 0;
  }

  function formatSpec(specJson) {
    if (!specJson || typeof specJson !== "object" || Array.isArray(specJson)) {
      return "暂无规格参数";
    }
    const values = Object.entries(specJson).map(([key, value]) => `${key}: ${formatDetailValue(value)}`);
    return values.length ? values.join(" / ") : "暂无规格参数";
  }

  function formatDetailValue(value) {
    if (value === null || value === undefined) {
      return "";
    }
    if (typeof value === "object") {
      return JSON.stringify(value);
    }
    return String(value);
  }

  function formatPrice(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
      return "价格待定";
    }
    return `¥${number.toFixed(2)}`;
  }

  function checkoutPath(skuId) {
    const id = String(skuId || "").trim();
    if (!id) {
      return "/shopping/user/console";
    }
    return `/shopping/user/checkout/${encodeURIComponent(id)}?quantity=1`;
  }

  async function startPage() {
    const pageGate = window.ShoppingPageAccessGate;
    if (pageGate?.ready) {
      const allowed = await pageGate.ready();
      if (allowed === false) {
        return;
      }
    }
    loadProductDetail();
  }

  startPage();
})();
