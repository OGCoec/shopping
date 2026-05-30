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
    input.type = type === "money" || type === "stock" ? "text" : type;
    input.value = value || "";
    const rawValue = input.value;
    input.autocomplete = "off";
    if (type === "number") {
      input.min = "0";
      input.step = label.includes("价格") || label.includes("原价") ? "0.01" : "1";
    }
    if (type === "money") {
      input.inputMode = "decimal";
      input.pattern = "\\d+(\\.\\d{0,2})?";
      input.value = sanitizeMoneyInput(input.value);
    } else if (type === "stock") {
      input.inputMode = "numeric";
      input.pattern = "\\d*";
      input.value = sanitizeStockInput(input.value);
    }
    if (input.value !== rawValue) {
      onInput(input.value.trim());
    }
    input.addEventListener("input", () => {
      if (type === "money") {
        input.value = sanitizeMoneyInput(input.value);
      } else if (type === "stock") {
        input.value = sanitizeStockInput(input.value);
      }
      onInput(input.value.trim());
    });
    field.append(labelSpan(label), input);
    return field;
  }

  function sanitizeMoneyInput(value) {
    const text = String(value ?? "").replace(/[^\d.]/g, "");
    let next = "";
    let hasDot = false;
    for (const ch of text) {
      if (ch === ".") {
        if (!hasDot) {
          next += ch;
          hasDot = true;
        }
        continue;
      }
      next += ch;
    }
    const dotIndex = next.indexOf(".");
    if (dotIndex >= 0) {
      return next.slice(0, dotIndex + 1) + next.slice(dotIndex + 1, dotIndex + 3);
    }
    return next;
  }

  function sanitizeStockInput(value) {
    return String(value ?? "").replace(/\D/g, "");
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
