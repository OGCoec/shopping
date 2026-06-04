(function (root) {
  const api = root.AdminApi;
  const router = root.AdminRouter;
  const orderApi = root.AdminOrderApi;
  const CONSOLE_ORDERS_PATH = "/shopping/admin/console/orders";

  const state = {
    mounted: false,
    page: 1,
    pageSize: 20,
    total: 0,
    records: [],
    currentOrderNo: "",
    detail: null,
    pageBusy: false
  };

  const el = {};

  const statusLabels = {
    PENDING_PAYMENT: "待支付",
    CLOSING: "关闭确认中",
    PAID: "已支付",
    CANCELLED: "已取消",
    CLOSED: "已关闭"
  };

  function $(id) {
    return document.getElementById(id);
  }

  function mount(panel) {
    if (state.mounted) {
      return;
    }
    Object.assign(el, {
      panel,
      card: panel.querySelector(".admin-order-card"),
      refresh: $("admin-order-refresh"),
      filterForm: $("admin-order-filter-form"),
      filterOrderNo: $("admin-order-filter-order-no"),
      filterStatus: $("admin-order-filter-status"),
      pageSize: $("admin-order-page-size"),
      total: $("admin-order-total"),
      pageLabel: $("admin-order-page-label"),
      status: $("admin-order-status"),
      list: $("admin-order-list"),
      prev: $("admin-order-prev"),
      next: $("admin-order-next"),
      reset: $("admin-order-filter-reset"),
      detailPage: $("admin-order-detail-page"),
      detailStatus: $("admin-order-detail-status"),
      detailBody: $("admin-order-detail-body")
    });
    if (!el.list || !el.detailPage) {
      return;
    }
    state.mounted = true;
    bindEvents();
    router?.register?.("orders", routeOrders);
  }

  function bindEvents() {
    el.refresh?.addEventListener("click", () => {
      if (state.currentOrderNo) {
        openDetail(state.currentOrderNo);
        return;
      }
      loadPage();
    });
    el.filterForm?.addEventListener("submit", (event) => {
      event.preventDefault();
      const orderNo = String(el.filterOrderNo?.value || "").trim();
      if (orderNo) {
        navigateToOrderDetail(orderNo);
        return;
      }
      state.page = 1;
      loadPage();
    });
    el.reset?.addEventListener("click", () => {
      el.filterOrderNo.value = "";
      el.filterStatus.value = "";
      state.page = 1;
      loadPage();
    });
    el.pageSize?.addEventListener("change", () => {
      const pageSize = readPageSize();
      if (!pageSize) {
        return;
      }
      state.pageSize = pageSize;
      state.page = 1;
      loadPage();
    });
    el.prev?.addEventListener("click", () => {
      if (state.page > 1) {
        state.page -= 1;
        loadPage();
      }
    });
    el.next?.addEventListener("click", () => {
      if (state.page * state.pageSize < state.total) {
        state.page += 1;
        loadPage();
      }
    });
  }

  async function routeOrders() {
    const orderNo = orderNoFromLocation();
    if (orderNo) {
      await openDetail(orderNo);
      return;
    }
    closeDetail(true);
    showListView();
    await loadPage();
  }

  function orderNoFromLocation() {
    const normalizedPath = String(window.location.pathname || "").replace(/\/+$/, "");
    const prefix = `${CONSOLE_ORDERS_PATH}/`;
    if (!normalizedPath.startsWith(prefix)) {
      return "";
    }
    return decodeURIComponent(normalizedPath.slice(prefix.length).split("/")[0] || "").trim();
  }

  function navigateToOrderDetail(orderNo) {
    const id = String(orderNo || "").trim();
    if (!id) {
      return;
    }
    if (window.history?.pushState) {
      const url = new URL(window.location.href);
      url.pathname = `${CONSOLE_ORDERS_PATH}/${encodeURIComponent(id)}`;
      url.search = "";
      window.history.pushState({ adminSection: "orders", orderNo: id }, "", url.pathname + url.search + url.hash);
      routeOrders();
      return;
    }
    openDetail(id);
  }

  function navigateToOrderList() {
    state.currentOrderNo = "";
    if (router?.switchSection) {
      router.switchSection("orders");
      return;
    }
    if (window.history?.pushState) {
      window.history.pushState({ adminSection: "orders" }, "", CONSOLE_ORDERS_PATH);
    }
    routeOrders();
  }

  function showListView() {
    state.currentOrderNo = "";
    if (el.card) {
      el.card.hidden = false;
    }
    if (el.detailPage) {
      el.detailPage.hidden = true;
    }
  }

  function showDetailView() {
    if (el.card) {
      el.card.hidden = true;
    }
    if (el.detailPage) {
      el.detailPage.hidden = false;
    }
  }

  function closeDetail(silent = false) {
    state.currentOrderNo = "";
    state.detail = null;
    if (!silent && el.detailBody) {
      el.detailBody.replaceChildren();
    }
  }

  function readPageSize() {
    const rawValue = String(el.pageSize?.value || "").trim();
    const value = Number(rawValue);
    if (!rawValue || !Number.isInteger(value) || value <= 0) {
      api.setStatus(el.status, "每页数量必须是大于 0 的整数。", "error");
      return null;
    }
    return Math.min(value, 100);
  }

  async function loadPage() {
    const pageSize = readPageSize();
    if (!pageSize) {
      return;
    }
    state.pageSize = pageSize;
    state.pageBusy = true;
    renderSummary();
    api.setStatus(el.status, "正在加载订单列表。");
    try {
      const params = new URLSearchParams();
      params.set("page", String(state.page));
      params.set("pageSize", String(pageSize));
      appendParam(params, "status", el.filterStatus?.value);
      const response = await orderApi.fetchOrderPage(params);
      const data = response.data || {};
      state.total = Number(data.total || 0);
      state.page = Number(data.page || state.page);
      state.pageSize = Number(data.pageSize || state.pageSize);
      state.records = Array.isArray(data.records) ? data.records : [];
      renderOrders(state.records);
      renderSummary();
      api.setStatus(el.status, "订单列表已刷新。", "ok");
    } catch (error) {
      state.records = [];
      renderOrders([]);
      renderSummary();
      api.setStatus(el.status, error.message || "订单列表加载失败。", "error");
    } finally {
      state.pageBusy = false;
      renderSummary();
    }
  }

  function appendParam(params, key, value) {
    const text = String(value || "").trim();
    if (text) {
      params.set(key, text);
    }
  }

  function renderOrders(records) {
    el.list.replaceChildren();
    if (!records.length) {
      const empty = document.createElement("div");
      empty.className = "admin-order-empty";
      empty.textContent = "暂无订单";
      el.list.appendChild(empty);
      return;
    }
    records.forEach((order) => el.list.appendChild(renderOrderRow(order)));
  }

  function renderOrderRow(order) {
    const orderNo = String(order.orderNo || "");
    const row = document.createElement("div");
    row.className = "admin-order-row";
    row.dataset.orderNo = orderNo;
    row.dataset.status = String(order.status || "");
    row.classList.toggle("is-clickable", Boolean(orderNo));
    if (orderNo) {
      row.tabIndex = 0;
      row.setAttribute("role", "button");
      row.setAttribute("aria-label", `查看订单 ${orderNo}`);
      row.addEventListener("click", () => navigateToOrderDetail(orderNo));
      row.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          navigateToOrderDetail(orderNo);
        }
      });
    }

    const primary = document.createElement("div");
    primary.className = "admin-order-primary-cell";
    const strong = document.createElement("strong");
    strong.textContent = orderNo || "-";
    const small = document.createElement("small");
    small.textContent = formatDate(order.createdAt);
    primary.append(strong, small);
    row.appendChild(primary);

    row.appendChild(textCell(order.userId || "-"));
    row.appendChild(statusBadge(order.status));
    row.appendChild(textCell(formatMoney(order.payAmountYuan)));
    row.appendChild(textCell(order.firstSkuName || "-"));
    row.appendChild(sourceBadge(order.storageSource));
    row.appendChild(actionCell(orderNo));
    return row;
  }

  function actionCell(orderNo) {
    const cell = document.createElement("div");
    cell.className = "admin-order-actions";
    cell.addEventListener("click", (event) => event.stopPropagation());
    const button = document.createElement("button");
    button.className = "admin-risk-ip-action-btn admin-spring-button is-add";
    button.type = "button";
    button.textContent = "详情";
    button.disabled = !orderNo;
    button.addEventListener("click", () => navigateToOrderDetail(orderNo));
    cell.appendChild(button);
    return cell;
  }

  function renderSummary() {
    setText(el.total, String(state.total || 0));
    setText(el.pageLabel, String(state.page || 1));
    if (el.prev) {
      el.prev.disabled = state.pageBusy || state.page <= 1;
    }
    if (el.next) {
      el.next.disabled = state.pageBusy || state.page * state.pageSize >= state.total;
    }
  }

  async function openDetail(orderNo) {
    const id = String(orderNo || "").trim();
    if (!id) {
      navigateToOrderList();
      return;
    }
    state.currentOrderNo = id;
    if (el.filterOrderNo) {
      el.filterOrderNo.value = id;
    }
    showDetailView();
    renderDetailLoading();
    try {
      const response = await orderApi.getOrderDetail(id);
      state.detail = response.data || null;
      renderDetail();
    } catch (error) {
      state.detail = null;
      renderDetailError(error.status === 404 ? "订单不存在。" : error.message || "订单详情加载失败。");
    }
  }

  function renderDetailLoading() {
    api.setStatus(el.detailStatus, "");
    el.detailBody.replaceChildren(emptyNode("正在加载订单详情"));
  }

  function renderDetailError(message) {
    el.detailBody.replaceChildren(emptyNode(message));
    api.setStatus(el.detailStatus, message, "error");
  }

  function renderDetail() {
    const order = state.detail;
    if (!order) {
      renderDetailError("订单不存在。");
      return;
    }
    el.detailBody.replaceChildren();
    const shell = document.createElement("div");
    shell.className = "admin-product-detail-content admin-order-detail-content";
    shell.appendChild(detailToolbar(order));
    shell.appendChild(stateCopy(order));
    shell.appendChild(detailGrid(order));
    shell.appendChild(itemsSection(order.items));
    el.detailBody.appendChild(shell);
    api.setStatus(el.detailStatus, "");
  }

  function detailToolbar(order) {
    const toolbar = document.createElement("div");
    toolbar.className = "admin-product-detail-toolbar";
    const heading = document.createElement("div");
    heading.className = "admin-product-detail-title";
    const title = document.createElement("strong");
    title.textContent = `订单 ${order.orderNo || "-"}`;
    const small = document.createElement("small");
    small.textContent = `用户 ${order.userId || "-"} · ${order.storageSource || "-"}`;
    heading.append(title, small);
    const actions = document.createElement("div");
    actions.className = "admin-product-detail-actions admin-order-detail-actions";
    actions.appendChild(button("返回列表", "admin-api-back", navigateToOrderList));
    actions.appendChild(button("刷新", "admin-ghost-button", () => openDetail(state.currentOrderNo)));
    toolbar.append(heading, actions);
    return toolbar;
  }

  function stateCopy(order) {
    const node = document.createElement("p");
    node.className = "admin-order-state-copy";
    if (order.status === "CLOSING") {
      node.textContent = `订单正在关闭中，系统等待支付结果确认到 ${formatDate(order.closingDeadlineAt)}。`;
    } else if (order.status === "PENDING_PAYMENT") {
      node.textContent = `订单待支付，支付截止时间 ${formatDate(order.expireAt)}。`;
    } else if (order.status === "PAID") {
      node.textContent = `订单已支付，支付时间 ${formatDate(order.paidAt)}。`;
    } else if (order.status === "CANCELLED") {
      node.textContent = `订单已取消，取消时间 ${formatDate(order.cancelledAt)}。`;
    } else if (order.status === "CLOSED") {
      node.textContent = `订单已关闭，关闭时间 ${formatDate(order.closedAt)}。`;
    } else {
      node.textContent = "订单状态已更新。";
    }
    return node;
  }

  function detailGrid(order) {
    const grid = document.createElement("div");
    grid.className = "admin-product-detail-grid admin-order-detail-grid";
    [
      ["订单号", order.orderNo || "-"],
      ["用户 ID", order.userId || "-"],
      ["状态", statusText(order.status)],
      ["商品金额", formatMoney(order.totalAmountYuan)],
      ["优惠金额", formatMoney(order.discountAmountYuan)],
      ["应付金额", formatMoney(order.payAmountYuan)],
      ["优惠券", order.userCouponId || "-"],
      ["支付截止", formatDate(order.expireAt)],
      ["关闭开始", formatDate(order.closingAt)],
      ["关闭截止", formatDate(order.closingDeadlineAt)],
      ["支付时间", formatDate(order.paidAt)],
      ["取消时间", formatDate(order.cancelledAt)],
      ["关闭时间", formatDate(order.closedAt)],
      ["创建时间", formatDate(order.createdAt)],
      ["更新时间", formatDate(order.updatedAt)],
      ["数据来源", order.storageSource || "-"]
    ].forEach(([label, value]) => {
      const item = document.createElement("div");
      const term = document.createElement("span");
      term.textContent = label;
      const desc = document.createElement("strong");
      desc.textContent = value;
      item.append(term, desc);
      grid.appendChild(item);
    });
    return grid;
  }

  function itemsSection(items) {
    const section = document.createElement("section");
    section.className = "admin-product-detail-section";
    const heading = document.createElement("div");
    heading.className = "admin-product-detail-section-heading";
    const title = document.createElement("h3");
    title.textContent = "商品明细";
    heading.appendChild(title);
    section.appendChild(heading);

    const table = document.createElement("div");
    table.className = "admin-order-item-table";
    const header = document.createElement("div");
    header.className = "admin-order-item-row admin-order-item-header";
    ["商品", "SKU 编码", "单价", "数量", "小计", "库存类型"].forEach((text) => {
      const cell = document.createElement("span");
      cell.textContent = text;
      header.appendChild(cell);
    });
    const list = document.createElement("div");
    list.className = "admin-order-item-list";
    const records = Array.isArray(items) ? items : [];
    if (!records.length) {
      list.appendChild(emptyNode("暂无商品明细"));
    } else {
      records.forEach((item) => list.appendChild(itemRow(item)));
    }
    table.append(header, list);
    section.appendChild(table);
    return section;
  }

  function itemRow(item) {
    const row = document.createElement("div");
    row.className = "admin-order-item-row";
    const sku = document.createElement("div");
    sku.className = "admin-order-sku-cell";
    const title = document.createElement("strong");
    title.textContent = item.skuName || "-";
    const small = document.createElement("small");
    small.textContent = item.skuId || "-";
    sku.append(title, small);
    row.appendChild(sku);
    row.appendChild(textCell(item.skuCode || "-"));
    row.appendChild(textCell(formatMoney(item.salePriceYuan)));
    row.appendChild(textCell(item.quantity ?? "-"));
    row.appendChild(textCell(formatMoney(item.lineAmountYuan)));
    row.appendChild(textCell(item.hotSku ? "HOT" : "NORMAL"));
    return row;
  }

  function textCell(value) {
    const cell = document.createElement("span");
    cell.className = "admin-order-cell";
    cell.textContent = String(value ?? "-");
    return cell;
  }

  function statusBadge(status) {
    const badge = document.createElement("span");
    badge.className = `admin-order-status-badge is-${String(status || "").toLowerCase().replace(/_/g, "-")}`;
    badge.textContent = statusText(status);
    return badge;
  }

  function sourceBadge(source) {
    const text = String(source || "-");
    const badge = document.createElement("span");
    badge.className = `admin-order-source-badge is-${text.toLowerCase()}`;
    badge.textContent = text;
    return badge;
  }

  function statusText(status) {
    const value = String(status || "");
    return statusLabels[value] || value || "-";
  }

  function button(text, className, onClick) {
    const node = document.createElement("button");
    node.className = `${className} admin-spring-button`;
    node.type = "button";
    node.textContent = text;
    node.addEventListener("click", onClick);
    return node;
  }

  function emptyNode(message) {
    const node = document.createElement("div");
    node.className = "admin-product-detail-empty admin-order-empty";
    node.textContent = message;
    return node;
  }

  function setText(node, value) {
    if (node) {
      node.textContent = value;
    }
  }

  function formatMoney(value) {
    const number = Number(value);
    return Number.isFinite(number) ? `¥${number.toFixed(2)}` : "¥0.00";
  }

  function formatDate(value) {
    if (!value) {
      return "-";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return date.toLocaleString("zh-CN", { hour12: false });
  }

  root.AdminOrdersModule = { mount };
})(window);
