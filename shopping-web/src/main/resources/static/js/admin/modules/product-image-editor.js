(function (root) {
  function create(options = {}) {
    const imageUtils = options.imageUtils || root.AdminProductImageUtils;
    const formUi = options.formUi || root.AdminProductFormUi;
    const uploadSession = options.uploadSession;
    const {
      normalizeImageItems,
      normalizeImageOrder,
      syncMainImageFromDisplayImages,
      imageItemUrl,
      setImageItemUrl,
      escapeHtml,
      appendImageOrPlaceholder
    } = imageUtils;
    const {
      actionButton,
      appendImageOrderBadge,
      imageOrderControls
    } = formUi;

  function mainImagePreview(draft) {
    const section = document.createElement("section");
    section.className = "admin-product-detail-section";
    section.innerHTML = `<h3>当前主图</h3>`;
    const editor = document.createElement("div");
    editor.className = "admin-product-detail-image-editor";
    const preview = document.createElement("div");
    preview.className = "admin-product-detail-edit-preview admin-product-main-image-preview";
    appendImageOrPlaceholder(preview, draft.mainImageUrl, "主图");
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
      headingActions.appendChild(actionButton("上传新主图", async () => uploadSession.uploadMainIntoDisplay(editorOptions.draft, rerender)));
    }
    headingActions.appendChild(actionButton("批量上传图片", async () => uploadSession.uploadImages(items, () => {
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
      const preview = document.createElement("div");
      preview.className = "admin-product-detail-edit-preview";
      appendImageOrPlaceholder(preview, url, title);
      preview.classList.add("admin-product-image-drag-handle");
      preview.draggable = items.length > 1;
      preview.querySelector("img")?.setAttribute("draggable", "false");
      appendImageOrderBadge(preview, index, editorOptions.displayImages && index === 0 ? "主图 / #1" : `#${index + 1}`);
      const input = document.createElement("input");
      input.type = "url";
      input.value = url;
      input.draggable = false;
      input.addEventListener("input", () => {
        setImageItemUrl(item, input.value.trim());
        if (editorOptions.displayImages && editorOptions.draft) {
          syncMainImageFromDisplayImages(editorOptions.draft);
        }
      });
      row.append(preview, input);
      row.appendChild(imageEditorControls(items, index, rerender, async () => {
        await uploadSession.cancelByUrl(imageItemUrl(item));
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
      appendImageOrPlaceholder(preview, url, sku.skuName || "SKU");
      appendImageOrderBadge(preview, index);
      const controls = imageOrderControls(sku.skuImageUrls, index, rerender, async () => {
        await uploadSession.cancelByUrl(url);
        sku.skuImageUrls.splice(index, 1);
      });
      row.append(preview, controls);
      list.appendChild(row);
    });
    return list;
  }

    return {
      mainImagePreview,
      imageListEditor,
      skuImageList
    };
  }

  root.AdminProductImageEditor = { create };
})(window);
