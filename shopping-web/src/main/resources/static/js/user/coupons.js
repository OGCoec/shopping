(function () {
  const API_BASE = "/shopping/user/api/coupons";
  const PAGE_BASE = "/shopping/user/coupons";
  const PAGE_SIZE = 20;
  const HIGHLIGHT_START = "[[HL]]";
  const HIGHLIGHT_END = "[[/HL]]";
  const authClient = window.ShoppingAuthClient;

  const statusEl = document.getElementById("coupon-page-status");
  const availableView = document.getElementById("coupon-available-view");
  const availableForm = document.getElementById("coupon-available-form");
  const availableName = document.getElementById("coupon-available-name");
  const availableClear = document.getElementById("coupon-available-clear");
  const availableSummary = document.getElementById("coupon-available-summary");
  const availableList = document.getElementById("coupon-available-list");
  const availablePrev = document.getElementById("coupon-available-prev");
  const availableNext = document.getElementById("coupon-available-next");
  const availablePage = document.getElementById("coupon-available-page");
  const templateDetailView = document.getElementById("coupon-template-detail-view");

  const mineView = document.getElementById("coupon-mine-view");
  const mineForm = document.getElementById("coupon-mine-form");
  const mineStatus = document.getElementById("coupon-mine-status");
  const mineClear = document.getElementById("coupon-mine-clear");
  const mineSummary = document.getElementById("coupon-mine-summary");
  const mineList = document.getElementById("coupon-mine-list");
  const minePrev = document.getElementById("coupon-mine-prev");
  const mineNext = document.getElementById("coupon-mine-next");
  const minePage = document.getElementById("coupon-mine-page");
  const mineDetailView = document.getElementById("coupon-mine-detail-view");

  const state = {
    availablePage: 1,
    availablePageSize: PAGE_SIZE,
    availableTotal: 0,
    availableName: "",
    minePage: 1,
    minePageSize: PAGE_SIZE,
    mineTotal: 0,
    mineStatus: ""
  };

  function setStatus(message, type = "") {
    if (!statusEl) {
      return;
    }
    statusEl.textContent = message || "";
    statusEl.classList.toggle("is-error", type === "error");
    statusEl.classList.toggle("is-ok", type === "ok");
  }

  function show(view) {
    [availableView, templateDetailView, mineView, mineDetailView].forEach((node) => {
      if (node) {
        node.hidden = node !== view;
      }
    });
    document.querySelectorAll("[data-coupon-tab]").forEach((tab) => {
      const target = tab.dataset.couponTab;
      tab.classList.toggle("is-active", view === mineView || view === mineDetailView ? target === "mine" : target === "available");
    });
  }

  async function fetchJson(path, params = null, options = {}) {
    if (!authClient?.fetchWithAuth) {
      window.location.assign("/shopping/user/log-in");
      throw new Error("Authentication client is unavailable.");
    }
    const url = new URL(path, window.location.origin);
    Object.entries(params || {}).forEach(([key, value]) => {
      if (value !== null && value !== undefined && String(value).trim() !== "") {
        url.searchParams.set(key, String(value));
      }
    });
    const response = await authClient.fetchWithAuth(url, {
      method: options.method || "GET",
      credentials: "same-origin",
      headers: {
        Accept: "application/json"
      }
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      const message = payload?.message || payload?.error || `HTTP ${response.status}`;
      const error = new Error(message);
      error.status = response.status;
      error.payload = payload;
      throw error;
    }
    return payload;
  }

  function route() {
    const path = String(window.location.pathname || "").replace(/\/+$/, "");
    if (path === PAGE_BASE || path === "") {
      show(availableView);
      return loadAvailable();
    }
    if (path === `${PAGE_BASE}/mine`) {
      show(mineView);
      return loadMine();
    }
    if (path.startsWith(`${PAGE_BASE}/mine/`)) {
      const id = decodeURIComponent(path.slice(`${PAGE_BASE}/mine/`.length));
      show(mineDetailView);
      return loadMineDetail(id);
    }
    if (path.startsWith(`${PAGE_BASE}/`)) {
      const id = decodeURIComponent(path.slice(`${PAGE_BASE}/`.length));
      show(templateDetailView);
      return loadTemplateDetail(id);
    }
    show(availableView);
    return loadAvailable();
  }

  async function loadAvailable() {
    setStatus("正在加载可领取优惠券。");
    try {
      const payload = await fetchJson(API_BASE, {
        page: state.availablePage,
        pageSize: state.availablePageSize,
        name: state.availableName
      });
      state.availableTotal = Number(payload.total || 0);
      state.availablePage = Number(payload.page || state.availablePage || 1);
      state.availablePageSize = Number(payload.pageSize || state.availablePageSize || PAGE_SIZE);
      renderAvailable(payload.records || []);
      setStatus("", "ok");
    } catch (error) {
      state.availableTotal = 0;
      renderAvailable([]);
      setStatus(error.message || "优惠券加载失败。", "error");
    }
  }

  function renderAvailable(records) {
    const totalPages = Math.max(1, Math.ceil(state.availableTotal / state.availablePageSize));
    availableSummary.textContent = `共 ${state.availableTotal} 张可领取优惠券`;
    availablePage.textContent = `${state.availablePage} / ${totalPages}`;
    availablePrev.disabled = state.availablePage <= 1;
    availableNext.disabled = state.availablePage >= totalPages;
    availableList.replaceChildren();
    if (!records.length) {
      availableList.appendChild(emptyNode("暂无可领取优惠券"));
      return;
    }
    records.forEach((coupon) => availableList.appendChild(couponCard(coupon, () => {
      window.location.assign(`${PAGE_BASE}/${encodeURIComponent(coupon.couponTemplateId)}`);
    })));
  }

  async function loadTemplateDetail(id) {
    setStatus("正在加载优惠券详情。");
    templateDetailView.replaceChildren();
    try {
      const coupon = await fetchJson(`${API_BASE}/${encodeURIComponent(id)}`);
      renderTemplateDetail(coupon);
      setStatus("", "ok");
    } catch (error) {
      templateDetailView.appendChild(emptyNode(error.status === 404 ? "优惠券不存在。" : error.message || "优惠券详情加载失败。"));
      setStatus(error.message || "优惠券详情加载失败。", "error");
    }
  }

  function renderTemplateDetail(coupon) {
    templateDetailView.replaceChildren();
    const content = document.createElement("div");
    content.className = "coupon-detail-content";
    content.appendChild(detailToolbar(coupon.name || "-", `ID ${coupon.couponTemplateId || "-"}`, [
      linkButton("返回列表", PAGE_BASE),
      linkButton("我的券", `${PAGE_BASE}/mine`),
      claimButton(coupon)
    ]));
    content.appendChild(detailGrid([
      ["编码", coupon.couponCode || "-"],
      ["优惠", discountText(coupon)],
      ["状态", coupon.claimed ? `已领取 / ${coupon.userCouponStatus || "-"}` : "未领取"],
      ["库存", `${coupon.remainingQuantity ?? "-"} / ${coupon.totalQuantity ?? "-"}`],
      ["每人限制", coupon.perUserLimit ?? "-"],
      ["适用范围", `${coupon.scopeType || "-"} ${formatTargetIds(coupon.targetIds)}`],
      ["领取时间", `${formatDate(coupon.receiveStartAt)} - ${formatDate(coupon.receiveEndAt)}`],
      ["有效期", `${formatDate(coupon.validStartAt)} - ${formatDate(coupon.validEndAt)}`],
      ["是否可领取", coupon.canClaim ? "可以领取" : "不可领取"]
    ]));
    templateDetailView.appendChild(content);
  }

  function claimButton(coupon) {
    const button = document.createElement("button");
    button.className = "coupon-primary-button";
    button.type = "button";
    button.textContent = coupon.claimed ? "已领取" : "立即领取";
    button.disabled = !coupon.canClaim;
    button.addEventListener("click", async () => {
      button.disabled = true;
      setStatus("正在领取优惠券。");
      try {
        const payload = await fetchJson(`${API_BASE}/${encodeURIComponent(coupon.couponTemplateId)}/claim`, null, { method: "POST" });
        if (!payload.success) {
          setStatus(payload.message || payload.code || "领取失败。", "error");
          button.disabled = false;
          return;
        }
        setStatus("领取成功。", "ok");
        await loadTemplateDetail(coupon.couponTemplateId);
      } catch (error) {
        const message = error.payload?.message || error.payload?.code || error.message || "领取失败。";
        setStatus(message, "error");
        button.disabled = false;
      }
    });
    return button;
  }

  async function loadMine() {
    setStatus("正在加载我的优惠券。");
    try {
      const payload = await fetchJson(`${API_BASE}/mine`, {
        page: state.minePage,
        pageSize: state.minePageSize,
        status: state.mineStatus
      });
      state.mineTotal = Number(payload.total || 0);
      state.minePage = Number(payload.page || state.minePage || 1);
      state.minePageSize = Number(payload.pageSize || state.minePageSize || PAGE_SIZE);
      renderMine(payload.records || []);
      setStatus("", "ok");
    } catch (error) {
      state.mineTotal = 0;
      renderMine([]);
      setStatus(error.message || "我的优惠券加载失败。", "error");
    }
  }

  function renderMine(records) {
    const totalPages = Math.max(1, Math.ceil(state.mineTotal / state.minePageSize));
    mineSummary.textContent = `共 ${state.mineTotal} 张已领取优惠券`;
    minePage.textContent = `${state.minePage} / ${totalPages}`;
    minePrev.disabled = state.minePage <= 1;
    mineNext.disabled = state.minePage >= totalPages;
    mineList.replaceChildren();
    if (!records.length) {
      mineList.appendChild(emptyNode("暂无优惠券"));
      return;
    }
    records.forEach((coupon) => mineList.appendChild(mineCard(coupon, () => {
      window.location.assign(`${PAGE_BASE}/mine/${encodeURIComponent(coupon.userCouponId)}`);
    })));
  }

  async function loadMineDetail(id) {
    setStatus("正在加载我的优惠券详情。");
    mineDetailView.replaceChildren();
    try {
      const coupon = await fetchJson(`${API_BASE}/mine/${encodeURIComponent(id)}`);
      renderMineDetail(coupon);
      setStatus("", "ok");
    } catch (error) {
      mineDetailView.appendChild(emptyNode(error.status === 404 ? "优惠券不存在。" : error.message || "我的优惠券详情加载失败。"));
      setStatus(error.message || "我的优惠券详情加载失败。", "error");
    }
  }

  function renderMineDetail(coupon) {
    mineDetailView.replaceChildren();
    const content = document.createElement("div");
    content.className = "coupon-detail-content";
    content.appendChild(detailToolbar(coupon.name || "-", `用户券 ID ${coupon.userCouponId || "-"}`, [
      linkButton("返回我的券", `${PAGE_BASE}/mine`),
      linkButton("可领取券", PAGE_BASE)
    ]));
    content.appendChild(detailGrid([
      ["模板 ID", coupon.couponTemplateId || "-"],
      ["编码", coupon.couponCode || "-"],
      ["优惠", discountText(coupon)],
      ["用户券状态", coupon.status || "-"],
      ["模板状态", coupon.templateStatus || "-"],
      ["领取时间", formatDate(coupon.receivedAt)],
      ["有效期", `${formatDate(coupon.validStartAt)} - ${formatDate(coupon.validEndAt)}`],
      ["锁定订单", `${coupon.lockedOrderNo || "-"} / ${formatDate(coupon.lockedAt)}`],
      ["使用订单", `${coupon.usedOrderNo || "-"} / ${formatDate(coupon.usedAt)}`],
      ["适用范围", `${coupon.scopeType || "-"} ${formatTargetIds(coupon.targetIds)}`]
    ]));
    mineDetailView.appendChild(content);
  }

  function couponCard(coupon, onClick) {
    const card = baseCard(coupon.name, coupon.couponTemplateId, onClick);
    card.appendChild(badge(coupon.claimed ? `已领取 ${coupon.userCouponStatus || ""}` : coupon.canClaim ? "可领取" : "不可领取", coupon.canClaim && !coupon.claimed ? "" : "is-muted"));
    card.appendChild(metaGrid([
      ["优惠", discountText(coupon)],
      ["库存", `${coupon.remainingQuantity ?? "-"} / ${coupon.totalQuantity ?? "-"}`],
      ["领取", `${formatDate(coupon.receiveStartAt)} - ${formatDate(coupon.receiveEndAt)}`],
      ["有效期", `${formatDate(coupon.validStartAt)} - ${formatDate(coupon.validEndAt)}`]
    ]));
    return card;
  }

  function mineCard(coupon, onClick) {
    const card = baseCard(coupon.name, coupon.userCouponId, onClick);
    card.appendChild(badge(coupon.status || "-", coupon.status === "UNUSED" ? "" : coupon.status === "USED" ? "is-muted" : "is-danger"));
    card.appendChild(metaGrid([
      ["优惠", discountText(coupon)],
      ["领取时间", formatDate(coupon.receivedAt)],
      ["有效期", `${formatDate(coupon.validStartAt)} - ${formatDate(coupon.validEndAt)}`],
      ["使用时间", formatDate(coupon.usedAt)]
    ]));
    return card;
  }

  function baseCard(title, id, onClick) {
    const card = document.createElement("article");
    card.className = "coupon-card is-clickable";
    card.tabIndex = 0;
    card.setAttribute("role", "button");
    card.addEventListener("click", onClick);
    card.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        onClick();
      }
    });
    const heading = document.createElement("div");
    const h2 = document.createElement("h2");
    renderHighlightedText(h2, title || "-");
    const small = document.createElement("small");
    small.textContent = `ID ${id || "-"}`;
    heading.append(h2, small);
    card.appendChild(heading);
    return card;
  }

  function detailToolbar(title, subtitle, actions) {
    const toolbar = document.createElement("div");
    toolbar.className = "coupon-detail-toolbar";
    const heading = document.createElement("div");
    heading.className = "coupon-detail-title";
    const h2 = document.createElement("h2");
    h2.textContent = title;
    const small = document.createElement("small");
    small.textContent = subtitle;
    heading.append(h2, small);
    const actionWrap = document.createElement("div");
    actionWrap.className = "coupon-detail-actions";
    actions.forEach((action) => actionWrap.appendChild(action));
    toolbar.append(heading, actionWrap);
    return toolbar;
  }

  function linkButton(label, href) {
    const link = document.createElement("a");
    link.className = "coupon-ghost-button";
    link.href = href;
    link.textContent = label;
    return link;
  }

  function metaGrid(items) {
    const grid = document.createElement("div");
    grid.className = "coupon-meta-grid";
    appendGridItems(grid, items);
    return grid;
  }

  function detailGrid(items) {
    const grid = document.createElement("div");
    grid.className = "coupon-detail-grid";
    appendGridItems(grid, items);
    return grid;
  }

  function appendGridItems(grid, items) {
    items.forEach(([label, value]) => {
      const item = document.createElement("div");
      const span = document.createElement("span");
      span.textContent = label;
      const strong = document.createElement("strong");
      strong.textContent = String(value ?? "-");
      item.append(span, strong);
      grid.appendChild(item);
    });
  }

  function badge(text, extraClass = "") {
    const node = document.createElement("span");
    node.className = `coupon-badge ${extraClass}`.trim();
    node.textContent = text || "-";
    return node;
  }

  function emptyNode(text) {
    const node = document.createElement("div");
    node.className = "coupon-empty";
    node.textContent = text;
    return node;
  }

  function discountText(coupon) {
    if (coupon.discountType === "PERCENT") {
      return `${coupon.discountRate ?? "-"} 折扣${coupon.maxDiscountAmountYuan ? `，最高减 ${coupon.maxDiscountAmountYuan}` : ""}`;
    }
    return `满 ${coupon.thresholdAmountYuan ?? 0} 减 ${coupon.discountAmountYuan ?? 0}`;
  }

  function formatTargetIds(values) {
    const ids = Array.isArray(values) ? values.filter(Boolean) : [];
    return ids.length ? `/ ${ids.join(", ")}` : "";
  }

  function formatDate(value) {
    if (!value) {
      return "-";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return new Intl.DateTimeFormat("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    }).format(date);
  }

  function renderHighlightedText(target, value) {
    const source = String(value || "-");
    let cursor = 0;
    while (cursor < source.length) {
      const start = source.indexOf(HIGHLIGHT_START, cursor);
      if (start < 0) {
        target.appendChild(document.createTextNode(source.slice(cursor)));
        return;
      }
      if (start > cursor) {
        target.appendChild(document.createTextNode(source.slice(cursor, start)));
      }
      const contentStart = start + HIGHLIGHT_START.length;
      const end = source.indexOf(HIGHLIGHT_END, contentStart);
      if (end < 0) {
        target.appendChild(document.createTextNode(source.slice(start)));
        return;
      }
      const mark = document.createElement("span");
      mark.className = "coupon-highlight";
      mark.textContent = source.slice(contentStart, end);
      target.appendChild(mark);
      cursor = end + HIGHLIGHT_END.length;
    }
  }

  availableForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    state.availableName = availableName.value.trim();
    state.availablePage = 1;
    loadAvailable();
  });

  availableClear?.addEventListener("click", () => {
    availableName.value = "";
    state.availableName = "";
    state.availablePage = 1;
    loadAvailable();
  });

  availablePrev?.addEventListener("click", () => {
    if (state.availablePage > 1) {
      state.availablePage -= 1;
      loadAvailable();
    }
  });

  availableNext?.addEventListener("click", () => {
    if (state.availablePage * state.availablePageSize < state.availableTotal) {
      state.availablePage += 1;
      loadAvailable();
    }
  });

  mineForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    state.mineStatus = mineStatus.value;
    state.minePage = 1;
    loadMine();
  });

  mineClear?.addEventListener("click", () => {
    mineStatus.value = "";
    state.mineStatus = "";
    state.minePage = 1;
    loadMine();
  });

  minePrev?.addEventListener("click", () => {
    if (state.minePage > 1) {
      state.minePage -= 1;
      loadMine();
    }
  });

  mineNext?.addEventListener("click", () => {
    if (state.minePage * state.minePageSize < state.mineTotal) {
      state.minePage += 1;
      loadMine();
    }
  });

  route().catch((error) => {
    setStatus(error.message || "优惠券页面加载失败。", "error");
  });
})();
