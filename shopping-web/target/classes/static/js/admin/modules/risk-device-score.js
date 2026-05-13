(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;
  const router = root.AdminRouter;

  const PREFIX = "admin-risk-device";
  const SECTION = "riskDeviceScore";
  const LEVEL_LABELS = {
    L1: "L1 · 8500+",
    L2: "L2 · 7500-8499",
    L3: "L3 · 6000-7499",
    L4: "L4 · 4800-5999",
    L5: "L5 · 3000-4799",
    L6: "L6 · 0-2999"
  };
  const SORT_LABELS = {
    risk_first: "风险优先",
    recent_first: "最近活跃"
  };

  function createCell(tagName, text, className) {
    const node = document.createElement(tagName);
    if (className) {
      node.className = className;
    }
    node.textContent = text;
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
    return normalized ? LEVEL_LABELS[normalized] : "全部分数区间";
  }

  function sortLabel(sort) {
    return SORT_LABELS[sort] || "风险优先";
  }

  function shortDeviceId(deviceId) {
    if (!deviceId || deviceId.length < 8) {
      return deviceId || "-";
    }
    return deviceId.substring(0, 8);
  }

  function buildHeaderRow() {
    const row = document.createElement("div");
    row.className = "admin-risk-ip-row is-header";
    ["设备", "分数", "最近 IP", "关联", "网络", "最近扣分", "时间"].forEach((label) => row.append(createCell("span", label)));
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

  function createRow(item) {
    const row = document.createElement("div");
    row.className = "admin-risk-ip-row";
    appendStackCell(row, item.maskedFingerprint || "-", shortDeviceId(item.deviceId));
    appendStackCell(row, formatNumber(item.currentScore), item.riskLevel || "");
    appendStackCell(row, item.lastLoginIp || "-", "");
    appendStackCell(row, String(item.linkedUserCount ?? 0), "账号");
    appendStackCell(
      row,
      `${item.recentDistinctIpCount ?? 0} IP / ${item.recentIpSwitchCount ?? 0} 次`,
      ""
    );
    appendStackCell(
      row,
      item.lastPenaltyReason || "-",
      item.lastPenaltyScore ? `-${item.lastPenaltyScore}` : ""
    );
    appendStackCell(row, formatDateTime(item.lastSeenAt), "");
    return row;
  }

  class RiskDeviceScoreView {
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
        level: document.getElementById(`${PREFIX}-level`),
        query: document.getElementById(`${PREFIX}-query`),
        pageSize: document.getElementById(`${PREFIX}-page-size`),
        sort: document.getElementById(`${PREFIX}-sort`),
        search: document.getElementById(`${PREFIX}-search`),
        refresh: document.getElementById(`${PREFIX}-refresh`),
        total: document.getElementById(`${PREFIX}-total`),
        pageLabel: document.getElementById(`${PREFIX}-page-label`),
        source: document.getElementById(`${PREFIX}-source`),
        currentLevel: document.getElementById(`${PREFIX}-current-level`),
        currentSort: document.getElementById(`${PREFIX}-current-sort`),
        status: document.getElementById(`${PREFIX}-status`),
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
      this.nodes.level?.addEventListener("change", () => this.load({ resetPage: true }));
      this.nodes.pageSize?.addEventListener("change", () => this.load({ resetPage: true }));
      this.nodes.sort?.addEventListener("change", () => this.load({ resetPage: true }));
      this.nodes.refresh?.addEventListener("click", () => {
        dom.playPress(this.nodes.refresh);
        this.load();
      });
      this.nodes.prev?.addEventListener("click", () => {
        if (this.page <= 1) {
          return;
        }
        this.page -= 1;
        this.load();
      });
      this.nodes.next?.addEventListener("click", () => {
        if (!this.hasNext) {
          return;
        }
        this.page += 1;
        this.load();
      });
    }

    readParams() {
      const level = normalizeLevel(this.nodes.level?.value || "");
      const pageSize = Number(this.nodes.pageSize?.value || 50);
      const sortValue = (this.nodes.sort?.value || "risk_first").trim();
      const q = (this.nodes.query?.value || "").trim();
      return {
        level,
        q,
        pageSize: [50, 100, 200].includes(pageSize) ? pageSize : 50,
        sort: SORT_LABELS[sortValue] ? sortValue : "risk_first"
      };
    }

    updateCurrentLabels(params) {
      dom.setText(this.nodes.currentLevel, levelLabel(params.level));
      dom.setText(this.nodes.currentSort, sortLabel(params.sort));
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

    buildUrl(params) {
      const query = new URLSearchParams();
      query.set("page", String(this.page));
      query.set("pageSize", String(params.pageSize));
      query.set("sort", params.sort);
      if (params.level) {
        query.set("level", params.level);
      }
      if (params.q) {
        query.set("q", params.q);
      }
      return `/shopping/admin/api/risk-credit/device?${query.toString()}`;
    }

    async load(options = {}) {
      if (options.resetPage) {
        this.page = 1;
      }
      const params = this.readParams();
      this.updateCurrentLabels(params);
      this.setLoading(true);
      dom.setStatusNode(this.nodes.status, "正在读取设备分数...");
      try {
        const response = await api.get(this.buildUrl(params));
        this.loaded = true;
        this.render(response.data || {});
        const source = response.data?.source === "redis" ? "Redis 缓存" : "数据库";
        const count = Array.isArray(response.data?.items) ? response.data.items.length : 0;
        dom.setText(this.nodes.source, source);
        dom.setStatusNode(this.nodes.status, `已从${source}读取 ${count} 条设备记录。`, "ok");
      } catch (error) {
        this.render({ items: [], total: 0, page: this.page, hasNext: false });
        dom.setText(this.nodes.source, "-");
        dom.setStatusNode(this.nodes.status, error.message || "读取设备分数失败。", "error");
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
        emptyNode.textContent = "暂无匹配设备。";
        this.nodes.list.replaceChildren(emptyNode);
        this.updatePaginationButtons();
        return;
      }
      this.nodes.list.replaceChildren(buildHeaderRow(), ...items.map((item) => createRow(item)));
      this.updatePaginationButtons();
    }
  }

  function mount() {
    new RiskDeviceScoreView().mount();
  }

  root.AdminRiskDeviceScoreModule = { mount };
})(window);
