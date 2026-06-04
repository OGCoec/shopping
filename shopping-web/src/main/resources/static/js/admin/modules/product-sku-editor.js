(function (root) {
  function create(options = {}) {
    const formUi = options.formUi || root.AdminProductFormUi;
    const imageUtils = options.imageUtils || root.AdminProductImageUtils;
    const imageEditor = options.imageEditor;
    const {
      actionButton,
      skuInput
    } = formUi;
    const {
      imagePayload,
      normalizeImageItems,
      imageItemUrl,
      escapeAttribute,
      buildSkuNumericPayload
    } = imageUtils;

    function isSaving(context) {
      return typeof context.isSaving === "function" ? context.isSaving() : Boolean(context.saving);
    }

    function setSaving(context, saving) {
      if (typeof context.setSaving === "function") {
        context.setSaving(saving);
      } else {
        context.saving = Boolean(saving);
      }
    }

    function setStatus(context, message, type = "") {
      if (typeof context.setStatus === "function") {
        context.setStatus(message, type);
      }
    }

    function ensureSelectionState(draft, context) {
      if (!context.selectedKeys) {
        context.selectedKeys = new Set();
      }
      if (!draft || !Array.isArray(draft.skus)) {
        context.selectedKeys.clear();
        return;
      }
      const activeKeys = new Set();
      draft.skus.forEach((sku) => {
        if (!sku.clientKey) {
          sku.clientKey = context.allocateSkuClientKey(sku);
        }
        activeKeys.add(sku.clientKey);
      });
      Array.from(context.selectedKeys).forEach((key) => {
        if (!activeKeys.has(key)) {
          context.selectedKeys.delete(key);
        }
      });
    }

    function selectedSkus(draft, context) {
      ensureSelectionState(draft, context);
      return draft.skus.filter((sku) => context.selectedKeys.has(sku.clientKey));
    }

    function setAllSkuSelection(draft, selected, context) {
      ensureSelectionState(draft, context);
      draft.skus.forEach((sku) => {
        if (selected) {
          context.selectedKeys.add(sku.clientKey);
        } else {
          context.selectedKeys.delete(sku.clientKey);
        }
      });
      context.rerender();
    }

    function setSkuSelection(clientKey, selected, context) {
      if (!clientKey) {
        return;
      }
      if (selected) {
        context.selectedKeys.add(clientKey);
      } else {
        context.selectedKeys.delete(clientKey);
      }
      context.rerender();
    }

    function productId(context) {
      return typeof context.productId === "function" ? context.productId() : String(context.productId || "");
    }

    function createEmptySkuDraft() {
      return {
        clientKey: "",
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

    function toEditableSku(raw, context, fallback = null) {
      const sku = raw || {};
      return {
        clientKey: fallback?.clientKey || context.allocateSkuClientKey(sku),
        id: sku.id ? String(sku.id) : "",
        skuCode: sku.skuCode || "",
        skuName: sku.skuName || "",
        specJsonText: formatJson(sku.specJson || {}),
        skuImageUrls: normalizeImageItems(sku.skuImageUrls),
        priceYuan: String(sku.priceYuan ?? 0),
        originalPriceYuan: sku.originalPriceYuan == null ? "" : String(sku.originalPriceYuan),
        stockQuantity: String(sku.stockQuantity ?? 0),
        status: sku.status || "ACTIVE"
      };
    }

    function formatJson(value) {
      try {
        return JSON.stringify(value || {}, null, 2);
      } catch (_) {
        return "{}";
      }
    }

    function imageUploadSessionsForSku(sku, context) {
      const urls = new Set(imagePayload(sku?.skuImageUrls || []));
      const sessions = typeof context.imageUploadSessions === "function" ? context.imageUploadSessions() : [];
      return sessions.filter((session) => urls.has(session?.tempUrl));
    }

    function clearCommittedSkuImages(sku, context) {
      if (typeof context.clearCommittedByUrls !== "function") {
        return;
      }
      context.clearCommittedByUrls(imagePayload(sku?.skuImageUrls || []));
    }

    function buildSkuPayload(sku, context, setDialogStatus) {
      const skuCode = String(sku.skuCode || "").trim();
      const skuName = String(sku.skuName || "").trim();
      if (!skuCode || !skuName) {
        setDialogStatus("SKU 编码和名称不能为空。", "error");
        return null;
      }
      let specJson;
      try {
        specJson = JSON.parse(sku.specJsonText || "{}");
      } catch (_) {
        setDialogStatus("SKU 规格 JSON 格式无效。", "error");
        return null;
      }
      const numeric = buildSkuNumericPayload(sku);
      if (!numeric.ok) {
        setDialogStatus(numeric.message, "error");
        return null;
      }
      return {
        skuCode,
        skuName,
        specJson,
        skuImageUrls: imagePayload(sku.skuImageUrls || []),
        priceYuan: numeric.priceYuan,
        originalPriceYuan: numeric.originalPriceYuan,
        stockQuantity: numeric.stockQuantity,
        status: sku.status || "ACTIVE",
        imageUploadSessions: imageUploadSessionsForSku(sku, context)
      };
    }

    async function batchUpdateSkuStatus(draft, status, context) {
      if (isSaving(context)) {
        return;
      }
      const ids = selectedSkus(draft, context).map((sku) => sku.id).filter(Boolean);
      if (!ids.length) {
        return;
      }
      const spuId = productId(context);
      setSaving(context, true);
      context.rerender();
      try {
        await context.productApi.batchChangeSkuStatus(spuId, ids, status);
        await context.refreshAfterSkuChange("SKU 状态已批量更新。");
      } catch (error) {
        setStatus(context, error.message || "SKU 状态批量更新失败。", "error");
        context.rerender();
      } finally {
        setSaving(context, false);
        context.rerender();
      }
    }

    async function batchDeleteSelectedSkus(draft, context) {
      if (isSaving(context)) {
        return;
      }
      const ids = selectedSkus(draft, context).map((sku) => sku.id).filter(Boolean);
      if (!ids.length) {
        return;
      }
      const spuId = productId(context);
      setSaving(context, true);
      context.rerender();
      try {
        await context.productApi.batchDeleteSku(spuId, ids);
        await context.refreshAfterSkuChange("SKU 已批量删除。");
      } catch (error) {
        setStatus(context, error.message || "SKU 批量删除失败。", "error");
        context.rerender();
      } finally {
        setSaving(context, false);
        context.rerender();
      }
    }

    function currentHotSkus(context) {
      if (typeof context.hotSkus === "function") {
        return context.hotSkus();
      }
      return Array.isArray(context.hotSkus) ? context.hotSkus : [];
    }

    function renderCardSecretImportSection(product, sku, context, setPageStatus) {
      const section = document.createElement("section");
      section.className = "admin-product-detail-section admin-product-card-secret-import";
      const heading = document.createElement("div");
      heading.className = "admin-product-detail-section-heading";
      heading.innerHTML = `<h3>卡密导入</h3>`;

      const status = document.createElement("p");
      status.className = "admin-product-sku-page-status admin-product-card-secret-import-status";
      const body = document.createElement("div");
      body.className = "admin-product-card-secret-import-body";

      const textField = document.createElement("label");
      textField.className = "admin-product-detail-compact-field admin-product-card-secret-import-wide";
      const textLabel = document.createElement("span");
      textLabel.textContent = "手动输入";
      const textArea = document.createElement("textarea");
      textArea.rows = 8;
      textArea.placeholder = "一行一个卡密";
      textField.append(textLabel, textArea);

      const fileField = document.createElement("div");
      fileField.className = "admin-product-detail-compact-field admin-product-card-secret-file-field";
      const fileLabel = document.createElement("span");
      fileLabel.textContent = "TXT 文件";
      const fileInput = document.createElement("input");
      fileInput.type = "file";
      fileInput.accept = ".txt,text/plain";
      fileInput.className = "admin-product-card-secret-file-input";
      fileInput.hidden = true;
      const filePicker = document.createElement("div");
      filePicker.className = "admin-product-card-secret-file-picker";
      const chooseFileButton = actionButton("选择 TXT 文件", () => fileInput.click(), "admin-ghost-button", !sku?.id || isSaving(context));
      const clearFileButton = actionButton("清除", () => {
        fileInput.value = "";
        updateFileState();
      }, "admin-ghost-button admin-product-card-secret-file-clear", true);
      const fileName = document.createElement("strong");
      fileName.className = "admin-product-card-secret-file-name";
      const fileHint = document.createElement("small");
      fileHint.className = "admin-product-card-secret-file-hint";
      fileHint.textContent = "仅支持 .txt，一行一个卡密";
      filePicker.append(chooseFileButton, clearFileButton, fileName);
      fileField.append(fileLabel, fileInput, filePicker, fileHint);

      const batchField = document.createElement("label");
      batchField.className = "admin-product-detail-compact-field";
      const batchLabel = document.createElement("span");
      batchLabel.textContent = "批次号";
      const batchInput = document.createElement("input");
      batchInput.type = "text";
      batchInput.maxLength = 64;
      batchInput.autocomplete = "off";
      batchInput.placeholder = "留空由后端生成";
      batchField.append(batchLabel, batchInput);

      body.append(textField, fileField, batchField);

      const result = document.createElement("div");
      result.className = "admin-product-sku-card-metrics admin-product-card-secret-import-result";
      result.hidden = true;

      const actions = document.createElement("div");
      actions.className = "admin-product-detail-actions admin-product-card-secret-import-actions";
      const submitButton = actionButton("导入卡密", () => importCardSecrets(), "admin-nav-button", !sku?.id || isSaving(context));
      actions.appendChild(submitButton);
      heading.appendChild(actions);

      function setImportStatus(message, type = "") {
        status.textContent = message || "";
        status.dataset.type = type || "";
        if (message) {
          setPageStatus(message, type);
        }
      }

      function updateFileState(disabled = !sku?.id || isSaving(context)) {
        const file = fileInput.files && fileInput.files.length > 0 ? fileInput.files[0] : null;
        fileName.textContent = file ? file.name : "未选择文件";
        fileName.title = file ? file.name : "未选择文件";
        fileField.classList.toggle("has-file", Boolean(file));
        fileInput.disabled = Boolean(disabled);
        chooseFileButton.disabled = Boolean(disabled);
        clearFileButton.disabled = Boolean(disabled) || !file;
      }

      function setImportBusy(busy) {
        const disabled = Boolean(busy) || !sku?.id;
        [textArea, batchInput, submitButton].forEach((node) => {
          if (node) {
            node.disabled = disabled;
          }
        });
        updateFileState(disabled);
      }

      fileInput.addEventListener("change", () => updateFileState());

      function renderImportResult(data) {
        result.replaceChildren(
          metric("接收行数", data?.receivedLineCount ?? 0),
          metric("空行", data?.blankLineCount ?? 0),
          metric("请求内重复", data?.duplicateInRequestCount ?? 0),
          metric("候选卡密", data?.uniqueCandidateCount ?? 0),
          metric("已插入", data?.insertedCount ?? 0),
          metric("库内重复", data?.duplicateInDbCount ?? 0),
          metric("库存增加", data?.stockIncrementCount ?? 0),
          metric("当前库存", data?.skuStockQuantity ?? "-"),
          metric("失败", data?.failedCount ?? 0),
          metric("批次号", data?.batchNo || "-")
        );
        result.hidden = false;
      }

      async function importCardSecrets() {
        if (!sku?.id || isSaving(context)) {
          return;
        }
        const secretText = String(textArea.value || "");
        const file = fileInput.files && fileInput.files.length > 0 ? fileInput.files[0] : null;
        if (!secretText.trim() && !file) {
          setImportStatus("请填写卡密文本或选择 TXT 文件。", "error");
          return;
        }
        const formData = new FormData();
        if (secretText) {
          formData.append("secretText", secretText);
        }
        if (file) {
          formData.append("file", file);
        }
        const batchNo = String(batchInput.value || "").trim();
        if (batchNo) {
          formData.append("batchNo", batchNo);
        }
        formData.append("duplicatePolicy", "SKIP_DUPLICATE");
        setImportBusy(true);
        setImportStatus("正在导入卡密...");
        try {
          const response = await context.productApi.importSkuCardSecrets(productId(context), sku.id, formData);
          const data = response.data || {};
          if (data.skuStockQuantity != null) {
            sku.stockQuantity = String(data.skuStockQuantity);
          }
          renderImportResult(data);
          textArea.value = "";
          fileInput.value = "";
          updateFileState(true);
          setImportStatus(`卡密导入完成，新增 ${data.insertedCount ?? 0} 条。`, "ok");
        } catch (error) {
          setImportStatus(error.message || "卡密导入失败。", "error");
        } finally {
          setImportBusy(false);
        }
      }

      if (!sku?.id) {
        setImportStatus("保存 SKU 后才能导入卡密。");
      }
      updateFileState();
      section.append(heading, status, body, result);
      return section;
    }

    function hotSkuMap(context) {
      const map = new Map();
      currentHotSkus(context).forEach((item) => {
        const skuId = String(item?.skuId || "").trim();
        if (skuId) {
          map.set(skuId, item);
        }
      });
      return map;
    }

    function hotSkuLabel(item) {
      if (!item) {
        return "-";
      }
      return `${item.status || "-"} ${item.remainingQuantity ?? 0}/${item.stockQuantity ?? 0}`;
    }

    function parseHotStock(value) {
      const text = String(value ?? "").trim();
      if (!/^\d+$/.test(text)) {
        return { ok: false, message: "热点库存只能输入数字。" };
      }
      const stock = Number.parseInt(text, 10);
      if (!Number.isSafeInteger(stock) || stock <= 0 || stock > 2147483647) {
        return { ok: false, message: "热点库存必须大于 0。" };
      }
      return { ok: true, value: stock };
    }

    function toDateTimeLocalValue(value) {
      const text = String(value || "").trim();
      if (!text) {
        return "";
      }
      const date = new Date(text);
      if (Number.isNaN(date.getTime())) {
        return text.length >= 16 ? text.slice(0, 16) : text;
      }
      const pad = (number) => String(number).padStart(2, "0");
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function hotPageSkus(product) {
      return (Array.isArray(product?.skus) ? product.skus : [])
        .filter((sku) => String(sku?.id || "").trim());
    }

    function ensureHotPageSelection(product, context) {
      if (!context.selectedKeys) {
        context.selectedKeys = new Set();
      }
      const activeIds = new Set(hotPageSkus(product).map((sku) => String(sku.id)));
      Array.from(context.selectedKeys).forEach((id) => {
        if (!activeIds.has(id)) {
          context.selectedKeys.delete(id);
        }
      });
    }

    function selectedHotPageSkus(product, context) {
      ensureHotPageSelection(product, context);
      return hotPageSkus(product).filter((sku) => context.selectedKeys.has(String(sku.id)));
    }

    function setAllHotPageSelection(product, selected, context) {
      ensureHotPageSelection(product, context);
      hotPageSkus(product).forEach((sku) => {
        const skuId = String(sku.id);
        if (selected) {
          context.selectedKeys.add(skuId);
        } else {
          context.selectedKeys.delete(skuId);
        }
      });
      context.rerender();
    }

    function setHotPageSelection(skuId, selected, context) {
      const id = String(skuId || "").trim();
      if (!id) {
        return;
      }
      if (selected) {
        context.selectedKeys.add(id);
      } else {
        context.selectedKeys.delete(id);
      }
      context.rerender();
    }

    function ensureHotPageDraft(product, context) {
      const draft = context.hotSkuDraft || {};
      const hotMap = hotSkuMap(context);
      const hotItems = currentHotSkus(context);
      const firstHot = hotItems[0] || null;
      if (!draft.status) {
        draft.status = firstHot?.status || "ENABLED";
      }
      if (draft.startAt == null) {
        draft.startAt = toDateTimeLocalValue(firstHot?.startAt);
      }
      if (draft.endAt == null) {
        draft.endAt = toDateTimeLocalValue(firstHot?.endAt);
      }
      if (!draft.stocks || typeof draft.stocks !== "object") {
        draft.stocks = {};
      }
      const activeIds = new Set();
      hotPageSkus(product).forEach((sku) => {
        const skuId = String(sku.id);
        const hot = hotMap.get(skuId);
        activeIds.add(skuId);
        if (draft.stocks[skuId] == null) {
          draft.stocks[skuId] = String(hot?.stockQuantity ?? sku.stockQuantity ?? "1");
        }
      });
      Object.keys(draft.stocks).forEach((skuId) => {
        if (!activeIds.has(skuId)) {
          delete draft.stocks[skuId];
        }
      });
      context.hotSkuDraft = draft;
      return draft;
    }

    async function saveHotSkuPage(product, context, setPageStatus) {
      if (isSaving(context)) {
        return;
      }
      const skus = selectedHotPageSkus(product, context);
      if (!skus.length) {
        setPageStatus("请选择需要设置为热点的 SKU。", "error");
        return;
      }
      const draft = ensureHotPageDraft(product, context);
      const items = [];
      for (const sku of skus) {
        const stock = parseHotStock(draft.stocks[String(sku.id)]);
        if (!stock.ok) {
          setPageStatus(`${sku.skuName || sku.skuCode || sku.id}：${stock.message}`, "error");
          return;
        }
        items.push({
          skuId: sku.id,
          stockQuantity: stock.value,
          status: draft.status || "ENABLED",
          startAt: draft.startAt || null,
          endAt: draft.endAt || null
        });
      }
      setSaving(context, true);
      context.rerender();
      try {
        await context.productApi.batchEnableHotSkus(productId(context), items);
        await context.refreshAfterHotSkuPageChange?.("热点 SKU 已保存。");
      } catch (error) {
        setPageStatus(error.message || "热点 SKU 保存失败。", "error");
        setSaving(context, false);
        context.rerender();
      }
    }

    async function deleteHotSkuPage(product, context, setPageStatus) {
      if (isSaving(context)) {
        return;
      }
      const ids = selectedHotPageSkus(product, context).map((sku) => sku.id);
      if (!ids.length) {
        setPageStatus("请选择需要删除热点的 SKU。", "error");
        return;
      }
      setSaving(context, true);
      context.rerender();
      try {
        await context.productApi.batchDeleteHotSkus(productId(context), ids);
        await context.refreshAfterHotSkuPageChange?.("热点 SKU 已删除。");
      } catch (error) {
        setPageStatus(error.message || "热点 SKU 删除失败。", "error");
        setSaving(context, false);
        context.rerender();
      }
    }

    async function openCreateSkuDialog(draft, context) {
      if (typeof context.navigateToSkuCreate === "function") {
        context.navigateToSkuCreate();
        return;
      }
      await openSkuDialog(draft, context, null);
    }

    async function openEditSkuDialog(draft, context, sku) {
      if (typeof context.navigateToSku === "function" && sku?.id) {
        context.navigateToSku(sku.id);
        return;
      }
      await openSkuDialog(draft, context, sku);
    }

    async function openSkuDialog(draft, context, sourceSku) {
      if (isSaving(context)) {
        return;
      }
      const spuId = productId(context);
      let sku = sourceSku ? toEditableSku(sourceSku, context, sourceSku) : createEmptySkuDraft();
      if (sourceSku?.id) {
        setSaving(context, true);
        context.rerender();
        try {
          const response = await context.productApi.getSkuDetail(spuId, sourceSku.id);
          sku = toEditableSku(response.data || sourceSku, context, sourceSku);
        } catch (error) {
          setStatus(context, error.message || "SKU 详情加载失败。", "error");
          return;
        } finally {
          setSaving(context, false);
          context.rerender();
        }
      }

      let confirmed = false;
      let closing = false;
      let dialogBusy = false;
      const overlay = document.createElement("div");
      overlay.className = "admin-product-sku-dialog";
      overlay.setAttribute("role", "dialog");
      overlay.setAttribute("aria-modal", "true");
      overlay.setAttribute("aria-labelledby", "admin-product-sku-dialog-title");

      const backdrop = document.createElement("button");
      backdrop.className = "admin-product-sku-dialog-backdrop";
      backdrop.type = "button";
      backdrop.setAttribute("aria-label", "关闭 SKU 弹窗");

      const panel = document.createElement("form");
      panel.className = "admin-product-sku-dialog-panel";
      const header = document.createElement("div");
      header.className = "admin-product-sku-dialog-header";
      const titleGroup = document.createElement("div");
      const eyebrow = document.createElement("span");
      eyebrow.textContent = sku.id ? "SKU 详情" : "SKU";
      const title = document.createElement("strong");
      title.id = "admin-product-sku-dialog-title";
      title.textContent = sku.id ? (sku.skuName || sku.skuCode || "编辑 SKU") : "新增 SKU";
      titleGroup.append(eyebrow, title);
      const closeButton = actionButton("关闭", () => closeDialog(true), "admin-ghost-button");
      header.append(titleGroup, closeButton);

      const body = document.createElement("div");
      body.className = "admin-product-sku-dialog-body";
      const status = document.createElement("p");
      status.className = "admin-product-sku-dialog-status";

      const actions = document.createElement("div");
      actions.className = "admin-product-sku-dialog-actions";
      const cancelButton = actionButton("取消", () => closeDialog(true), "admin-ghost-button");
      const submitButton = document.createElement("button");
      submitButton.className = "admin-nav-button admin-spring-button";
      submitButton.type = "submit";
      submitButton.textContent = sku.id ? "保存 SKU" : "添加 SKU";
      actions.append(cancelButton, submitButton);

      function setDialogStatus(message, type = "") {
        status.textContent = message || "";
        status.dataset.type = type || "";
      }

      function setDialogBusy(busy) {
        dialogBusy = Boolean(busy);
        closeButton.disabled = dialogBusy;
        cancelButton.disabled = dialogBusy;
        submitButton.disabled = dialogBusy;
      }

      function renderDialogBody() {
        body.replaceChildren();
        const imageField = document.createElement("div");
        imageField.className = "admin-product-sku-dialog-field admin-product-sku-dialog-wide";
        const imageLabel = document.createElement("span");
        imageLabel.textContent = "SKU 图片";
        const imageShell = document.createElement("div");
        imageShell.className = "admin-product-sku-dialog-image-shell";
        const imageActions = document.createElement("div");
        imageActions.className = "admin-product-sku-dialog-image-actions";
        imageActions.appendChild(actionButton("批量上传图", async () => {
          if (dialogBusy) {
            return;
          }
          setDialogBusy(true);
          try {
            await context.uploadImages(sku.skuImageUrls, renderDialogBody);
          } finally {
            setDialogBusy(false);
            renderDialogBody();
          }
        }, "admin-ghost-button", dialogBusy || isSaving(context)));
        imageShell.append(imageEditor.skuImageList(sku, renderDialogBody), imageActions);
        imageField.append(imageLabel, imageShell);

        const statusField = document.createElement("label");
        statusField.className = "admin-product-detail-compact-field";
        const statusLabel = document.createElement("span");
        statusLabel.textContent = "状态";
        const statusSelect = document.createElement("select");
        ["ACTIVE", "DISABLED"].forEach((value) => {
          const option = document.createElement("option");
          option.value = value;
          option.textContent = value;
          statusSelect.appendChild(option);
        });
        statusSelect.value = sku.status;
        statusSelect.addEventListener("change", () => { sku.status = statusSelect.value; });
        statusField.append(statusLabel, statusSelect);

        const specField = document.createElement("label");
        specField.className = "admin-product-detail-compact-field admin-product-sku-dialog-wide";
        const specLabel = document.createElement("span");
        specLabel.textContent = "规格 JSON";
        const spec = document.createElement("textarea");
        spec.rows = 5;
        spec.value = sku.specJsonText || "{}";
        spec.addEventListener("input", () => { sku.specJsonText = spec.value; });
        specField.append(specLabel, spec);

        body.append(
          imageField,
          skuInput("SKU 编码", sku.skuCode, (value) => { sku.skuCode = value; }),
          skuInput("SKU 名称", sku.skuName, (value) => { sku.skuName = value; }),
          skuInput("价格(元)", sku.priceYuan, (value) => { sku.priceYuan = value; }, "money"),
          skuInput("原价(元)", sku.originalPriceYuan, (value) => { sku.originalPriceYuan = value; }, "money"),
          skuInput("库存", sku.stockQuantity, (value) => { sku.stockQuantity = value; }, "stock"),
          statusField,
          specField
        );
      }

      async function closeDialog(cleanupImages) {
        if (dialogBusy) {
          setDialogStatus("SKU 图片上传中，请稍候。", "error");
          return;
        }
        if (closing) {
          return;
        }
        closing = true;
        document.removeEventListener("keydown", handleKeydown);
        overlay.remove();
        if (cleanupImages && !confirmed) {
          await context.cancelSkuImages(sku);
        }
      }

      function handleKeydown(event) {
        if (event.key === "Escape") {
          event.preventDefault();
          closeDialog(true);
        }
      }

      backdrop.addEventListener("click", () => closeDialog(true));
      panel.addEventListener("submit", async (event) => {
        event.preventDefault();
        const payload = buildSkuPayload(sku, context, setDialogStatus);
        if (!payload) {
          return;
        }
        setDialogBusy(true);
        try {
          if (sku.id) {
            await context.productApi.updateSku(spuId, sku.id, payload);
          } else {
            await context.productApi.createSku(spuId, payload);
          }
          confirmed = true;
          clearCommittedSkuImages(sku, context);
          await closeDialog(false);
          await context.refreshAfterSkuChange(sku.id ? "SKU 已保存。" : "SKU 已添加。");
        } catch (error) {
          setDialogStatus(error.message || "SKU 保存失败。", "error");
        } finally {
          setDialogBusy(false);
        }
      });

      renderDialogBody();
      panel.append(header, body, status, actions);
      overlay.append(backdrop, panel);
      document.body.appendChild(overlay);
      document.addEventListener("keydown", handleKeydown);
      window.setTimeout(() => {
        const firstInput = panel.querySelector("input");
        firstInput?.focus();
      }, 0);
    }

    async function changeSingleSkuStatus(sku, nextStatus, context) {
      if (isSaving(context) || !sku?.id || sku.status === nextStatus) {
        return;
      }
      const spuId = productId(context);
      setSaving(context, true);
      context.rerender();
      try {
        await context.productApi.changeSkuStatus(spuId, sku.id, nextStatus);
        await context.refreshAfterSkuChange("SKU 状态已更新。");
      } catch (error) {
        setStatus(context, error.message || "SKU 状态更新失败。", "error");
        context.rerender();
      } finally {
        setSaving(context, false);
        context.rerender();
      }
    }

    async function deleteSku(sku, context) {
      if (isSaving(context) || !sku?.id) {
        return;
      }
      const spuId = productId(context);
      setSaving(context, true);
      context.rerender();
      try {
        await context.productApi.deleteSku(spuId, sku.id);
        context.selectedKeys.delete(sku.clientKey);
        await context.refreshAfterSkuChange("SKU 已删除。");
      } catch (error) {
        setStatus(context, error.message || "SKU 删除失败。", "error");
        context.rerender();
      } finally {
        setSaving(context, false);
        context.rerender();
      }
    }

    function renderSkuPage(product, sku, context) {
      const form = document.createElement("form");
      form.className = "admin-product-detail-content admin-product-detail-form admin-product-sku-page";
      const busy = isSaving(context);
      const titleText = sku?.id ? (sku.skuName || sku.skuCode || "SKU 详情") : "新增 SKU";

      const toolbar = document.createElement("div");
      toolbar.className = "admin-product-detail-toolbar";
      const heading = document.createElement("div");
      heading.className = "admin-product-detail-title";
      const small = document.createElement("small");
      small.textContent = product?.name ? `商品 / ${product.name}` : "商品 SKU";
      const title = document.createElement("strong");
      title.textContent = titleText;
      heading.append(small, title);

      const actions = document.createElement("div");
      actions.className = "admin-product-detail-actions";
      const saveButton = document.createElement("button");
      saveButton.className = "admin-nav-button admin-spring-button";
      saveButton.type = "submit";
      saveButton.textContent = sku?.id ? "保存 SKU" : "添加 SKU";
      saveButton.disabled = busy;
      actions.append(
        actionButton("返回商品", () => context.returnToDetail?.(), "admin-ghost-button", busy),
        actionButton("删除", () => context.deleteSku?.(sku), "admin-api-back", busy || !sku?.id),
        saveButton
      );
      toolbar.append(heading, actions);

      const pageStatus = document.createElement("p");
      pageStatus.className = "admin-product-sku-page-status";
      function setPageStatus(message, type = "") {
        pageStatus.textContent = message || "";
        pageStatus.dataset.type = type || "";
        setStatus(context, message, type);
      }

      const section = document.createElement("section");
      section.className = "admin-product-detail-section";
      const body = document.createElement("div");
      body.className = "admin-product-sku-page-body";

      const imageField = document.createElement("div");
      imageField.className = "admin-product-sku-page-field admin-product-sku-page-wide";
      const imageLabel = document.createElement("span");
      imageLabel.textContent = "SKU 图片";
      const imageShell = document.createElement("div");
      imageShell.className = "admin-product-sku-page-image-shell";
      const imageActions = document.createElement("div");
      imageActions.className = "admin-product-sku-page-image-actions";
      imageActions.appendChild(actionButton("批量上传图片", async () => {
        await context.uploadImages(sku.skuImageUrls, context.rerender);
      }, "admin-ghost-button", busy));
      imageShell.append(imageEditor.skuImageList(sku, context.rerender), imageActions);
      imageField.append(imageLabel, imageShell);

      const statusField = document.createElement("label");
      statusField.className = "admin-product-detail-compact-field";
      const statusLabel = document.createElement("span");
      statusLabel.textContent = "状态";
      const statusSelect = document.createElement("select");
      ["ACTIVE", "DISABLED"].forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        statusSelect.appendChild(option);
      });
      statusSelect.value = sku.status || "ACTIVE";
      statusSelect.addEventListener("change", () => { sku.status = statusSelect.value; });
      statusField.append(statusLabel, statusSelect);

      const specField = document.createElement("label");
      specField.className = "admin-product-detail-compact-field admin-product-sku-page-wide";
      const specLabel = document.createElement("span");
      specLabel.textContent = "规格 JSON";
      const spec = document.createElement("textarea");
      spec.rows = 7;
      spec.value = sku.specJsonText || "{}";
      spec.addEventListener("input", () => { sku.specJsonText = spec.value; });
      specField.append(specLabel, spec);

      body.append(
        imageField,
        skuInput("SKU 编码", sku.skuCode, (value) => { sku.skuCode = value; }),
        skuInput("SKU 名称", sku.skuName, (value) => { sku.skuName = value; }),
        skuInput("价格(元)", sku.priceYuan, (value) => { sku.priceYuan = value; }, "money"),
        skuInput("原价(元)", sku.originalPriceYuan, (value) => { sku.originalPriceYuan = value; }, "money"),
        skuInput("库存", sku.stockQuantity, (value) => { sku.stockQuantity = value; }, "stock"),
        statusField,
        specField
      );
      section.appendChild(body);

      form.append(toolbar, pageStatus, section, renderCardSecretImportSection(product, sku, context, setPageStatus));
      if (busy) {
        form.querySelectorAll("input, select, textarea, button").forEach((node) => {
          node.disabled = true;
        });
      }
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const payload = buildSkuPayload(sku, context, setPageStatus);
        if (!payload) {
          return;
        }
        await context.saveSku?.(sku, payload, setPageStatus);
      });
      return form;
    }

    function renderHotSkuPage(product, context) {
      const form = document.createElement("form");
      form.className = "admin-product-detail-content admin-product-detail-form admin-product-hot-sku-page";
      const busy = isSaving(context);
      const skus = hotPageSkus(product);
      ensureHotPageSelection(product, context);
      const selectedCount = selectedHotPageSkus(product, context).length;
      const draft = ensureHotPageDraft(product, context);
      const hotMap = hotSkuMap(context);

      const toolbar = document.createElement("div");
      toolbar.className = "admin-product-detail-toolbar";
      const heading = document.createElement("div");
      heading.className = "admin-product-detail-title";
      const small = document.createElement("small");
      small.textContent = product?.name ? `商品 / ${product.name}` : "商品热点 SKU";
      const title = document.createElement("strong");
      title.textContent = "热点 SKU";
      heading.append(small, title);

      const actions = document.createElement("div");
      actions.className = "admin-product-detail-actions";
      const saveButton = document.createElement("button");
      saveButton.className = "admin-nav-button admin-spring-button";
      saveButton.type = "submit";
      saveButton.textContent = "保存热点";
      saveButton.disabled = busy || selectedCount === 0;
      actions.append(
        actionButton("返回商品", () => context.returnToDetail?.(), "admin-ghost-button", busy),
        actionButton("删除热点", () => deleteHotSkuPage(product, context, setPageStatus), "admin-api-back", busy || selectedCount === 0),
        saveButton
      );
      toolbar.append(heading, actions);

      const pageStatus = document.createElement("p");
      pageStatus.className = "admin-product-sku-page-status";
      function setPageStatus(message, type = "") {
        pageStatus.textContent = message || "";
        pageStatus.dataset.type = type || "";
        setStatus(context, message, type);
      }

      const section = document.createElement("section");
      section.className = "admin-product-detail-section";
      const config = document.createElement("div");
      config.className = "admin-product-sku-page-body";

      const statusField = document.createElement("label");
      statusField.className = "admin-product-detail-compact-field";
      const statusLabel = document.createElement("span");
      statusLabel.textContent = "热点状态";
      const statusSelect = document.createElement("select");
      ["ENABLED", "DISABLED", "SOLD_OUT"].forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        statusSelect.appendChild(option);
      });
      statusSelect.value = draft.status || "ENABLED";
      statusSelect.addEventListener("change", () => { draft.status = statusSelect.value; });
      statusField.append(statusLabel, statusSelect);

      config.append(
        statusField,
        skuInput("开始时间", draft.startAt, (value) => { draft.startAt = value; }, "datetime-local"),
        skuInput("结束时间", draft.endAt, (value) => { draft.endAt = value; }, "datetime-local")
      );

      const listHeader = document.createElement("div");
      listHeader.className = "admin-product-detail-section-heading";
      listHeader.innerHTML = `<h3>选择 SKU</h3>`;
      const listActions = document.createElement("div");
      listActions.className = "admin-product-detail-actions admin-product-sku-batch-actions";
      const selectAllLabel = document.createElement("label");
      selectAllLabel.className = "admin-product-sku-select-all";
      const selectAll = document.createElement("input");
      selectAll.type = "checkbox";
      selectAll.disabled = busy || skus.length === 0;
      selectAll.checked = skus.length > 0 && selectedCount === skus.length;
      selectAll.indeterminate = selectedCount > 0 && selectedCount < skus.length;
      selectAll.addEventListener("change", () => setAllHotPageSelection(product, selectAll.checked, context));
      const selectAllText = document.createElement("span");
      selectAllText.textContent = selectedCount > 0 ? `已选 ${selectedCount}/${skus.length}` : `全选 ${skus.length}`;
      selectAllLabel.append(selectAll, selectAllText);
      listActions.appendChild(selectAllLabel);
      listHeader.appendChild(listActions);

      const list = document.createElement("div");
      list.className = "admin-product-sku-card-list";
      if (!skus.length) {
        list.innerHTML = `<div class="admin-product-detail-empty-row">暂无 SKU</div>`;
      }
      skus.forEach((sku, index) => {
        const skuId = String(sku.id);
        const selected = context.selectedKeys.has(skuId);
        const hotItem = hotMap.get(skuId);
        const card = document.createElement("article");
        card.className = "admin-product-sku-card";
        card.classList.toggle("is-selected", selected);

        const top = document.createElement("div");
        top.className = "admin-product-sku-card-top";
        const selectCell = document.createElement("label");
        selectCell.className = "admin-product-sku-select-cell";
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.value = skuId;
        checkbox.checked = selected;
        checkbox.disabled = busy;
        checkbox.setAttribute("aria-label", `选择热点 SKU ${sku.skuName || sku.skuCode || index + 1}`);
        checkbox.addEventListener("change", () => setHotPageSelection(skuId, checkbox.checked, context));
        selectCell.appendChild(checkbox);

        const preview = document.createElement("div");
        preview.className = "admin-product-sku-card-preview";
        const firstImage = imagePayload(sku.skuImageUrls || [])[0] || "";
        preview.innerHTML = firstImage
          ? `<img src="${escapeAttribute(firstImage)}" alt="${escapeAttribute(sku.skuName || "SKU")}" />`
          : "<span>-</span>";

        const name = document.createElement("div");
        name.className = "admin-product-sku-card-title";
        name.innerHTML = `<strong>${escapeHtml(sku.skuName || "未命名 SKU")}</strong><span>${escapeHtml(sku.skuCode || "-")}</span>`;
        const status = document.createElement("strong");
        status.className = "admin-product-sku-card-status";
        status.textContent = hotItem?.status || "未设置";
        top.append(selectCell, preview, name, status);

        const metrics = document.createElement("div");
        metrics.className = "admin-product-sku-card-metrics";
        metrics.append(
          metric("SKU 库存", sku.stockQuantity ?? "0"),
          metric("热点", hotSkuLabel(hotItem)),
          metric("价格", sku.priceYuan || "0")
        );

        const stockField = skuInput("热点库存", draft.stocks[skuId], (value) => { draft.stocks[skuId] = value; }, "stock");
        const stockInput = stockField.querySelector("input");
        if (stockInput) {
          stockInput.disabled = busy || !selected;
        }
        const cardActions = document.createElement("div");
        cardActions.className = "admin-product-detail-actions admin-product-sku-card-actions";
        cardActions.appendChild(actionButton(
          "查看详情",
          () => context.navigateToHotSkuDetail?.(skuId),
          "admin-ghost-button",
          busy || !hotItem));
        card.append(top, metrics, stockField, cardActions);
        list.appendChild(card);
      });

      section.append(config, listHeader, list);
      form.append(toolbar, pageStatus, section);
      if (busy) {
        form.querySelectorAll("input, select, textarea, button").forEach((node) => {
          node.disabled = true;
        });
      }
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        await saveHotSkuPage(product, context, setPageStatus);
      });
      return form;
    }

    function renderHotSkuDetailPage(product, hotSku, context) {
      const wrapper = document.createElement("div");
      wrapper.className = "admin-product-detail-content admin-product-hot-sku-detail-page";

      const toolbar = document.createElement("div");
      toolbar.className = "admin-product-detail-toolbar";
      const heading = document.createElement("div");
      heading.className = "admin-product-detail-title";
      const small = document.createElement("small");
      small.textContent = product?.name ? `商品 / ${product.name}` : "商品热点 SKU";
      const title = document.createElement("strong");
      title.textContent = "热点 SKU 详情";
      heading.append(small, title);
      const actions = document.createElement("div");
      actions.className = "admin-product-detail-actions";
      actions.append(actionButton("返回热点列表", () => context.returnToHotSku?.(), "admin-ghost-button", false));
      toolbar.append(heading, actions);

      const section = document.createElement("section");
      section.className = "admin-product-detail-section";

      if (!hotSku) {
        const empty = document.createElement("div");
        empty.className = "admin-product-detail-empty-row";
        empty.textContent = "热点 SKU 不存在。";
        section.appendChild(empty);
        wrapper.append(toolbar, section);
        return wrapper;
      }

      const metrics = document.createElement("div");
      metrics.className = "admin-product-sku-card-metrics";
      metrics.append(
        metric("SKU 名称", hotSku.skuName || "-"),
        metric("SKU 编码", hotSku.skuCode || "-"),
        metric("热点状态", hotSku.status || "-"),
        metric("热点库存", hotSku.stockQuantity ?? "-"),
        metric("剩余库存", hotSku.remainingQuantity ?? "-"),
        metric("SKU 库存", hotSku.skuStockQuantity ?? "-"),
        metric("SKU 状态", hotSku.skuStatus || "-"),
        metric("开始时间", formatDateTimeText(hotSku.startAt)),
        metric("结束时间", formatDateTimeText(hotSku.endAt)),
        metric("版本", hotSku.version ?? "-"),
        metric("创建时间", formatDateTimeText(hotSku.createdAt)),
        metric("更新时间", formatDateTimeText(hotSku.updatedAt))
      );
      section.appendChild(metrics);
      wrapper.append(toolbar, section);
      return wrapper;
    }

    function formatDateTimeText(value) {
      const text = String(value || "").trim();
      if (!text) {
        return "-";
      }
      const date = new Date(text);
      if (Number.isNaN(date.getTime())) {
        return text;
      }
      const pad = (number) => String(number).padStart(2, "0");
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function render(draft, context) {
      const section = document.createElement("section");
      section.className = "admin-product-detail-section";
      ensureSelectionState(draft, context);
      const skuCount = draft.skus.length;
      const selectedCount = selectedSkus(draft, context).length;
      const batchDisabled = isSaving(context) || selectedCount === 0;
      const heading = document.createElement("div");
      heading.className = "admin-product-detail-section-heading";
      heading.innerHTML = `<h3>SKU</h3>`;
      const headingActions = document.createElement("div");
      headingActions.className = "admin-product-detail-actions admin-product-sku-batch-actions";
      const selectAllLabel = document.createElement("label");
      selectAllLabel.className = "admin-product-sku-select-all";
      const selectAll = document.createElement("input");
      selectAll.type = "checkbox";
      selectAll.disabled = isSaving(context) || skuCount === 0;
      selectAll.checked = skuCount > 0 && selectedCount === skuCount;
      selectAll.indeterminate = selectedCount > 0 && selectedCount < skuCount;
      selectAll.addEventListener("change", () => setAllSkuSelection(draft, selectAll.checked, context));
      const selectAllText = document.createElement("span");
      selectAllText.textContent = selectedCount > 0 ? `已选 ${selectedCount}/${skuCount}` : `全选 ${skuCount}`;
      selectAllLabel.append(selectAll, selectAllText);
      const hotButtonDisabled = isSaving(context) || !productId(context);
      const setHotButton = actionButton("设置热点", () => context.navigateToHotSku?.(), "admin-ghost-button", hotButtonDisabled);
      headingActions.append(
        selectAllLabel,
        setHotButton,
        actionButton("批量启用", () => batchUpdateSkuStatus(draft, "ACTIVE", context), "admin-ghost-button", batchDisabled),
        actionButton("批量禁用", () => batchUpdateSkuStatus(draft, "DISABLED", context), "admin-ghost-button", batchDisabled),
        actionButton("批量删除", () => batchDeleteSelectedSkus(draft, context), "admin-api-back", batchDisabled),
        actionButton("新增 SKU", () => openCreateSkuDialog(draft, context), "admin-ghost-button", isSaving(context))
      );
      heading.appendChild(headingActions);
      section.appendChild(heading);
      const list = document.createElement("div");
      list.className = "admin-product-sku-card-list";
      if (!draft.skus.length) {
        list.innerHTML = `<div class="admin-product-detail-empty-row">暂无 SKU</div>`;
      }
      draft.skus.forEach((sku, index) => list.appendChild(skuCard(draft, sku, index, context)));
      section.appendChild(list);
      return section;
    }

    function skuCard(draft, sku, index, context) {
      const card = document.createElement("article");
      card.className = "admin-product-sku-card";
      card.classList.toggle("is-selected", context.selectedKeys.has(sku.clientKey));
      const top = document.createElement("div");
      top.className = "admin-product-sku-card-top";
      const selectCell = document.createElement("label");
      selectCell.className = "admin-product-sku-select-cell";
      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.value = sku.clientKey;
      checkbox.checked = context.selectedKeys.has(sku.clientKey);
      checkbox.disabled = isSaving(context);
      checkbox.setAttribute("aria-label", `选择 SKU ${sku.skuName || sku.skuCode || index + 1}`);
      checkbox.addEventListener("change", () => setSkuSelection(sku.clientKey, checkbox.checked, context));
      selectCell.appendChild(checkbox);
      const preview = document.createElement("div");
      preview.className = "admin-product-sku-card-preview";
      const firstImage = imagePayload(sku.skuImageUrls || [])[0] || "";
      preview.innerHTML = firstImage
        ? `<img src="${escapeAttribute(firstImage)}" alt="${escapeAttribute(sku.skuName || "SKU")}" />`
        : "<span>-</span>";
      const title = document.createElement("div");
      title.className = "admin-product-sku-card-title";
      title.innerHTML = `<strong>${escapeHtml(sku.skuName || "未命名 SKU")}</strong><span>${escapeHtml(sku.skuCode || "-")}</span>`;
      const status = document.createElement("select");
      status.className = "admin-product-sku-card-status";
      ["ACTIVE", "DISABLED"].forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        status.appendChild(option);
      });
      status.value = sku.status || "ACTIVE";
      status.disabled = isSaving(context) || !sku.id;
      status.addEventListener("change", () => changeSingleSkuStatus(sku, status.value, context));
      top.append(selectCell, preview, title, status);

      const metrics = document.createElement("div");
      metrics.className = "admin-product-sku-card-metrics";
      const hotItem = hotSkuMap(context).get(String(sku.id || ""));
      metrics.append(
        metric("价格", sku.priceYuan || "0"),
        metric("原价", sku.originalPriceYuan || "-"),
        metric("库存", sku.stockQuantity ?? "0")
      );

      metrics.append(metric("热点", hotSkuLabel(hotItem)));

      const spec = document.createElement("pre");
      spec.className = "admin-product-sku-card-spec";
      spec.textContent = compactJson(sku.specJsonText || "{}");

      const actions = document.createElement("div");
      actions.className = "admin-product-detail-actions admin-product-sku-card-actions";
      actions.append(
        actionButton("编辑/详情", () => openEditSkuDialog(draft, context, sku), "admin-ghost-button", isSaving(context) || !sku.id),
        actionButton("删除", () => deleteSku(sku, context), "admin-api-back", isSaving(context) || !sku.id)
      );

      card.append(top, metrics, spec, actions);
      return card;
    }

    function metric(label, value) {
      const node = document.createElement("div");
      node.className = "admin-product-sku-card-metric";
      node.innerHTML = `<span>${escapeHtml(label)}</span><strong>${escapeHtml(String(value ?? "-"))}</strong>`;
      return node;
    }

    function compactJson(value) {
      try {
        return JSON.stringify(JSON.parse(value || "{}"));
      } catch (_) {
        return value || "{}";
      }
    }

    function escapeHtml(value) {
      return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
    }

    return {
      render,
      renderSkuPage,
      renderHotSkuPage,
      renderHotSkuDetailPage,
      createEmptySkuDraft,
      toEditableSku,
      ensureSelectionState
    };
  }

  root.AdminProductSkuEditor = { create };
})(window);
