(function () {
  const PAGE_BASE = "/shopping/user/orders";
  const PAGE_SIZE = 12;
  const orderApi = window.ShoppingOrderApi;

  const statusEl = document.getElementById("order-page-status");
  const listView = document.getElementById("order-list-view");
  const detailView = document.getElementById("order-detail-view");
  const filterForm = document.getElementById("order-filter-form");
  const statusFilter = document.getElementById("order-status-filter");
  const filterClear = document.getElementById("order-filter-clear");
  const summaryEl = document.getElementById("order-list-summary");
  const listEl = document.getElementById("order-list");
  const prevButton = document.getElementById("order-prev");
  const nextButton = document.getElementById("order-next");
  const pageIndicator = document.getElementById("order-page-indicator");

  function safeImageUrl(value) {
    return window.ShoppingSecurityUrls?.safeImageUrl?.(value, "", {
      allowData: false,
      allowBlob: false,
      allowAnyHttps: true,
      allowLocalHttp: true,
      allowedPathPrefixes: ["/shopping/"]
    }) || "";
  }

  const state = {
    page: 1,
    pageSize: PAGE_SIZE,
    total: 0,
    status: ""
  };

  const statusLabels = {
    PENDING_PAYMENT: "待支付",
    CLOSING: "关闭确认中",
    PAID: "已支付",
    CANCELLED: "已取消",
    CLOSED: "已关闭"
  };

  function setStatus(message, type = "") {
    if (!statusEl) {
      return;
    }
    statusEl.textContent = message || "";
    statusEl.hidden = !message;
    statusEl.classList.toggle("is-error", type === "error");
    statusEl.classList.toggle("is-ok", type === "ok");
  }

  function show(view) {
    [listView, detailView].forEach((node) => {
      if (node) {
        node.hidden = node !== view;
      }
    });
    document.querySelectorAll("[data-order-nav]").forEach((node) => {
      node.classList.toggle("is-active", view === listView);
    });
  }

  function currentOrderNo() {
    const path = String(window.location.pathname || "").replace(/\/+$/, "");
    if (!path.startsWith(`${PAGE_BASE}/`)) {
      return "";
    }
    try {
      return decodeURIComponent(path.slice(`${PAGE_BASE}/`.length)).trim();
    } catch (_) {
      return "";
    }
  }

  async function route() {
    const orderNo = currentOrderNo();
    if (orderNo) {
      show(detailView);
      await loadDetail(orderNo);
      return;
    }
    show(listView);
    await loadOrders();
  }

  async function loadOrders() {
    setStatus("正在加载订单");
    try {
      const payload = await orderApi.page({
        page: state.page,
        pageSize: state.pageSize,
        status: state.status
      });
      state.total = Number(payload?.total || 0);
      state.page = Number(payload?.page || state.page || 1);
      state.pageSize = Number(payload?.pageSize || state.pageSize || PAGE_SIZE);
      renderOrders(Array.isArray(payload?.records) ? payload.records : []);
      setStatus("", "ok");
    } catch (error) {
      state.total = 0;
      renderOrders([]);
      setStatus(error.message || "订单加载失败", "error");
    }
  }

  function renderOrders(records) {
    const totalPages = Math.max(1, Math.ceil(state.total / state.pageSize));
    summaryEl.textContent = `共 ${state.total} 个订单`;
    pageIndicator.textContent = `${Math.min(state.page, totalPages)} / ${totalPages}`;
    prevButton.disabled = state.page <= 1;
    nextButton.disabled = state.page >= totalPages;
    listEl.replaceChildren();
    if (!records.length) {
      listEl.appendChild(emptyNode("暂无订单"));
      return;
    }
    records.forEach((order) => listEl.appendChild(orderCard(order)));
  }

  function orderCard(order) {
    const orderNo = String(order?.orderNo || "");
    const card = document.createElement("a");
    card.className = "order-card";
    card.href = `${PAGE_BASE}/${encodeURIComponent(orderNo)}`;
    card.dataset.orderNo = orderNo;
    card.dataset.status = String(order?.status || "");

    const media = document.createElement("div");
    media.className = "order-card-media";
    const imageUrl = safeImageUrl(order?.firstSkuImageUrl);
    if (imageUrl) {
      const image = document.createElement("img");
      image.src = imageUrl;
      image.alt = String(order?.firstSkuName || "订单商品");
      image.loading = "lazy";
      media.appendChild(image);
    } else {
      media.textContent = "NO IMAGE";
    }

    const body = document.createElement("div");
    body.className = "order-card-body";
    const title = document.createElement("h2");
    title.textContent = String(order?.firstSkuName || "订单商品");
    const meta = document.createElement("p");
    meta.textContent = `订单号 ${orderNo}`;
    const detail = document.createElement("p");
    detail.textContent = `${Number(order?.itemCount || 0)} 件商品 · ${formatDate(order?.createdAt)}`;
    body.append(title, meta, detail);

    const aside = document.createElement("div");
    aside.className = "order-card-aside";
    aside.append(statusBadge(order?.status), amountNode(order?.payAmountYuan));

    card.append(media, body, aside);
    return card;
  }

  async function loadDetail(orderNo) {
    setStatus("正在加载订单详情");
    detailView.replaceChildren();
    try {
      const order = await orderApi.detail(orderNo);
      renderDetail(order);
      setStatus("", "ok");
    } catch (error) {
      detailView.appendChild(emptyNode(error.message || "订单详情加载失败"));
      setStatus(error.message || "订单详情加载失败", "error");
    }
  }

  function renderDetail(order) {
    detailView.replaceChildren();
    const content = document.createElement("div");
    content.className = "order-detail";

    const toolbar = document.createElement("div");
    toolbar.className = "order-detail-toolbar";
    const titleWrap = document.createElement("div");
    const title = document.createElement("h1");
    title.textContent = "订单详情";
    const subtitle = document.createElement("p");
    subtitle.textContent = `订单号 ${order?.orderNo || "-"}`;
    titleWrap.append(title, subtitle);
    const actions = document.createElement("div");
    actions.className = "order-detail-actions";
    actions.append(linkButton("返回订单", PAGE_BASE));
    if (order?.status === "PENDING_PAYMENT") {
      const cancelButton = document.createElement("button");
      cancelButton.className = "order-danger-button";
      cancelButton.type = "button";
      cancelButton.dataset.action = "cancel-order";
      cancelButton.dataset.orderNo = String(order?.orderNo || "");
      cancelButton.textContent = "取消订单";
      actions.appendChild(cancelButton);
    }
    toolbar.append(titleWrap, actions);

    content.append(
      toolbar,
      detailStatusPanel(order),
      detailGrid([
        ["订单状态", statusLabel(order?.status)],
        ["应付金额", formatMoney(order?.payAmountYuan)],
        ["商品金额", formatMoney(order?.totalAmountYuan)],
        ["优惠金额", formatMoney(order?.discountAmountYuan)],
        ["优惠券", order?.userCouponId || "-"],
        ["支付截止", formatDate(order?.expireAt)],
        ["关闭开始", formatDate(order?.closingAt)],
        ["关闭截止", formatDate(order?.closingDeadlineAt)],
        ["支付时间", formatDate(order?.paidAt)],
        ["取消时间", formatDate(order?.cancelledAt)],
        ["关闭时间", formatDate(order?.closedAt)]
      ]),
      itemSection(order?.items)
    );
    detailView.appendChild(content);
    document.title = `订单 ${order?.orderNo || ""} - Shopping`;
  }

  function detailStatusPanel(order) {
    const panel = document.createElement("section");
    panel.className = "order-detail-status-panel";
    panel.dataset.status = String(order?.status || "");
    panel.appendChild(statusBadge(order?.status));
    const copy = document.createElement("p");
    if (order?.status === "CLOSING") {
      copy.textContent = `订单正在关闭中，系统等待支付结果确认到 ${formatDate(order?.closingDeadlineAt)}。`;
    } else if (order?.status === "PENDING_PAYMENT") {
      copy.textContent = `请在 ${formatDate(order?.expireAt)} 前完成支付。`;
    } else if (order?.status === "PAID") {
      copy.textContent = `订单已支付，支付时间 ${formatDate(order?.paidAt)}。`;
    } else if (order?.status === "CANCELLED") {
      copy.textContent = `订单已取消，取消时间 ${formatDate(order?.cancelledAt)}。`;
    } else if (order?.status === "CLOSED") {
      copy.textContent = `订单已关闭，关闭时间 ${formatDate(order?.closedAt)}。`;
    } else {
      copy.textContent = "订单状态已更新。";
    }
    panel.appendChild(copy);
    return panel;
  }

  function itemSection(items) {
    const section = document.createElement("section");
    section.className = "order-items";
    const heading = document.createElement("h2");
    heading.textContent = "商品明细";
    section.appendChild(heading);
    const list = document.createElement("div");
    list.className = "order-item-list";
    const records = Array.isArray(items) ? items : [];
    if (!records.length) {
      list.appendChild(emptyNode("暂无商品明细"));
    } else {
      records.forEach((item) => list.appendChild(itemRow(item)));
    }
    section.appendChild(list);
    return section;
  }

  function itemRow(item) {
    const row = document.createElement("article");
    row.className = "order-item";
    row.dataset.skuId = String(item?.skuId || "");
    const media = document.createElement("div");
    media.className = "order-item-media";
    const imageUrl = safeImageUrl(item?.skuImageUrl);
    if (imageUrl) {
      const image = document.createElement("img");
      image.src = imageUrl;
      image.alt = String(item?.skuName || "订单商品");
      image.loading = "lazy";
      media.appendChild(image);
    } else {
      media.textContent = "NO IMAGE";
    }
    const body = document.createElement("div");
    body.className = "order-item-body";
    const title = document.createElement("h3");
    title.textContent = String(item?.skuName || "订单商品");
    const spec = document.createElement("p");
    spec.textContent = formatSpec(item?.specJson);
    body.append(title, spec);
    const price = document.createElement("div");
    price.className = "order-item-price";
    price.textContent = `${formatMoney(item?.salePriceYuan)} × ${Number(item?.quantity || 0)}`;
    const amount = document.createElement("strong");
    amount.textContent = formatMoney(item?.lineAmountYuan);
    row.append(media, body, price, amount);
    return row;
  }

  async function cancelCurrentOrder(orderNo, button) {
    if (!orderNo) {
      return;
    }
    button.disabled = true;
    setStatus("正在取消订单");
    try {
      await orderApi.cancel(orderNo, "USER_CANCEL");
      await loadDetail(orderNo);
      setStatus("订单已取消", "ok");
    } catch (error) {
      button.disabled = false;
      setStatus(error.message || "订单取消失败", "error");
    }
  }

  function detailGrid(entries) {
    const grid = document.createElement("dl");
    grid.className = "order-detail-grid";
    entries.forEach(([label, value]) => {
      const term = document.createElement("dt");
      term.textContent = label;
      const desc = document.createElement("dd");
      desc.textContent = value || "-";
      grid.append(term, desc);
    });
    return grid;
  }

  function statusBadge(status) {
    const badge = document.createElement("span");
    badge.className = "order-status-badge";
    badge.dataset.status = String(status || "");
    badge.textContent = statusLabel(status);
    return badge;
  }

  function statusLabel(status) {
    return statusLabels[String(status || "")] || String(status || "-");
  }

  function amountNode(value) {
    const node = document.createElement("strong");
    node.className = "order-money";
    node.textContent = formatMoney(value);
    return node;
  }

  function linkButton(text, href) {
    const link = document.createElement("a");
    link.className = "order-ghost-button";
    link.href = href;
    link.textContent = text;
    return link;
  }

  function emptyNode(message) {
    const node = document.createElement("div");
    node.className = "order-empty";
    node.textContent = message;
    return node;
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

  function formatSpec(value) {
    if (!value) {
      return "暂无规格参数";
    }
    if (typeof value === "string") {
      try {
        const parsed = JSON.parse(value);
        return formatSpec(parsed);
      } catch (_) {
        return value;
      }
    }
    if (typeof value !== "object" || Array.isArray(value)) {
      return String(value);
    }
    const entries = Object.entries(value);
    return entries.length ? entries.map(([key, item]) => `${key}: ${item}`).join(" / ") : "暂无规格参数";
  }

  filterForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    state.status = statusFilter.value;
    state.page = 1;
    loadOrders();
  });

  filterClear?.addEventListener("click", () => {
    statusFilter.value = "";
    state.status = "";
    state.page = 1;
    loadOrders();
  });

  prevButton?.addEventListener("click", () => {
    if (state.page <= 1) {
      return;
    }
    state.page -= 1;
    loadOrders();
  });

  nextButton?.addEventListener("click", () => {
    if (state.page * state.pageSize >= state.total) {
      return;
    }
    state.page += 1;
    loadOrders();
  });

  detailView?.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target.closest("[data-action]") : null;
    if (!target || target.dataset.action !== "cancel-order") {
      return;
    }
    cancelCurrentOrder(target.dataset.orderNo || "", target);
  });

  async function startPage() {
    const pageGate = window.ShoppingPageAccessGate;
    if (pageGate?.ready) {
      const allowed = await pageGate.ready();
      if (allowed === false) {
        return;
      }
    }
    await route();
  }

  startPage().catch((error) => {
    setStatus(error.message || "订单页面加载失败", "error");
  });
})();
