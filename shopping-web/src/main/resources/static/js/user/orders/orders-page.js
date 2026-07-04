(function () {
  const PAGE_BASE = "/shopping/user/orders";
  const PAGE_SIZE = 12;
  const orderApi = window.ShoppingOrderApi;
  let paymentCountdownTimerId = 0;

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
    STOCK_CONFIRMING: "库存确认中",
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
    clearPaymentCountdown();
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
    clearPaymentCountdown();
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
      actions.appendChild(paymentButton("现金支付 / 模拟支付", "SIMULATED", "order-ghost-button"));
      if (pointsPaymentAvailable(order)) {
        actions.appendChild(paymentButton(`积分支付：需要 ${formatPoints(requiredPoints(order))}`, "POINTS", "order-primary-button"));
      }
      const cancelButton = document.createElement("button");
      cancelButton.className = "order-danger-button";
      cancelButton.type = "button";
      cancelButton.dataset.action = "cancel-order";
      cancelButton.dataset.orderNo = String(order?.orderNo || "");
      cancelButton.textContent = "取消订单";
      actions.appendChild(cancelButton);
    } else if (order?.status === "PAID") {
      actions.appendChild(cardSecretsButton(order?.orderNo));
    }
    toolbar.append(titleWrap, actions);

    const detailNodes = [
      toolbar,
      detailStatusPanel(order),
      detailGrid([
        ["订单状态", statusLabel(order?.status)],
        ["支付方式", paymentTypeLabel(order?.paymentType)],
        ["应付金额", paymentAmountText(order)],
        ["应付积分", requiredPoints(order) > 0 ? formatPoints(requiredPoints(order)) : "-"],
        ["消耗积分", Number(order?.usedPoints || 0) > 0 ? formatPoints(order?.usedPoints) : "-"],
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
    ];
    if (order?.status === "PAID") {
      detailNodes.push(cardSecretsSection(order?.orderNo));
    }
    content.append(...detailNodes);
    detailView.appendChild(content);
    startPaymentCountdown(order);
    document.title = `订单 ${order?.orderNo || ""} - Shopping`;
  }

  function detailStatusPanel(order) {
    const panel = document.createElement("section");
    panel.className = "order-detail-status-panel";
    panel.dataset.status = String(order?.status || "");
    panel.appendChild(statusBadge(order?.status));
    const copy = document.createElement("p");
    if (order?.status === "STOCK_CONFIRMING") {
      copy.textContent = "订单库存正在确认，确认成功后可继续支付。";
    } else if (order?.status === "CLOSING") {
      copy.textContent = `订单正在关闭中，系统等待支付结果确认到 ${formatDate(order?.closingDeadlineAt)}。`;
    } else if (order?.status === "PENDING_PAYMENT") {
      copy.textContent = `请在 ${formatDate(order?.expireAt)} 前完成支付。`;
    } else if (order?.status === "PAID") {
      if (order?.paymentType === "POINTS") {
        copy.textContent = `订单已使用 ${formatPoints(order?.usedPoints)}支付，支付时间 ${formatDate(order?.paidAt)}。`;
      } else {
        copy.textContent = `订单已支付，支付时间 ${formatDate(order?.paidAt)}。`;
      }
    } else if (order?.status === "CANCELLED") {
      copy.textContent = `订单已取消，取消时间 ${formatDate(order?.cancelledAt)}。`;
    } else if (order?.status === "CLOSED") {
      copy.textContent = `订单已关闭，关闭时间 ${formatDate(order?.closedAt)}。`;
    } else {
      copy.textContent = "订单状态已更新。";
    }
    panel.appendChild(copy);
    if (order?.status === "PENDING_PAYMENT") {
      panel.appendChild(paymentCountdownNode(order));
    }
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

  function cardSecretsSection(orderNo) {
    const section = document.createElement("section");
    section.className = "order-card-secrets";
    section.dataset.role = "order-card-secrets";
    section.dataset.orderNo = String(orderNo || "");
    section.hidden = true;
    return section;
  }

  async function loadCardSecrets(orderNo, button) {
    if (!orderNo) {
      return;
    }
    const section = detailView.querySelector("[data-role='order-card-secrets']");
    if (!section) {
      return;
    }
    button.disabled = true;
    button.textContent = "正在加载卡密";
    setStatus("正在加载卡密");
    renderCardSecretsLoading(section);
    try {
      const payload = await orderApi.cardSecrets(orderNo);
      renderCardSecrets(section, payload);
      button.textContent = "刷新卡密";
      setStatus("", "ok");
    } catch (error) {
      const message = error.message || "卡密加载失败";
      renderCardSecretsError(section, message);
      button.textContent = "重新加载卡密";
      setStatus(message, "error");
    } finally {
      button.disabled = false;
    }
  }

  function renderCardSecretsLoading(section) {
    section.hidden = false;
    section.replaceChildren(cardSecretsHeading(null), emptyNode("正在加载卡密"));
  }

  function renderCardSecretsError(section, message) {
    section.hidden = false;
    section.replaceChildren(cardSecretsHeading(null), emptyNode(message || "卡密加载失败"));
  }

  function renderCardSecrets(section, payload) {
    section.hidden = false;
    const list = document.createElement("div");
    list.className = "order-card-secret-list";
    const items = Array.isArray(payload?.items) ? payload.items : [];
    let renderedSecrets = 0;
    items.forEach((item) => {
      const group = cardSecretGroup(item);
      if (group) {
        renderedSecrets += Number(group.dataset.secretCount || 0);
        list.appendChild(group);
      }
    });
    const nodes = [cardSecretsHeading(payload)];
    if (renderedSecrets > 0) {
      nodes.push(list);
    }
    if (payload?.deliveryStatus === "PENDING" || renderedSecrets <= 0) {
      nodes.push(emptyNode("卡密正在交付中，请稍后刷新"));
    }
    section.replaceChildren(...nodes);
  }

  function cardSecretsHeading(payload) {
    const header = document.createElement("div");
    header.className = "order-card-secrets-heading";
    const title = document.createElement("h2");
    title.textContent = "卡密信息";
    header.appendChild(title);
    if (payload) {
      const summary = document.createElement("p");
      summary.textContent = `已交付 ${Number(payload?.deliveredCount || 0)} / 应交付 ${Number(payload?.requiredCount || 0)}`;
      header.appendChild(summary);
    }
    return header;
  }

  function cardSecretGroup(item) {
    const secrets = Array.isArray(item?.secrets) ? item.secrets : [];
    if (!secrets.length) {
      return null;
    }
    const group = document.createElement("article");
    group.className = "order-card-secret-group";
    group.dataset.skuId = String(item?.skuId || "");
    group.dataset.secretCount = String(secrets.length);

    const header = document.createElement("div");
    header.className = "order-card-secret-group-heading";
    const title = document.createElement("strong");
    title.textContent = String(item?.skuName || "订单商品");
    const count = document.createElement("span");
    count.textContent = `${Number(item?.deliveredCount || secrets.length)} / ${Number(item?.quantity || secrets.length)}`;
    header.append(title, count);

    const rows = document.createElement("div");
    rows.className = "order-card-secret-rows";
    secrets.forEach((secret) => rows.appendChild(cardSecretRow(secret)));
    group.append(header, rows);
    return group;
  }

  function cardSecretRow(secret) {
    const row = document.createElement("div");
    row.className = "order-card-secret-row";
    row.dataset.cardSecretId = String(secret?.cardSecretId || "");
    const value = document.createElement("code");
    value.textContent = String(secret?.secret || "");
    const deliveredAt = document.createElement("span");
    deliveredAt.textContent = formatDate(secret?.deliveredAt);
    row.append(value, deliveredAt);
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

  async function payCurrentOrder(orderNo, paymentType, button) {
    if (!orderNo || !paymentType) {
      return;
    }
    button.disabled = true;
    setStatus(paymentType === "POINTS" ? "正在使用积分支付" : "正在模拟支付");
    try {
      await orderApi.pay(orderNo, { paymentType });
      await loadDetail(orderNo);
      setStatus(paymentType === "POINTS" ? "积分支付成功" : "支付成功", "ok");
    } catch (error) {
      button.disabled = false;
      setStatus(error.message || "支付失败", "error");
    }
  }

  function paymentCountdownNode(order) {
    const node = document.createElement("div");
    node.className = "order-payment-countdown";
    const label = document.createElement("span");
    label.textContent = "剩余支付时间";
    const value = document.createElement("strong");
    value.dataset.role = "order-payment-countdown";
    value.textContent = countdownText(order?.expireAt);
    node.append(label, value);
    return node;
  }

  function startPaymentCountdown(order) {
    clearPaymentCountdown();
    if (order?.status !== "PENDING_PAYMENT") {
      return;
    }
    const deadline = paymentDeadline(order?.expireAt);
    const countdown = detailView.querySelector("[data-role='order-payment-countdown']");
    if (!deadline || !countdown) {
      return;
    }
    const tick = () => {
      const remainingMs = deadline.getTime() - Date.now();
      countdown.textContent = formatCountdownDuration(Math.max(0, remainingMs));
      if (remainingMs > 0) {
        return true;
      }
      clearPaymentCountdown();
      disablePaymentButtons();
      setStatus("支付倒计时已结束，请刷新或查看订单状态", "error");
      return false;
    };
    if (tick()) {
      paymentCountdownTimerId = window.setInterval(tick, 1000);
    }
  }

  function clearPaymentCountdown() {
    if (paymentCountdownTimerId) {
      window.clearInterval(paymentCountdownTimerId);
      paymentCountdownTimerId = 0;
    }
  }

  function disablePaymentButtons() {
    detailView.querySelectorAll("[data-action='pay-order']").forEach((button) => {
      button.disabled = true;
    });
  }

  function paymentDeadline(value) {
    if (!value) {
      return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  function countdownText(value) {
    const deadline = paymentDeadline(value);
    if (!deadline) {
      return "--:--";
    }
    return formatCountdownDuration(Math.max(0, deadline.getTime() - Date.now()));
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

  function paymentButton(text, paymentType, className) {
    const button = document.createElement("button");
    button.className = className;
    button.type = "button";
    button.dataset.action = "pay-order";
    button.dataset.paymentType = paymentType;
    button.textContent = text;
    return button;
  }

  function cardSecretsButton(orderNo) {
    const button = document.createElement("button");
    button.className = "order-primary-button";
    button.type = "button";
    button.dataset.action = "show-card-secrets";
    button.dataset.orderNo = String(orderNo || "");
    button.textContent = "查看卡密";
    return button;
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

  function formatPoints(value) {
    const number = Number(value || 0);
    return `${Number.isFinite(number) ? Math.max(0, Math.trunc(number)) : 0} 积分`;
  }

  function paymentTypeLabel(paymentType) {
    if (paymentType === "POINTS") {
      return "积分支付";
    }
    if (paymentType === "SIMULATED") {
      return "模拟支付";
    }
    return "未支付";
  }

  function paymentAmountText(order) {
    if (order?.status === "PAID" && order?.paymentType === "POINTS") {
      return `已使用 ${formatPoints(order?.usedPoints)}支付`;
    }
    return formatMoney(order?.payAmountYuan);
  }

  function pointsPaymentAvailable(order) {
    const items = Array.isArray(order?.items) ? order.items : [];
    return items.length > 0
      && requiredPoints(order) > 0
      && items.every((item) => item?.pointExchangeEnabled === true);
  }

  function requiredPoints(order) {
    const direct = Number(order?.requiredPoints || 0);
    if (Number.isFinite(direct) && direct > 0) {
      return direct;
    }
    const items = Array.isArray(order?.items) ? order.items : [];
    return items.reduce((sum, item) => sum + Math.max(0, Number(item?.linePoints || 0)), 0);
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

  function formatCountdownDuration(durationMs) {
    const seconds = Math.max(0, Math.ceil(durationMs / 1000));
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return `${String(minutes).padStart(2, "0")}:${String(rest).padStart(2, "0")}`;
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
    if (!target) {
      return;
    }
    if (target.dataset.action === "cancel-order") {
      cancelCurrentOrder(target.dataset.orderNo || "", target);
      return;
    }
    if (target.dataset.action === "pay-order") {
      payCurrentOrder(currentOrderNo(), target.dataset.paymentType || "", target);
      return;
    }
    if (target.dataset.action === "show-card-secrets") {
      loadCardSecrets(target.dataset.orderNo || currentOrderNo(), target);
    }
  });

  window.addEventListener("pagehide", clearPaymentCountdown);

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
