(function (root, factory) {
  const api = factory(root);
  root.ShoppingLoginCountryPicker = api;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const DEFAULT_PHONE_COUNTRY_CODE = "";
  const PHONE_COUNTRY_API = "/shopping/auth/preauth/phone-country";

  let phoneCountryPicker = null;
  let registerPhoneRequiredCountryPicker = null;

  const FALLBACK_COUNTRIES = [
    { name: "China", iso2: "cn", dialCode: "+86" },
    { name: "United States", iso2: "us", dialCode: "+1" },
    { name: "Canada", iso2: "ca", dialCode: "+1" },
    { name: "United Kingdom", iso2: "gb", dialCode: "+44" },
    { name: "France", iso2: "fr", dialCode: "+33" },
    { name: "Germany", iso2: "de", dialCode: "+49" },
    { name: "Japan", iso2: "jp", dialCode: "+81" },
    { name: "South Korea", iso2: "kr", dialCode: "+82" },
    { name: "Singapore", iso2: "sg", dialCode: "+65" },
    { name: "Hong Kong", iso2: "hk", dialCode: "+852" },
    { name: "Macao", iso2: "mo", dialCode: "+853" },
    { name: "Taiwan", iso2: "tw", dialCode: "+886" },
    { name: "Australia", iso2: "au", dialCode: "+61" },
    { name: "New Zealand", iso2: "nz", dialCode: "+64" },
    { name: "India", iso2: "in", dialCode: "+91" },
    { name: "Brazil", iso2: "br", dialCode: "+55" }
  ];

  const NORTH_AMERICA_ONE_ISO2 = new Set(["us", "ca"]);
  const SHARED_DIAL_CODE_PREFERRED_ISO2 = {
    "+1": "us"
  };

  function normalizeDialCode(rawDialCode) {
    if (typeof rawDialCode !== "string") {
      return "";
    }

    const compact = rawDialCode.replace(/[^\d+]/g, "").trim();
    if (!compact) {
      return "";
    }
    if (compact.startsWith("+")) {
      return compact;
    }

    return `+${compact}`;
  }

  function normalizeCountryCode(rawCountryCode) {
    if (typeof rawCountryCode !== "string") {
      return "";
    }
    const normalized = rawCountryCode.trim().toLowerCase();
    return /^[a-z]{2}$/.test(normalized) ? normalized : "";
  }

  function normalizeCountryOption(country) {
    const iso2 = normalizeCountryCode((country?.iso2 || "").toString());
    const name = (country?.name || "").toString().trim();
    const rawDialCode = country?.dialCode || "";
    let dialCode = normalizeDialCode(rawDialCode);

    if (!iso2 || !name || !dialCode) {
      return null;
    }

    if (NORTH_AMERICA_ONE_ISO2.has(iso2) && dialCode.startsWith("+1")) {
      dialCode = "+1";
    }

    return { name, iso2, dialCode };
  }

  function compareCountriesByDialCode(firstCountry, secondCountry) {
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

    const nameOrder = firstCountry.name.localeCompare(secondCountry.name, "en");
    if (nameOrder !== 0) {
      return nameOrder;
    }

    return firstCountry.iso2.localeCompare(secondCountry.iso2, "en");
  }

  function createCountryDisplayNameResolver() {
    if (typeof Intl === "undefined" || !Intl.DisplayNames) {
      return null;
    }
    return new Intl.DisplayNames(["zh-CN"], { type: "region" });
  }

  function normalizeAndSortCountries(countries) {
    const dedupedCountries = new Map();

    (Array.isArray(countries) ? countries : []).forEach((country) => {
      const normalizedCountry = normalizeCountryOption(country);
      if (!normalizedCountry) {
        return;
      }

      const key = `${normalizedCountry.iso2}|${normalizedCountry.dialCode}`;
      if (dedupedCountries.has(key)) {
        return;
      }

      dedupedCountries.set(key, normalizedCountry);
    });

    return Array.from(dedupedCountries.values()).sort(compareCountriesByDialCode);
  }

  async function fetchCountryOptions() {
    const response = await fetch("https://restcountries.com/v3.1/all?fields=cca2,idd,name", {
      method: "GET",
      cache: "force-cache"
    });

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
      if (!isoRaw || isoRaw.length !== 2) {
        return;
      }

      const iddRoot = item?.idd?.root;
      if (typeof iddRoot !== "string") {
        return;
      }

      const countryName = displayNameResolver?.of(isoRaw) || item?.name?.common || isoRaw;
      const iso2 = isoRaw.toLowerCase();
      const dialCode = normalizeDialCode(iddRoot + (item?.idd?.suffixes?.[0] || ""));
      if (!dialCode) {
        return;
      }

      if (dialCode === "+1" && !NORTH_AMERICA_ONE_ISO2.has(iso2)) {
        return;
      }

      options.push({ name: countryName, iso2, dialCode });
    });

    return normalizeAndSortCountries(options);
  }

  class CountryPicker {
    constructor(config = {}) {
      const {
        containerId = "phone-country-picker",
        hiddenInputId = "phone-country-code",
        triggerId = "phone-country-trigger",
        popoverId = "phone-country-popover",
        searchInputId = "phone-country-search",
        listId = "phone-country-list",
        triggerFlagId = "phone-country-flag",
        triggerNameId = "phone-country-name",
        triggerCodeId = "phone-country-code-label",
        optionIdPrefix = "phone-country-option"
      } = config;

      this.container = document.getElementById(containerId);
      this.hiddenInput = document.getElementById(hiddenInputId);
      this.trigger = document.getElementById(triggerId);
      this.popover = document.getElementById(popoverId);
      this.searchInput = document.getElementById(searchInputId);
      this.list = document.getElementById(listId);
      this.triggerFlag = document.getElementById(triggerFlagId);
      this.triggerName = document.getElementById(triggerNameId);
      this.triggerCode = document.getElementById(triggerCodeId);
      this.optionIdPrefix = optionIdPrefix;

      this.allCountries = [];
      this.filteredCountries = [];
      this.highlightedIndex = -1;
      this.selectedCountry = null;
      this.preferredIso2 = "";
    }

    init() {
      if (!this.container || !this.hiddenInput || !this.trigger || !this.popover || !this.searchInput || !this.list) {
        return false;
      }

      this.bindEvents();
      this.setCountries(FALLBACK_COUNTRIES);
      this.hydrateCountryOptions();
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
        this.applyFilter(event.target.value);
      });

      this.searchInput.addEventListener("keydown", (event) => {
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
      });

      this.list.addEventListener("mousemove", (event) => {
        const index = this.getOptionIndexFromEventTarget(event.target);
        if (index < 0 || index === this.highlightedIndex) {
          return;
        }
        this.highlightedIndex = index;
        this.renderList();
      });

      this.list.addEventListener("click", (event) => {
        const index = this.getOptionIndexFromEventTarget(event.target);
        if (index < 0) {
          return;
        }

        const country = this.filteredCountries[index];
        if (!country) {
          return;
        }

        this.select(country);
      });

      document.addEventListener("click", (event) => {
        if (!this.isOpen()) {
          return;
        }
        if (this.container.contains(event.target)) {
          return;
        }
        this.close();
      });

      document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape" || !this.isOpen()) {
          return;
        }
        this.close();
      });
    }

    async hydrateCountryOptions() {
      try {
        const remoteCountries = await fetchCountryOptions();
        if (!Array.isArray(remoteCountries) || remoteCountries.length === 0) {
          return;
        }

        const currentDialCode = this.hiddenInput.value || "";
        this.setCountries(remoteCountries);

        if (this.preferredIso2 && this.setCountryIso2(this.preferredIso2, { silent: true })) {
          return;
        }
        if (currentDialCode) {
          this.setDialCode(currentDialCode, { silent: true });
        }
      } catch (_) {
      }
    }

    setCountries(countries) {
      this.allCountries = normalizeAndSortCountries(countries);
      this.applyFilter(this.searchInput.value || "");
    }

    applyFilter(keyword) {
      const query = (keyword || "").trim().toLowerCase();

      if (!query) {
        this.filteredCountries = [...this.allCountries];
      } else {
        this.filteredCountries = this.allCountries.filter((country) => this.matchesCountryQuery(country, query));
      }

      const selectedIndex = this.filteredCountries.findIndex((country) => this.isSameCountry(country, this.selectedCountry));
      this.highlightedIndex = selectedIndex >= 0 ? selectedIndex : (this.filteredCountries.length > 0 ? 0 : -1);
      this.renderList();
    }

    matchesCountryQuery(country, query) {
      return country.name.toLowerCase().includes(query)
        || country.iso2.toLowerCase().includes(query)
        || country.dialCode.toLowerCase().includes(query);
    }

    isSameCountry(firstCountry, secondCountry) {
      if (!firstCountry || !secondCountry) {
        return false;
      }

      return firstCountry.iso2 === secondCountry.iso2
        && firstCountry.dialCode === secondCountry.dialCode;
    }

    getOptionIndexFromEventTarget(target) {
      const option = target.closest(".phone-country-option");
      if (!option) {
        return -1;
      }

      const index = Number(option.dataset.index);
      return Number.isNaN(index) ? -1 : index;
    }

    getOptionId(index) {
      return `${this.optionIdPrefix}-${index}`;
    }

    renderList() {
      this.list.innerHTML = "";

      if (this.filteredCountries.length === 0) {
        const emptyNode = document.createElement("li");
        emptyNode.className = "phone-country-empty";
        emptyNode.textContent = "No matching country or region";
        this.list.appendChild(emptyNode);
        this.list.removeAttribute("aria-activedescendant");
        return;
      }

      this.filteredCountries.forEach((country, index) => {
        const option = document.createElement("li");
        option.className = "phone-country-option";
        option.dataset.index = String(index);
        option.id = this.getOptionId(index);
        option.setAttribute("role", "option");
        option.setAttribute("aria-selected", String(this.isSameCountry(country, this.selectedCountry)));

        if (index === this.highlightedIndex) {
          option.classList.add("is-highlighted");
        }

        const main = document.createElement("span");
        main.className = "phone-country-option-main";

        const flag = document.createElement("span");
        flag.className = `fi fi-${country.iso2}`;

        const name = document.createElement("span");
        name.className = "phone-country-option-name";
        name.textContent = country.name;

        const code = document.createElement("span");
        code.className = "phone-country-option-code";
        code.textContent = country.dialCode;

        main.append(flag, name);
        option.append(main, code);
        this.list.appendChild(option);
      });

      if (this.highlightedIndex >= 0) {
        this.list.setAttribute("aria-activedescendant", this.getOptionId(this.highlightedIndex));
        return;
      }

      this.list.removeAttribute("aria-activedescendant");
    }

    moveHighlight(step) {
      if (this.filteredCountries.length === 0) {
        return;
      }

      if (this.highlightedIndex < 0) {
        this.highlightedIndex = 0;
      } else {
        this.highlightedIndex = (this.highlightedIndex + step + this.filteredCountries.length) % this.filteredCountries.length;
      }

      this.renderList();

      const targetNode = document.getElementById(this.getOptionId(this.highlightedIndex));
      if (targetNode) {
        targetNode.scrollIntoView({ block: "nearest" });
      }
    }

    updateTrigger(country) {
      this.hiddenInput.value = country.dialCode;
      this.triggerFlag.className = `fi fi-${country.iso2}`;
      this.triggerName.textContent = country.name;
      this.triggerCode.textContent = country.dialCode;
    }

    select(country, options = {}) {
      const { silent = false } = options;
      if (!country) {
        return;
      }

      this.selectedCountry = country;
      this.updateTrigger(country);
      this.applyFilter(this.searchInput.value || "");

      if (!silent) {
        this.hiddenInput.dispatchEvent(new Event("change", { bubbles: true }));
      }

      this.close();
    }

    setDialCode(dialCode, options = {}) {
      const normalized = normalizeDialCode(dialCode);
      if (!normalized) {
        return false;
      }

      const exactMatch = this.allCountries.find((country) => country.dialCode === normalized);
      if (exactMatch) {
        this.select(exactMatch, options);
        return true;
      }

      if (normalized.startsWith("+1")) {
        const northAmerica = this.allCountries.find((country) => country.dialCode === "+1");
        if (northAmerica) {
          this.select(northAmerica, options);
          return true;
        }
      }

      return false;
    }

    setCountryIso2(iso2, options = {}) {
      const normalizedIso2 = normalizeCountryCode(iso2);
      if (!normalizedIso2) {
        return false;
      }
      const matchedCountry = this.allCountries.find((country) => country.iso2 === normalizedIso2);
      if (!matchedCountry) {
        return false;
      }
      this.select(matchedCountry, options);
      return true;
    }

    setPreferredIso2(iso2) {
      this.preferredIso2 = normalizeCountryCode(iso2);
      if (!this.preferredIso2) {
        return false;
      }
      return this.setCountryIso2(this.preferredIso2, { silent: true });
    }

    isOpen() {
      return this.container.dataset.open === "true";
    }

    open() {
      this.container.dataset.open = "true";
      this.trigger.setAttribute("aria-expanded", "true");
      this.popover.hidden = false;
      this.applyFilter(this.searchInput.value || "");
      this.searchInput.focus();
    }

    close() {
      this.container.dataset.open = "false";
      this.trigger.setAttribute("aria-expanded", "false");
      this.popover.hidden = true;
      this.searchInput.value = "";
      this.applyFilter("");
    }

    toggle() {
      if (this.isOpen()) {
        this.close();
        return;
      }
      this.open();
    }
  }

  function setPhoneCountryCode(dialCode) {
    if (!phoneCountryPicker || !dialCode) {
      return false;
    }
    return phoneCountryPicker.setDialCode(dialCode);
  }

  function setRegisterPhoneRequiredCountryCode(dialCode) {
    if (!registerPhoneRequiredCountryPicker || !dialCode) {
      return false;
    }
    return registerPhoneRequiredCountryPicker.setDialCode(dialCode);
  }

  function setCountryCodeForAvailablePickers(dialCode) {
    let updated = false;
    if (setPhoneCountryCode(dialCode)) {
      updated = true;
    }
    if (setRegisterPhoneRequiredCountryCode(dialCode)) {
      updated = true;
    }
    return updated;
  }

  function setCountryIso2ForAvailablePickers(iso2) {
    let updated = false;
    if (phoneCountryPicker?.setPreferredIso2(iso2)) {
      updated = true;
    }
    if (registerPhoneRequiredCountryPicker?.setPreferredIso2(iso2)) {
      updated = true;
    }
    return updated;
  }

  function initPhoneCountryPicker() {
    phoneCountryPicker = new CountryPicker({
      containerId: "phone-country-picker",
      hiddenInputId: "phone-country-code",
      triggerId: "phone-country-trigger",
      popoverId: "phone-country-popover",
      searchInputId: "phone-country-search",
      listId: "phone-country-list",
      triggerFlagId: "phone-country-flag",
      triggerNameId: "phone-country-name",
      triggerCodeId: "phone-country-code-label",
      optionIdPrefix: "phone-country-option"
    });
    return phoneCountryPicker.init();
  }

  function initRegisterPhoneRequiredCountryPicker() {
    registerPhoneRequiredCountryPicker = new CountryPicker({
      containerId: "register-phone-country-picker",
      hiddenInputId: "register-phone-country-code",
      triggerId: "register-phone-country-trigger",
      popoverId: "register-phone-country-popover",
      searchInputId: "register-phone-country-search",
      listId: "register-phone-country-list",
      triggerFlagId: "register-phone-country-flag",
      triggerNameId: "register-phone-country-name",
      triggerCodeId: "register-phone-country-code-label",
      optionIdPrefix: "register-phone-country-option"
    });
    return registerPhoneRequiredCountryPicker.init();
  }

  async function fetchDetectedCountryCodeFromBackend() {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 2500);

    try {
      const response = await fetch(PHONE_COUNTRY_API, {
        method: "GET",
        cache: "no-store",
        credentials: "same-origin",
        signal: controller.signal
      });

      if (!response.ok) {
        return "";
      }

      const payload = await response.json();
      return normalizeCountryCode(payload?.country || "");
    } catch (_) {
      return "";
    } finally {
      clearTimeout(timeoutId);
    }
  }

  async function autoDetectPhoneCountryCode() {
    const detectedCountryCode = await fetchDetectedCountryCodeFromBackend();
    if (!detectedCountryCode) {
      return;
    }

    setCountryIso2ForAvailablePickers(detectedCountryCode);
  }

  return {
    DEFAULT_PHONE_COUNTRY_CODE,
    initPhoneCountryPicker,
    initRegisterPhoneRequiredCountryPicker,
    autoDetectPhoneCountryCode
  };
});
