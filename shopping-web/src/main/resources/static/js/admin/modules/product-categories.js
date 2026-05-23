(function (root) {
  const api = root.AdminApi;
  const router = root.AdminRouter;
  const API_BASE = "/shopping/admin/api/product-categories";
  const MAX_LEVEL = 3;

  const state = {
    mounted: false,
    treeLoaded: false,
    tree: [],
    expanded: new Set(),
    selectedIds: new Set(),
    dialogMode: "create",
    editingNode: null,
    parentNode: null
  };

  const el = {};

  function $(id) {
    return document.getElementById(id);
  }

  function mount() {
    if (state.mounted) {
      return;
    }
    Object.assign(el, {
      createRoot: $("admin-product-category-create-root"),
      batchDisable: $("admin-product-category-batch-disable"),
      selectAll: $("admin-product-category-select-all"),
      expandAll: $("admin-product-category-expand-all"),
      collapseAll: $("admin-product-category-collapse-all"),
      refresh: $("admin-product-category-refresh"),
      total: $("admin-product-category-total"),
      activeTotal: $("admin-product-category-active-total"),
      activeProducts: $("admin-product-category-active-products"),
      status: $("admin-product-category-status"),
      list: $("admin-product-category-list"),
      dialog: $("admin-product-category-dialog"),
      dialogBackdrop: $("admin-product-category-dialog-backdrop"),
      dialogClose: $("admin-product-category-dialog-close"),
      dialogTitle: $("admin-product-category-dialog-title"),
      dialogSubtitle: $("admin-product-category-dialog-subtitle"),
      form: $("admin-product-category-form"),
      idInput: $("admin-product-category-id"),
      parentIdInput: $("admin-product-category-parent-id"),
      parentLabel: $("admin-product-category-parent-label"),
      nameInput: $("admin-product-category-name"),
      codeInput: $("admin-product-category-code"),
      sortOrderInput: $("admin-product-category-sort-order"),
      statusInput: $("admin-product-category-edit-status"),
      iconUrlsInput: $("admin-product-category-icon-urls"),
      descriptionInput: $("admin-product-category-description"),
      formStatus: $("admin-product-category-form-status"),
      cancel: $("admin-product-category-cancel"),
      save: $("admin-product-category-save")
    });
    if (!el.list) {
      return;
    }
    state.mounted = true;
    bindEvents();
    router?.register?.("productCategories", loadTree);
  }

  function bindEvents() {
    el.createRoot?.addEventListener("click", () => openCreateDialog(null));
    el.batchDisable?.addEventListener("click", batchDisableCategories);
    el.selectAll?.addEventListener("change", toggleVisibleSelection);
    el.expandAll?.addEventListener("click", () => {
      expandAllNodes();
      render();
    });
    el.collapseAll?.addEventListener("click", () => {
      state.expanded.clear();
      render();
    });
    el.refresh?.addEventListener("click", loadTree);
    el.dialogBackdrop?.addEventListener("click", closeDialog);
    el.dialogClose?.addEventListener("click", closeDialog);
    el.cancel?.addEventListener("click", closeDialog);
    el.form?.addEventListener("submit", submitForm);
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && isDialogOpen()) {
        closeDialog();
      }
    });
  }

  async function loadTree() {
    setPageBusy(true);
    api.setStatus(el.status, "正在加载分类树。");
    try {
      const response = await api.get(`${API_BASE}/tree`);
      state.tree = Array.isArray(response.data) ? response.data : [];
      if (!state.treeLoaded) {
        expandAllNodes();
        state.treeLoaded = true;
      } else {
        pruneExpanded();
      }
      pruneSelected();
      render();
      api.setStatus(el.status, "分类树已刷新。", "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "分类树加载失败。", "error");
      render();
    } finally {
      setPageBusy(false);
    }
  }

  function render() {
    renderSummary();
    el.list.replaceChildren();
    const rows = visibleRows();
    if (rows.length === 0) {
      const empty = document.createElement("div");
      empty.className = "admin-product-category-empty";
      empty.textContent = "暂无商品分类";
      el.list.appendChild(empty);
      updateBulkActionState();
      return;
    }
    rows.forEach(({ node, depth }) => {
      el.list.appendChild(renderRow(node, depth));
    });
    updateBulkActionState();
  }

  function renderSummary() {
    const summary = { total: 0, active: 0, activeProducts: 0 };
    walkNodes(state.tree, (node) => {
      summary.total += 1;
      if (node.status === "ACTIVE") {
        summary.active += 1;
      }
      summary.activeProducts += Number(node.activeProductCount || 0);
    });
    setText(el.total, String(summary.total));
    setText(el.activeTotal, String(summary.active));
    setText(el.activeProducts, String(summary.activeProducts));
  }

  function renderRow(node, depth) {
    const nodeId = String(node.id || "");
    const row = document.createElement("div");
    row.className = "admin-product-category-row";
    row.classList.toggle("is-selected", state.selectedIds.has(nodeId));
    row.style.setProperty("--category-depth", String(Math.max(1, depth)));

    const selectCell = document.createElement("div");
    selectCell.className = "admin-product-category-select-cell";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.value = nodeId;
    checkbox.checked = state.selectedIds.has(nodeId);
    checkbox.setAttribute("aria-label", `选择分类 ${node.name || node.id || ""}`);
    checkbox.addEventListener("change", () => {
      if (checkbox.checked) {
        state.selectedIds.add(nodeId);
      } else {
        state.selectedIds.delete(nodeId);
      }
      row.classList.toggle("is-selected", state.selectedIds.has(nodeId));
      updateBulkActionState();
    });
    selectCell.appendChild(checkbox);
    row.appendChild(selectCell);

    const nameCell = document.createElement("div");
    nameCell.className = "admin-product-category-name-cell";
    const hasChildren = Array.isArray(node.children) && node.children.length > 0;
    const toggle = document.createElement("button");
    toggle.className = "admin-product-category-toggle admin-spring-button";
    toggle.type = "button";
    toggle.disabled = !hasChildren;
    toggle.setAttribute("aria-expanded", String(isExpanded(node)));
    toggle.setAttribute("aria-label", hasChildren ? "展开或收起分类" : "无子分类");
    toggle.textContent = hasChildren ? (isExpanded(node) ? "▾" : "▸") : "";
    toggle.addEventListener("click", () => toggleExpanded(node));
    nameCell.appendChild(toggle);

    const nameText = document.createElement("div");
    nameText.className = "admin-product-category-name-text";
    const nameStrong = document.createElement("strong");
    nameStrong.textContent = node.name || "-";
    const nameSmall = document.createElement("small");
    nameSmall.textContent = `ID ${node.id || "-"}`;
    nameText.append(nameStrong, nameSmall);
    nameCell.appendChild(nameText);
    row.appendChild(nameCell);

    row.appendChild(textCell(node.code || "-"));
    row.appendChild(textCell(`${node.level || 1} 级`));
    row.appendChild(textCell(String(node.sortOrder ?? 0)));
    row.appendChild(statusCell(node.status));
    row.appendChild(productCell(node));
    row.appendChild(actionCell(node));
    return row;
  }

  function textCell(text) {
    const cell = document.createElement("div");
    cell.className = "admin-product-category-cell";
    cell.textContent = text;
    return cell;
  }

  function statusCell(status) {
    const cell = document.createElement("div");
    const badge = document.createElement("span");
    badge.className = `admin-product-category-status-badge ${status === "ACTIVE" ? "is-active" : "is-disabled"}`;
    badge.textContent = status === "ACTIVE" ? "启用" : "禁用";
    cell.appendChild(badge);
    return cell;
  }

  function productCell(node) {
    const cell = document.createElement("div");
    cell.className = "admin-product-category-product-cell";
    const total = document.createElement("strong");
    total.textContent = String(node.productCount || 0);
    const active = document.createElement("small");
    active.textContent = `启用 ${node.activeProductCount || 0}`;
    cell.append(total, active);
    return cell;
  }

  function actionCell(node) {
    const cell = document.createElement("div");
    cell.className = "admin-product-category-actions";
    if (Number(node.level || 1) < MAX_LEVEL && Number(node.productCount || 0) === 0) {
      cell.appendChild(actionButton("添加子分类", "", () => openCreateDialog(node)));
    }
    cell.appendChild(actionButton("修改", "", () => openEditDialog(node)));
    if (node.status === "ACTIVE") {
      cell.appendChild(actionButton("禁用", "is-add", () => changeStatus(node, "DISABLED")));
    } else {
      cell.appendChild(actionButton("启用", "is-remove", () => changeStatus(node, "ACTIVE")));
    }
    cell.appendChild(actionButton("删除", "is-add", () => deleteCategory(node)));
    return cell;
  }

  function actionButton(label, extraClass, onClick) {
    const button = document.createElement("button");
    button.className = `admin-risk-ip-action-btn admin-spring-button ${extraClass || ""}`.trim();
    button.type = "button";
    button.textContent = label;
    button.addEventListener("click", onClick);
    return button;
  }

  function visibleRows() {
    const rows = [];
    const append = (nodes, depth) => {
      (Array.isArray(nodes) ? nodes : []).forEach((node) => {
        rows.push({ node, depth });
        if (isExpanded(node)) {
          append(node.children, depth + 1);
        }
      });
    };
    append(state.tree, 1);
    return rows;
  }

  function toggleVisibleSelection() {
    const ids = visibleRows().map(({ node }) => String(node.id || "")).filter(Boolean);
    const allSelected = ids.length > 0 && ids.every((id) => state.selectedIds.has(id));
    ids.forEach((id) => {
      if (allSelected) {
        state.selectedIds.delete(id);
      } else {
        state.selectedIds.add(id);
      }
    });
    render();
  }

  async function batchDisableCategories() {
    const ids = selectedCategoryIds();
    if (ids.length === 0) {
      api.setStatus(el.status, "请选择需要禁用的商品分类。", "error");
      return;
    }
    const stats = selectedSubtreeStats(ids);
    if (stats.activeProducts > 0) {
      api.setStatus(el.status, "选中的分类或子分类下存在启用商品，请先将商品禁用后再批量禁用分类。", "error");
      return;
    }
    const confirmed = window.confirm(`确认批量禁用 ${ids.length} 个选中分类及其子分类？`);
    if (!confirmed) {
      return;
    }
    setPageBusy(true);
    try {
      const response = await send("POST", `${API_BASE}/batch-disable`, { ids });
      const result = response.data || {};
      state.selectedIds.clear();
      await loadTree();
      api.setStatus(el.status, `已批量禁用 ${result.affectedCount || 0} 个分类，涉及子树分类 ${result.subtreeCount || 0} 个。`, "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "批量禁用失败。", "error");
    } finally {
      setPageBusy(false);
    }
  }

  function selectedCategoryIds() {
    return Array.from(state.selectedIds)
      .map((id) => Number.parseInt(id, 10))
      .filter((id) => Number.isSafeInteger(id) && id > 0);
  }

  function openCreateDialog(parentNode) {
    if (parentNode && Number(parentNode.productCount || 0) > 0) {
      api.setStatus(el.status, "该分类下已存在商品，不能继续添加子分类，请先移动或删除该分类下的商品。", "error");
      return;
    }
    state.dialogMode = "create";
    state.editingNode = null;
    state.parentNode = parentNode || null;
    const parentId = parentNode?.id || 0;
    el.form?.reset();
    el.idInput.value = "";
    el.parentIdInput.value = String(parentId);
    el.parentLabel.textContent = parentNode ? `${parentNode.name} / ${parentNode.level || 1} 级` : "一级分类";
    el.dialogTitle.textContent = parentNode ? "添加子分类" : "添加一级分类";
    el.dialogSubtitle.textContent = "Create category";
    el.sortOrderInput.value = "0";
    el.statusInput.value = parentNode?.status === "DISABLED" ? "DISABLED" : "ACTIVE";
    el.iconUrlsInput.value = "[]";
    el.descriptionInput.value = "";
    api.setStatus(el.formStatus, "");
    openDialog();
  }

  function openEditDialog(node) {
    state.dialogMode = "edit";
    state.editingNode = node;
    state.parentNode = findNodeById(node.parentId);
    el.form?.reset();
    el.idInput.value = String(node.id || "");
    el.parentIdInput.value = String(node.parentId || 0);
    el.parentLabel.textContent = state.parentNode ? state.parentNode.name : "一级分类";
    el.dialogTitle.textContent = "修改分类";
    el.dialogSubtitle.textContent = "Edit category";
    el.nameInput.value = node.name || "";
    el.codeInput.value = node.code || "";
    el.sortOrderInput.value = String(node.sortOrder ?? 0);
    el.statusInput.value = node.status === "DISABLED" ? "DISABLED" : "ACTIVE";
    el.iconUrlsInput.value = JSON.stringify(Array.isArray(node.iconUrls) ? node.iconUrls : [], null, 2);
    el.descriptionInput.value = node.description || "";
    api.setStatus(el.formStatus, "");
    openDialog();
  }

  function openDialog() {
    el.dialog.hidden = false;
    el.dialog.setAttribute("aria-hidden", "false");
    window.setTimeout(() => el.nameInput?.focus(), 0);
  }

  function closeDialog() {
    if (!el.dialog) {
      return;
    }
    el.dialog.hidden = true;
    el.dialog.setAttribute("aria-hidden", "true");
    state.editingNode = null;
    state.parentNode = null;
    api.setStatus(el.formStatus, "");
  }

  function isDialogOpen() {
    return el.dialog && !el.dialog.hidden;
  }

  async function submitForm(event) {
    event.preventDefault();
    let payload;
    try {
      payload = readFormPayload();
    } catch (error) {
      api.setStatus(el.formStatus, error.message, "error");
      return;
    }

    setFormBusy(true);
    try {
      if (state.dialogMode === "edit") {
        await send("PUT", `${API_BASE}/${encodeURIComponent(el.idInput.value)}`, payload);
      } else {
        await send("POST", API_BASE, payload);
        if (payload.parentId) {
          state.expanded.add(String(payload.parentId));
        }
      }
      closeDialog();
      await loadTree();
      api.setStatus(el.status, state.dialogMode === "edit" ? "分类已修改。" : "分类已添加。", "ok");
    } catch (error) {
      api.setStatus(el.formStatus, error.message || "保存失败。", "error");
    } finally {
      setFormBusy(false);
    }
  }

  function readFormPayload() {
    const name = el.nameInput.value.trim();
    const code = el.codeInput.value.trim();
    if (!name) {
      throw new Error("分类名称不能为空。");
    }
    if (!code) {
      throw new Error("分类编码不能为空。");
    }
    const sortOrder = Number.parseInt(el.sortOrderInput.value || "0", 10);
    return {
      parentId: el.parentIdInput.value.trim() || "0",
      name,
      code,
      sortOrder: Number.isFinite(sortOrder) ? sortOrder : 0,
      iconUrls: parseIconUrlsInput(),
      description: el.descriptionInput.value.trim(),
      status: el.statusInput.value
    };
  }

  function parseIconUrlsInput() {
    const raw = el.iconUrlsInput.value.trim();
    if (!raw) {
      return [];
    }
    let parsed;
    try {
      parsed = JSON.parse(raw);
    } catch (_) {
      throw new Error("分类图标必须是 JSON 数组。");
    }
    if (!Array.isArray(parsed)) {
      throw new Error("分类图标必须是 JSON 数组。");
    }
    return parsed;
  }

  async function changeStatus(node, status) {
    const isDisable = status === "DISABLED";
    if (isDisable && Number(node.childCount || 0) > 0) {
      const confirmed = window.confirm("禁用该分类会同时禁用它的子分类，是否继续？");
      if (!confirmed) {
        return;
      }
    }
    setPageBusy(true);
    try {
      await send("PATCH", `${API_BASE}/${encodeURIComponent(node.id)}/status`, { status });
      await loadTree();
      api.setStatus(el.status, status === "ACTIVE" ? "分类已启用。" : "分类已禁用。", "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "状态更新失败。", "error");
    } finally {
      setPageBusy(false);
    }
  }

  async function deleteCategory(node) {
    if (Number(node.childCount || 0) > 0) {
      api.setStatus(el.status, "该分类下存在子分类，请先处理子分类后再删除。", "error");
      return;
    }
    if (Number(node.activeProductCount || 0) > 0) {
      api.setStatus(el.status, "该分类下存在启用商品，请先将商品禁用后再删除分类。", "error");
      return;
    }
    if (!window.confirm(`确认删除分类「${node.name || node.id}」？`)) {
      return;
    }
    setPageBusy(true);
    try {
      await send("DELETE", `${API_BASE}/${encodeURIComponent(node.id)}`);
      state.expanded.delete(String(node.id));
      await loadTree();
      api.setStatus(el.status, "分类已删除。", "ok");
    } catch (error) {
      api.setStatus(el.status, error.message || "删除失败。", "error");
    } finally {
      setPageBusy(false);
    }
  }

  function expandAllNodes() {
    state.expanded.clear();
    walkNodes(state.tree, (node) => {
      if (Array.isArray(node.children) && node.children.length > 0) {
        state.expanded.add(String(node.id));
      }
    });
  }

  function pruneExpanded() {
    const ids = new Set();
    walkNodes(state.tree, (node) => ids.add(String(node.id)));
    Array.from(state.expanded).forEach((id) => {
      if (!ids.has(id)) {
        state.expanded.delete(id);
      }
    });
  }

  function pruneSelected() {
    const ids = new Set();
    walkNodes(state.tree, (node) => ids.add(String(node.id)));
    Array.from(state.selectedIds).forEach((id) => {
      if (!ids.has(id)) {
        state.selectedIds.delete(id);
      }
    });
  }

  function selectedSubtreeStats(selectedIds) {
    const selected = new Set(selectedIds.map(String));
    const included = new Set();
    let activeProducts = 0;
    walkNodes(state.tree, (node) => {
      if (selected.has(String(node.id))) {
        walkNodes([node], (child) => {
          const id = String(child.id);
          if (!included.has(id)) {
            included.add(id);
            activeProducts += Number(child.activeProductCount || 0);
          }
        });
      }
    });
    return {
      categoryCount: included.size,
      activeProducts
    };
  }

  function toggleExpanded(node) {
    const id = String(node.id);
    if (state.expanded.has(id)) {
      state.expanded.delete(id);
    } else {
      state.expanded.add(id);
    }
    render();
  }

  function isExpanded(node) {
    return state.expanded.has(String(node.id));
  }

  function findNodeById(id) {
    let found = null;
    walkNodes(state.tree, (node) => {
      if (!found && String(node.id) === String(id)) {
        found = node;
      }
    });
    return found;
  }

  function walkNodes(nodes, callback) {
    (Array.isArray(nodes) ? nodes : []).forEach((node) => {
      callback(node);
      walkNodes(node.children, callback);
    });
  }

  function setPageBusy(busy) {
    [el.createRoot, el.batchDisable, el.expandAll, el.collapseAll, el.refresh].forEach((button) => {
      if (button) {
        button.disabled = Boolean(busy);
      }
    });
    if (el.selectAll) {
      el.selectAll.disabled = Boolean(busy);
    }
    el.list?.querySelectorAll("input[type='checkbox']").forEach((checkbox) => {
      checkbox.disabled = Boolean(busy);
    });
    if (!busy) {
      updateBulkActionState();
    }
  }

  function updateBulkActionState() {
    const selectedCount = state.selectedIds.size;
    if (el.batchDisable) {
      el.batchDisable.disabled = selectedCount === 0;
      el.batchDisable.textContent = selectedCount > 0 ? `批量禁用 (${selectedCount})` : "批量禁用";
    }
    if (el.selectAll) {
      const visibleIds = visibleRows().map(({ node }) => String(node.id || "")).filter(Boolean);
      const selectedVisibleCount = visibleIds.filter((id) => state.selectedIds.has(id)).length;
      el.selectAll.disabled = visibleIds.length === 0;
      el.selectAll.checked = visibleIds.length > 0 && selectedVisibleCount === visibleIds.length;
      el.selectAll.indeterminate = selectedVisibleCount > 0 && selectedVisibleCount < visibleIds.length;
    }
  }

  function setFormBusy(busy) {
    [el.save, el.cancel, el.dialogClose].forEach((button) => {
      if (button) {
        button.disabled = Boolean(busy);
      }
    });
  }

  function setText(node, text) {
    if (node) {
      node.textContent = text;
    }
  }

  async function send(method, path, payload) {
    if (typeof api.requestWithMethod === "function") {
      return api.requestWithMethod(method, path, payload);
    }
    if (method === "POST") {
      return api.request(path, payload);
    }
    throw new Error("当前管理端请求工具不支持该操作。");
  }

  root.AdminProductCategoriesModule = {
    mount
  };
})(window);
