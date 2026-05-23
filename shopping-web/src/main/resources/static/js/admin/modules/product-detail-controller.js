(function (root) {
  const api = root.AdminApi;
  const productApi = root.AdminProductApi;
  const imageUtils = root.AdminProductImageUtils;
  const formUi = root.AdminProductFormUi;
  const {
    normalizeImageItems,
    normalizeImageOrder,
    imagePayload,
    buildDisplayImages,
    syncMainImageFromDisplayImages,
    displayImagePayload,
    imageUrlsFromNode,
    imageItemUrl,
    setImageItemUrl,
    formatJson,
    integerOrZero,
    escapeHtml,
    escapeAttribute,
    formatDate
  } = imageUtils;
  const {
    actionButton,
    labelSpan,
    skuInput,
    pickImageFile,
    pickImageFiles,
    appendImageOrderBadge,
    imageOrderControls
  } = formUi;

  function create(options) {
    const el = options.el;
    const state = {
      product: null,
      draft: null,
      editing: false,
      tempImages: new Map(),
      page: null,
      pendingStatus: null,
      saving: false,
      mode: "detail"
    };
    const carouselController = root.AdminProductCarouselController.create({
      modulePath: options.carouselModulePath,
      setStatus: (message, type) => api.setStatus(detailStatus(), message, type)
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
      await cleanupTempImages();
      state.product = null;
      state.draft = null;
      state.editing = false;
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
        await cleanupTempImages();
      }
      if (state.page) {
        state.page.hidden = true;
      }
      hideUploadProgress();
      state.product = null;
      state.draft = null;
      state.editing = false;
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

    function createUploadProgressTracker(files) {
      const records = files.map((file) => ({
        loaded: 0,
        total: Math.max(0, Number(file?.size) || 0),
        status: "uploading"
      }));
      const totalBytes = records.reduce((sum, record) => sum + record.total, 0);
      let lastLoaded = 0;
      let lastAt = performance.now();
      let speedBytesPerSecond = 0;

      function loadedBytes() {
        return records.reduce((sum, record) => sum + Math.max(0, Math.min(record.loaded, record.total || record.loaded)), 0);
      }

      function completedCount() {
        return records.filter((record) => record.status === "success").length;
      }

      function failedCount() {
        return records.filter((record) => record.status === "failed").length;
      }

      function currentOrdinal() {
        const index = records.findIndex((record) => record.status !== "success" && record.status !== "failed");
        return index === -1 ? records.length : index + 1;
      }

      function refresh(forcedTitle = "") {
        const now = performance.now();
        const loaded = loadedBytes();
        const elapsed = now - lastAt;
        if (elapsed >= 250) {
          speedBytesPerSecond = Math.max(0, (loaded - lastLoaded) / (elapsed / 1000));
          lastLoaded = loaded;
          lastAt = now;
        }
        const percent = totalBytes > 0
          ? (loaded / totalBytes) * 100
          : records.length > 0
            ? ((completedCount() + failedCount()) / records.length) * 100
            : 0;
        const hasUploading = records.some((record) => record.status === "uploading");
        const hasProcessing = records.some((record) => record.status === "processing");
        const title = forcedTitle
          || (hasUploading ? `正在上传 ${currentOrdinal()} / ${records.length}`
            : hasProcessing ? "正在写入 OSS..."
              : `已上传 ${completedCount()} 张图片`);
        renderUploadProgress({
          title,
          loadedBytes: loaded,
          totalBytes,
          percent,
          speedBytesPerSecond,
          speedLabel: hasProcessing ? "等待 OSS 返回" : "",
          phase: hasProcessing ? "processing" : hasUploading ? "uploading" : "done"
        });
      }

      return {
        start() {
          lastLoaded = 0;
          lastAt = performance.now();
          speedBytesPerSecond = 0;
          refresh();
        },
        progress(index, event) {
          const record = records[index];
          if (!record) {
            return;
          }
          record.status = "uploading";
          if (event?.lengthComputable && Number(event.total) > 0) {
            record.total = Number(event.total);
          }
          record.loaded = Math.max(0, Number(event?.loaded) || 0);
          refresh();
        },
        uploadDone(index) {
          const record = records[index];
          if (!record || record.status === "success" || record.status === "failed") {
            return;
          }
          record.loaded = record.total || record.loaded;
          record.status = "processing";
          refresh("正在写入 OSS...");
        },
        success(index) {
          const record = records[index];
          if (!record) {
            return;
          }
          record.loaded = record.total || record.loaded;
          record.status = "success";
          refresh();
        },
        failure(index) {
          const record = records[index];
          if (!record) {
            return;
          }
          record.status = "failed";
          refresh();
        },
        finish(successCount, failCount) {
          const title = failCount > 0
            ? `已上传 ${successCount} 张图片，${failCount} 张失败或不是图片`
            : `已上传 ${successCount} 张图片`;
          renderUploadProgress({
            title,
            loadedBytes: loadedBytes(),
            totalBytes,
            percent: records.length ? 100 : 0,
            speedBytesPerSecond: 0,
            speedLabel: "完成",
            phase: failCount > 0 ? "error" : "done"
          });
        }
      };
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

    function renderReadonly() {
      const product = state.product;
      const body = detailBody();
      if (!body) {
        return;
      }
      api.setStatus(detailStatus(), "");
      body.innerHTML = "";
      const carouselImages = carouselController.collectImages(product);
      const shell = document.createElement("div");
      shell.className = "admin-product-detail-content";
      shell.appendChild(detailToolbar(product.name || "-", [
        actionButton("图片轮播", () => options.navigateToProductCarousel(product.id), "admin-ghost-button", carouselImages.length === 0),
        actionButton("图片管理", () => options.navigateToProductImages(product.id), "admin-ghost-button"),
        actionButton("修改商品详情", () => enterEdit(product)),
        actionButton("返回列表", () => closeAndNavigate(true), "admin-api-back")
      ]));
      shell.appendChild(readonlyMetaGrid(product));
      shell.appendChild(readonlyImageSection("展示图片", buildDisplayImages(product), true));
      shell.appendChild(readonlyImageSection("详情图片", imageUrlsFromNode(product.detailImageUrls)));
      shell.appendChild(readonlySkuTable(product.skus || []));
      shell.appendChild(readonlyTextPanel("商品参数", formatJson(product.attributes || {})));
      shell.appendChild(readonlyTextPanel("文字详情", product.description || "-"));
      shell.appendChild(readonlyTextPanel("售后说明", product.afterSale || "-"));
      body.appendChild(shell);
      carouselController.schedulePrewarm(product);
    }

    function renderEdit() {
      carouselController.destroy();
      carouselController.cancelPrewarm();
      const draft = state.draft || buildDraft(state.product);
      state.draft = draft;
      const body = detailBody();
      if (!body) {
        return;
      }
      api.setStatus(detailStatus(), "");
      body.innerHTML = "";
      const form = document.createElement("form");
      form.className = "admin-product-detail-content admin-product-detail-form";
      form.appendChild(detailToolbar(draft.name || "-", [
        actionButton("取消修改", () => cancelEdit(), "admin-api-back"),
        actionButton(state.saving ? "保存中" : "保存修改", () => saveEdit(), "admin-nav-button", state.saving)
      ]));
      const grid = document.createElement("div");
      grid.className = "admin-product-detail-edit-grid";
      grid.appendChild(detailInput("商品名称", draft.name, true, (value) => { draft.name = value; }));
      grid.appendChild(detailCategorySelect(draft));
      grid.appendChild(detailInput("品牌名称", draft.brandName, false, (value) => { draft.brandName = value; }, 64));
      grid.appendChild(detailStatusSelect(draft));
      grid.appendChild(detailInput("商品副标题", draft.subtitle, false, (value) => { draft.subtitle = value; }, 255, true));
      form.appendChild(grid);
      syncMainImageFromDisplayImages(draft);
      form.appendChild(mainImagePreview(draft));
      form.appendChild(imageListEditor("展示图片", draft.imageUrls, () => renderEdit(), {
        displayImages: true,
        draft,
        allowUploadMain: true
      }));
      form.appendChild(imageListEditor("详情图片", draft.detailImageUrls, () => renderEdit()));
      form.appendChild(attributesEditor(draft));
      form.appendChild(textareaEditor("文字详情", draft.description, (value) => { draft.description = value; }));
      form.appendChild(textareaEditor("售后说明", draft.afterSale, (value) => { draft.afterSale = value; }));
      form.appendChild(skuEditor(draft));
      form.addEventListener("submit", (event) => {
        event.preventDefault();
        saveEdit();
      });
      body.appendChild(form);
    }

    function renderImageManagement() {
      carouselController.destroy();
      carouselController.cancelPrewarm();
      const draft = state.draft || buildDraft(state.product);
      state.draft = draft;
      syncMainImageFromDisplayImages(draft);
      const body = detailBody();
      if (!body) {
        return;
      }
      api.setStatus(detailStatus(), "");
      body.innerHTML = "";
      const shell = document.createElement("div");
      shell.className = "admin-product-detail-content admin-product-image-management-page";
      shell.appendChild(detailToolbar(`${draft.name || "-"} / 图片管理`, [
        actionButton(state.saving ? "保存中" : "保存", () => saveImages(), "admin-nav-button", state.saving),
        actionButton("取消", () => cancelImageManagement(), "admin-api-back", state.saving),
        actionButton("返回详情", () => returnToDetail(), "admin-ghost-button", state.saving)
      ]));
      shell.appendChild(mainImagePreview(draft));
      shell.appendChild(imageListEditor("展示图片", draft.imageUrls, () => renderImageManagement(), {
        displayImages: true,
        draft,
        allowUploadMain: true
      }));
      shell.appendChild(imageListEditor("详情图片", draft.detailImageUrls, () => renderImageManagement()));
      body.appendChild(shell);
    }

    function renderCarouselPage() {
      carouselController.destroy();
      carouselController.cancelPrewarm();
      const product = state.product;
      const body = detailBody();
      if (!body) {
        return;
      }
      const images = carouselController.collectImages(product);
      api.setStatus(detailStatus(), "");
      body.innerHTML = "";
      const shell = document.createElement("div");
      shell.className = "admin-product-detail-content admin-product-carousel-page";
      shell.appendChild(detailToolbar(`${product.name || "-"} / 图片轮播`, [
        actionButton("返回详情", () => returnToDetail(), "admin-ghost-button"),
        actionButton("图片管理", () => options.navigateToProductImages(product.id), "admin-ghost-button"),
        actionButton("返回列表", () => closeAndNavigate(true), "admin-api-back")
      ]));
      if (!images.length) {
        const empty = document.createElement("div");
        empty.className = "admin-product-detail-empty admin-product-carousel-empty";
        empty.textContent = "当前商品没有可轮播图片。";
        shell.appendChild(empty);
        body.appendChild(shell);
        api.setStatus(detailStatus(), "当前商品没有可轮播图片。", "error");
        return;
      }
      const stage = document.createElement("div");
      stage.className = "admin-product-carousel-page-stage";
      shell.appendChild(stage);
      body.appendChild(shell);
      carouselController.mount(stage, product);
    }

    async function enterEdit(product) {
      try {
        api.setStatus(detailStatus(), "正在加载叶子分类。");
        await options.loadLeafCategories();
        state.editing = true;
        state.draft = buildDraft(product);
        render();
      } catch (error) {
        api.setStatus(detailStatus(), error.message || "叶子分类加载失败，暂时不能修改商品详情。", "error");
      }
    }

    function detailToolbar(title, buttons) {
      const toolbar = document.createElement("div");
      toolbar.className = "admin-product-detail-toolbar";
      const heading = document.createElement("div");
      heading.className = "admin-product-detail-title";
      const strong = document.createElement("strong");
      strong.textContent = title;
      const small = document.createElement("small");
      small.textContent = `ID ${state.product?.id || ""}`;
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

    function readonlySkuTable(skus) {
      const section = document.createElement("section");
      section.className = "admin-product-detail-section";
      section.innerHTML = `<h3>SKU</h3>`;
      const table = document.createElement("div");
      table.className = "admin-product-detail-sku-table";
      table.appendChild(skuReadonlyRow(["图片", "名称", "编码", "价格", "库存", "状态"], true));
      if (!skus.length) {
        const empty = document.createElement("div");
        empty.className = "admin-product-detail-empty-row";
        empty.textContent = "暂无 SKU";
        table.appendChild(empty);
      }
      skus.forEach((sku) => {
        table.appendChild(skuReadonlyRow([
          sku.skuImageUrl || "",
          sku.skuName || "-",
          sku.skuCode || "-",
          `${sku.priceCent ?? 0}`,
          `${sku.stockQuantity ?? 0}`,
          sku.status || "-"
        ], false, sku));
      });
      section.appendChild(table);
      return section;
    }

    function skuReadonlyRow(values, header, sku = null) {
      const row = document.createElement("div");
      row.className = `admin-product-detail-sku-row${header ? " is-header" : ""}`;
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

    function buildDraft(product) {
      return {
        id: String(product?.id || ""),
        categoryId: String(product?.categoryId || ""),
        name: product?.name || "",
        subtitle: product?.subtitle || "",
        brandName: product?.brandName || "",
        mainImageUrl: product?.mainImageUrl || "",
        status: product?.status || "ACTIVE",
        imageUrls: buildDisplayImages(product),
        detailImageUrls: normalizeImageItems(product?.detailImageUrls),
        attributesText: formatJson(product?.attributes || {}),
        description: product?.description || "",
        afterSale: product?.afterSale || "",
        skus: (Array.isArray(product?.skus) ? product.skus : []).map((sku) => ({
          id: sku.id ? String(sku.id) : "",
          skuCode: sku.skuCode || "",
          skuName: sku.skuName || "",
          specJsonText: formatJson(sku.specJson || {}),
          skuImageUrl: sku.skuImageUrl || "",
          priceCent: String(sku.priceCent ?? 0),
          originalPriceCent: sku.originalPriceCent == null ? "" : String(sku.originalPriceCent),
          stockQuantity: String(sku.stockQuantity ?? 0),
          status: sku.status || "ACTIVE"
        }))
      };
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

    function detailCategorySelect(draft) {
      const field = document.createElement("label");
      field.className = "admin-risk-ip-field";
      const select = document.createElement("select");
      options.getLeafCategories().forEach((category) => {
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

    function mainImagePreview(draft) {
      const section = document.createElement("section");
      section.className = "admin-product-detail-section";
      section.innerHTML = `<h3>当前主图</h3>`;
      const editor = document.createElement("div");
      editor.className = "admin-product-detail-image-editor";
      const preview = document.createElement("div");
      preview.className = "admin-product-detail-edit-preview admin-product-main-image-preview";
      preview.innerHTML = draft.mainImageUrl ? `<img src="${escapeAttribute(draft.mainImageUrl)}" alt="主图" />` : `<span>-</span>`;
      const actions = document.createElement("div");
      actions.className = "admin-product-detail-actions admin-product-main-image-actions";
      const hint = document.createElement("span");
      hint.className = "admin-product-detail-muted admin-product-main-image-hint";
      hint.textContent = "主图由展示图片第 1 张决定。";
      actions.appendChild(hint);
      editor.append(preview, actions);
      section.appendChild(editor);
      return section;
    }

    function imageListEditor(title, items, rerender, editorOptions = {}) {
      const section = document.createElement("section");
      section.className = "admin-product-detail-section";
      normalizeImageOrder(items);
      if (editorOptions.displayImages && editorOptions.draft) {
        syncMainImageFromDisplayImages(editorOptions.draft);
      }
      const heading = document.createElement("div");
      heading.className = "admin-product-detail-section-heading";
      heading.innerHTML = `<h3>${escapeHtml(title)}</h3>`;
      const headingActions = document.createElement("div");
      headingActions.className = "admin-product-detail-actions";
      if (editorOptions.allowUploadMain) {
        headingActions.appendChild(actionButton("上传新主图", async () => uploadMainIntoDisplay(editorOptions.draft, rerender)));
      }
      headingActions.appendChild(actionButton("批量上传图片", async () => uploadImages(items, () => {
        if (editorOptions.displayImages && editorOptions.draft) {
          syncMainImageFromDisplayImages(editorOptions.draft);
        }
        rerender();
      })));
      heading.appendChild(headingActions);
      section.appendChild(heading);
      const list = document.createElement("div");
      list.className = "admin-product-detail-edit-images";
      if (!items.length) {
        list.innerHTML = `<span class="admin-product-detail-muted">-</span>`;
      }
      items.forEach((item, index) => {
        const row = document.createElement("div");
        row.className = "admin-product-detail-edit-image-row";
        row.draggable = items.length > 1;
        row.dataset.imageIndex = String(index);
        row.classList.toggle("is-main", Boolean(editorOptions.displayImages && index === 0));
        const url = imageItemUrl(item);
        row.innerHTML = `
          <div class="admin-product-detail-edit-preview">${url ? `<img src="${escapeAttribute(url)}" alt="${escapeAttribute(title)}" />` : "<span>-</span>"}</div>
          <input type="url" value="${escapeAttribute(url)}" />
        `;
        const preview = row.querySelector(".admin-product-detail-edit-preview");
        preview.classList.add("admin-product-image-drag-handle");
        preview.draggable = items.length > 1;
        preview.querySelector("img")?.setAttribute("draggable", "false");
        appendImageOrderBadge(preview, index, editorOptions.displayImages && index === 0 ? "主图 / #1" : `#${index + 1}`);
        const input = row.querySelector("input");
        input.draggable = false;
        input.addEventListener("input", () => {
          setImageItemUrl(item, input.value.trim());
          if (editorOptions.displayImages && editorOptions.draft) {
            syncMainImageFromDisplayImages(editorOptions.draft);
          }
        });
        row.appendChild(imageEditorControls(items, index, rerender, async () => {
          await cancelTempByUrl(imageItemUrl(item));
          items.splice(index, 1);
          if (editorOptions.displayImages && editorOptions.draft) {
            syncMainImageFromDisplayImages(editorOptions.draft);
          }
        }, editorOptions));
        bindImageDragSorting(row, preview, items, index, rerender, editorOptions);
        list.appendChild(row);
      });
      section.appendChild(list);
      return section;
    }

    function bindImageDragSorting(row, dragHandle, items, index, rerender, editorOptions = {}) {
      if (!row || !dragHandle || !Array.isArray(items) || items.length < 2) {
        return;
      }
      const syncDisplayImages = () => {
        if (editorOptions.displayImages && editorOptions.draft) {
          syncMainImageFromDisplayImages(editorOptions.draft);
        }
      };
      const clearDragState = () => {
        row.parentElement?.querySelectorAll(".admin-product-detail-edit-image-row.is-dragging, .admin-product-detail-edit-image-row.is-drag-over")
          .forEach((item) => item.classList.remove("is-dragging", "is-drag-over"));
      };
      row.querySelectorAll("input, button, textarea, select").forEach((node) => {
        node.draggable = false;
      });
      row.addEventListener("dragstart", (event) => {
        if (!event.target?.closest?.(".admin-product-image-drag-handle")) {
          event.preventDefault();
          return;
        }
        row.classList.add("is-dragging");
        event.dataTransfer.effectAllowed = "move";
        event.dataTransfer.setData("text/plain", String(index));
      });
      row.addEventListener("dragover", (event) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = "move";
      });
      row.addEventListener("dragenter", (event) => {
        event.preventDefault();
        row.classList.add("is-drag-over");
      });
      row.addEventListener("dragleave", (event) => {
        if (event.relatedTarget && row.contains(event.relatedTarget)) {
          return;
        }
        row.classList.remove("is-drag-over");
      });
      row.addEventListener("drop", (event) => {
        event.preventDefault();
        const fromIndex = Number.parseInt(event.dataTransfer.getData("text/plain"), 10);
        const toIndex = Number.parseInt(row.dataset.imageIndex || String(index), 10);
        clearDragState();
        if (!Number.isFinite(fromIndex) || !Number.isFinite(toIndex) || fromIndex === toIndex) {
          return;
        }
        imageUtils.moveImageItem(items, fromIndex, toIndex);
        syncDisplayImages();
        rerender();
      });
      row.addEventListener("dragend", clearDragState);
    }

    function imageEditorControls(items, index, rerender, onRemove, editorOptions = {}) {
      const controls = imageOrderControls(items, index, () => {
        if (editorOptions.displayImages && editorOptions.draft) {
          syncMainImageFromDisplayImages(editorOptions.draft);
        }
        rerender();
      }, onRemove);
      if (editorOptions.displayImages) {
        controls.insertBefore(actionButton("设为主图", () => {
          imageUtils.moveImageItem(items, index, 0);
          if (editorOptions.draft) {
            syncMainImageFromDisplayImages(editorOptions.draft);
          }
          rerender();
        }, "admin-product-image-order-button", index === 0), controls.firstChild);
      }
      return controls;
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

    function skuEditor(draft) {
      const section = document.createElement("section");
      section.className = "admin-product-detail-section";
      const heading = document.createElement("div");
      heading.className = "admin-product-detail-section-heading";
      heading.innerHTML = `<h3>SKU</h3>`;
      heading.appendChild(actionButton("新增 SKU", () => {
        draft.skus.push({
          id: "",
          skuCode: "",
          skuName: "",
          specJsonText: "{}",
          skuImageUrl: "",
          priceCent: "0",
          originalPriceCent: "",
          stockQuantity: "0",
          status: "ACTIVE"
        });
        renderEdit();
      }));
      section.appendChild(heading);
      const list = document.createElement("div");
      list.className = "admin-product-detail-sku-edit-list";
      if (!draft.skus.length) {
        list.innerHTML = `<div class="admin-product-detail-empty-row">暂无 SKU</div>`;
      }
      draft.skus.forEach((sku, index) => list.appendChild(skuEditRow(draft, sku, index)));
      section.appendChild(list);
      return section;
    }

    function skuEditRow(draft, sku, index) {
      const row = document.createElement("div");
      row.className = "admin-product-detail-sku-edit-row";
      const image = document.createElement("div");
      image.className = "admin-product-detail-edit-preview";
      image.innerHTML = sku.skuImageUrl ? `<img src="${escapeAttribute(sku.skuImageUrl)}" alt="${escapeAttribute(sku.skuName || "SKU")}" />` : `<span>-</span>`;
      row.appendChild(image);
      row.appendChild(skuInput("SKU 编码", sku.skuCode, (value) => { sku.skuCode = value; }));
      row.appendChild(skuInput("SKU 名称", sku.skuName, (value) => { sku.skuName = value; }));
      row.appendChild(skuInput("价格(分)", sku.priceCent, (value) => { sku.priceCent = value; }, "number"));
      row.appendChild(skuInput("原价(分)", sku.originalPriceCent, (value) => { sku.originalPriceCent = value; }, "number"));
      row.appendChild(skuInput("库存", sku.stockQuantity, (value) => { sku.stockQuantity = value; }, "number"));
      const status = document.createElement("select");
      ["ACTIVE", "DISABLED"].forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        status.appendChild(option);
      });
      status.value = sku.status;
      status.addEventListener("change", () => { sku.status = status.value; });
      row.appendChild(status);
      const spec = document.createElement("textarea");
      spec.rows = 3;
      spec.value = sku.specJsonText || "{}";
      spec.addEventListener("input", () => { sku.specJsonText = spec.value; });
      row.appendChild(spec);
      const actions = document.createElement("div");
      actions.className = "admin-product-detail-actions";
      actions.append(
        actionButton("上传图", async () => {
          const uploaded = await uploadImage();
          if (uploaded) {
            await cancelTempByUrl(sku.skuImageUrl);
            sku.skuImageUrl = uploaded.tempUrl || "";
            renderEdit();
          }
        }),
        actionButton("删除", async () => {
          await cancelTempByUrl(sku.skuImageUrl);
          draft.skus.splice(index, 1);
          renderEdit();
        }, "admin-api-back")
      );
      row.appendChild(actions);
      return row;
    }

    async function uploadImage() {
      const file = await pickImageFile();
      if (!file) {
        return null;
      }
      if (!file.type || !file.type.startsWith("image/")) {
        api.setStatus(detailStatus(), "只能上传图片文件。", "error");
        return null;
      }
      const tracker = createUploadProgressTracker([file]);
      tracker.start();
      try {
        api.setStatus(detailStatus(), "正在预上传图片。");
        const response = await productApi.preuploadImage(file, {
          onUploadProgress: (event) => tracker.progress(0, event),
          onUploadDone: () => tracker.uploadDone(0)
        });
        const uploaded = response.data || null;
        if (uploaded?.uploadSessionId && uploaded?.tempUrl) {
          state.tempImages.set(uploaded.tempUrl, uploaded);
        }
        tracker.success(0);
        tracker.finish(uploaded?.uploadSessionId && uploaded?.tempUrl ? 1 : 0, uploaded?.uploadSessionId && uploaded?.tempUrl ? 0 : 1);
        api.setStatus(detailStatus(), "图片已预上传。", "ok");
        return uploaded;
      } catch (error) {
        tracker.failure(0);
        tracker.finish(0, 1);
        api.setStatus(detailStatus(), error.message || "图片预上传失败。", "error");
        return null;
      }
    }

    async function uploadImages(items, rerender) {
      const files = await pickImageFiles();
      if (!files.length) {
        return;
      }
      const images = files.filter((file) => file?.type?.startsWith("image/"));
      if (!images.length) {
        api.setStatus(detailStatus(), "请选择图片文件。", "error");
        return;
      }
      const tracker = createUploadProgressTracker(images);
      tracker.start();
      api.setStatus(detailStatus(), `正在预上传 ${images.length} 张图片。`);
      const results = await Promise.allSettled(images.map((file, index) => productApi.preuploadImage(file, {
        onUploadProgress: (event) => tracker.progress(index, event),
        onUploadDone: () => tracker.uploadDone(index)
      }).then((response) => {
        tracker.success(index);
        return response;
      }).catch((error) => {
        tracker.failure(index);
        throw error;
      })));
      let successCount = 0;
      results.forEach((result) => {
        const uploaded = result.status === "fulfilled" ? result.value?.data : null;
        if (!uploaded?.uploadSessionId || !uploaded?.tempUrl) {
          return;
        }
        state.tempImages.set(uploaded.tempUrl, uploaded);
        items.push({ url: uploaded.tempUrl, sort: items.length + 1 });
        successCount += 1;
      });
      normalizeImageOrder(items);
      rerender();
      const failedCount = files.length - successCount;
      tracker.finish(successCount, failedCount);
      if (successCount === images.length && failedCount === 0) {
        api.setStatus(detailStatus(), `已预上传 ${successCount} 张图片。`, "ok");
      } else {
        api.setStatus(detailStatus(), `已预上传 ${successCount} 张图片，${failedCount} 张失败或不是图片。`, "error");
      }
    }

    async function uploadMainIntoDisplay(draft, rerender) {
      if (!draft) {
        return;
      }
      const uploaded = await uploadImage();
      if (!uploaded?.tempUrl) {
        return;
      }
      draft.imageUrls = Array.isArray(draft.imageUrls) ? draft.imageUrls : [];
      draft.imageUrls.unshift({ url: uploaded.tempUrl, sort: 1 });
      syncMainImageFromDisplayImages(draft);
      rerender();
    }

    async function cancelTempByUrl(url) {
      const key = String(url || "");
      const tempImage = state.tempImages.get(key);
      if (!tempImage) {
        return;
      }
      state.tempImages.delete(key);
      try {
        await productApi.cancelPreupload(tempImage);
      } catch (_) {
      }
    }

    async function cleanupTempImages() {
      const images = Array.from(state.tempImages.values());
      state.tempImages.clear();
      await Promise.all(images.map((tempImage) => productApi.cancelPreupload(tempImage).catch(() => null)));
    }

    async function cancelEdit() {
      await cleanupTempImages();
      state.editing = false;
      state.draft = null;
      render();
    }

    async function cancelImageManagement() {
      await cleanupTempImages();
      state.draft = buildDraft(state.product);
      renderImageManagement();
      api.setStatus(detailStatus(), "已取消未保存的图片修改。", "ok");
    }

    async function returnToDetail() {
      const productId = state.product?.id || state.draft?.id;
      carouselController.destroy();
      carouselController.cancelPrewarm();
      await cleanupTempImages();
      state.draft = null;
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
      syncMainImageFromDisplayImages(draft);
      let attributes;
      try {
        attributes = JSON.parse(draft.attributesText || "{}");
      } catch (_) {
        api.setStatus(detailStatus(), "商品参数 JSON 格式无效。", "error");
        return;
      }
      const skus = [];
      for (const sku of draft.skus) {
        let specJson;
        try {
          specJson = JSON.parse(sku.specJsonText || "{}");
        } catch (_) {
          api.setStatus(detailStatus(), "SKU 规格 JSON 格式无效。", "error");
          return;
        }
        skus.push({
          id: sku.id || null,
          skuCode: sku.skuCode.trim(),
          skuName: sku.skuName.trim(),
          specJson,
          skuImageUrl: sku.skuImageUrl.trim(),
          priceCent: integerOrZero(sku.priceCent),
          originalPriceCent: sku.originalPriceCent === "" ? null : integerOrZero(sku.originalPriceCent),
          stockQuantity: integerOrZero(sku.stockQuantity),
          status: sku.status
        });
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
        const response = await productApi.updateSpuDetail(draft.id, {
          categoryId: draft.categoryId,
          subtitle: draft.subtitle.trim(),
          brandName: draft.brandName.trim(),
          mainImageUrl: draft.mainImageUrl.trim(),
          status: draft.status,
          imageUrls: displayImagePayload(draft.imageUrls),
          detailImageUrls: imagePayload(draft.detailImageUrls),
          attributes,
          description: draft.description,
          afterSale: draft.afterSale,
          skus,
          imageUploadSessions: Array.from(state.tempImages.values())
        });
        state.tempImages.clear();
        state.product = response.data || state.product;
        state.draft = null;
        state.editing = false;
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
