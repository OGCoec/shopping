(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;
  const router = root.AdminRouter;

  const COUNTRY_API = "https://restcountries.com/v3.1/all?fields=cca2,idd,name";
  const ALL_COUNTRIES = { name: "全部国家/地区", iso2: "", dialCode: "All" };
  const FALLBACK_COUNTRIES = [
    { name: "中国", iso2: "cn", dialCode: "+86" },
    { name: "美国", iso2: "us", dialCode: "+1" },
    { name: "加拿大", iso2: "ca", dialCode: "+1" },
    { name: "英国", iso2: "gb", dialCode: "+44" },
    { name: "法国", iso2: "fr", dialCode: "+33" },
    { name: "德国", iso2: "de", dialCode: "+49" },
    { name: "日本", iso2: "jp", dialCode: "+81" },
    { name: "韩国", iso2: "kr", dialCode: "+82" },
    { name: "新加坡", iso2: "sg", dialCode: "+65" },
    { name: "中国香港", iso2: "hk", dialCode: "+852" },
    { name: "中国澳门", iso2: "mo", dialCode: "+853" },
    { name: "中国台湾", iso2: "tw", dialCode: "+886" },
    { name: "澳大利亚", iso2: "au", dialCode: "+61" },
    { name: "新西兰", iso2: "nz", dialCode: "+64" },
    { name: "印度", iso2: "in", dialCode: "+91" },
    { name: "巴西", iso2: "br", dialCode: "+55" },
    { name: "俄罗斯", iso2: "ru", dialCode: "+7" },
    { name: "越南", iso2: "vn", dialCode: "+84" },
    { name: "泰国", iso2: "th", dialCode: "+66" },
    { name: "马来西亚", iso2: "my", dialCode: "+60" },
    { name: "印度尼西亚", iso2: "id", dialCode: "+62" },
    { name: "菲律宾", iso2: "ph", dialCode: "+63" },
    { name: "土耳其", iso2: "tr", dialCode: "+90" },
    { name: "墨西哥", iso2: "mx", dialCode: "+52" },
    { name: "荷兰", iso2: "nl", dialCode: "+31" },
    { name: "西班牙", iso2: "es", dialCode: "+34" },
    { name: "意大利", iso2: "it", dialCode: "+39" }
  ];
  const NORTH_AMERICA_ONE_ISO2 = new Set(["us", "ca"]);
  const SHARED_DIAL_CODE_PREFERRED_ISO2 = {
    "+1": "us"
  };
  const LEVEL_LABELS = {
    L1: "L1 · 8500+",
    L2: "L2 · 7500-8499",
    L3: "L3 · 6000-7499",
    L4: "L4 · 4800-5999",
    L5: "L5 · 3000-4799",
    L6: "L6 · 0-2999"
  };
  const FAMILY_CONFIG = {
    ipv4: { section: "riskIpScoreIpv4", prefix: "admin-risk-ip-ipv4", label: "IPv4" },
    ipv6: { section: "riskIpScoreIpv6", prefix: "admin-risk-ip-ipv6", label: "IPv6" }
  };

  function normalizeCountryCode(rawCountryCode) {
    if (typeof rawCountryCode !== "string") {
      return "";
    }
    const normalized = rawCountryCode.trim().toLowerCase();
    return /^[a-z]{2}$/.test(normalized) ? normalized : "";
  }

  function normalizeDialCode(rawDialCode) {
    if (typeof rawDialCode !== "string") {
      return "";
    }
    const compact = rawDialCode.replace(/[^\d+]/g, "").trim();
    if (!compact) {
      return "";
    }
    return compact.startsWith("+") ? compact : `+${compact}`;
  }

  function normalizeCountryOption(country) {
    if (country === ALL_COUNTRIES) {
      return ALL_COUNTRIES;
    }
    const iso2 = normalizeCountryCode((country?.iso2 || "").toString());
    const name = (country?.name || "").toString().trim();
    let dialCode = normalizeDialCode((country?.dialCode || "").toString());
    if (!iso2 || !name || !dialCode) {
      return null;
    }
    if (NORTH_AMERICA_ONE_ISO2.has(iso2) && dialCode.startsWith("+1")) {
      dialCode = "+1";
    }
    return { name, iso2, dialCode };
  }

  function getDialCodeDigits(country) {
    return (country?.dialCode || "").replace(/\D/g, "");
  }

  function compareCountries(firstCountry, secondCountry) {
    if (!firstCountry.iso2) {
      return -1;
    }
    if (!secondCountry.iso2) {
      return 1;
    }
    const dialOrder = firstCountry.dialCode.localeCompare(secondCountry.dialCode, "en");
    if (dialOrder !== 0) {
      return dialOrder;
    }
    const preferredIso2 = SHARED_DIAL_CODE_PREFERRED_ISO2[firstCountry.dialCode];
    if (preferredIso2) {
      if (firstCountry.iso2 === preferredIso2 && secondCountry.iso2 !== preferredIso2) {
        return -1;
      }
      if (secondCountry.iso2 === preferredIso2 && firstCountry.iso2 !== preferredIso2) {
        return 1;
      }
    }
    const nameOrder = firstCountry.name.localeCompare(secondCountry.name, "zh-CN");
    return nameOrder !== 0 ? nameOrder : firstCountry.iso2.localeCompare(secondCountry.iso2, "en");
  }

  function compareCountriesByDialSearch(firstCountry, secondCountry, queryDigits) {
    if (!firstCountry.iso2 || !secondCountry.iso2) {
      return compareCountries(firstCountry, secondCountry);
    }
    const firstDigits = getDialCodeDigits(firstCountry);
    const secondDigits = getDialCodeDigits(secondCountry);
    const firstExact = firstDigits === queryDigits;
    const secondExact = secondDigits === queryDigits;
    if (firstExact !== secondExact) {
      return firstExact ? -1 : 1;
    }
    const firstStartsWith = firstDigits.startsWith(queryDigits);
    const secondStartsWith = secondDigits.startsWith(queryDigits);
    if (firstStartsWith !== secondStartsWith) {
      return firstStartsWith ? -1 : 1;
    }
    const lengthOrder = firstDigits.length - secondDigits.length;
    return lengthOrder !== 0 ? lengthOrder : compareCountries(firstCountry, secondCountry);
  }

  function normalizeAndSortCountries(countries) {
    const dedupedCountries = new Map();
    dedupedCountries.set("", ALL_COUNTRIES);
    (Array.isArray(countries) ? countries : []).forEach((country) => {
      const normalizedCountry = normalizeCountryOption(country);
      if (!normalizedCountry) {
        return;
      }
      const key = normalizedCountry.iso2 ? `${normalizedCountry.iso2}|${normalizedCountry.dialCode}` : "";
      if (!dedupedCountries.has(key)) {
        dedupedCountries.set(key, normalizedCountry);
      }
    });
    return Array.from(dedupedCountries.values()).sort(compareCountries);
  }

  function createCountryDisplayNameResolver() {
    if (typeof Intl === "undefined" || !Intl.DisplayNames) {
      return null;
    }
    return new Intl.DisplayNames(["zh-CN"], { type: "region" });
  }

  async function fetchCountryOptions() {
    const response = await fetch(COUNTRY_API, { method: "GET", cache: "force-cache" });
    if (!response.ok) {
      return [];
    }
    const payload = await response.json();
    if (!Array.isArray(payload)) {
      return [];
    }
    const displayNameResolver = createCountryDisplayNameResolver();
    const options = [];
    payload.forEach((item) => {
      const isoRaw = (item?.cca2 || "").toString().toUpperCase();
      const iddRoot = item?.idd?.root;
      if (!isoRaw || isoRaw.length !== 2 || typeof iddRoot !== "string") {
        return;
      }
      const iso2 = isoRaw.toLowerCase();
      const dialCode = normalizeDialCode(iddRoot + (item?.idd?.suffixes?.[0] || ""));
      if (!dialCode || (dialCode === "+1" && !NORTH_AMERICA_ONE_ISO2.has(iso2))) {
        return;
      }
      options.push({
        name: displayNameResolver?.of(isoRaw) || item?.name?.common || isoRaw,
        iso2,
        dialCode
      });
    });
    return normalizeAndSortCountries(options);
  }

  class AdminCountryPicker {
    constructor(prefix, onChange) {
      this.prefix = prefix;
      this.onChange = onChange;
      this.container = document.getElementById(`${prefix}-country-picker`);
      this.hiddenInput = document.getElementById(`${prefix}-country-code`);
      this.trigger = document.getElementById(`${prefix}-country-trigger`);
      this.popover = document.getElementById(`${prefix}-country-popover`);
      this.searchInput = document.getElementById(`${prefix}-country-search`);
      this.list = document.getElementById(`${prefix}-country-list`);
      this.triggerFlag = document.getElementById(`${prefix}-country-flag`);
      this.triggerName = document.getElementById(`${prefix}-country-name`);
      this.triggerDial = document.getElementById(`${prefix}-country-dial`);
      this.allCountries = [];
      this.filteredCountries = [];
      this.highlightedIndex = -1;
      this.selectedCountry = ALL_COUNTRIES;
      this.bound = false;
    }

    init() {
      if (!this.container || !this.hiddenInput || !this.trigger || !this.popover || !this.searchInput || !this.list) {
        return false;
      }
      this.bindEvents();
      this.setCountries(FALLBACK_COUNTRIES);
      this.select(ALL_COUNTRIES, { silent: true });
      this.hydrate();
      return true;
    }

    bindEvents() {
      if (this.bound) {
        return;
      }
      this.bound = true;
      this.trigger.addEventListener("click", () => this.toggle());
      this.trigger.addEventListener("keydown", (event) => {
        if (event.key === "ArrowDown" || event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          if (!this.isOpen()) {
            this.open();
          }
          this.moveHighlight(1);
        }
      });
      this.searchInput.addEventListener("input", (event) => this.applyFilter(event.target.value));
      this.searchInput.addEventListener("keydown", (event) => this.handleSearchKeydown(event));
      this.list.addEventListener("mousemove", (event) => {
        const index = this.getOptionIndexFromEventTarget(event.target);
        if (index >= 0 && index !== this.highlightedIndex) {
          this.highlightedIndex = index;
          this.renderList();
        }
      });
      this.list.addEventListener("click", (event) => {
        const index = this.getOptionIndexFromEventTarget(event.target);
        const country = this.filteredCountries[index];
        if (country) {
          this.select(country);
        }
      });
      document.addEventListener("click", (event) => {
        if (this.isOpen() && !this.container.contains(event.target)) {
          this.close();
        }
      });
    }

    async hydrate() {
      try {
        const remoteCountries = await fetchCountryOptions();
        if (!remoteCountries.length) {
          return;
        }
        const selectedIso2 = this.selectedCountry?.iso2 || "";
        this.setCountries(remoteCountries);
        this.setCountryIso2(selectedIso2, { silent: true });
      } catch (_) {
      }
    }

    setCountries(countries) {
      this.allCountries = normalizeAndSortCountries(countries);
      this.applyFilter(this.searchInput.value || "");
    }

    handleSearchKeydown(event) {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        this.moveHighlight(1);
        return;
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        this.moveHighlight(-1);
        return;
      }
      if (event.key === "Enter") {
        event.preventDefault();
        const country = this.filteredCountries[this.highlightedIndex];
        if (country) {
          this.select(country);
        }
        return;
      }
      if (event.key === "Escape") {
        event.preventDefault();
        this.close();
      }
    }

    matchesCountryQuery(country, query) {
      if (!country.iso2) {
        return "all".includes(query) || "全部".includes(query);
      }
      const queryDigits = query.replace(/\D/g, "");
      return (queryDigits && getDialCodeDigits(country).includes(queryDigits))
        || country.name.toLowerCase().includes(query)
        || country.iso2.toLowerCase().includes(query)
        || country.dialCode.toLowerCase().includes(query);
    }

    applyFilter(keyword) {
      const query = (keyword || "").trim().toLowerCase();
      const queryDigits = query.replace(/\D/g, "");
      this.filteredCountries = query
        ? this.allCountries.filter((country) => this.matchesCountryQuery(country, query))
        : [...this.allCountries];
      if (queryDigits) {
        this.filteredCountries.sort((firstCountry, secondCountry) =>
          compareCountriesByDialSearch(firstCountry, secondCountry, queryDigits));
      }
      const selectedIndex = this.filteredCountries.findIndex((country) =>
        country.iso2 === this.selectedCountry?.iso2 && country.dialCode === this.selectedCountry?.dialCode);
      this.highlightedIndex = selectedIndex >= 0 ? selectedIndex : (this.filteredCountries.length > 0 ? 0 : -1);
      this.renderList();
    }

    renderList() {
      if (this.filteredCountries.length === 0) {
        const emptyNode = document.createElement("li");
        emptyNode.className = "admin-risk-country-empty";
        emptyNode.textContent = "没有匹配的国家或区号";
        this.list.replaceChildren(emptyNode);
        return;
      }
      const rows = this.filteredCountries.map((country, index) => {
        const option = document.createElement("li");
        option.className = "admin-risk-country-option";
        option.dataset.index = String(index);
        option.setAttribute("role", "option");
        option.setAttribute("aria-selected", String(country.iso2 === this.selectedCountry?.iso2));
        if (index === this.highlightedIndex) {
          option.classList.add("is-highlighted");
        }
        const main = document.createElement("span");
        main.className = "admin-risk-country-option-main";
        const flag = document.createElement("span");
        flag.className = country.iso2 ? `fi fi-${country.iso2}` : "admin-risk-country-flag";
        const name = document.createElement("span");
        name.className = "admin-risk-country-option-name";
        name.textContent = country.name;
        const code = document.createElement("span");
        code.className = "admin-risk-country-option-code";
        code.textContent = country.dialCode;
        main.append(flag, name);
        option.append(main, code);
        return option;
      });
      this.list.replaceChildren(...rows);
    }

    getOptionIndexFromEventTarget(target) {
      const option = target.closest(".admin-risk-country-option");
      if (!option) {
        return -1;
      }
      const index = Number(option.dataset.index);
      return Number.isNaN(index) ? -1 : index;
    }

    moveHighlight(step) {
      if (!this.filteredCountries.length) {
        return;
      }
      this.highlightedIndex = this.highlightedIndex < 0
        ? 0
        : (this.highlightedIndex + step + this.filteredCountries.length) % this.filteredCountries.length;
      this.renderList();
      this.list.children[this.highlightedIndex]?.scrollIntoView({ block: "nearest" });
    }

    updateTrigger(country) {
      this.hiddenInput.value = country.iso2 ? country.iso2.toUpperCase() : "";
      this.triggerFlag.className = country.iso2 ? `admin-risk-country-flag fi fi-${country.iso2}` : "admin-risk-country-flag";
      this.triggerName.textContent = country.name;
      this.triggerDial.textContent = country.dialCode;
    }

    select(country, options = {}) {
      const normalized = normalizeCountryOption(country) || ALL_COUNTRIES;
      this.selectedCountry = normalized;
      this.updateTrigger(normalized);
      this.applyFilter(this.searchInput.value || "");
      this.close();
      if (!options.silent) {
        this.hiddenInput.dispatchEvent(new Event("change", { bubbles: true }));
        this.onChange?.(normalized);
      }
    }

    setCountryIso2(iso2, options = {}) {
      const normalizedIso2 = normalizeCountryCode(iso2);
      if (!normalizedIso2) {
        this.select(ALL_COUNTRIES, options);
        return true;
      }
      const country = this.allCountries.find((item) => item.iso2 === normalizedIso2);
      if (!country) {
        return false;
      }
      this.select(country, options);
      return true;
    }

    getCountry(iso2) {
      const normalizedIso2 = normalizeCountryCode(iso2);
      if (!normalizedIso2) {
        return ALL_COUNTRIES;
      }
      return this.allCountries.find((country) => country.iso2 === normalizedIso2) || null;
    }

    isOpen() {
      return this.container.dataset.open === "true";
    }

    open() {
      this.container.dataset.open = "true";
      this.trigger.setAttribute("aria-expanded", "true");
      window.setTimeout(() => this.searchInput.focus(), 0);
    }

    close() {
      this.container.dataset.open = "false";
      this.trigger.setAttribute("aria-expanded", "false");
    }

    toggle() {
      if (this.isOpen()) {
        this.close();
      } else {
        this.open();
      }
    }
  }

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

  function buildHeaderRow() {
    const row = document.createElement("div");
    row.className = "admin-risk-ip-row is-header";
    ["IP", "分数", "国家", "网络", "标记", "时间", "操作"].forEach((label) => row.append(createCell("span", label)));
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

  function createCountryCell(item, countryPicker) {
    const code = normalizeCountryCode(item.countryCode || item.country?.code || "");
    const meta = countryPicker.getCountry(code);
    const name = item.countryName || meta?.name || (code ? code.toUpperCase() : "-");
    const dialCode = item.dialCode || meta?.dialCode || "";
    const region = [item.region, item.city].filter(Boolean).join(" / ");
    const cell = document.createElement("div");
    cell.className = "admin-risk-ip-country-cell";
    const flag = document.createElement("span");
    flag.className = code ? `fi fi-${code}` : "admin-risk-country-flag";
    const text = document.createElement("div");
    text.append(createCell("strong", name));
    text.append(createCell("small", [code ? code.toUpperCase() : "", dialCode, region].filter(Boolean).join(" · ") || "-"));
    cell.append(flag, text);
    return cell;
  }

  function createBadge(label, active) {
    const badge = document.createElement("span");
    badge.className = `admin-risk-ip-badge${active ? " is-risk" : ""}`;
    badge.textContent = label;
    return badge;
  }

  function createFlagsCell(item) {
    const cell = document.createElement("div");
    cell.className = "admin-risk-ip-badges";
    cell.append(
      createBadge("TOR", Boolean(item.tor)),
      createBadge("Proxy", Boolean(item.proxy)),
      createBadge("VPN", Boolean(item.vpn)),
      createBadge("IDC", Boolean(item.datacenter))
    );
    return cell;
  }

  function createRow(item, countryPicker, onAction) {
    const row = document.createElement("div");
    row.className = "admin-risk-ip-row";
    appendStackCell(row, item.ip || "-", item.sourceProvider || "");
    appendStackCell(row, formatNumber(item.score), item.level || "");
    row.append(createCountryCell(item, countryPicker));
    appendStackCell(row, item.providerName || "-", [item.ipType, item.asn].filter(Boolean).join(" · "));
    row.append(createFlagsCell(item));
    appendStackCell(row, formatDateTime(item.lastSeenAt), `查询 ${formatDateTime(item.queriedAt)} · 过期 ${formatDateTime(item.expiresAt)}`);
    row.append(createActionCell(item, onAction));
    return row;
  }

  function createActionCell(item, onAction) {
    const cell = document.createElement("div");
    cell.className = "admin-risk-ip-action-cell";
    const score = Number(item.score);
    const isL6 = Number.isFinite(score) && score < 3000;
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `admin-risk-ip-action-btn admin-spring-button ${isL6 ? "is-remove" : "is-add"}`;
    btn.textContent = isL6 ? "移出风险" : "添加风险";
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      if (isL6) {
        openScoreDialog(item.ip, "remove_risk", onAction);
      } else {
        openScoreDialog(item.ip, "add_risk", onAction);
      }
    });
    cell.append(btn);
    return cell;
  }

  function openScoreDialog(ip, action, onAction) {
    const existing = document.getElementById("admin-risk-ip-score-dialog");
    if (existing) {
      existing.remove();
    }
    const isRemove = action === "remove_risk";
    const overlay = document.createElement("div");
    overlay.id = "admin-risk-ip-score-dialog";
    overlay.className = "admin-risk-score-overlay";
    const dialog = document.createElement("div");
    dialog.className = "admin-risk-score-dialog";
    const title = document.createElement("strong");
    title.textContent = isRemove ? "移出 IP 风险" : "添加 IP 风险";
    const ipLabel = document.createElement("p");
    ipLabel.textContent = `IP: ${ip}`;
    ipLabel.className = "admin-risk-score-ip";
    const label = document.createElement("label");
    label.className = "admin-risk-score-field";
    const labelText = document.createElement("span");
    labelText.textContent = isRemove ? "恢复分数（3000-10000）" : "风险分数（0-2999）";
    const input = document.createElement("input");
    input.type = "number";
    input.className = "admin-risk-score-input";
    input.value = isRemove ? "6000" : "0";
    input.min = isRemove ? "3000" : "0";
    input.max = isRemove ? "10000" : "2999";
    input.step = "1";
    label.append(labelText, input);
    const hint = document.createElement("small");
    hint.className = "admin-risk-score-hint";
    hint.textContent = isRemove
      ? "最低 3000（脱离 L6），建议 6000（L3 中等信任）"
      : "默认 0（最高风险 L6），可自定义 0-2999";
    const statusNode = document.createElement("p");
    statusNode.className = "admin-oauth-config-status";
    const actions = document.createElement("div");
    actions.className = "admin-risk-score-actions";
    const cancelBtn = document.createElement("button");
    cancelBtn.type = "button";
    cancelBtn.className = "admin-api-back admin-spring-button";
    cancelBtn.textContent = "取消";
    cancelBtn.addEventListener("click", () => overlay.remove());
    const confirmBtn = document.createElement("button");
    confirmBtn.type = "button";
    confirmBtn.className = "admin-nav-button admin-spring-button";
    confirmBtn.textContent = isRemove ? "确认移出" : "确认添加";
    confirmBtn.addEventListener("click", async () => {
      const score = parseInt(input.value, 10);
      if (!Number.isFinite(score)) {
        dom.setStatusNode(statusNode, "请输入有效分数。", "error");
        return;
      }
      if (isRemove && (score < 3000 || score > 10000)) {
        dom.setStatusNode(statusNode, "移出风险分数必须在 3000-10000 之间。", "error");
        return;
      }
      if (!isRemove && (score < 0 || score > 2999)) {
        dom.setStatusNode(statusNode, "添加风险分数必须在 0-2999 之间。", "error");
        return;
      }
      confirmBtn.disabled = true;
      cancelBtn.disabled = true;
      dom.setStatusNode(statusNode, "正在提交...");
      try {
        await onAction(ip, action, score);
        dom.setStatusNode(statusNode, "操作成功。", "ok");
        setTimeout(() => overlay.remove(), 600);
      } catch (err) {
        dom.setStatusNode(statusNode, err.message || "操作失败。", "error");
        confirmBtn.disabled = false;
        cancelBtn.disabled = false;
      }
    });
    actions.append(cancelBtn, confirmBtn);
    dialog.append(title, ipLabel, label, hint, statusNode, actions);
    overlay.append(dialog);
    overlay.addEventListener("click", (e) => {
      if (e.target === overlay) {
        overlay.remove();
      }
    });
    document.body.append(overlay);
    input.focus();
    input.select();
  }

  class RiskIpFamilyView {
    constructor(family) {
      this.family = family;
      this.config = FAMILY_CONFIG[family];
      this.page = 1;
      this.hasNext = false;
      this.loaded = false;
      this.loading = false;
      this.countryPicker = null;
      this.nodes = {};
    }

    mount() {
      const prefix = this.config.prefix;
      this.nodes = {
        form: document.getElementById(`${prefix}-filter-form`),
        countryCode: document.getElementById(`${prefix}-country-code`),
        level: document.getElementById(`${prefix}-level`),
        query: document.getElementById(`${prefix}-query`),
        pageSize: document.getElementById(`${prefix}-page-size`),
        search: document.getElementById(`${prefix}-search`),
        refresh: document.getElementById(`${prefix}-refresh`),
        total: document.getElementById(`${prefix}-total`),
        pageLabel: document.getElementById(`${prefix}-page-label`),
        currentCountry: document.getElementById(`${prefix}-current-country`),
        currentLevel: document.getElementById(`${prefix}-current-level`),
        status: document.getElementById(`${prefix}-status`),
        list: document.getElementById(`${prefix}-list`),
        prev: document.getElementById(`${prefix}-prev`),
        next: document.getElementById(`${prefix}-next`)
      };
      if (!this.nodes.form) {
        return;
      }
      this.countryPicker = new AdminCountryPicker(prefix, () => this.load({ resetPage: true }));
      this.countryPicker.init();
      this.bindEvents();
      router.register(this.config.section, () => {
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
      const country = normalizeCountryCode(this.nodes.countryCode?.value || "").toUpperCase();
      const level = normalizeLevel(this.nodes.level?.value || "");
      const pageSize = Number(this.nodes.pageSize?.value || 50);
      const q = (this.nodes.query?.value || "").trim();
      return {
        country,
        level,
        q,
        pageSize: [50, 100, 200].includes(pageSize) ? pageSize : 50
      };
    }

    updateCurrentLabels(params) {
      const countryMeta = this.countryPicker.getCountry(params.country);
      const countryText = countryMeta && countryMeta.iso2
        ? `${countryMeta.name} · ${countryMeta.dialCode}`
        : "全部国家/地区";
      dom.setText(this.nodes.currentCountry, countryText);
      dom.setText(this.nodes.currentLevel, levelLabel(params.level));
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
      query.set("sort", "risk_first");
      if (params.country) {
        query.set("country", params.country);
      }
      if (params.level) {
        query.set("level", params.level);
      }
      if (params.q) {
        query.set("q", params.q);
      }
      return `/shopping/admin/api/risk-credit/ip/${this.family}?${query.toString()}`;
    }

    async load(options = {}) {
      if (options.resetPage) {
        this.page = 1;
      }
      const params = this.readParams();
      this.updateCurrentLabels(params);
      this.setLoading(true);
      dom.setStatusNode(this.nodes.status, `正在读取 ${this.config.label} IP 分数...`);
      try {
        const response = await api.get(this.buildUrl(params));
        this.loaded = true;
        this.render(response.data || {});
        const source = response.data?.source === "redis" ? "Redis 缓存" : "数据库";
        const count = Array.isArray(response.data?.items) ? response.data.items.length : 0;
        dom.setStatusNode(this.nodes.status, `已从${source}读取 ${count} 条 ${this.config.label} 记录。`, "ok");
      } catch (error) {
        this.render({ items: [], total: 0, page: this.page, hasNext: false });
        dom.setStatusNode(this.nodes.status, error.message || `读取 ${this.config.label} IP 分数失败。`, "error");
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
        emptyNode.textContent = "暂无匹配 IP。";
        this.nodes.list.replaceChildren(emptyNode);
        this.updatePaginationButtons();
        return;
      }
      const onAction = (ip, action, score) => this.submitBatchUpdate(ip, action, score);
      this.nodes.list.replaceChildren(buildHeaderRow(), ...items.map((item) => createRow(item, this.countryPicker, onAction)));
      this.updatePaginationButtons();
    }

    async submitBatchUpdate(ip, action, targetScore) {
      const response = await api.request(`/shopping/admin/api/risk-credit/ip/${this.family}/batch-update`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ips: [ip], targetScore: targetScore, action: action })
      });
      if (!response.success) {
        throw new Error(response.message || "操作失败。");
      }
      this.loaded = false;
      this.load();
    }
  }

  const views = {};

  function mount() {
    Object.keys(FAMILY_CONFIG).forEach((family) => {
      const view = new RiskIpFamilyView(family);
      view.mount();
      views[family] = view;
    });
  }

  function presetLevel(family, level) {
    const view = views[family];
    if (!view || !view.nodes.level) {
      return;
    }
    view.nodes.level.value = level || "";
    view.loaded = false;
    view.page = 1;
  }

  root.AdminRiskIpScoreModule = { mount, presetLevel };
})(window);
