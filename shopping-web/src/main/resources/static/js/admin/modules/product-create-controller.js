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
    displayImagePayload,
    imageItemUrl,
    escapeAttribute,
    buildSkuNumericPayload
  } = imageUtils;
  const {
    actionButton,
    skuInput,
    pickImageFiles,
    appendImageOrderBadge,
    imageOrderControls
  } = formUi;

  function create(options) {
    const el = options.el;
    const state = {
      tempImage: null,
      imageUrls: [],
      detailImageUrls: [],
      skus: [],
      tempImages: new Map(),
      saving: false
    };

    function bindEvents() {
      el.create?.addEventListener("click", open);
      el.dialogBackdrop?.addEventListener("click", () => close(true));
      el.dialogClose?.addEventListener("click", () => close(true));
      el.cancel?.addEventListener("click", () => close(true));
      el.form?.addEventListener("submit", submit);
      el.categorySearch?.addEventListener("input", () => options.renderDialogCategorySelect());
      el.imagePick?.addEventListener("click", () => el.imageInput?.click());
      el.imageInput?.addEventListener("change", uploadMainImage);
      el.imageRemove?.addEventListener("click", () => cleanupMainImage(true));
    }

    async function open() {
      setBusy(true);
      try {
        await options.loadLeafCategories(true);
        await cleanupMainImage(false);
        await cleanupTempImages(false);
        el.form?.reset();
        if (el.categorySearch) {
          el.categorySearch.value = "";
        }
        options.renderDialogCategorySelect();
        state.tempImage = null;
        state.imageUrls = [];
        state.detailImageUrls = [];
        state.skus = [];
        renderImagePreview();
        renderImageLists();
        renderSkus();
        api.setStatus(el.formStatus, "");
        if (options.getLeafCategories().length === 0) {
          api.setStatus(el.formStatus, "暂无可用叶子分类，请先启用或创建没有子分类的分类。", "error");
        }
        el.editStatus.value = "ACTIVE";
        el.dialog.hidden = false;
        el.dialog.setAttribute("aria-hidden", "false");
        window.setTimeout(() => el.name?.focus(), 0);
      } catch (error) {
        api.setStatus(el.status, error.message || "商品表单打开失败。", "error");
      } finally {
        setBusy(false);
      }
    }

    async function close(cleanup) {
      if (cleanup) {
        await cleanupMainImage(false);
        await cleanupTempImages(false);
      }
      el.dialog.hidden = true;
      el.dialog.setAttribute("aria-hidden", "true");
      api.setStatus(el.formStatus, "");
    }

    function isOpen() {
      return el.dialog && !el.dialog.hidden;
    }

    function ensureEditors() {
      if (!el.form || el.createImagesList) {
        return;
      }
      const grid = el.form.querySelector(".admin-product-category-form-grid");
      if (!grid) {
        return;
      }
      const displaySection = createImageSection("展示图片", "display");
      const detailSection = createImageSection("详情图片", "detail");
      const skuSection = createSkuSection();
      grid.append(displaySection, detailSection, skuSection);
      renderImageLists();
      renderSkus();
    }

    async function uploadMainImage() {
      const file = el.imageInput?.files?.[0];
      if (!file) {
        return;
      }
      if (!file.type || !file.type.startsWith("image/")) {
        api.setStatus(el.formStatus, "商品主图只支持图片文件。", "error");
        el.imageInput.value = "";
        return;
      }
      setBusy(true);
      try {
        await cleanupMainImage(false);
        api.setStatus(el.formStatus, "正在预上传商品主图。");
        const response = await productApi.preuploadImage(file);
        state.tempImage = response.data || null;
        renderImagePreview();
        api.setStatus(el.formStatus, "商品主图已预上传。", "ok");
      } catch (error) {
        api.setStatus(el.formStatus, error.message || "商品主图预上传失败。", "error");
      } finally {
        el.imageInput.value = "";
        setBusy(false);
      }
    }

    async function cleanupMainImage(showStatus) {
      const tempImage = state.tempImage;
      if (!tempImage?.uploadSessionId) {
        renderImagePreview();
        return;
      }
      state.tempImage = null;
      renderImagePreview();
      try {
        await productApi.cancelPreupload(tempImage);
        if (showStatus) {
          api.setStatus(el.formStatus, "临时主图已删除。", "ok");
        }
      } catch (error) {
        if (showStatus) {
          api.setStatus(el.formStatus, error.message || "临时主图删除失败。", "error");
        }
      }
    }

    function renderImagePreview() {
      const tempUrl = state.tempImage?.tempUrl || "";
      if (tempUrl) {
        el.imagePreview.src = tempUrl;
        el.imagePreview.hidden = false;
        el.imageEmpty.hidden = true;
        el.imageRemove.hidden = false;
      } else {
        el.imagePreview.removeAttribute("src");
        el.imagePreview.hidden = true;
        el.imageEmpty.hidden = false;
        el.imageRemove.hidden = true;
      }
    }

    function createImageSection(title, type) {
      const field = document.createElement("div");
      field.className = "admin-risk-ip-field admin-product-category-wide-field admin-product-spu-image-field";
      const label = document.createElement("span");
      label.textContent = title;
      const shell = document.createElement("div");
      shell.className = "admin-product-spu-multi-upload";
      const actions = document.createElement("div");
      actions.className = "admin-product-spu-multi-actions";
      const pick = document.createElement("button");
      pick.className = "admin-ghost-button admin-spring-button";
      pick.type = "button";
      pick.textContent = "批量上传";
      const input = document.createElement("input");
      input.type = "file";
      input.accept = "image/*";
      input.multiple = true;
      input.hidden = true;
      const list = document.createElement("div");
      list.className = "admin-product-spu-multi-list";
      pick.addEventListener("click", () => input.click());
      input.addEventListener("change", async () => {
        await uploadImages(Array.from(input.files || []), type);
        input.value = "";
      });
      actions.append(pick, input);
      shell.append(actions, list);
      field.append(label, shell);
      if (type === "detail") {
        el.createDetailImagesPick = pick;
        el.createDetailImagesList = list;
      } else {
        el.createImagesPick = pick;
        el.createImagesList = list;
      }
      return field;
    }

    function createSkuSection() {
      const field = document.createElement("div");
      field.className = "admin-risk-ip-field admin-product-category-wide-field admin-product-spu-sku-field";
      const heading = document.createElement("div");
      heading.className = "admin-product-detail-section-heading";
      const title = document.createElement("span");
      title.textContent = "SKU";
      const add = document.createElement("button");
      add.className = "admin-ghost-button admin-spring-button";
      add.type = "button";
      add.textContent = "新增 SKU";
      add.addEventListener("click", () => {
        state.skus.push(createEmptySkuDraft());
        renderSkus();
      });
      const list = document.createElement("div");
      list.className = "admin-product-spu-create-sku-list";
      heading.append(title, add);
      field.append(heading, list);
      el.createSkuAdd = add;
      el.createSkuList = list;
      return field;
    }

    function createEmptySkuDraft() {
      return {
        id: "",
        skuCode: "",
        skuName: "",
        specJsonText: "{}",
        skuImageUrls: [],
        priceYuan: "0",
        originalPriceYuan: "",
        stockQuantity: "1",
        status: "ACTIVE"
      };
    }

    async function uploadImages(files, type) {
      const images = files.filter((file) => file?.type?.startsWith("image/"));
      if (!images.length) {
        api.setStatus(el.formStatus, "请选择图片文件。", "error");
        return;
      }
      const target = type === "detail" ? state.detailImageUrls : state.imageUrls;
      setBusy(true);
      try {
        api.setStatus(el.formStatus, `正在预上传 ${images.length} 张图片。`);
        const results = await Promise.allSettled(images.map((file) => uploadOneImage(file)));
        let successCount = 0;
        results.forEach((result) => {
          if (result.status !== "fulfilled" || !result.value?.uploadSessionId || !result.value?.tempUrl) {
            return;
          }
          const uploaded = result.value;
          state.tempImages.set(uploaded.tempUrl, uploaded);
          target.push({ url: uploaded.tempUrl, sort: target.length + 1 });
          successCount += 1;
        });
        renderImageLists();
        if (successCount === images.length) {
          api.setStatus(el.formStatus, `已预上传 ${successCount} 张图片。`, "ok");
        } else {
          api.setStatus(el.formStatus, `已预上传 ${successCount} 张图片，${images.length - successCount} 张失败。`, "error");
        }
      } finally {
        setBusy(false);
      }
    }

    async function uploadOneImage(file) {
      const response = await productApi.preuploadImage(file);
      return response.data || null;
    }

    function renderImageLists() {
      renderImageList(el.createImagesList, state.imageUrls);
      renderImageList(el.createDetailImagesList, state.detailImageUrls);
    }

    function renderImageList(list, items) {
      if (!list) {
        return;
      }
      normalizeImageOrder(items);
      list.replaceChildren();
      if (!items.length) {
        const empty = document.createElement("div");
        empty.className = "admin-product-detail-muted";
        empty.textContent = "未上传";
        list.appendChild(empty);
        return;
      }
      items.forEach((item, index) => {
        const row = document.createElement("div");
        row.className = "admin-product-spu-multi-item";
        const url = imageItemUrl(item);
        const preview = document.createElement("div");
        preview.className = "admin-product-spu-preview";
        preview.innerHTML = url ? `<img src="${escapeAttribute(url)}" alt="商品图片" />` : "<span>-</span>";
        appendImageOrderBadge(preview, index);
        const controls = imageOrderControls(items, index, renderImageLists, async () => {
          await cancelTempByUrl(url);
          items.splice(index, 1);
        });
        row.append(preview, controls);
        list.appendChild(row);
      });
    }

    function renderSkus() {
      const list = el.createSkuList;
      if (!list) {
        return;
      }
      list.replaceChildren();
      if (!state.skus.length) {
        const empty = document.createElement("div");
        empty.className = "admin-product-detail-muted";
        empty.textContent = "暂无 SKU";
        list.appendChild(empty);
        return;
      }
      state.skus.forEach((sku, index) => {
        list.appendChild(createSkuDraftRow(sku, index));
      });
    }

    function createSkuDraftRow(sku, index) {
      const row = document.createElement("article");
      row.className = "admin-product-sku-card admin-product-spu-create-sku-row";
      const header = document.createElement("div");
      header.className = "admin-product-sku-card-top";
      const preview = document.createElement("div");
      preview.className = "admin-product-sku-card-preview";
      const firstImage = imagePayload(sku.skuImageUrls || [])[0] || "";
      preview.innerHTML = firstImage ? `<img src="${escapeAttribute(firstImage)}" alt="${escapeAttribute(sku.skuName || "SKU")}" />` : "<span>-</span>";
      const title = document.createElement("div");
      title.className = "admin-product-sku-card-title";
      title.innerHTML = `<strong>${escapeAttribute(sku.skuName || "新增 SKU")}</strong><span>${escapeAttribute(sku.skuCode || "未填写编码")}</span>`;
      const status = document.createElement("select");
      status.className = "admin-product-sku-card-status";
      ["ACTIVE", "DISABLED"].forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        status.appendChild(option);
      });
      status.value = sku.status;
      status.addEventListener("change", () => { sku.status = status.value; });
      header.append(preview, title, status);
      const fields = document.createElement("div");
      fields.className = "admin-product-sku-card-form";
      fields.append(
        skuInput("SKU 编码", sku.skuCode, (value) => { sku.skuCode = value; }),
        skuInput("SKU 名称", sku.skuName, (value) => { sku.skuName = value; }),
        skuInput("价格(元)", sku.priceYuan, (value) => { sku.priceYuan = value; }, "money"),
        skuInput("原价(元)", sku.originalPriceYuan, (value) => { sku.originalPriceYuan = value; }, "money"),
        skuInput("库存", sku.stockQuantity, (value) => { sku.stockQuantity = value; }, "stock")
      );
      const spec = document.createElement("textarea");
      spec.className = "admin-product-sku-card-spec";
      spec.rows = 3;
      spec.value = sku.specJsonText || "{}";
      spec.addEventListener("input", () => { sku.specJsonText = spec.value; });
      const images = document.createElement("div");
      images.className = "admin-product-sku-card-images";
      images.appendChild(skuImageList(sku, renderSkus));
      const actions = document.createElement("div");
      actions.className = "admin-product-detail-actions admin-product-sku-card-actions";
      actions.append(
        actionButton("批量上传图", async () => uploadSkuImages(sku, renderSkus)),
        actionButton("删除", async () => {
          await cancelSkuTempImages(sku);
          state.skus.splice(index, 1);
          renderSkus();
        }, "admin-api-back")
      );
      row.append(header, fields, images, spec, actions);
      return row;
    }

    function skuImageList(sku, rerender) {
      sku.skuImageUrls = normalizeImageItems(sku.skuImageUrls);
      const list = document.createElement("div");
      list.className = "admin-product-sku-image-list";
      if (!sku.skuImageUrls.length) {
        const empty = document.createElement("div");
        empty.className = "admin-product-detail-edit-preview";
        empty.innerHTML = "<span>-</span>";
        list.appendChild(empty);
        return list;
      }
      sku.skuImageUrls.forEach((item, index) => {
        const row = document.createElement("div");
        row.className = "admin-product-spu-multi-item";
        const url = imageItemUrl(item);
        const preview = document.createElement("div");
        preview.className = "admin-product-detail-edit-preview";
        preview.innerHTML = url ? `<img src="${escapeAttribute(url)}" alt="${escapeAttribute(sku.skuName || "SKU")}" />` : "<span>-</span>";
        appendImageOrderBadge(preview, index);
        const controls = imageOrderControls(sku.skuImageUrls, index, rerender, async () => {
          await cancelTempByUrl(url);
          sku.skuImageUrls.splice(index, 1);
        });
        row.append(preview, controls);
        list.appendChild(row);
      });
      return list;
    }

    async function uploadSkuImages(sku, rerender) {
      const files = await pickImageFiles();
      if (!files.length) {
        return null;
      }
      const images = files.filter((file) => file?.type?.startsWith("image/"));
      if (!images.length) {
        api.setStatus(el.formStatus, "只能上传图片文件。", "error");
        return null;
      }
      setBusy(true);
      try {
        api.setStatus(el.formStatus, `正在预上传 ${images.length} 张 SKU 图片。`);
        const results = await Promise.allSettled(images.map((file) => uploadOneImage(file)));
        let successCount = 0;
        sku.skuImageUrls = normalizeImageItems(sku.skuImageUrls);
        results.forEach((result) => {
          if (result.status !== "fulfilled" || !result.value?.uploadSessionId || !result.value?.tempUrl) {
            return;
          }
          const uploaded = result.value;
          state.tempImages.set(uploaded.tempUrl, uploaded);
          sku.skuImageUrls.push({ url: uploaded.tempUrl, sort: sku.skuImageUrls.length + 1 });
          successCount += 1;
        });
        normalizeImageOrder(sku.skuImageUrls);
        rerender();
        api.setStatus(el.formStatus, `已预上传 ${successCount} 张 SKU 图片。`, successCount === images.length ? "ok" : "error");
        return successCount;
      } catch (error) {
        api.setStatus(el.formStatus, error.message || "SKU 图片预上传失败。", "error");
        return null;
      } finally {
        setBusy(false);
      }
    }

    async function cancelSkuTempImages(sku) {
      const urls = imagePayload(sku?.skuImageUrls || []);
      await Promise.all(urls.map((url) => cancelTempByUrl(url)));
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

    async function cleanupTempImages(showStatus = false) {
      const images = Array.from(state.tempImages.values());
      state.tempImages.clear();
      state.imageUrls = [];
      state.detailImageUrls = [];
      state.skus = [];
      renderImageLists();
      renderSkus();
      await Promise.all(images.map((tempImage) => productApi.cancelPreupload(tempImage).catch(() => null)));
      if (showStatus) {
        api.setStatus(el.formStatus, "临时图片已清理。", "ok");
      }
    }

    async function submit(event) {
      event.preventDefault();
      if (state.saving) {
        return;
      }
      const categoryId = el.category.value || "";
      const name = el.name.value.trim();
      if (!categoryId) {
        api.setStatus(el.formStatus, "请选择启用叶子分类。", "error");
        return;
      }
      if (!name) {
        api.setStatus(el.formStatus, "商品名称不能为空。", "error");
        return;
      }
      if (!state.tempImage?.tempUrl || !state.tempImage?.uploadSessionId) {
        api.setStatus(el.formStatus, "请先上传商品主图。", "error");
        return;
      }
      const createSkus = buildSkuPayload();
      if (createSkus === null) {
        return;
      }
      state.saving = true;
      setBusy(true);
      let detailSaved = true;
      try {
        const createResponse = await productApi.createSpu({
          categoryId,
          name,
          subtitle: el.subtitle.value.trim(),
          brandName: el.brand.value.trim(),
          mainImageTempUrl: state.tempImage.tempUrl,
          uploadSessionId: state.tempImage.uploadSessionId,
          status: el.editStatus.value
        });
        const createdProduct = createResponse.data || {};
        const createdId = String(createdProduct.id || "");
        state.tempImage = null;
        detailSaved = true;
        if (createdId) {
          try {
            const displayImages = buildDisplayImages({
              mainImageUrl: createdProduct.mainImageUrl || "",
              imageUrls: state.imageUrls
            });
            await productApi.updateSpuDetail(createdId, {
              categoryId,
              name,
              subtitle: el.subtitle.value.trim(),
              brandName: el.brand.value.trim(),
              mainImageUrl: createdProduct.mainImageUrl || "",
              status: createdProduct.status || el.editStatus.value,
              imageUrls: displayImagePayload(displayImages),
              detailImageUrls: imagePayload(state.detailImageUrls),
              attributes: {},
              description: null,
              afterSale: null,
              skus: createSkus,
              imageUploadSessions: Array.from(state.tempImages.values())
            });
            state.tempImages.clear();
          } catch (_) {
            detailSaved = false;
            await cleanupTempImages(false);
          }
        }
        state.imageUrls = [];
        state.detailImageUrls = [];
        state.skus = [];
        renderImagePreview();
        renderImageLists();
        renderSkus();
        await close(false);
        if (!detailSaved) {
          options.setPendingDetailStatus({
            message: "商品已创建，但额外图片保存失败，请在详情页继续补充。",
            type: "error"
          });
        }
        if (createdId) {
          options.navigateToProductDetail(createdId);
        } else {
          options.setPage(1);
          await options.loadPage();
        }
        api.setStatus(el.status, detailSaved ? "商品已创建。" : "商品已创建，但额外图片保存失败，请在详情页继续补充。", detailSaved ? "ok" : "error");
      } catch (error) {
        api.setStatus(el.formStatus, error.message || "商品创建失败。", "error");
      } finally {
        state.saving = false;
        setBusy(false);
      }
    }

    function buildSkuPayload() {
      const skus = [];
      for (const sku of state.skus) {
        const skuCode = String(sku.skuCode || "").trim();
        const skuName = String(sku.skuName || "").trim();
        if (!skuCode || !skuName) {
          api.setStatus(el.formStatus, "SKU 编码和名称不能为空。", "error");
          return null;
        }
        let specJson;
        try {
          specJson = JSON.parse(sku.specJsonText || "{}");
        } catch (_) {
          api.setStatus(el.formStatus, "SKU 规格 JSON 格式无效。", "error");
          return null;
        }
        const numeric = buildSkuNumericPayload(sku);
        if (!numeric.ok) {
          api.setStatus(el.formStatus, numeric.message, "error");
          return null;
        }
        skus.push({
          id: null,
          skuCode,
          skuName,
          specJson,
          skuImageUrls: imagePayload(sku.skuImageUrls || []),
          priceYuan: numeric.priceYuan,
          originalPriceYuan: numeric.originalPriceYuan,
          stockQuantity: numeric.stockQuantity,
          status: sku.status || "ACTIVE"
        });
      }
      return skus;
    }

    function setBusy(busy) {
      [el.categorySearch, el.category, el.name, el.subtitle, el.brand, el.editStatus, el.imagePick, el.imageRemove, el.createImagesPick, el.createDetailImagesPick, el.createSkuAdd, el.cancel, el.save].forEach((node) => {
        if (node) {
          node.disabled = busy;
        }
      });
    }

    return {
      bindEvents,
      ensureEditors,
      open,
      close,
      isOpen,
      cleanupTempImages
    };
  }

  root.AdminProductCreateController = { create };
})(window);
