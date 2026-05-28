(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;
  const router = root.AdminRouter;

  const PREFIX = "admin-account-credit";
  const SECTION = "accountCredit";
  const PAGE_SIZE_ERROR = "每页数量必须是大于 0 的整数。";
  const LEVEL_LABELS = {
    L1: "L1 · 8500+",
    L2: "L2 · 7500-8499",
    L3: "L3 · 6000-7499",
    L4: "L4 · 4800-5999",
    L5: "L5 · 3000-4799",
    L6: "L6 · 0-2999"
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

  function normalizeLevel(level) {
    const normalized = String(level || "").trim().toUpperCase();
    return LEVEL_LABELS[normalized] ? normalized : "";
  }

  function levelLabel(level) {
    const normalized = normalizeLevel(level);
    return normalized ? LEVEL_LABELS[normalized] : "全部风险等级";
  }

  function readPositivePageSize(node, statusNode) {
    const rawValue = String(node?.value || "").trim();
    const pageSize = Number(rawValue);
    if (!rawValue || !Number.isInteger(pageSize) || pageSize <= 0) {
      dom.setStatusNode(statusNode, PAGE_SIZE_ERROR, "error");
      return null;
    }
    return pageSize;
  }

  function buildHeaderRow() {
    const row = document.createElement("div");
    row.className = "admin-risk-ip-row is-header";
    ["账号", "分数", "状态", "锁定", "最近登录", "更新时间", "操作"].forEach((label) => {
      row.append(createCell("span", label));
    });
    return row;
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

  function createRow(item, view) {
    const row = document.createElement("div");
    row.className = "admin-risk-ip-row";
    appendStackCell(row, item.email || `User ${item.userId}`, item.phone || `ID ${item.userId}`);
    appendStackCell(row, formatNumber(item.currentScore), item.riskLevel || "-");
    appendStackCell(row, item.status || "-", "");
    appendStackCell(row, String(item.lockCount ?? 0), item.lockReason || item.lockUntil || "");
    appendStackCell(row, formatDateTime(item.lastLoginAt), "");
    appendStackCell(row, formatDateTime(item.updatedAt), "");
    const actions = document.createElement("div");
    actions.className = "admin-account-actions";
    actions.append(
      createActionButton("详情", "", () => view.openDetail(item.userId)),
      createActionButton("调分", Number(item.currentScore) < 3000 ? "is-remove" : "is-add", () => view.openAdjustDialog(item))
    );
    row.append(actions);
    return row;
  }

  function createOverlay() {
    const existing = document.getElementById("admin-account-credit-dialog");
    if (existing) {
      existing.remove();
    }
    const overlay = document.createElement("div");
    overlay.id = "admin-account-credit-dialog";
    overlay.className = "admin-risk-score-overlay";
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) {
        overlay.remove();
      }
    });
    return overlay;
  }

  class AccountCreditView {
    constructor() {
      this.page = 1;
      this.hasNext = false;
      this.loaded = false;
      this.loading = false;
      this.nodes = {};
    }

    mount() {
      this.nodes = {
        form: document.getElementById(`${PREFIX}-filter-form`),
        userId: document.getElementById(`${PREFIX}-user-id`),
        email: document.getElementById(`${PREFIX}-email`),
        phone: document.getElementById(`${PREFIX}-phone`),
        status: document.getElementById(`${PREFIX}-status`),
        riskLevel: document.getElementById(`${PREFIX}-risk-level`),
        pageSize: document.getElementById(`${PREFIX}-page-size`),
        search: document.getElementById(`${PREFIX}-search`),
        refresh: document.getElementById(`${PREFIX}-refresh`),
        total: document.getElementById(`${PREFIX}-total`),
        pageLabel: document.getElementById(`${PREFIX}-page-label`),
        currentFilter: document.getElementById(`${PREFIX}-current-filter`),
        currentLevel: document.getElementById(`${PREFIX}-current-level`),
        statusText: document.getElementById(`${PREFIX}-status-text`),
        list: document.getElementById(`${PREFIX}-list`),
        prev: document.getElementById(`${PREFIX}-prev`),
        next: document.getElementById(`${PREFIX}-next`)
      };
      if (!this.nodes.form) {
        return;
      }
      this.bindEvents();
      router.register(SECTION, () => {
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
      [this.nodes.status, this.nodes.riskLevel, this.nodes.pageSize].forEach((node) => {
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
      const pageSize = readPositivePageSize(this.nodes.pageSize, this.nodes.statusText);
      if (pageSize == null) {
        return null;
      }
      return {
        userId: (this.nodes.userId?.value || "").trim(),
        email: (this.nodes.email?.value || "").trim(),
        phone: (this.nodes.phone?.value || "").trim(),
        status: (this.nodes.status?.value || "").trim(),
        riskLevel: normalizeLevel(this.nodes.riskLevel?.value || ""),
        pageSize
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
      if (params.status) {
        filters.push(params.status);
      }
      dom.setText(this.nodes.currentFilter, filters.join(" / ") || "全部账号");
      dom.setText(this.nodes.currentLevel, levelLabel(params.riskLevel));
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
      if (params.status) {
        query.set("status", params.status);
      }
      if (params.riskLevel) {
        query.set("riskLevel", params.riskLevel);
      }
      return `/shopping/admin/api/accounts/credit?${query.toString()}`;
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
      if (!params) {
        return;
      }
      this.updateCurrentLabels(params);
      this.setLoading(true);
      dom.setStatusNode(this.nodes.statusText, "正在读取账号信用分...");
      try {
        const response = await api.get(this.buildUrl(params));
        this.loaded = true;
        this.render(response.data || {});
        const count = Array.isArray(response.data?.items) ? response.data.items.length : 0;
        dom.setStatusNode(this.nodes.statusText, `已从主库读取 ${count} 条账号信用分记录。`, "ok");
      } catch (error) {
        this.render({ items: [], total: 0, page: this.page, hasNext: false });
        dom.setStatusNode(this.nodes.statusText, error.message || "读取账号信用分失败。", "error");
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
        emptyNode.textContent = "暂无匹配账号信用分记录。";
        this.nodes.list.replaceChildren(emptyNode);
        this.updatePaginationButtons();
        return;
      }
      this.nodes.list.replaceChildren(buildHeaderRow(), ...items.map((item) => createRow(item, this)));
      this.updatePaginationButtons();
    }

    async openDetail(userId) {
      const overlay = createOverlay();
      const dialog = document.createElement("div");
      dialog.className = "admin-risk-score-dialog admin-account-detail-dialog";
      const title = document.createElement("strong");
      title.textContent = `账号信用分详情 · ${userId}`;
      const status = document.createElement("p");
      status.className = "admin-oauth-config-status";
      status.textContent = "正在加载详情...";
      dialog.append(title, status);
      overlay.append(dialog);
      document.body.append(overlay);
      try {
        const detailResponse = await api.get(`/shopping/admin/api/accounts/credit/${encodeURIComponent(userId)}`);
        const detail = detailResponse.data || {};
        dialog.replaceChildren(title, this.renderDetailSummary(detail), this.createEventPanel(userId));
        this.loadEventPage(userId, 1);
      } catch (error) {
        dom.setStatusNode(status, error.message || "读取账号详情失败。", "error");
      }
    }

    renderDetailSummary(detail) {
      const panel = document.createElement("div");
      panel.className = "admin-account-detail-grid";
      [
        ["邮箱", detail.email || "-"],
        ["手机号", detail.phone || "-"],
        ["状态", detail.status || "-"],
        ["当前分", `${formatNumber(detail.currentScore)} / ${detail.riskLevel || "-"}`],
        ["环境分", formatNumber(detail.currentEnvScore)],
        ["行为分", formatNumber(detail.behaviorScoreDelta)],
        ["锁定次数", String(detail.lockCount ?? 0)],
        ["锁定原因", detail.lockReason || "-"],
        ["最近登录", formatDateTime(detail.lastLoginAt)],
        ["最近 IP", detail.lastLoginIp || "-"],
        ["首次登录", detail.firstLogin ? `${formatDateTime(detail.firstLogin.loginAt)} · ${detail.firstLogin.loginType || "-"}` : "-"],
        ["更新时间", formatDateTime(detail.updatedAt)]
      ].forEach(([label, value]) => {
        const item = document.createElement("div");
        item.append(createCell("span", label), createCell("strong", value));
        panel.append(item);
      });
      return panel;
    }

    createEventPanel(userId) {
      const panel = document.createElement("div");
      panel.className = "admin-account-event-panel";
      const heading = document.createElement("div");
      heading.className = "admin-account-event-heading";
      heading.append(createCell("strong", "信用分流水"));
      const actions = document.createElement("div");
      const prev = createActionButton("上一页", "", () => this.loadEventPage(userId, Math.max(1, Number(panel.dataset.page || 1) - 1)));
      const next = createActionButton("下一页", "", () => this.loadEventPage(userId, Number(panel.dataset.page || 1) + 1));
      prev.id = "admin-account-credit-event-prev";
      next.id = "admin-account-credit-event-next";
      actions.append(prev, next);
      heading.append(actions);
      const status = document.createElement("p");
      status.id = "admin-account-credit-event-status";
      status.className = "admin-oauth-config-status";
      const list = document.createElement("div");
      list.id = "admin-account-credit-event-list";
      list.className = "admin-account-event-list";
      panel.append(heading, status, list);
      panel.dataset.page = "1";
      return panel;
    }

    async loadEventPage(userId, page) {
      const panel = document.querySelector(".admin-account-event-panel");
      const status = document.getElementById("admin-account-credit-event-status");
      const list = document.getElementById("admin-account-credit-event-list");
      const prev = document.getElementById("admin-account-credit-event-prev");
      const next = document.getElementById("admin-account-credit-event-next");
      if (!panel || !status || !list) {
        return;
      }
      const safePage = Math.max(1, page);
      const pageSize = readPositivePageSize(this.nodes.pageSize, status);
      if (pageSize == null) {
        return;
      }
      dom.setStatusNode(status, "正在读取信用分流水...");
      try {
        const response = await api.get(`/shopping/admin/api/accounts/credit/${encodeURIComponent(userId)}/events?page=${safePage}&pageSize=${pageSize}`);
        const data = response.data || {};
        panel.dataset.page = String(data.page || safePage);
        if (prev) {
          prev.disabled = Number(panel.dataset.page) <= 1;
        }
        if (next) {
          next.disabled = !data.hasNext;
        }
        const items = Array.isArray(data.items) ? data.items : [];
        if (!items.length) {
          list.replaceChildren(createCell("div", "暂无信用分流水。", "admin-risk-ip-empty"));
        } else {
          list.replaceChildren(...items.map((item) => this.renderEventItem(item)));
        }
        dom.setStatusNode(status, `已读取 ${items.length} 条流水。`, "ok");
      } catch (error) {
        dom.setStatusNode(status, error.message || "读取信用分流水失败。", "error");
      }
    }

    renderEventItem(item) {
      const row = document.createElement("div");
      row.className = "admin-account-event-row";
      const delta = Number(item.scoreDelta || 0);
      const primary = `${item.eventType || "-"} · ${delta > 0 ? "+" : ""}${formatNumber(delta)}`;
      row.append(
        createCell("strong", primary),
        createCell("span", `${formatNumber(item.scoreBefore)} → ${formatNumber(item.scoreAfter)} · ${item.riskLevelBefore || "-"} → ${item.riskLevelAfter || "-"}`),
        createCell("small", `${formatDateTime(item.createdAt)} · ${item.reason || "-"}`),
        createCell("small", item.metadata || "")
      );
      return row;
    }

    openAdjustDialog(item) {
      const overlay = createOverlay();
      const dialog = document.createElement("div");
      dialog.className = "admin-risk-score-dialog";
      const title = createCell("strong", "管理员调分");
      const account = createCell("p", `${item.email || item.userId} · 当前分 ${formatNumber(item.currentScore)}`, "admin-risk-score-ip");
      const scoreLabel = document.createElement("label");
      scoreLabel.className = "admin-risk-score-field";
      scoreLabel.append(createCell("span", "调整分数（加分为正数，扣分为负数）"));
      const scoreInput = document.createElement("input");
      scoreInput.type = "number";
      scoreInput.className = "admin-risk-score-input";
      scoreInput.value = "100";
      scoreInput.step = "1";
      scoreInput.min = "-10000";
      scoreInput.max = "10000";
      scoreLabel.append(scoreInput);
      const reasonLabel = document.createElement("label");
      reasonLabel.className = "admin-risk-score-field";
      reasonLabel.append(createCell("span", "调整原因"));
      const reasonInput = document.createElement("textarea");
      reasonInput.className = "admin-risk-score-input admin-risk-score-textarea";
      reasonInput.rows = 4;
      reasonInput.placeholder = "必须填写原因";
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
      confirm.textContent = "确认调分";
      confirm.addEventListener("click", async () => {
        const scoreDelta = Number(scoreInput.value);
        const reason = reasonInput.value.trim();
        if (!Number.isInteger(scoreDelta) || scoreDelta === 0) {
          dom.setStatusNode(status, "调整分数必须是非 0 整数。", "error");
          return;
        }
        if (!reason) {
          dom.setStatusNode(status, "必须填写调整原因。", "error");
          return;
        }
        confirm.disabled = true;
        cancel.disabled = true;
        dom.setStatusNode(status, "正在提交调分...");
        try {
          await api.request(`/shopping/admin/api/accounts/credit/${encodeURIComponent(item.userId)}/adjust`, {
            scoreDelta,
            reason
          });
          dom.setStatusNode(status, "调分成功，流水已记录。", "ok");
          this.loaded = false;
          this.load();
          window.setTimeout(() => overlay.remove(), 650);
        } catch (error) {
          dom.setStatusNode(status, error.message || "调分失败。", "error");
          confirm.disabled = false;
          cancel.disabled = false;
        }
      });
      actions.append(cancel, confirm);
      dialog.append(title, account, scoreLabel, reasonLabel, status, actions);
      overlay.append(dialog);
      document.body.append(overlay);
      scoreInput.focus();
      scoreInput.select();
    }
  }

  function mount() {
    new AccountCreditView().mount();
  }

  root.AdminAccountCreditModule = { mount };
})(window);
