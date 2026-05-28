(function (root) {
  function create(options = {}) {
    const formUi = options.formUi || root.AdminProductFormUi;
    const imageEditor = options.imageEditor;
    const {
      actionButton,
      skuInput
    } = formUi;

    function isSaving(context) {
      return typeof context.isSaving === "function" ? context.isSaving() : Boolean(context.saving);
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

    function batchUpdateSkuStatus(draft, status, context) {
      if (isSaving(context)) {
        return;
      }
      const skus = selectedSkus(draft, context);
      if (!skus.length) {
        return;
      }
      skus.forEach((sku) => {
        sku.status = status;
      });
      context.rerender();
    }

    async function batchDeleteSelectedSkus(draft, context) {
      if (isSaving(context)) {
        return;
      }
      const selectedKeys = new Set(selectedSkus(draft, context).map((sku) => sku.clientKey));
      if (!selectedKeys.size) {
        return;
      }
      const removedSkus = draft.skus.filter((sku) => selectedKeys.has(sku.clientKey));
      await Promise.all(removedSkus.map((sku) => context.cancelSkuImages(sku)));
      draft.skus = draft.skus.filter((sku) => !selectedKeys.has(sku.clientKey));
      selectedKeys.forEach((key) => context.selectedKeys.delete(key));
      ensureSelectionState(draft, context);
      context.rerender();
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
        stockQuantity: "0",
        status: "ACTIVE"
      };
    }

    async function openCreateSkuDialog(draft, context) {
      if (isSaving(context)) {
        return;
      }
      const sku = createEmptySkuDraft();
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
      backdrop.setAttribute("aria-label", "关闭新增 SKU 弹窗");

      const panel = document.createElement("form");
      panel.className = "admin-product-sku-dialog-panel";
      const header = document.createElement("div");
      header.className = "admin-product-sku-dialog-header";
      const titleGroup = document.createElement("div");
      const eyebrow = document.createElement("span");
      eyebrow.textContent = "SKU";
      const title = document.createElement("strong");
      title.id = "admin-product-sku-dialog-title";
      title.textContent = "新增 SKU";
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
      submitButton.textContent = "添加 SKU";
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
          skuInput("价格(元)", sku.priceYuan, (value) => { sku.priceYuan = value; }, "number"),
          skuInput("原价(元)", sku.originalPriceYuan, (value) => { sku.originalPriceYuan = value; }, "number"),
          skuInput("库存", sku.stockQuantity, (value) => { sku.stockQuantity = value; }, "number"),
          statusField,
          specField
        );
      }

      function validateSku() {
        if (!String(sku.skuCode || "").trim() || !String(sku.skuName || "").trim()) {
          setDialogStatus("SKU 编码和名称不能为空。", "error");
          return false;
        }
        try {
          JSON.parse(sku.specJsonText || "{}");
        } catch (_) {
          setDialogStatus("SKU 规格 JSON 格式无效。", "error");
          return false;
        }
        setDialogStatus("");
        return true;
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
        if (!validateSku()) {
          return;
        }
        confirmed = true;
        sku.clientKey = context.allocateSkuClientKey();
        draft.skus.push(sku);
        ensureSelectionState(draft, context);
        await closeDialog(false);
        context.rerender();
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
    headingActions.append(
      selectAllLabel,
      actionButton("批量启用", () => batchUpdateSkuStatus(draft, "ACTIVE", context), "admin-ghost-button", batchDisabled),
      actionButton("批量禁用", () => batchUpdateSkuStatus(draft, "DISABLED", context), "admin-ghost-button", batchDisabled),
      actionButton("批量删除", () => batchDeleteSelectedSkus(draft, context), "admin-api-back", batchDisabled),
      actionButton("新增 SKU", () => openCreateSkuDialog(draft, context), "admin-ghost-button", isSaving(context))
    );
    heading.appendChild(headingActions);
    section.appendChild(heading);
    const list = document.createElement("div");
    list.className = "admin-product-detail-sku-edit-list";
    if (!draft.skus.length) {
      list.innerHTML = `<div class="admin-product-detail-empty-row">暂无 SKU</div>`;
    }
    draft.skus.forEach((sku, index) => list.appendChild(skuEditRow(draft, sku, index, context)));
    section.appendChild(list);
    return section;
  }

  function skuEditRow(draft, sku, index, context) {
    ensureSelectionState(draft, context);
    const row = document.createElement("div");
    row.className = "admin-product-detail-sku-edit-row";
    row.classList.toggle("is-selected", context.selectedKeys.has(sku.clientKey));
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
    row.appendChild(selectCell);
    row.appendChild(imageEditor.skuImageList(sku, context.rerender));
    row.appendChild(skuInput("SKU 编码", sku.skuCode, (value) => { sku.skuCode = value; }));
    row.appendChild(skuInput("SKU 名称", sku.skuName, (value) => { sku.skuName = value; }));
    row.appendChild(skuInput("价格(元)", sku.priceYuan, (value) => { sku.priceYuan = value; }, "number"));
    row.appendChild(skuInput("原价(元)", sku.originalPriceYuan, (value) => { sku.originalPriceYuan = value; }, "number"));
    row.appendChild(skuInput("库存", sku.stockQuantity, (value) => { sku.stockQuantity = value; }, "number"));
    const status = document.createElement("select");
    ["ACTIVE", "DISABLED"].forEach((value) => {
      const option = document.createElement("option");
      option.value = value;
      option.textContent = value;
      status.appendChild(option);
    });
    status.value = sku.status;
    status.disabled = isSaving(context);
    status.addEventListener("change", () => { sku.status = status.value; });
    row.appendChild(status);
    const spec = document.createElement("textarea");
    spec.rows = 3;
    spec.value = sku.specJsonText || "{}";
    spec.disabled = isSaving(context);
    spec.addEventListener("input", () => { sku.specJsonText = spec.value; });
    row.appendChild(spec);
    const actions = document.createElement("div");
    actions.className = "admin-product-detail-actions";
    actions.append(
      actionButton("批量上传图", async () => context.uploadImages(sku.skuImageUrls, context.rerender), "admin-ghost-button", isSaving(context)),
      actionButton("删除", async () => {
        if (isSaving(context)) {
          return;
        }
        await context.cancelSkuImages(sku);
        context.selectedKeys.delete(sku.clientKey);
        draft.skus.splice(index, 1);
        ensureSelectionState(draft, context);
        context.rerender();
      }, "admin-api-back", isSaving(context))
    );
    row.appendChild(actions);
    return row;
  }

    return {
      render,
      ensureSelectionState
    };
  }

  root.AdminProductSkuEditor = { create };
})(window);
