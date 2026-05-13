(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;
  const router = root.AdminRouter;

  function getNodes() {
    return {
      mailToolButton: document.getElementById("admin-risk-api-ip2location-mail-tool-open"),
      configToggle: document.getElementById("admin-risk-api-ip2location-config-toggle"),
      configCard: document.getElementById("admin-risk-api-ip2location-config-card"),
      loadButton: document.getElementById("admin-risk-api-ip2location-keys-load"),
      deleteButton: document.getElementById("admin-risk-api-ip2location-keys-delete"),
      status: document.getElementById("admin-risk-api-ip2location-keys-status"),
      db: document.getElementById("admin-risk-api-ip2location-keys-db"),
      prefix: document.getElementById("admin-risk-api-ip2location-keys-prefix"),
      total: document.getElementById("admin-risk-api-ip2location-keys-total"),
      list: document.getElementById("admin-risk-api-ip2location-keys-list"),
      form: document.getElementById("admin-risk-api-ip2location-keys-add-form"),
      input: document.getElementById("admin-risk-api-ip2location-keys-input"),
      accountType: document.getElementById("admin-risk-api-ip2location-keys-account-type"),
      quota: document.getElementById("admin-risk-api-ip2location-keys-quota"),
      addButton: document.getElementById("admin-risk-api-ip2location-keys-add")
    };
  }

  function setConfigCardVisible(visible) {
    const nodes = getNodes();
    if (nodes.configCard) { nodes.configCard.hidden = !visible; }
    if (nodes.configToggle) {
      nodes.configToggle.setAttribute("aria-expanded", String(visible));
      nodes.configToggle.textContent = visible ? "隐藏配置" : "API 配置";
    }
  }

  function toggleConfigCard() {
    const nodes = getNodes();
    const visible = Boolean(nodes.configCard?.hidden);
    setConfigCardVisible(visible);
    if (visible) {
      root.AdminRiskApiConfigModule?.loadConfig("ip2location", "IP2Location API");
    }
  }

  function formatQuotaTtl(ttlSeconds) {
    const ttl = Number(ttlSeconds);
    if (ttl === -1) { return "永久"; }
    if (ttl < 0 || Number.isNaN(ttl)) { return "-"; }
    const days = Math.floor(ttl / 86400);
    const hours = Math.floor((ttl % 86400) / 3600);
    const minutes = Math.floor((ttl % 3600) / 60);
    if (days > 0) { return `${days}天 ${hours}小时`; }
    if (hours > 0) { return `${hours}小时 ${minutes}分钟`; }
    return `${minutes}分钟`;
  }

  function createHeader() {
    const row = document.createElement("div");
    row.className = "admin-risk-api-key-row is-header";
    ["", "API key", "账户类型", "剩余额度", "TTL", "创建分钟"].forEach((text) => {
      const cell = document.createElement("span");
      cell.textContent = text;
      row.append(cell);
    });
    return row;
  }

  function createRow(item) {
    const row = document.createElement("div");
    row.className = "admin-risk-api-key-row";
    const check = document.createElement("input");
    check.type = "checkbox";
    check.value = item.redisKey || "";
    check.setAttribute("aria-label", `选择 ${item.apiKey || "IP2Location API key"}`);
    const apiKey = document.createElement("strong");
    apiKey.textContent = item.apiKey || "-";
    apiKey.title = item.redisKey || "";
    const accountType = document.createElement("span");
    accountType.textContent = item.accountType || "-";
    const quota = document.createElement("span");
    quota.textContent = String(item.remainingQuota ?? 0);
    const ttl = document.createElement("span");
    ttl.textContent = formatQuotaTtl(item.ttlSeconds);
    const createdAt = document.createElement("span");
    createdAt.textContent = item.createdAtMinute || "-";
    row.append(check, apiKey, accountType, quota, ttl, createdAt);
    return row;
  }

  function renderKeys(nodes, data) {
    const keys = Array.isArray(data.keys) ? data.keys : [];
    dom.setText(nodes.db, data.redisDatabase || "2");
    dom.setText(nodes.prefix, data.quotaPrefix || "ip2location:quota:");
    const realTotal = data.realTotalQuotaCount ?? 0;
    const aggregateTotal = data.aggregateTotalQuotaCount ?? realTotal;
    dom.setText(nodes.total, realTotal === aggregateTotal ? String(realTotal) : `${realTotal} / count=${aggregateTotal}`);
    if (!nodes.list) { return; }
    const rows = [createHeader(), ...keys.map(createRow)];
    nodes.list.replaceChildren(...rows);
    nodes.list.toggleAttribute("hidden", keys.length === 0);
  }

  async function loadKeys() {
    const nodes = getNodes();
    if (!nodes.loadButton) { return; }
    nodes.loadButton.disabled = true;
    dom.setStatusNode(nodes.status, "正在读取 Redis DB 2 中的 IP2Location API keys...");
    try {
      const response = await api.get("/shopping/admin/api/risk-api/ip2location/keys");
      renderKeys(nodes, response.data || {});
      const count = Array.isArray(response.data?.keys) ? response.data.keys.length : 0;
      dom.setStatusNode(nodes.status, `已读取 ${count} 个 IP2Location API key。`, "ok");
    } catch (error) {
      dom.setStatusNode(nodes.status, error.message || "读取 IP2Location API keys 失败。", "error");
    } finally { nodes.loadButton.disabled = false; }
  }

  function parseAddItems(nodes) {
    const raw = nodes.input?.value || "";
    const defaultAccountType = nodes.accountType?.value || "STARTER";
    const defaultQuotaText = nodes.quota?.value?.trim() || "";
    const defaultQuota = defaultQuotaText ? Number(defaultQuotaText) : null;
    if (defaultQuotaText && (!Number.isInteger(defaultQuota) || defaultQuota < 0)) {
      throw new Error("剩余额度必须是大于等于 0 的整数。");
    }
    const items = raw.split(/\r?\n/).map((l) => l.trim()).filter(Boolean).map((line) => {
      const parts = line.split(",").map((p) => p.trim()).filter(Boolean);
      const quotaText = parts[2] || "";
      const quota = quotaText ? Number(quotaText) : defaultQuota;
      if (quotaText && (!Number.isInteger(quota) || quota < 0)) {
        throw new Error(`剩余额度格式不正确：${line}`);
      }
      return { apiKey: parts[0] || line, accountType: parts[1] || defaultAccountType, remainingQuota: quota };
    });
    if (items.length === 0) { throw new Error("请至少填写一个 IP2Location API key。"); }
    return items;
  }

  async function batchAdd() {
    const nodes = getNodes();
    let items;
    try { items = parseAddItems(nodes); }
    catch (error) { dom.setStatusNode(nodes.status, error.message, "error"); return; }
    if (nodes.addButton) { nodes.addButton.disabled = true; }
    if (nodes.loadButton) { nodes.loadButton.disabled = true; }
    dom.setStatusNode(nodes.status, `正在批量添加 ${items.length} 个 IP2Location API key...`);
    try {
      const response = await api.request("/shopping/admin/api/risk-api/ip2location/keys/batch-add", { items });
      const result = response.data || {};
      if (nodes.input) { nodes.input.value = ""; }
      if (nodes.quota) { nodes.quota.value = ""; }
      await loadKeys();
      dom.setStatusNode(nodes.status, `已批量添加 ${result.affectedCount || 0} 个，替换旧 key ${result.replacedOldCount || 0} 个，总额度 ${result.totalQuotaCount || 0}。`, "ok");
    } catch (error) {
      dom.setStatusNode(nodes.status, error.message || "批量添加 IP2Location API key 失败。", "error");
    } finally {
      if (nodes.addButton) { nodes.addButton.disabled = false; }
      if (nodes.loadButton) { nodes.loadButton.disabled = false; }
    }
  }

  function selectedKeys(nodes) {
    return Array.from(nodes.list?.querySelectorAll("input[type='checkbox']:checked") || []).map((i) => i.value).filter(Boolean);
  }

  async function batchDelete() {
    const nodes = getNodes();
    const redisKeys = selectedKeys(nodes);
    if (redisKeys.length === 0) {
      dom.setStatusNode(nodes.status, "请选择需要删除的 IP2Location API key。", "error");
      return;
    }
    if (nodes.deleteButton) { nodes.deleteButton.disabled = true; }
    if (nodes.loadButton) { nodes.loadButton.disabled = true; }
    dom.setStatusNode(nodes.status, `正在批量删除 ${redisKeys.length} 个 IP2Location API key...`);
    try {
      const response = await api.request("/shopping/admin/api/risk-api/ip2location/keys/batch-delete", { redisKeys });
      const result = response.data || {};
      await loadKeys();
      dom.setStatusNode(nodes.status, `已批量删除 ${result.affectedCount || 0} 个 IP2Location API key，总额度 ${result.totalQuotaCount || 0}。`, "ok");
    } catch (error) {
      dom.setStatusNode(nodes.status, error.message || "批量删除 IP2Location API key 失败。", "error");
    } finally {
      if (nodes.deleteButton) { nodes.deleteButton.disabled = false; }
      if (nodes.loadButton) { nodes.loadButton.disabled = false; }
    }
  }

  function mount() {
    const nodes = getNodes();
    nodes.mailToolButton?.addEventListener("click", () => {
      dom.playPress(nodes.mailToolButton);
      root.AdminIp2LocationMailToolModule?.clearResults();
      root.AdminIp2LocationMailToolModule?.setOpen(true);
    });
    nodes.configToggle?.addEventListener("click", () => { dom.playPress(nodes.configToggle); toggleConfigCard(); });
    nodes.loadButton?.addEventListener("click", () => { dom.playPress(nodes.loadButton); loadKeys(); });
    nodes.deleteButton?.addEventListener("click", () => { dom.playPress(nodes.deleteButton); batchDelete(); });
    nodes.form?.addEventListener("submit", (event) => { event.preventDefault(); dom.playPress(nodes.addButton); batchAdd(); });

    router.register("riskApiIp2Location", () => { setConfigCardVisible(false); loadKeys(); });
  }

  root.AdminIp2LocationQuotaKeysModule = { mount };
})(window);
