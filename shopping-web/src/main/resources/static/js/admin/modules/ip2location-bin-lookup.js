(function (root) {
  const api = root.AdminApi;
  const router = root.AdminRouter;

  const LOOKUP_API = "/shopping/admin/api/ip2location/bin/wildcard-lookup";
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
    { name: "菲律宾", iso2: "ph", dialCode: "+63" }
  ];
  const NORTH_AMERICA_ONE_ISO2 = new Set(["us", "ca"]);
  const SHARED_DIAL_CODE_PREFERRED_ISO2 = { "+1": "us" };
  const COUNTRY_CODE_ALIASES = { uk: "gb", usa: "us" };
  const MISMATCH_LABELS = {
    country: "国家",
    region: "地区",
    city: "城市",
    status: "状态"
  };

  const state = {
    mounted: false,
    page: 1,
    hasNext: false,
    loading: false,
    countryPicker: null,
    nodes: {}
  };

  function normalizeCountryCode(rawCountryCode) {
    if (typeof rawCountryCode !== "string") {
      return "";
    }
    const normalized = rawCountryCode.trim().toLowerCase();
    return /^[a-z]{2}$/.test(normalized) ? normalized : "";
  }

  function normalizeCountryCodeSearch(rawCountryCode) {
    if (typeof rawCountryCode !== "string") {
      return "";
    }
    const normalized = rawCountryCode.trim().toLowerCase();
    if (!/^[a-z]{1,3}$/.test(normalized)) {
      return "";
    }
    return COUNTRY_CODE_ALIASES[normalized] || normalized;
  }

  function normalizeSearchInputValue(rawValue) {
    const value = typeof rawValue === "string" ? rawValue.trim() : "";
    return /^[a-z]{1,3}$/i.test(value) ? value.toUpperCase() : value;
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

  function formatCountryCode(country) {
    return country?.iso2 ? country.iso2.toUpperCase() : "All";
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

  function compareCountriesByCountryCodeSearch(firstCountry, secondCountry, queryCountryCode) {
    if (!firstCountry.iso2 || !secondCountry.iso2) {
      return compareCountries(firstCountry, secondCountry);
    }
    const firstExact = firstCountry.iso2 === queryCountryCode;
    const secondExact = secondCountry.iso2 === queryCountryCode;
    if (firstExact !== secondExact) {
      return firstExact ? -1 : 1;
    }
    return compareCountries(firstCountry, secondCountry);
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

  class CountryPicker {
    constructor(prefix) {
      this.prefix = prefix;
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
      this.searchInput.addEventListener("input", (event) => {
        const normalizedValue = normalizeSearchInputValue(event.target.value);
        if (normalizedValue !== event.target.value) {
          event.target.value = normalizedValue;
        }
        this.applyFilter(normalizedValue);
      });
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
      const queryCountryCode = normalizeCountryCodeSearch(query);
      return (queryDigits && getDialCodeDigits(country).includes(queryDigits))
        || (queryCountryCode && country.iso2.includes(queryCountryCode))
        || country.name.toLowerCase().includes(query)
        || country.iso2.toLowerCase().includes(query)
        || country.dialCode.toLowerCase().includes(query);
    }

    applyFilter(keyword) {
      const query = (keyword || "").trim().toLowerCase();
      const queryDigits = query.replace(/\D/g, "");
      const queryCountryCode = normalizeCountryCodeSearch(query);
      this.filteredCountries = query
        ? this.allCountries.filter((country) => this.matchesCountryQuery(country, query))
        : [...this.allCountries];
      if (queryCountryCode) {
        this.filteredCountries.sort((firstCountry, secondCountry) =>
          compareCountriesByCountryCodeSearch(firstCountry, secondCountry, queryCountryCode));
      } else if (queryDigits) {
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
        code.textContent = formatCountryCode(country);
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
      this.triggerDial.textContent = formatCountryCode(country);
    }

    select(country, options = {}) {
      const normalized = normalizeCountryOption(country) || ALL_COUNTRIES;
      this.selectedCountry = normalized;
      this.updateTrigger(normalized);
      this.applyFilter(this.searchInput.value || "");
      this.close();
      if (!options.silent) {
        this.hiddenInput.dispatchEvent(new Event("change", { bubbles: true }));
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

  function setText(node, value) {
    if (node) {
      node.textContent = value == null || value === "" ? "-" : String(value);
    }
  }

  function formatNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) ? new Intl.NumberFormat("zh-CN").format(number) : "-";
  }

  function formatCoordinate(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number.toFixed(6).replace(/0+$/, "").replace(/\.$/, "") : "-";
  }

  function readNodes() {
    const prefix = "admin-ip2location-bin";
    state.nodes = {
      form: document.getElementById(`${prefix}-form`),
      pattern: document.getElementById(`${prefix}-pattern`),
      countryCode: document.getElementById(`${prefix}-country-code`),
      region: document.getElementById(`${prefix}-region`),
      city: document.getElementById(`${prefix}-city`),
      includeUnmatched: document.getElementById(`${prefix}-include-unmatched`),
      pageSize: document.getElementById(`${prefix}-page-size`),
      submit: document.getElementById(`${prefix}-submit`),
      clear: document.getElementById(`${prefix}-clear`),
      candidates: document.getElementById(`${prefix}-candidates`),
      queried: document.getElementById(`${prefix}-queried`),
      matched: document.getElementById(`${prefix}-matched`),
      unmatched: document.getElementById(`${prefix}-unmatched`),
      pageLabel: document.getElementById(`${prefix}-page-label`),
      status: document.getElementById(`${prefix}-status`),
      list: document.getElementById(`${prefix}-list`),
      prev: document.getElementById(`${prefix}-prev`),
      next: document.getElementById(`${prefix}-next`)
    };
  }

  function setStatus(message, type = "") {
    const node = state.nodes.status;
    if (!node) {
      return;
    }
    node.textContent = message || "";
    node.classList.toggle("is-error", type === "error");
    node.classList.toggle("is-ok", type === "ok");
  }

  function readPayload() {
    const pageSize = Number(state.nodes.pageSize?.value || "100");
    if (!Number.isInteger(pageSize) || pageSize <= 0) {
      setStatus("每页数量必须是大于 0 的整数。", "error");
      return null;
    }
    return {
      ipPattern: (state.nodes.pattern?.value || "").trim(),
      countryCode: (state.nodes.countryCode?.value || "").trim(),
      region: (state.nodes.region?.value || "").trim(),
      city: (state.nodes.city?.value || "").trim(),
      includeUnmatched: Boolean(state.nodes.includeUnmatched?.checked),
      page: state.page,
      pageSize
    };
  }

  function setLoading(loading) {
    state.loading = loading;
    [state.nodes.submit, state.nodes.clear, state.nodes.prev, state.nodes.next].forEach((button) => {
      if (button) {
        button.disabled = loading;
      }
    });
    if (!loading) {
      updatePagination();
    }
  }

  function updatePagination() {
    if (state.nodes.prev) {
      state.nodes.prev.disabled = state.loading || state.page <= 1;
    }
    if (state.nodes.next) {
      state.nodes.next.disabled = state.loading || !state.hasNext;
    }
  }

  function resetSummary() {
    setText(state.nodes.candidates, "-");
    setText(state.nodes.queried, "-");
    setText(state.nodes.matched, "-");
    setText(state.nodes.unmatched, "-");
    setText(state.nodes.pageLabel, "1");
  }

  async function load(options = {}) {
    if (options.resetPage) {
      state.page = 1;
    }
    const payload = readPayload();
    if (!payload) {
      return;
    }
    if (!payload.ipPattern) {
      setStatus("请输入 IPv4 通配模式。", "error");
      return;
    }
    setLoading(true);
    setStatus("正在查询本地 IP2Location BIN...");
    try {
      const response = await api.request(LOOKUP_API, payload);
      render(response.data || {});
      setStatus("查询完成。", "ok");
    } catch (error) {
      render({ items: [], page: state.page, hasNext: false });
      setStatus(error.message || "查询失败，请稍后重试。", "error");
    } finally {
      setLoading(false);
    }
  }

  function render(data) {
    const items = Array.isArray(data.items) ? data.items : [];
    state.page = Number(data.page || state.page || 1);
    state.hasNext = Boolean(data.hasNext);
    setText(state.nodes.candidates, formatNumber(data.candidateCount));
    setText(state.nodes.queried, formatNumber(data.queriedCount));
    setText(state.nodes.matched, formatNumber(data.matchedCount));
    setText(state.nodes.unmatched, formatNumber(data.unmatchedCount));
    setText(state.nodes.pageLabel, String(state.page));

    if (!state.nodes.list) {
      updatePagination();
      return;
    }
    if (!items.length) {
      const emptyNode = document.createElement("div");
      emptyNode.className = "admin-ip2location-bin-empty";
      emptyNode.textContent = "暂无匹配结果。";
      state.nodes.list.replaceChildren(emptyNode);
      updatePagination();
      return;
    }
    state.nodes.list.replaceChildren(buildHeaderRow(), ...items.map(createRow));
    updatePagination();
  }

  function buildHeaderRow() {
    const row = document.createElement("div");
    row.className = "admin-ip2location-bin-row is-header";
    ["IP", "匹配", "国家", "地区", "城市", "经纬度", "不匹配原因"].forEach((label) => {
      const cell = document.createElement("span");
      cell.textContent = label;
      row.append(cell);
    });
    return row;
  }

  function createRow(item) {
    const row = document.createElement("div");
    row.className = `admin-ip2location-bin-row${item.matched ? " is-match" : ""}`;
    row.append(
      textCell(item.ip || "-"),
      matchCell(Boolean(item.matched)),
      countryCell(item),
      textCell(item.region || "-"),
      stackCell(item.city || "-", item.district || ""),
      stackCell(formatCoordinate(item.latitude), formatCoordinate(item.longitude)),
      reasonsCell(item.mismatchReasons)
    );
    return row;
  }

  function textCell(text) {
    const cell = document.createElement("span");
    cell.textContent = text || "-";
    return cell;
  }

  function stackCell(primary, secondary) {
    const cell = document.createElement("div");
    const strong = document.createElement("strong");
    strong.textContent = primary || "-";
    cell.append(strong);
    if (secondary) {
      const small = document.createElement("small");
      small.textContent = secondary;
      cell.append(small);
    }
    return cell;
  }

  function matchCell(matched) {
    const cell = document.createElement("span");
    cell.className = `admin-ip2location-bin-match${matched ? " is-match" : " is-miss"}`;
    cell.textContent = matched ? "MATCH" : "MISS";
    return cell;
  }

  function countryCell(item) {
    const code = normalizeCountryCode(item.countryCode || "");
    const cell = document.createElement("div");
    cell.className = "admin-ip2location-bin-country-cell";
    const flag = document.createElement("span");
    flag.className = code ? `fi fi-${code}` : "admin-risk-country-flag";
    const text = document.createElement("div");
    const strong = document.createElement("strong");
    strong.textContent = item.countryName || (code ? code.toUpperCase() : "-");
    const small = document.createElement("small");
    small.textContent = code ? code.toUpperCase() : "-";
    text.append(strong, small);
    cell.append(flag, text);
    return cell;
  }

  function reasonsCell(reasons) {
    const normalizedReasons = Array.isArray(reasons) ? reasons : [];
    const cell = document.createElement("span");
    cell.className = "admin-ip2location-bin-reasons";
    cell.textContent = normalizedReasons.length
      ? normalizedReasons.map((reason) => MISMATCH_LABELS[reason] || reason).join(" / ")
      : "-";
    return cell;
  }

  function clearForm() {
    state.page = 1;
    state.hasNext = false;
    if (state.nodes.pattern) {
      state.nodes.pattern.value = "";
    }
    if (state.nodes.region) {
      state.nodes.region.value = "";
    }
    if (state.nodes.city) {
      state.nodes.city.value = "";
    }
    if (state.nodes.includeUnmatched) {
      state.nodes.includeUnmatched.checked = false;
    }
    if (state.nodes.pageSize) {
      state.nodes.pageSize.value = "100";
    }
    state.countryPicker?.select(ALL_COUNTRIES, { silent: true });
    resetSummary();
    if (state.nodes.list) {
      state.nodes.list.replaceChildren();
    }
    setStatus("输入 IPv4 通配模式后开始查询，本工具只读取本地 BIN，不消耗 IP2Location.io 额度。");
    updatePagination();
  }

  function bindEvents() {
    state.nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      load({ resetPage: true });
    });
    state.nodes.clear?.addEventListener("click", clearForm);
    state.nodes.prev?.addEventListener("click", () => {
      if (state.page <= 1) {
        return;
      }
      state.page -= 1;
      load();
    });
    state.nodes.next?.addEventListener("click", () => {
      if (!state.hasNext) {
        return;
      }
      state.page += 1;
      load();
    });
  }

  function mount() {
    if (state.mounted) {
      return;
    }
    readNodes();
    if (!state.nodes.form) {
      return;
    }
    state.countryPicker = new CountryPicker("admin-ip2location-bin");
    state.countryPicker.init();
    bindEvents();
    resetSummary();
    updatePagination();
    state.mounted = true;
  }

  router?.register?.("riskApiIp2LocationBinLookup", mount);
  root.AdminIp2LocationBinLookupModule = { mount };
})(window);
