(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;
  const router = root.AdminRouter;

  const SELF_PREFIX = "admin-account-self";
  const RISK_PREFIX = "admin-account-risk-term";
  const SELF_SECTION = "accountTerminationSelf";
  const RISK_SECTION = "accountTerminationRisk";
  const SELF_SCOPE_LABELS = {
    within7Days: "7 天内可恢复",
    expired: "7 天外未清理",
    deleted: "已清理",
    restored: "已恢复"
  };

  function createCell(tagName, text, className) {
    const node = document.createElement(tagName);
    if (className) {
      node.className = className;
    }
    node.textContent = text ?? "-";
    return node;
  }

  function formatNumber(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
      return "-";
    }
    return new Intl.NumberFormat("zh-CN").format(number);
  }

  function formatDateTime(value) {
    if (!value) {
      return "-";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return new Intl.DateTimeFormat("zh-CN", {
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    }).format(date);
  }

  function appendStackCell(row, primaryText, secondaryText) {
    const cell = document.createElement("div");
    cell.append(createCell("strong", primaryText || "-"));
    if (secondaryText) {
      cell.append(createCell("small", secondaryText));
    }
    row.append(cell);
  }

  function createActionButton(label, kind, onClick) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `admin-risk-ip-action-btn admin-spring-button ${kind || ""}`;
    button.textContent = label;
    button.addEventListener("click", (event) => {
      event.stopPropagation();
      onClick?.();
    });
    return button;
  }

  function createOverlay(id) {
    const existing = document.getElementById(id);
    if (existing) {
      existing.remove();
    }
    const overlay = document.createElement("div");
    overlay.id = id;
    overlay.className = "admin-risk-score-overlay";
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) {
        overlay.remove();
      }
    });
    return overlay;
  }

  function buildSelfHeaderRow() {
    const row = document.createElement("div");
    row.className = "admin-risk-ip-row is-header";
    ["账号", "状态", "范围", "停用时间", "恢复", "原因", "操作"].forEach((label) => row.append(createCell("span", label)));
    return row;
  }

  function buildRiskHeaderRow() {
    const row = document.createElement("div");
    row.className = "admin-risk-ip-row is-header";
    ["账号", "分数", "状态", "锁定", "停用原因", "停用时间", "操作"].forEach((label) => row.append(createCell("span", label)));
    return row;
  }

  class SelfTerminationView {
    constructor() {
      this.page = 1;
      this.hasNext = false;
      this.loaded = false;
      this.loading = false;
      this.nodes = {};
    }

    mount() {
      this.nodes = {
        form: document.getElementById(`${SELF_PREFIX}-filter-form`),
        scope: document.getElementById(`${SELF_PREFIX}-scope`),
        userId: document.getElementById(`${SELF_PREFIX}-user-id`),
        email: document.getElementById(`${SELF_PREFIX}-email`),
        phone: document.getElementById(`${SELF_PREFIX}-phone`),
        pageSize: document.getElementById(`${SELF_PREFIX}-page-size`),
        search: document.getElementById(`${SELF_PREFIX}-search`),
        refresh: document.getElementById(`${SELF_PREFIX}-refresh`),
        total: document.getElementById(`${SELF_PREFIX}-total`),
        pageLabel: document.getElementById(`${SELF_PREFIX}-page-label`),
        currentFilter: document.getElementById(`${SELF_PREFIX}-current-filter`),
        currentScope: document.getElementById(`${SELF_PREFIX}-current-scope`),
        statusText: document.getElementById(`${SELF_PREFIX}-status-text`),
        list: document.getElementById(`${SELF_PREFIX}-list`),
        prev: document.getElementById(`${SELF_PREFIX}-prev`),
        next: document.getElementById(`${SELF_PREFIX}-next`)
      };
      if (!this.nodes.form) {
        return;
      }
      this.bindEvents();
      router.register(SELF_SECTION, () => {
        if (!this.loaded) {
          this.load();
        }
      });
    }

    bindEvents() {
      this.nodes.form?.addEventListener("submit", (event) => {
        event.preventDefault();
        dom.playPress(this.nodes.search);
        this.load({ resetPage: true });
      });
      [this.nodes.scope, this.nodes.pageSize].forEach((node) => {
        node?.addEventListener("change", () => this.load({ resetPage: true }));
      });
      this.nodes.refresh?.addEventListener("click", () => {
        dom.playPress(this.nodes.refresh);
        this.load();
      });
      this.nodes.prev?.addEventListener("click", () => {
        if (this.page > 1) {
          this.page -= 1;
          this.load();
        }
      });
      this.nodes.next?.addEventListener("click", () => {
        if (this.hasNext) {
          this.page += 1;
          this.load();
        }
      });
    }

    readParams() {
      const pageSize = Number(this.nodes.pageSize?.value || 50);
      return {
        scope: (this.nodes.scope?.value || "").trim(),
        userId: (this.nodes.userId?.value || "").trim(),
        email: (this.nodes.email?.value || "").trim(),
        phone: (this.nodes.phone?.value || "").trim(),
        pageSize: [50, 100, 200].includes(pageSize) ? pageSize : 50
      };
    }

    updateCurrentLabels(params) {
      const filters = [];
      if (params.userId) {
        filters.push(`ID ${params.userId}`);
      }
      if (params.email) {
        filters.push(params.email);
      }
      if (params.phone) {
        filters.push(params.phone);
      }
      dom.setText(this.nodes.currentFilter, filters.join(" / ") || "全部主动停用");
      dom.setText(this.nodes.currentScope, SELF_SCOPE_LABELS[params.scope] || "全部范围");
    }

    buildUrl(params) {
      const query = new URLSearchParams();
      query.set("page", String(this.page));
      query.set("pageSize", String(params.pageSize));
      if (params.scope) {
        query.set("scope", params.scope);
      }
      if (params.userId) {
        query.set("userId", params.userId);
      }
      if (params.email) {
        query.set("email", params.email);
      }
      if (params.phone) {
        query.set("phone", params.phone);
      }
      return `/shopping/admin/api/accounts/terminations/self?${query.toString()}`;
    }

    setLoading(loading) {
      this.loading = loading;
      [this.nodes.search, this.nodes.refresh, this.nodes.prev, this.nodes.next].forEach((button) => {
        if (button) {
          button.disabled = loading;
        }
      });
      if (!loading) {
        this.updatePaginationButtons();
      }
    }

    updatePaginationButtons() {
      if (this.nodes.prev) {
        this.nodes.prev.disabled = this.page <= 1 || this.loading;
      }
      if (this.nodes.next) {
        this.nodes.next.disabled = !this.hasNext || this.loading;
      }
    }

    async load(options = {}) {
      if (options.resetPage) {
        this.page = 1;
      }
      const params = this.readParams();
      this.updateCurrentLabels(params);
      this.setLoading(true);
      dom.setStatusNode(this.nodes.statusText, "正在读取主动停用记录...");
      try {
        const response = await api.get(this.buildUrl(params));
        this.loaded = true;
        this.render(response.data || {});
        const count = Array.isArray(response.data?.items) ? response.data.items.length : 0;
        dom.setStatusNode(this.nodes.statusText, `已读取 ${count} 条主动停用记录。`, "ok");
      } catch (error) {
        this.render({ items: [], total: 0, page: this.page, hasNext: false });
        dom.setStatusNode(this.nodes.statusText, error.message || "读取主动停用记录失败。", "error");
      } finally {
        this.setLoading(false);
      }
    }

    render(data) {
      const items = Array.isArray(data.items) ? data.items : [];
      this.page = Number(data.page || this.page || 1);
      this.hasNext = Boolean(data.hasNext);
      dom.setText(this.nodes.total, formatNumber(data.total));
      dom.setText(this.nodes.pageLabel, String(this.page));
      if (!this.nodes.list) {
        return;
      }
      if (!items.length) {
        const emptyNode = document.createElement("div");
        emptyNode.className = "admin-risk-ip-empty";
        emptyNode.textContent = "暂无匹配主动停用记录。";
        this.nodes.list.replaceChildren(emptyNode);
        this.updatePaginationButtons();
        return;
      }
      this.nodes.list.replaceChildren(buildSelfHeaderRow(), ...items.map((item) => this.createRow(item)));
      this.updatePaginationButtons();
    }

    createRow(item) {
      const row = document.createElement("div");
      row.className = "admin-risk-ip-row";
      appendStackCell(row, item.email || `User ${item.userId}`, item.phone || `ID ${item.userId}`);
      appendStackCell(row, item.status || "-", item.deleted ? "已清理" : "未清理");
      appendStackCell(row, item.restorable ? "7 天内" : (item.restoredAt ? "已恢复" : (item.deleted ? "已清理" : "不可恢复")), item.restorable ? "可恢复" : "");
      appendStackCell(row, formatDateTime(item.deletedAt), "");
      appendStackCell(row, formatDateTime(item.restoredAt), item.restoredBy || "");
      appendStackCell(row, item.deletionReason || "-", item.restoreReason || "");
      const actions = document.createElement("div");
      actions.className = "admin-account-actions";
      if (item.restorable) {
        actions.append(createActionButton("恢复", "is-remove", () => this.openRestoreDialog(item)));
      } else {
        actions.append(createCell("small", "只读"));
      }
      row.append(actions);
      return row;
    }

    openRestoreDialog(item) {
      const overlay = createOverlay("admin-account-self-restore-dialog");
      const dialog = document.createElement("div");
      dialog.className = "admin-risk-score-dialog";
      const title = createCell("strong", "恢复主动停用账号");
      const account = createCell("p", `${item.email || item.userId} · ${formatDateTime(item.deletedAt)}`, "admin-risk-score-ip");
      const reasonLabel = document.createElement("label");
      reasonLabel.className = "admin-risk-score-field";
      reasonLabel.append(createCell("span", "恢复原因"));
      const reasonInput = document.createElement("textarea");
      reasonInput.className = "admin-risk-score-input admin-risk-score-textarea";
      reasonInput.rows = 4;
      reasonInput.placeholder = "可选，建议填写恢复依据";
      reasonLabel.append(reasonInput);
      const status = document.createElement("p");
      status.className = "admin-oauth-config-status";
      const actions = document.createElement("div");
      actions.className = "admin-risk-score-actions";
      const cancel = document.createElement("button");
      cancel.type = "button";
      cancel.className = "admin-api-back admin-spring-button";
      cancel.textContent = "取消";
      cancel.addEventListener("click", () => overlay.remove());
      const confirm = document.createElement("button");
      confirm.type = "button";
      confirm.className = "admin-nav-button admin-spring-button";
      confirm.textContent = "确认恢复";
      confirm.addEventListener("click", async () => {
        confirm.disabled = true;
        cancel.disabled = true;
        dom.setStatusNode(status, "正在恢复账号...");
        try {
          await api.request(`/shopping/admin/api/accounts/terminations/self/${encodeURIComponent(item.id)}/restore`, {
            reason: reasonInput.value.trim()
          });
          dom.setStatusNode(status, "账号已恢复为 ACTIVE。", "ok");
          this.loaded = false;
          this.load();
          window.setTimeout(() => overlay.remove(), 650);
        } catch (error) {
          dom.setStatusNode(status, error.message || "恢复失败。", "error");
          confirm.disabled = false;
          cancel.disabled = false;
        }
      });
      actions.append(cancel, confirm);
      dialog.append(title, account, reasonLabel, status, actions);
      overlay.append(dialog);
      document.body.append(overlay);
      reasonInput.focus();
    }
  }

  class RiskTerminationView {
    constructor() {
      this.page = 1;
      this.hasNext = false;
      this.loaded = false;
      this.loading = false;
      this.nodes = {};
    }

    mount() {
      this.nodes = {
        form: document.getElementById(`${RISK_PREFIX}-filter-form`),
        userId: document.getElementById(`${RISK_PREFIX}-user-id`),
        email: document.getElementById(`${RISK_PREFIX}-email`),
        phone: document.getElementById(`${RISK_PREFIX}-phone`),
        pageSize: document.getElementById(`${RISK_PREFIX}-page-size`),
        search: document.getElementById(`${RISK_PREFIX}-search`),
        refresh: document.getElementById(`${RISK_PREFIX}-refresh`),
        total: document.getElementById(`${RISK_PREFIX}-total`),
        pageLabel: document.getElementById(`${RISK_PREFIX}-page-label`),
        currentFilter: document.getElementById(`${RISK_PREFIX}-current-filter`),
        statusText: document.getElementById(`${RISK_PREFIX}-status-text`),
        list: document.getElementById(`${RISK_PREFIX}-list`),
        prev: document.getElementById(`${RISK_PREFIX}-prev`),
        next: document.getElementById(`${RISK_PREFIX}-next`)
      };
      if (!this.nodes.form) {
        return;
      }
      this.bindEvents();
      router.register(RISK_SECTION, () => {
        if (!this.loaded) {
          this.load();
        }
      });
    }

    bindEvents() {
      this.nodes.form?.addEventListener("submit", (event) => {
        event.preventDefault();
        dom.playPress(this.nodes.search);
        this.load({ resetPage: true });
      });
      this.nodes.pageSize?.addEventListener("change", () => this.load({ resetPage: true }));
      this.nodes.refresh?.addEventListener("click", () => {
        dom.playPress(this.nodes.refresh);
        this.load();
      });
      this.nodes.prev?.addEventListener("click", () => {
        if (this.page > 1) {
          this.page -= 1;
          this.load();
        }
      });
      this.nodes.next?.addEventListener("click", () => {
        if (this.hasNext) {
          this.page += 1;
          this.load();
        }
      });
    }

    readParams() {
      const pageSize = Number(this.nodes.pageSize?.value || 50);
      return {
        userId: (this.nodes.userId?.value || "").trim(),
        email: (this.nodes.email?.value || "").trim(),
        phone: (this.nodes.phone?.value || "").trim(),
        pageSize: [50, 100, 200].includes(pageSize) ? pageSize : 50
      };
    }

    updateCurrentLabels(params) {
      const filters = [];
      if (params.userId) {
        filters.push(`ID ${params.userId}`);
      }
      if (params.email) {
        filters.push(params.email);
      }
      if (params.phone) {
        filters.push(params.phone);
      }
      dom.setText(this.nodes.currentFilter, filters.join(" / ") || "全部风控停用");
    }

    buildUrl(params) {
      const query = new URLSearchParams();
      query.set("page", String(this.page));
      query.set("pageSize", String(params.pageSize));
      if (params.userId) {
        query.set("userId", params.userId);
      }
      if (params.email) {
        query.set("email", params.email);
      }
      if (params.phone) {
        query.set("phone", params.phone);
      }
      return `/shopping/admin/api/accounts/terminations/risk?${query.toString()}`;
    }

    setLoading(loading) {
      this.loading = loading;
      [this.nodes.search, this.nodes.refresh, this.nodes.prev, this.nodes.next].forEach((button) => {
        if (button) {
          button.disabled = loading;
        }
      });
      if (!loading) {
        this.updatePaginationButtons();
      }
    }

    updatePaginationButtons() {
      if (this.nodes.prev) {
        this.nodes.prev.disabled = this.page <= 1 || this.loading;
      }
      if (this.nodes.next) {
        this.nodes.next.disabled = !this.hasNext || this.loading;
      }
    }

    async load(options = {}) {
      if (options.resetPage) {
        this.page = 1;
      }
      const params = this.readParams();
      this.updateCurrentLabels(params);
      this.setLoading(true);
      dom.setStatusNode(this.nodes.statusText, "正在读取被动停用记录...");
      try {
        const response = await api.get(this.buildUrl(params));
        this.loaded = true;
        this.render(response.data || {});
        const count = Array.isArray(response.data?.items) ? response.data.items.length : 0;
        dom.setStatusNode(this.nodes.statusText, `已读取 ${count} 条被动停用记录。`, "ok");
      } catch (error) {
        this.render({ items: [], total: 0, page: this.page, hasNext: false });
        dom.setStatusNode(this.nodes.statusText, error.message || "读取被动停用记录失败。", "error");
      } finally {
        this.setLoading(false);
      }
    }

    render(data) {
      const items = Array.isArray(data.items) ? data.items : [];
      this.page = Number(data.page || this.page || 1);
      this.hasNext = Boolean(data.hasNext);
      dom.setText(this.nodes.total, formatNumber(data.total));
      dom.setText(this.nodes.pageLabel, String(this.page));
      if (!this.nodes.list) {
        return;
      }
      if (!items.length) {
        const emptyNode = document.createElement("div");
        emptyNode.className = "admin-risk-ip-empty";
        emptyNode.textContent = "暂无匹配被动停用记录。";
        this.nodes.list.replaceChildren(emptyNode);
        this.updatePaginationButtons();
        return;
      }
      this.nodes.list.replaceChildren(buildRiskHeaderRow(), ...items.map((item) => this.createRow(item)));
      this.updatePaginationButtons();
    }

    createRow(item) {
      const row = document.createElement("div");
      row.className = "admin-risk-ip-row";
      appendStackCell(row, item.email || `User ${item.userId}`, item.phone || `ID ${item.userId}`);
      appendStackCell(row, formatNumber(item.currentScore), item.riskLevel || "-");
      appendStackCell(row, item.status || "-", "");
      appendStackCell(row, String(item.lockCount ?? 0), "");
      appendStackCell(row, item.terminationReason || "-", "");
      appendStackCell(row, formatDateTime(item.terminatedAt), "");
      const actions = document.createElement("div");
      actions.className = "admin-account-actions";
      actions.append(createActionButton("详情", "", () => this.openDetail(item.id)));
      row.append(actions);
      return row;
    }

    async openDetail(id) {
      const overlay = createOverlay("admin-account-risk-term-detail-dialog");
      const dialog = document.createElement("div");
      dialog.className = "admin-risk-score-dialog admin-account-detail-dialog";
      const title = createCell("strong", `被动停用详情 · ${id}`);
      const status = document.createElement("p");
      status.className = "admin-oauth-config-status";
      status.textContent = "正在加载详情...";
      dialog.append(title, status);
      overlay.append(dialog);
      document.body.append(overlay);
      try {
        const response = await api.get(`/shopping/admin/api/accounts/terminations/risk/${encodeURIComponent(id)}`);
        const data = response.data || {};
        const termination = data.termination || {};
        dialog.replaceChildren(title, this.renderTerminationSummary(termination), this.renderEvents(data.recentEvents || []));
      } catch (error) {
        dom.setStatusNode(status, error.message || "读取被动停用详情失败。", "error");
      }
    }

    renderTerminationSummary(item) {
      const panel = document.createElement("div");
      panel.className = "admin-account-detail-grid";
      [
        ["User ID", item.userId || "-"],
        ["邮箱", item.email || "-"],
        ["手机号", item.phone || "-"],
        ["状态", item.status || "-"],
        ["当前分", `${formatNumber(item.currentScore)} / ${item.riskLevel || "-"}`],
        ["锁定次数", String(item.lockCount ?? 0)],
        ["停用原因", item.terminationReason || "-"],
        ["停用时间", formatDateTime(item.terminatedAt)]
      ].forEach(([label, value]) => {
        const cell = document.createElement("div");
        cell.append(createCell("span", label), createCell("strong", value));
        panel.append(cell);
      });
      return panel;
    }

    renderEvents(events) {
      const panel = document.createElement("div");
      panel.className = "admin-account-event-panel";
      panel.append(createCell("strong", "最近信用分流水"));
      const list = document.createElement("div");
      list.className = "admin-account-event-list";
      if (!Array.isArray(events) || !events.length) {
        list.append(createCell("div", "暂无信用分流水。", "admin-risk-ip-empty"));
      } else {
        events.forEach((event) => {
          const row = document.createElement("div");
          row.className = "admin-account-event-row";
          const delta = Number(event.scoreDelta || 0);
          row.append(
            createCell("strong", `${event.eventType || "-"} · ${delta > 0 ? "+" : ""}${formatNumber(delta)}`),
            createCell("span", `${formatNumber(event.scoreBefore)} → ${formatNumber(event.scoreAfter)}`),
            createCell("small", `${formatDateTime(event.createdAt)} · ${event.reason || "-"}`),
            createCell("small", event.metadata || "")
          );
          list.append(row);
        });
      }
      panel.append(list);
      return panel;
    }
  }

  function mount() {
    new SelfTerminationView().mount();
    new RiskTerminationView().mount();
  }

  root.AdminAccountTerminationModule = { mount };
})(window);
