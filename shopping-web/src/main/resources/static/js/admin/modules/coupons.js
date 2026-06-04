(function (root) {
  const api = root.AdminApi;
  const router = root.AdminRouter;
  const couponApi = root.AdminCouponApi;
  const CONSOLE_COUPONS_PATH = "/shopping/admin/console/coupons";
  const HIGHLIGHT_START = "[[HL]]";
  const HIGHLIGHT_END = "[[/HL]]";

  const state = {
    mounted: false,
    page: 1,
    pageSize: 20,
    total: 0,
    records: [],
    currentId: "",
    detail: null,
    claimsPage: 1,
    claimsPageSize: 20,
    claimsTotal: 0,
    claims: [],
    pageBusy: false,
    claimsBusy: false
  };

  const el = {};

  function $(id) {
    return document.getElementById(id);
  }

  function mount(panel) {
    if (state.mounted) {
      return;
    }
    Object.assign(el, {
      panel,
      card: panel.querySelector(".admin-coupon-card"),
      refresh: $("admin-coupon-refresh"),
      filterForm: $("admin-coupon-filter-form"),
      filterName: $("admin-coupon-filter-name"),
      filterStatus: $("admin-coupon-filter-status"),
      filterStartFrom: $("admin-coupon-filter-start-from"),
      filterEndTo: $("admin-coupon-filter-end-to"),
      pageSize: $("admin-coupon-page-size"),
      total: $("admin-coupon-total"),
      pageLabel: $("admin-coupon-page-label"),
      status: $("admin-coupon-status"),
      list: $("admin-coupon-list"),
      prev: $("admin-coupon-prev"),
      next: $("admin-coupon-next"),
      reset: $("admin-coupon-filter-reset"),
      detailPage: $("admin-coupon-detail-page"),
      detailStatus: $("admin-coupon-detail-status"),
      detailBody: $("admin-coupon-detail-body")
    });
    if (!el.list || !el.detailPage) {
      return;
    }
    state.mounted = true;
    bindEvents();
    router?.register?.("coupons", routeCoupons);
  }

  function bindEvents() {
    el.refresh?.addEventListener("click", () => {
      if (state.currentId) {
        openDetail(state.currentId);
        return;
      }
      loadPage();
    });
    el.filterForm?.addEventListener("submit", (event) => {
      event.preventDefault();
      state.page = 1;
      loadPage();
    });
    el.reset?.addEventListener("click", () => {
      el.filterName.value = "";
      el.filterStatus.value = "";
      el.filterStartFrom.value = "";
      el.filterEndTo.value = "";
      state.page = 1;
      loadPage();
    });
    el.pageSize?.addEventListener("change", () => {
      const pageSize = readPageSize(el.pageSize, state.pageSize);
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

  async function routeCoupons() {
    const couponId = couponIdFromLocation();
    if (couponId) {
      await openDetail(couponId);
      return;
    }
    closeDetail(true);
    showListView();
    await loadPage();
  }

  function couponIdFromLocation() {
    const normalizedPath = String(window.location.pathname || "").replace(/\/+$/, "");
    const prefix = `${CONSOLE_COUPONS_PATH}/`;
    if (!normalizedPath.startsWith(prefix)) {
      return "";
    }
    return decodeURIComponent(normalizedPath.slice(prefix.length).split("/")[0] || "").trim();
  }

  function navigateToCouponDetail(couponId) {
    const id = String(couponId || "").trim();
    if (!id) {
      return;
    }
    if (window.history?.pushState) {
      const url = new URL(window.location.href);
      url.pathname = `${CONSOLE_COUPONS_PATH}/${encodeURIComponent(id)}`;
      url.search = "";
      window.history.pushState({ adminSection: "coupons", couponId: id }, "", url.pathname + url.search + url.hash);
      routeCoupons();
      return;
    }
    openDetail(id);
  }

  function navigateToCouponList() {
    state.currentId = "";
    if (router?.switchSection) {
      router.switchSection("coupons");
      return;
    }
    if (window.history?.pushState) {
      window.history.pushState({ adminSection: "coupons" }, "", CONSOLE_COUPONS_PATH);
    }
    routeCoupons();
  }

  function showListView() {
    state.currentId = "";
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
    state.currentId = "";
    state.detail = null;
    state.claims = [];
    state.claimsTotal = 0;
    state.claimsPage = 1;
    if (!silent && el.detailBody) {
      el.detailBody.replaceChildren();
    }
  }

  function readPageSize(input, fallback) {
    const rawValue = String(input?.value || "").trim();
    const value = Number(rawValue);
    if (!rawValue || !Number.isInteger(value) || value <= 0) {
      api.setStatus(el.status, "每页数量必须是大于 0 的整数。", "error");
      return null;
    }
    return Math.min(value, 100);
  }

  async function loadPage() {
    const pageSize = readPageSize(el.pageSize, state.pageSize);
    if (!pageSize) {
      return;
    }
    state.pageSize = pageSize;
    state.pageBusy = true;
    renderSummary();
    api.setStatus(el.status, "正在加载优惠券列表。");
    try {
      const params = new URLSearchParams();
      params.set("page", String(state.page));
      params.set("pageSize", String(pageSize));
      appendParam(params, "name", el.filterName?.value);
      appendParam(params, "status", el.filterStatus?.value);
      appendParam(params, "receiveStartAtFrom", el.filterStartFrom?.value);
      appendParam(params, "receiveEndAtTo", el.filterEndTo?.value);
      const response = await couponApi.fetchTemplatePage(params);
      const data = response.data || {};
      state.total = Number(data.total || 0);
      state.page = Number(data.page || state.page);
      state.pageSize = Number(data.pageSize || state.pageSize);
      state.records = Array.isArray(data.records) ? data.records : [];
      renderCoupons(state.records);
      renderSummary();
      api.setStatus(el.status, "优惠券列表已刷新。", "ok");
    } catch (error) {
      state.records = [];
      renderCoupons([]);
      renderSummary();
      api.setStatus(el.status, error.message || "优惠券列表加载失败。", "error");
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

  function renderCoupons(records) {
    el.list.replaceChildren();
    if (!records.length) {
      const empty = document.createElement("div");
      empty.className = "admin-coupon-empty";
      empty.textContent = "暂无优惠券";
      el.list.appendChild(empty);
      return;
    }
    records.forEach((coupon) => el.list.appendChild(renderCouponRow(coupon)));
  }

  function renderCouponRow(coupon) {
    const couponId = String(coupon.id || "");
    const row = document.createElement("div");
    row.className = "admin-coupon-row";
    row.classList.toggle("is-clickable", Boolean(couponId));
    if (couponId) {
      row.tabIndex = 0;
      row.setAttribute("role", "button");
      row.setAttribute("aria-label", `查看优惠券 ${coupon.name || coupon.id || ""}`);
      row.addEventListener("click", () => navigateToCouponDetail(couponId));
      row.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          navigateToCouponDetail(couponId);
        }
      });
    }

    const nameCell = document.createElement("div");
    nameCell.className = "admin-coupon-name-cell";
    const name = document.createElement("strong");
    renderHighlightedName(name, coupon.name || "-");
    const id = document.createElement("small");
    id.textContent = `ID ${coupon.id || "-"}`;
    nameCell.append(name, id);
    row.appendChild(nameCell);

    row.appendChild(textCell(coupon.couponCode || "-"));
    row.appendChild(statusCell(coupon.status));
    row.appendChild(textCell(`${coupon.remainingQuantity ?? "-"} / ${coupon.totalQuantity ?? "-"}`));
    row.appendChild(textCell(`${formatDate(coupon.receiveStartAt)} - ${formatDate(coupon.receiveEndAt)}`));
    row.appendChild(textCell(`${formatDate(coupon.validStartAt)} - ${formatDate(coupon.validEndAt)}`));
    row.appendChild(actionCell(couponId));
    return row;
  }

  function actionCell(couponId) {
    const cell = document.createElement("div");
    cell.className = "admin-coupon-actions";
    cell.addEventListener("click", (event) => event.stopPropagation());
    const button = document.createElement("button");
    button.className = "admin-risk-ip-action-btn admin-spring-button is-add";
    button.type = "button";
    button.textContent = "详情";
    button.disabled = !couponId;
    button.addEventListener("click", () => navigateToCouponDetail(couponId));
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

  async function openDetail(couponId) {
    const id = String(couponId || "").trim();
    if (!id) {
      navigateToCouponList();
      return;
    }
    state.currentId = id;
    state.claimsPage = 1;
    showDetailView();
    renderDetailLoading();
    try {
      const response = await couponApi.getTemplateDetail(id);
      state.detail = response.data || null;
      renderDetail();
      await loadClaims();
    } catch (error) {
      state.detail = null;
      state.claims = [];
      state.claimsTotal = 0;
      renderDetailError(error.status === 404 ? "优惠券不存在。" : error.message || "优惠券详情加载失败。");
    }
  }

  function renderDetailLoading() {
    api.setStatus(el.detailStatus, "");
    el.detailBody.innerHTML = `<div class="admin-product-detail-empty">正在加载优惠券详情</div>`;
  }

  function renderDetailError(message) {
    el.detailBody.innerHTML = `<div class="admin-product-detail-empty">${escapeHtml(message)}</div>`;
    api.setStatus(el.detailStatus, message, "error");
  }

  function renderDetail() {
    const coupon = state.detail;
    if (!coupon) {
      renderDetailError("优惠券不存在。");
      return;
    }
    el.detailBody.replaceChildren();
    const shell = document.createElement("div");
    shell.className = "admin-product-detail-content admin-coupon-detail-content";
    shell.appendChild(detailToolbar(coupon));
    shell.appendChild(detailGrid(coupon));
    shell.appendChild(claimsSection());
    el.detailBody.appendChild(shell);
    api.setStatus(el.detailStatus, "");
  }

  function detailToolbar(coupon) {
    const toolbar = document.createElement("div");
    toolbar.className = "admin-product-detail-toolbar";
    const heading = document.createElement("div");
    heading.className = "admin-product-detail-title";
    const title = document.createElement("strong");
    title.textContent = coupon.name || "-";
    const small = document.createElement("small");
    small.textContent = `ID ${coupon.id || ""}`;
    heading.append(title, small);
    const actions = document.createElement("div");
    actions.className = "admin-product-detail-actions";
    actions.appendChild(button("返回列表", "admin-api-back", navigateToCouponList));
    actions.appendChild(button("刷新", "admin-ghost-button", () => openDetail(state.currentId)));
    toolbar.append(heading, actions);
    return toolbar;
  }

  function detailGrid(coupon) {
    const grid = document.createElement("div");
    grid.className = "admin-product-detail-grid admin-coupon-detail-grid";
    [
      ["编码", coupon.couponCode || "-"],
      ["状态", coupon.status || "-"],
      ["库存", `${coupon.remainingQuantity ?? "-"} / ${coupon.totalQuantity ?? "-"}`],
      ["每人限制", coupon.perUserLimit ?? "-"],
      ["优惠类型", coupon.discountType || "-"],
      ["适用范围", `${coupon.scopeType || "-"} ${formatTargetIds(coupon.targetIds)}`],
      ["领取时间", `${formatDate(coupon.receiveStartAt)} - ${formatDate(coupon.receiveEndAt)}`],
      ["有效期", `${formatDate(coupon.validStartAt)} - ${formatDate(coupon.validEndAt)}`],
      ["创建/更新", `${formatDate(coupon.createdAt)} / ${formatDate(coupon.updatedAt)}`]
    ].forEach(([label, value]) => {
      const item = document.createElement("div");
      item.innerHTML = `<span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong>`;
      grid.appendChild(item);
    });
    return grid;
  }

  function claimsSection() {
    const section = document.createElement("section");
    section.className = "admin-product-detail-section admin-coupon-claims-section";
    section.innerHTML = `
      <div class="admin-product-detail-section-heading">
        <h3>领取用户</h3>
        <div class="admin-product-detail-actions">
          <button class="admin-ghost-button admin-spring-button" type="button" data-coupon-claims-refresh>刷新领取记录</button>
        </div>
      </div>
      <form class="admin-risk-ip-filter-form admin-coupon-claims-filter" data-coupon-claims-filter>
        <label class="admin-risk-ip-field">
          <span>领取状态</span>
          <select data-coupon-claims-status>
            <option value="">全部状态</option>
            <option value="UNUSED">UNUSED</option>
            <option value="LOCKED">LOCKED</option>
            <option value="USED">USED</option>
            <option value="EXPIRED">EXPIRED</option>
            <option value="REVOKED">REVOKED</option>
          </select>
        </label>
        <label class="admin-risk-ip-field">
          <span>邮箱</span>
          <input data-coupon-claims-email type="search" autocomplete="off" placeholder="精确邮箱" />
        </label>
        <label class="admin-risk-ip-field">
          <span>每页</span>
          <input data-coupon-claims-page-size type="number" min="1" max="100" step="1" value="${state.claimsPageSize}" inputmode="numeric" />
        </label>
        <div class="admin-risk-ip-actions">
          <button class="admin-nav-button admin-spring-button" type="submit">查询</button>
          <button class="admin-api-back admin-spring-button" type="button" data-coupon-claims-reset>重置</button>
        </div>
      </form>
      <p class="admin-oauth-config-status" data-coupon-claims-status-text></p>
      <div class="admin-coupon-claims-table" aria-label="领取记录">
        <div class="admin-coupon-claim-row admin-coupon-claim-header">
          <span>邮箱</span>
          <span>用户 ID</span>
          <span>状态</span>
          <span>领取时间</span>
          <span>有效期</span>
          <span>锁定</span>
          <span>使用</span>
        </div>
        <div class="admin-coupon-claim-list" data-coupon-claims-list></div>
      </div>
      <div class="admin-risk-ip-pagination">
        <button class="admin-api-back admin-spring-button" type="button" data-coupon-claims-prev>上一页</button>
        <button class="admin-api-back admin-spring-button" type="button" data-coupon-claims-next>下一页</button>
      </div>`;
    bindClaimsSection(section);
    return section;
  }

  function bindClaimsSection(section) {
    section.querySelector("[data-coupon-claims-filter]")?.addEventListener("submit", (event) => {
      event.preventDefault();
      state.claimsPage = 1;
      loadClaims();
    });
    section.querySelector("[data-coupon-claims-reset]")?.addEventListener("click", () => {
      section.querySelector("[data-coupon-claims-status]").value = "";
      section.querySelector("[data-coupon-claims-email]").value = "";
      state.claimsPage = 1;
      loadClaims();
    });
    section.querySelector("[data-coupon-claims-refresh]")?.addEventListener("click", () => loadClaims());
    section.querySelector("[data-coupon-claims-prev]")?.addEventListener("click", () => {
      if (state.claimsPage > 1) {
        state.claimsPage -= 1;
        loadClaims();
      }
    });
    section.querySelector("[data-coupon-claims-next]")?.addEventListener("click", () => {
      if (state.claimsPage * state.claimsPageSize < state.claimsTotal) {
        state.claimsPage += 1;
        loadClaims();
      }
    });
    section.querySelector("[data-coupon-claims-page-size]")?.addEventListener("change", (event) => {
      const pageSize = readClaimsPageSize(event.target);
      if (!pageSize) {
        return;
      }
      state.claimsPageSize = pageSize;
      state.claimsPage = 1;
      loadClaims();
    });
  }

  async function loadClaims() {
    if (!state.currentId) {
      return;
    }
    const section = el.detailBody.querySelector(".admin-coupon-claims-section");
    if (!section) {
      return;
    }
    const pageSizeInput = section.querySelector("[data-coupon-claims-page-size]");
    const pageSize = readClaimsPageSize(pageSizeInput);
    if (!pageSize) {
      return;
    }
    state.claimsPageSize = pageSize;
    state.claimsBusy = true;
    renderClaimsState(section, "正在加载领取记录。");
    try {
      const params = new URLSearchParams();
      params.set("page", String(state.claimsPage));
      params.set("pageSize", String(pageSize));
      appendParam(params, "status", section.querySelector("[data-coupon-claims-status]")?.value);
      appendParam(params, "email", section.querySelector("[data-coupon-claims-email]")?.value);
      const response = await couponApi.fetchTemplateClaims(state.currentId, params);
      const data = response.data || {};
      state.claimsTotal = Number(data.total || 0);
      state.claimsPage = Number(data.page || state.claimsPage);
      state.claimsPageSize = Number(data.pageSize || state.claimsPageSize);
      state.claims = Array.isArray(data.records) ? data.records : [];
      renderClaims(section);
      renderClaimsState(section, "领取记录已刷新。", "ok");
    } catch (error) {
      state.claims = [];
      state.claimsTotal = 0;
      renderClaims(section);
      renderClaimsState(section, error.message || "领取记录加载失败。", "error");
    } finally {
      state.claimsBusy = false;
      updateClaimsPagination(section);
    }
  }

  function readClaimsPageSize(input) {
    const rawValue = String(input?.value || "").trim();
    const value = Number(rawValue);
    if (!rawValue || !Number.isInteger(value) || value <= 0) {
      renderClaimsState(el.detailBody?.querySelector(".admin-coupon-claims-section"), "领取记录每页数量必须是大于 0 的整数。", "error");
      return null;
    }
    return Math.min(value, 100);
  }

  function renderClaims(section) {
    const list = section.querySelector("[data-coupon-claims-list]");
    list.replaceChildren();
    if (!state.claims.length) {
      const empty = document.createElement("div");
      empty.className = "admin-coupon-empty";
      empty.textContent = "暂无领取记录";
      list.appendChild(empty);
      updateClaimsPagination(section);
      return;
    }
    state.claims.forEach((claim) => list.appendChild(renderClaimRow(claim)));
    updateClaimsPagination(section);
  }

  function renderClaimRow(claim) {
    const row = document.createElement("div");
    row.className = "admin-coupon-claim-row";
    row.appendChild(textCell(claim.email || "-"));
    row.appendChild(textCell(claim.userId || "-"));
    row.appendChild(statusCell(claim.status));
    row.appendChild(textCell(formatDate(claim.receivedAt)));
    row.appendChild(textCell(`${formatDate(claim.validStartAt)} - ${formatDate(claim.validEndAt)}`));
    row.appendChild(textCell(`${claim.lockedOrderNo || "-"} / ${formatDate(claim.lockedAt)}`));
    row.appendChild(textCell(`${claim.usedOrderNo || "-"} / ${formatDate(claim.usedAt)}`));
    return row;
  }

  function renderClaimsState(section, message, type = "") {
    const node = section?.querySelector?.("[data-coupon-claims-status-text]");
    if (node) {
      api.setStatus(node, message, type);
    }
  }

  function updateClaimsPagination(section) {
    const prev = section.querySelector("[data-coupon-claims-prev]");
    const next = section.querySelector("[data-coupon-claims-next]");
    if (prev) {
      prev.disabled = state.claimsBusy || state.claimsPage <= 1;
    }
    if (next) {
      next.disabled = state.claimsBusy || state.claimsPage * state.claimsPageSize >= state.claimsTotal;
    }
  }

  function textCell(text) {
    const cell = document.createElement("div");
    cell.className = "admin-coupon-cell";
    cell.textContent = String(text ?? "-");
    return cell;
  }

  function statusCell(status) {
    const cell = document.createElement("div");
    const badge = document.createElement("span");
    const value = String(status || "-");
    badge.className = `admin-coupon-status-badge ${statusClass(value)}`;
    badge.textContent = value;
    cell.appendChild(badge);
    return cell;
  }

  function statusClass(status) {
    if (status === "ACTIVE" || status === "UNUSED") {
      return "is-active";
    }
    if (status === "USED") {
      return "is-used";
    }
    if (status === "LOCKED") {
      return "is-locked";
    }
    if (status === "DISABLED" || status === "EXPIRED" || status === "REVOKED") {
      return "is-disabled";
    }
    return "is-draft";
  }

  function button(label, className, onClick) {
    const node = document.createElement("button");
    node.className = `${className} admin-spring-button`;
    node.type = "button";
    node.textContent = label;
    node.addEventListener("click", onClick);
    return node;
  }

  function renderHighlightedName(target, value) {
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
      mark.className = "admin-product-search-highlight";
      mark.textContent = source.slice(contentStart, end);
      target.appendChild(mark);
      cursor = end + HIGHLIGHT_END.length;
    }
  }

  function formatTargetIds(values) {
    const ids = Array.isArray(values) ? values.filter(Boolean) : [];
    return ids.length ? ` / ${ids.join(", ")}` : "";
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

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }

  function setText(node, value) {
    if (node) {
      node.textContent = value;
    }
  }

  root.AdminCouponsModule = {
    mount
  };
})(window);
