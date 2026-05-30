(function (root) {
  function create(options = {}) {
    const imageUtils = options.imageUtils || root.AdminProductImageUtils;
    const formUi = options.formUi || root.AdminProductFormUi;
    const imageEditor = options.imageEditor;
    const skuEditor = options.skuEditor;
    const {
      buildDisplayImages,
      imageUrlsFromNode,
      imageItemUrl,
      normalizeImageItems,
      formatJson,
      escapeHtml,
      escapeAttribute,
      formatDate,
      syncMainImageFromDisplayImages
    } = imageUtils;
    const {
      actionButton,
      labelSpan,
      appendImageOrderBadge
    } = formUi;

  function detailToolbar(title, buttons, productId = "") {
    const toolbar = document.createElement("div");
    toolbar.className = "admin-product-detail-toolbar";
    const heading = document.createElement("div");
    heading.className = "admin-product-detail-title";
    const strong = document.createElement("strong");
    strong.textContent = title;
    const small = document.createElement("small");
    small.textContent = `ID ${productId || ""}`;
    heading.append(strong, small);
    const actions = document.createElement("div");
    actions.className = "admin-product-detail-actions";
    buttons.forEach((button) => actions.appendChild(button));
    toolbar.append(heading, actions);
    return toolbar;
  }

  function readonlyMetaGrid(product) {
    const grid = document.createElement("div");
    grid.className = "admin-product-detail-grid";
    [
      ["分类", product.categoryName || product.categoryId || "-"],
      ["状态", product.status || "-"],
      ["品牌", product.brandName || "-"],
      ["副标题", product.subtitle || "-"],
      ["创建时间", formatDate(product.createdAt)],
      ["更新时间", formatDate(product.updatedAt)]
    ].forEach(([label, value]) => {
      const item = document.createElement("div");
      item.innerHTML = `<span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong>`;
      grid.appendChild(item);
    });
    return grid;
  }

  function readonlyImageSection(title, urls, markFirstAsMain = false) {
    const section = document.createElement("section");
    section.className = "admin-product-detail-section";
    section.innerHTML = `<h3>${escapeHtml(title)}</h3>`;
    const gallery = document.createElement("div");
    gallery.className = "admin-product-detail-gallery";
    const normalizedUrls = (Array.isArray(urls) ? urls : []).map(imageItemUrl).filter(Boolean);
    if (!normalizedUrls.length) {
      gallery.innerHTML = `<span class="admin-product-detail-muted">-</span>`;
    } else {
      normalizedUrls.forEach((url, index) => {
        const item = document.createElement("a");
        item.href = url;
        item.target = "_blank";
        item.rel = "noreferrer";
        item.innerHTML = `<img src="${escapeAttribute(url)}" alt="${escapeAttribute(title)}" />`;
        appendImageOrderBadge(item, index, markFirstAsMain && index === 0 ? "主图 / #1" : `#${index + 1}`);
        gallery.appendChild(item);
      });
    }
    section.appendChild(gallery);
    return section;
  }

  function readonlySkuTable(product, context) {
    const skus = Array.isArray(product?.skus) ? product.skus : [];
    const productId = String(product?.id || "").trim();
    const hotMap = readonlyHotSkuMap(context);
    const section = document.createElement("section");
    section.className = "admin-product-detail-section";
    const heading = document.createElement("div");
    heading.className = "admin-product-detail-section-heading";
    heading.innerHTML = `<h3>SKU</h3>`;
    const actions = document.createElement("div");
    actions.className = "admin-product-detail-actions";
    actions.appendChild(actionButton("设置热点", () => context.navigateToProductHotSku?.(productId), "admin-ghost-button", !productId));
    actions.appendChild(actionButton("新增 SKU", () => context.navigateToProductSkuCreate?.(productId), "admin-ghost-button", !productId));
    heading.appendChild(actions);
    section.appendChild(heading);
    const table = document.createElement("div");
    table.className = "admin-product-detail-sku-table";
    table.appendChild(skuReadonlyRow(["图片", "名称", "编码", "价格", "库存", "状态", "热点"], true));
    if (!skus.length) {
      const empty = document.createElement("div");
      empty.className = "admin-product-detail-empty-row";
      empty.textContent = "暂无 SKU";
      table.appendChild(empty);
    }
    skus.forEach((sku) => {
      const skuImages = normalizeImageItems(sku.skuImageUrls);
      const firstImage = imageItemUrl(skuImages[0]);
      table.appendChild(skuReadonlyRow([
        firstImage || "",
        sku.skuName || "-",
        sku.skuCode || "-",
        `${sku.priceYuan ?? 0}`,
        `${sku.stockQuantity ?? 0}`,
        skuImages.length > 1 ? `${sku.status || "-"} / ${skuImages.length} 图` : sku.status || "-",
        readonlyHotSkuLabel(hotMap.get(String(sku.id || "")))
      ], false, sku, productId, context));
    });
    section.appendChild(table);
    return section;
  }

  function readonlyHotSkuMap(context) {
    const hotSkus = Array.isArray(context?.hotSkus) ? context.hotSkus : [];
    const map = new Map();
    hotSkus.forEach((item) => {
      const skuId = String(item?.skuId || "").trim();
      if (skuId) {
        map.set(skuId, item);
      }
    });
    return map;
  }

  function readonlyHotSkuLabel(item) {
    if (!item) {
      return "-";
    }
    return `${item.status || "-"} ${item.remainingQuantity ?? 0}/${item.stockQuantity ?? 0}`;
  }

  function skuReadonlyRow(values, header, sku = null, productId = "", context = {}) {
    const row = document.createElement("div");
    row.className = `admin-product-detail-sku-row${header ? " is-header" : ""}`;
    const skuId = String(sku?.id || "").trim();
    const clickable = !header && productId && skuId;
    if (clickable) {
      row.classList.add("is-clickable");
      row.tabIndex = 0;
      row.setAttribute("role", "button");
      row.setAttribute("aria-label", `查看 SKU ${sku?.skuName || sku?.skuCode || skuId}`);
      row.addEventListener("click", () => context.navigateToProductSku?.(productId, skuId));
      row.addEventListener("keydown", (event) => {
        if (event.key !== "Enter" && event.key !== " ") {
          return;
        }
        event.preventDefault();
        context.navigateToProductSku?.(productId, skuId);
      });
    }
    values.forEach((value, index) => {
      const cell = document.createElement("div");
      if (!header && index === 0 && value) {
        cell.innerHTML = `<img src="${escapeAttribute(value)}" alt="${escapeAttribute(sku?.skuName || "SKU")}" />`;
      } else {
        cell.textContent = value || "-";
      }
      row.appendChild(cell);
    });
    return row;
  }

  function readonlyTextPanel(title, value) {
    const section = document.createElement("section");
    section.className = "admin-product-detail-section";
    section.innerHTML = `<h3>${escapeHtml(title)}</h3><pre>${escapeHtml(value || "-")}</pre>`;
    return section;
  }

  function detailInput(label, value, readonly, onInput, maxLength = 255, wide = false) {
    const field = document.createElement("label");
    field.className = `admin-risk-ip-field${wide ? " admin-product-detail-wide" : ""}`;
    const input = document.createElement("input");
    input.type = "text";
    input.value = value || "";
    input.readOnly = Boolean(readonly);
    input.maxLength = maxLength;
    input.addEventListener("input", () => onInput(input.value.trim()));
    field.append(labelSpan(label), input);
    return field;
  }

  function detailCategorySelect(draft, context) {
    const field = document.createElement("label");
    field.className = "admin-risk-ip-field";
    const select = document.createElement("select");
    context.getLeafCategories().forEach((category) => {
      const option = document.createElement("option");
      option.value = String(category.id || "");
      option.textContent = `${category.name || category.id}`;
      select.appendChild(option);
    });
    select.value = draft.categoryId;
    select.addEventListener("change", () => { draft.categoryId = select.value; });
    field.append(labelSpan("叶子分类"), select);
    return field;
  }

  function detailStatusSelect(draft) {
    const field = document.createElement("label");
    field.className = "admin-risk-ip-field";
    const select = document.createElement("select");
    ["ACTIVE", "DISABLED"].forEach((status) => {
      const option = document.createElement("option");
      option.value = status;
      option.textContent = status;
      select.appendChild(option);
    });
    select.value = draft.status;
    select.addEventListener("change", () => { draft.status = select.value; });
    field.append(labelSpan("状态"), select);
    return field;
  }

  function attributesEditor(draft) {
    return textareaEditor("商品参数 JSON", draft.attributesText, (value) => { draft.attributesText = value; }, true);
  }

  function textareaEditor(label, value, onInput, json = false) {
    const field = document.createElement("label");
    field.className = "admin-risk-ip-field admin-product-detail-wide";
    const textarea = document.createElement("textarea");
    textarea.rows = json ? 6 : 4;
    textarea.value = value || "";
    textarea.addEventListener("input", () => onInput(textarea.value));
    field.append(labelSpan(label), textarea);
    return field;
  }

  function readonly(product, context) {
    const carouselImages = context.carouselController.collectImages(product);
    const shell = document.createElement("div");
    shell.className = "admin-product-detail-content";
    shell.appendChild(detailToolbar(product.name || "-", [
      actionButton("图片轮播", () => context.navigateToProductCarousel(product.id), "admin-ghost-button", carouselImages.length === 0),
      actionButton("图片管理", () => context.navigateToProductImages(product.id), "admin-ghost-button"),
      actionButton("修改商品详情", () => context.enterEdit(product)),
      actionButton("返回列表", () => context.closeAndNavigate(true), "admin-api-back")
    ], product.id));
    shell.appendChild(readonlyMetaGrid(product));
    shell.appendChild(readonlyImageSection("展示图片", buildDisplayImages(product), true));
    shell.appendChild(readonlyImageSection("详情图片", imageUrlsFromNode(product.detailImageUrls)));
    shell.appendChild(readonlySkuTable(product, context));
    shell.appendChild(readonlyTextPanel("商品参数", formatJson(product.attributes || {})));
    shell.appendChild(readonlyTextPanel("文字详情", product.description || "-"));
    shell.appendChild(readonlyTextPanel("售后说明", product.afterSale || "-"));
    context.carouselController.schedulePrewarm(product);
    return shell;
  }

  function edit(draft, context) {
    const form = document.createElement("form");
    form.className = "admin-product-detail-content admin-product-detail-form";
    form.appendChild(detailToolbar(draft.name || "-", [
      actionButton("取消修改", () => context.cancelEdit(), "admin-api-back"),
      actionButton(context.saving ? "保存中" : "保存修改", () => context.saveEdit(), "admin-nav-button", context.saving)
    ], draft.id));
    const grid = document.createElement("div");
    grid.className = "admin-product-detail-edit-grid";
    grid.appendChild(detailInput("商品名称", draft.name, false, (value) => { draft.name = value; }, 128));
    grid.appendChild(detailCategorySelect(draft, context));
    grid.appendChild(detailInput("品牌名称", draft.brandName, false, (value) => { draft.brandName = value; }, 64));
    grid.appendChild(detailStatusSelect(draft));
    grid.appendChild(detailInput("商品副标题", draft.subtitle, false, (value) => { draft.subtitle = value; }, 255, true));
    form.appendChild(grid);
    syncMainImageFromDisplayImages(draft);
    form.appendChild(imageEditor.mainImagePreview(draft));
    form.appendChild(imageEditor.imageListEditor("展示图片", draft.imageUrls, context.rerender, {
      displayImages: true,
      draft,
      allowUploadMain: true
    }));
    form.appendChild(imageEditor.imageListEditor("详情图片", draft.detailImageUrls, context.rerender));
    form.appendChild(attributesEditor(draft));
    form.appendChild(textareaEditor("文字详情", draft.description, (value) => { draft.description = value; }));
    form.appendChild(textareaEditor("售后说明", draft.afterSale, (value) => { draft.afterSale = value; }));
    form.appendChild(skuEditor.render(draft, context.skuContext));
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      context.saveEdit();
    });
    return form;
  }

  function imageManagement(draft, context) {
    syncMainImageFromDisplayImages(draft);
    const shell = document.createElement("div");
    shell.className = "admin-product-detail-content admin-product-image-management-page";
    shell.appendChild(detailToolbar(`${draft.name || "-"} / 图片管理`, [
      actionButton(context.saving ? "保存中" : "保存", () => context.saveImages(), "admin-nav-button", context.saving),
      actionButton("取消", () => context.cancelImageManagement(), "admin-api-back", context.saving),
      actionButton("返回详情", () => context.returnToDetail(), "admin-ghost-button", context.saving)
    ], draft.id));
    shell.appendChild(imageEditor.mainImagePreview(draft));
    shell.appendChild(imageEditor.imageListEditor("展示图片", draft.imageUrls, context.rerender, {
      displayImages: true,
      draft,
      allowUploadMain: true
    }));
    shell.appendChild(imageEditor.imageListEditor("详情图片", draft.detailImageUrls, context.rerender));
    return shell;
  }

  function carousel(product, context) {
    const images = context.carouselController.collectImages(product);
    const shell = document.createElement("div");
    shell.className = "admin-product-detail-content admin-product-carousel-page";
    shell.appendChild(detailToolbar(`${product.name || "-"} / 图片轮播`, [
      actionButton("返回详情", () => context.returnToDetail(), "admin-ghost-button"),
      actionButton("图片管理", () => context.navigateToProductImages(product.id), "admin-ghost-button"),
      actionButton("返回列表", () => context.closeAndNavigate(true), "admin-api-back")
    ], product.id));
    if (!images.length) {
      const empty = document.createElement("div");
      empty.className = "admin-product-detail-empty admin-product-carousel-empty";
      empty.textContent = "当前商品没有可轮播图片。";
      shell.appendChild(empty);
      return { node: shell, stage: null, emptyMessage: empty.textContent };
    }
    const stage = document.createElement("div");
    stage.className = "admin-product-carousel-page-stage";
    shell.appendChild(stage);
    return { node: shell, stage, emptyMessage: "" };
  }

    return {
      readonly,
      edit,
      imageManagement,
      carousel
    };
  }

  root.AdminProductDetailViews = { create };
})(window);
