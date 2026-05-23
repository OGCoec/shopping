(function (root) {
  const imageUtils = root.AdminProductImageUtils;

  function actionButton(text, handler, variant = "admin-ghost-button", disabled = false) {
    const button = document.createElement("button");
    button.className = `${variant} admin-spring-button`;
    button.type = "button";
    button.textContent = text;
    button.disabled = Boolean(disabled);
    button.addEventListener("click", handler);
    return button;
  }

  function labelSpan(text) {
    const span = document.createElement("span");
    span.textContent = text;
    return span;
  }

  function skuInput(label, value, onInput, type = "text") {
    const field = document.createElement("label");
    field.className = "admin-product-detail-compact-field";
    const input = document.createElement("input");
    input.type = type;
    input.value = value || "";
    if (type === "number") {
      input.min = "0";
      input.step = "1";
    }
    input.addEventListener("input", () => onInput(input.value.trim()));
    field.append(labelSpan(label), input);
    return field;
  }

  function pickImageFile() {
    return new Promise((resolve) => {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = "image/*";
      input.addEventListener("change", () => resolve(input.files?.[0] || null), { once: true });
      input.click();
    });
  }

  function pickImageFiles() {
    return new Promise((resolve) => {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = "image/*";
      input.multiple = true;
      input.addEventListener("change", () => resolve(Array.from(input.files || [])), { once: true });
      input.click();
    });
  }

  function appendImageOrderBadge(preview, index, text = "") {
    if (!preview) {
      return;
    }
    const badge = document.createElement("span");
    badge.className = "admin-product-image-order-badge";
    badge.textContent = text || `#${index + 1}`;
    preview.appendChild(badge);
  }

  function imageOrderControls(items, index, rerender, onRemove) {
    const controls = document.createElement("div");
    controls.className = "admin-product-image-order-controls";
    const lastIndex = Array.isArray(items) ? items.length - 1 : 0;
    const addMoveButton = (text, targetIndex, disabled) => {
      controls.appendChild(actionButton(text, () => {
        imageUtils.moveImageItem(items, index, targetIndex);
        rerender();
      }, "admin-product-image-order-button", disabled));
    };
    addMoveButton("置顶", 0, index <= 0);
    addMoveButton("上移", index - 1, index <= 0);
    addMoveButton("下移", index + 1, index >= lastIndex);
    addMoveButton("置底", lastIndex, index >= lastIndex);
    controls.appendChild(actionButton("移除", async () => {
      if (typeof onRemove === "function") {
        await onRemove();
      }
      imageUtils.normalizeImageOrder(items);
      rerender();
    }, "admin-product-image-order-button admin-product-image-order-remove"));
    return controls;
  }

  root.AdminProductFormUi = {
    actionButton,
    labelSpan,
    skuInput,
    pickImageFile,
    pickImageFiles,
    appendImageOrderBadge,
    imageOrderControls
  };
})(window);
