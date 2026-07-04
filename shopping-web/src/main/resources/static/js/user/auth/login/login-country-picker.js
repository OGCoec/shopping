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
  let securityPhoneCountryPicker = null;
  let detectedPhoneCountryIso2 = "";

  const LOCAL_COUNTRY_DIAL_CODE_ROWS = [
    "ac|+247", "ad|+376", "ae|+971", "af|+93", "ag|+1268", "ai|+1264", "al|+355", "am|+374",
    "ao|+244", "ar|+54", "as|+1684", "at|+43", "au|+61", "aw|+297", "ax|+358", "az|+994",
    "ba|+387", "bb|+1246", "bd|+880", "be|+32", "bf|+226", "bg|+359", "bh|+973", "bi|+257",
    "bj|+229", "bl|+590", "bm|+1441", "bn|+673", "bo|+591", "bq|+599", "br|+55", "bs|+1242",
    "bt|+975", "bw|+267", "by|+375", "bz|+501", "ca|+1", "cc|+61", "cd|+243", "cf|+236",
    "cg|+242", "ch|+41", "ci|+225", "ck|+682", "cl|+56", "cm|+237", "cn|+86", "co|+57",
    "cr|+506", "cu|+53", "cv|+238", "cw|+599", "cx|+61", "cy|+357", "cz|+420", "de|+49",
    "dj|+253", "dk|+45", "dm|+1767", "do|+1809", "do|+1829", "do|+1849", "dz|+213", "ec|+593", "ee|+372", "eg|+20",
    "eh|+212", "er|+291", "es|+34", "et|+251", "fi|+358", "fj|+679", "fk|+500", "fm|+691",
    "fo|+298", "fr|+33", "ga|+241", "gb|+44", "gd|+1473", "ge|+995", "gf|+594", "gg|+44",
    "gh|+233", "gi|+350", "gl|+299", "gm|+220", "gn|+224", "gp|+590", "gq|+240", "gr|+30",
    "gt|+502", "gu|+1671", "gw|+245", "gy|+592", "hk|+852", "hn|+504", "hr|+385", "ht|+509",
    "hu|+36", "id|+62", "ie|+353", "il|+972", "im|+44", "in|+91", "io|+246", "iq|+964",
    "ir|+98", "is|+354", "it|+39", "je|+44", "jm|+1658", "jm|+1876", "jo|+962", "jp|+81", "ke|+254",
    "kg|+996", "kh|+855", "ki|+686", "km|+269", "kn|+1869", "kp|+850", "kr|+82", "kw|+965",
    "ky|+1345", "kz|+7", "la|+856", "lb|+961", "lc|+1758", "li|+423", "lk|+94", "lr|+231",
    "ls|+266", "lt|+370", "lu|+352", "lv|+371", "ly|+218", "ma|+212", "mc|+377", "md|+373",
    "me|+382", "mf|+590", "mg|+261", "mh|+692", "mk|+389", "ml|+223", "mm|+95", "mn|+976",
    "mo|+853", "mp|+1670", "mq|+596", "mr|+222", "ms|+1664", "mt|+356", "mu|+230", "mv|+960",
    "mw|+265", "mx|+52", "my|+60", "mz|+258", "na|+264", "nc|+687", "ne|+227", "nf|+672",
    "ng|+234", "ni|+505", "nl|+31", "no|+47", "np|+977", "nr|+674", "nu|+683", "nz|+64",
    "om|+968", "pa|+507", "pe|+51", "pf|+689", "pg|+675", "ph|+63", "pk|+92", "pl|+48",
    "pm|+508", "pr|+1787", "pr|+1939", "ps|+970", "pt|+351", "pw|+680", "py|+595", "qa|+974", "re|+262",
    "ro|+40", "rs|+381", "ru|+7", "rw|+250", "sa|+966", "sb|+677", "sc|+248", "sd|+249",
    "se|+46", "sg|+65", "sh|+290", "si|+386", "sj|+47", "sk|+421", "sl|+232", "sm|+378",
    "sn|+221", "so|+252", "sr|+597", "ss|+211", "st|+239", "sv|+503", "sx|+1721", "sy|+963",
    "sz|+268", "ta|+290", "tc|+1649", "td|+235", "tg|+228", "th|+66", "tj|+992", "tk|+690",
    "tl|+670", "tm|+993", "tn|+216", "to|+676", "tr|+90", "tt|+1868", "tv|+688", "tw|+886",
    "tz|+255", "ua|+380", "ug|+256", "us|+1", "uy|+598", "uz|+998", "va|+39", "vc|+1784",
    "ve|+58", "vg|+1284", "vi|+1340", "vn|+84", "vu|+678", "wf|+681", "ws|+685", "xk|+383",
    "ye|+967", "yt|+262", "za|+27", "zm|+260", "zw|+263"
  ];

  const LOCAL_COUNTRY_NAME_OVERRIDES = {
    ac: "Ascension Island",
    hk: "Hong Kong",
    mo: "Macao",
    ta: "Tristan da Cunha",
    xk: "Kosovo"
  };

  function createLocalCountryOptions() {
    const displayNameResolver = createCountryDisplayNameResolver();
    return LOCAL_COUNTRY_DIAL_CODE_ROWS.map((row) => {
      const [iso2, dialCode] = row.split("|");
      const isoRaw = iso2.toUpperCase();
      let name = LOCAL_COUNTRY_NAME_OVERRIDES[iso2] || "";
      if (!name) {
        try {
          name = displayNameResolver?.of(isoRaw) || isoRaw;
        } catch (_) {
          name = isoRaw;
        }
      }
      return { name, iso2, dialCode };
    });
  }

  const FALLBACK_COUNTRIES = createLocalCountryOptions();

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

  function getDialCodeDigits(country) {
    return (country?.dialCode || "").replace(/\D/g, "");
  }

  function compareDigitStringsByPriority(firstDigits, secondDigits) {
    const maxLength = Math.max(firstDigits.length, secondDigits.length);
    for (let index = 0; index < maxLength; index += 1) {
      const firstDigit = firstDigits.charCodeAt(index) || 0;
      const secondDigit = secondDigits.charCodeAt(index) || 0;
      if (firstDigit !== secondDigit) {
        return firstDigit - secondDigit;
      }
    }
    return 0;
  }

  function compareCountriesByDialSearch(firstCountry, secondCountry, queryDigits) {
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
    if (lengthOrder !== 0) {
      return lengthOrder;
    }

    const digitOrder = compareDigitStringsByPriority(firstDigits, secondDigits);
    if (digitOrder !== 0) {
      return digitOrder;
    }

    return compareCountriesByDialCode(firstCountry, secondCountry);
  }

  function createCountryDisplayNameResolver() {
    if (typeof Intl === "undefined" || !Intl.DisplayNames) {
      return null;
    }
    return new Intl.DisplayNames(["en"], { type: "region" });
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
    return normalizeAndSortCountries(FALLBACK_COUNTRIES);
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
      const queryDigits = query.replace(/\D/g, "");

      if (!query) {
        this.filteredCountries = [...this.allCountries];
      } else {
        this.filteredCountries = this.allCountries.filter((country) => this.matchesCountryQuery(country, query));
        if (queryDigits) {
          this.filteredCountries.sort((firstCountry, secondCountry) =>
            compareCountriesByDialSearch(firstCountry, secondCountry, queryDigits));
        }
      }

      const selectedIndex = this.filteredCountries.findIndex((country) => this.isSameCountry(country, this.selectedCountry));
      this.highlightedIndex = selectedIndex >= 0 ? selectedIndex : (this.filteredCountries.length > 0 ? 0 : -1);
      this.renderList();
    }

    matchesCountryQuery(country, query) {
      const queryDigits = query.replace(/\D/g, "");
      if (queryDigits && getDialCodeDigits(country).includes(queryDigits)) {
        return true;
      }

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

    selectCountryForInternationalNumber(rawPhoneNumber, options = {}) {
      const normalizedPhoneNumber = normalizeInternationalPhoneNumber(rawPhoneNumber);
      if (!normalizedPhoneNumber) {
        return null;
      }

      const matchedCountry = findBestCountryForInternationalNumber(this.allCountries, normalizedPhoneNumber);
      if (!matchedCountry) {
        return null;
      }

      this.select(matchedCountry, { silent: Boolean(options.silent) });
      const nationalNumber = stripDialCodeFromInternationalNumber(normalizedPhoneNumber, matchedCountry.dialCode);
      return {
        country: matchedCountry,
        dialCode: matchedCountry.dialCode,
        nationalNumber,
        e164: `${matchedCountry.dialCode}${nationalNumber}`
      };
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

    restorePreferredCountry(options = {}) {
      if (!this.preferredIso2) {
        return false;
      }
      return this.setCountryIso2(this.preferredIso2, options);
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

  function setSecurityPhoneCountryCode(dialCode) {
    if (!securityPhoneCountryPicker || !dialCode) {
      return false;
    }
    return securityPhoneCountryPicker.setDialCode(dialCode);
  }

  function setCountryCodeForAvailablePickers(dialCode) {
    let updated = false;
    if (setPhoneCountryCode(dialCode)) {
      updated = true;
    }
    if (setRegisterPhoneRequiredCountryCode(dialCode)) {
      updated = true;
    }
    if (setSecurityPhoneCountryCode(dialCode)) {
      updated = true;
    }
    return updated;
  }

  function setCountryIso2ForAvailablePickers(iso2) {
    detectedPhoneCountryIso2 = normalizeCountryCode(iso2);
    let updated = false;
    if (phoneCountryPicker?.setPreferredIso2(iso2)) {
      updated = true;
    }
    if (registerPhoneRequiredCountryPicker?.setPreferredIso2(iso2)) {
      updated = true;
    }
    if (securityPhoneCountryPicker?.setPreferredIso2(iso2)) {
      updated = true;
    }
    return updated;
  }

  function restorePreferredCountry(getPicker, options = {}) {
    const picker = getPicker();
    if (!picker) {
      return false;
    }
    if (!picker.preferredIso2 && detectedPhoneCountryIso2) {
      picker.preferredIso2 = detectedPhoneCountryIso2;
    }
    return picker.restorePreferredCountry({
      silent: Boolean(options.silent)
    });
  }

  function normalizeInternationalPhoneNumber(rawPhoneNumber) {
    if (typeof rawPhoneNumber !== "string") {
      return "";
    }
    const trimmed = rawPhoneNumber.trim();
    if (!trimmed.startsWith("+")) {
      return "";
    }
    const compact = `+${trimmed.slice(1).replace(/\D/g, "")}`;
    return /^\+\d{1,17}$/.test(compact) ? compact : "";
  }

  function findBestCountryForInternationalNumber(countries, normalizedPhoneNumber) {
    const matches = (Array.isArray(countries) ? countries : [])
      .filter((country) => {
        const dialCode = normalizeDialCode(country?.dialCode || "");
        return dialCode && normalizedPhoneNumber.startsWith(dialCode);
      })
      .sort((firstCountry, secondCountry) => {
        const lengthOrder = secondCountry.dialCode.length - firstCountry.dialCode.length;
        if (lengthOrder !== 0) {
          return lengthOrder;
        }
        return compareCountriesByDialCode(firstCountry, secondCountry);
      });
    return matches[0] || null;
  }

  function stripDialCodeFromInternationalNumber(normalizedPhoneNumber, dialCode) {
    const normalizedDialCode = normalizeDialCode(dialCode);
    if (!normalizedPhoneNumber || !normalizedDialCode || !normalizedPhoneNumber.startsWith(normalizedDialCode)) {
      return normalizedPhoneNumber.replace(/\D/g, "");
    }
    return normalizedPhoneNumber.slice(normalizedDialCode.length).replace(/\D/g, "");
  }

  function findLeadingDialCodeTokenRange(rawPhoneNumber, getPicker) {
    const value = typeof rawPhoneNumber === "string" ? rawPhoneNumber : "";
    const picker = getPicker();
    const normalizedPhoneNumber = normalizeInternationalPhoneNumber(value);
    if (!picker || !normalizedPhoneNumber) {
      return null;
    }

    const matchedCountry = findBestCountryForInternationalNumber(picker.allCountries, normalizedPhoneNumber);
    const dialDigits = String(matchedCountry?.dialCode || "").replace(/\D/g, "");
    if (!dialDigits) {
      return null;
    }

    const leadingWhitespaceLength = value.match(/^\s*/)?.[0]?.length || 0;
    let index = leadingWhitespaceLength;
    if (value.charAt(index) !== "+") {
      return null;
    }

    index += 1;
    let matchedDigitCount = 0;
    while (index < value.length && matchedDigitCount < dialDigits.length) {
      const char = value.charAt(index);
      if (/\d/.test(char)) {
        if (char !== dialDigits.charAt(matchedDigitCount)) {
          return null;
        }
        matchedDigitCount += 1;
      } else if (!/[\s().-]/.test(char)) {
        return null;
      }
      index += 1;
    }

    if (matchedDigitCount !== dialDigits.length) {
      return null;
    }

    const dialEnd = index;
    while (index < value.length && /\s/.test(value.charAt(index))) {
      index += 1;
    }

    return {
      start: leadingWhitespaceLength,
      dialEnd,
      end: index
    };
  }

  function handleAtomicDialCodeDelete(event, input, getPicker) {
    if (event.key !== "Backspace" && event.key !== "Delete") {
      return false;
    }

    const value = input.value || "";
    const tokenRange = findLeadingDialCodeTokenRange(value, getPicker);
    if (!tokenRange) {
      return false;
    }

    const selectionStart = input.selectionStart ?? 0;
    const selectionEnd = input.selectionEnd ?? selectionStart;
    let deleteStart = tokenRange.start;
    let deleteEnd = tokenRange.end;

    if (selectionStart !== selectionEnd) {
      const intersectsDialCode = selectionStart < tokenRange.dialEnd && selectionEnd > tokenRange.start;
      if (!intersectsDialCode) {
        return false;
      }
      deleteStart = Math.min(selectionStart, tokenRange.start);
      deleteEnd = Math.max(selectionEnd, tokenRange.end);
    } else if (event.key === "Backspace") {
      if (selectionStart <= tokenRange.start || selectionStart > tokenRange.end) {
        return false;
      }
    } else if (selectionStart < tokenRange.start || selectionStart >= tokenRange.end) {
      return false;
    }

    event.preventDefault();
    const nextValue = `${value.slice(0, deleteStart)}${value.slice(deleteEnd)}`;
    input.value = nextValue;
    input.setSelectionRange(deleteStart, deleteStart);

    if (!nextValue.trim()) {
      restorePreferredCountry(getPicker);
    } else if (nextValue.trim().startsWith("+")) {
      applyInternationalPhoneNumber(input.id, getPicker);
    } else {
      restorePreferredCountry(getPicker);
    }

    input.dispatchEvent(new Event("input", { bubbles: true }));
    return true;
  }

  function sanitizePhoneInput(input) {
    const value = input.value || "";
    const hasLeadingPlus = value.trimStart().startsWith("+");
    const sanitized = `${hasLeadingPlus ? "+" : ""}${value.replace(/\D/g, "").slice(0, 15)}`;
    if (value === sanitized) {
      return false;
    }

    const selectionStart = input.selectionStart ?? value.length;
    const originalPrefix = value.slice(0, selectionStart);
    const digitsBeforeCursor = originalPrefix.replace(/\D/g, "").length;
    const plusBeforeCursor = hasLeadingPlus && /\+/.test(originalPrefix);
    input.value = sanitized;
    const nextCursor = Math.min(digitsBeforeCursor + (plusBeforeCursor ? 1 : 0), sanitized.length);
    input.setSelectionRange(nextCursor, nextCursor);
    return true;
  }

  function applyInternationalPhoneNumber(inputId, getPicker, options = {}) {
    const input = document.getElementById(inputId);
    const picker = getPicker();
    if (!picker || !input || !input.value.trim().startsWith("+")) {
      return null;
    }
    const result = picker.selectCountryForInternationalNumber(input.value, { silent: Boolean(options.silent) });
    if (!result) {
      return null;
    }
    if (options.mutateInput === true && result.nationalNumber) {
      input.value = result.nationalNumber;
      input.dispatchEvent(new Event("change", { bubbles: true }));
    }
    return result;
  }

  function resolvePhoneSubmissionParts(inputId, dialCodeInputId, getPicker, options = {}) {
    const input = document.getElementById(inputId);
    const rawValue = input ? input.value.trim() : "";
    const dialCodeInput = document.getElementById(dialCodeInputId);
    const selectedDialCode = (dialCodeInput?.value || "").trim();

    if (rawValue.startsWith("+")) {
      const result = applyInternationalPhoneNumber(inputId, getPicker, {
        silent: Boolean(options.silent)
      });
      if (result) {
        return {
          dialCode: result.dialCode,
          phoneNumber: result.nationalNumber,
          rawPhoneNumber: rawValue,
          isInternational: true
        };
      }
      return {
        dialCode: selectedDialCode,
        phoneNumber: rawValue.replace(/\D/g, ""),
        rawPhoneNumber: rawValue,
        isInternational: true
      };
    }

    return {
      dialCode: selectedDialCode,
      phoneNumber: rawValue.replace(/\D/g, ""),
      rawPhoneNumber: rawValue,
      isInternational: false
    };
  }

  function bindInternationalPhoneInput(inputId, getPicker) {
    const input = document.getElementById(inputId);
    if (!input || input.dataset.internationalPhoneBound === "true") {
      return false;
    }
    input.dataset.internationalPhoneBound = "true";

    input.setAttribute("inputmode", "tel");
    input.setAttribute("pattern", "\\+?[0-9]*");
    input.setAttribute("maxlength", "16");

    input.addEventListener("beforeinput", (event) => {
      const data = event.data || "";
      if (event.inputType !== "insertText" || !data) {
        return;
      }
      const selectionStart = input.selectionStart ?? 0;
      const selectionEnd = input.selectionEnd ?? selectionStart;
      const nextValue = `${input.value.slice(0, selectionStart)}${data}${input.value.slice(selectionEnd)}`;
      if (!/^\+?\d*$/.test(nextValue)) {
        event.preventDefault();
      }
    });
    input.addEventListener("keydown", (event) => {
      handleAtomicDialCodeDelete(event, input, getPicker);
    });
    input.addEventListener("input", () => {
      sanitizePhoneInput(input);
      if (!input.value.trim()) {
        restorePreferredCountry(getPicker);
        return;
      }
      applyInternationalPhoneNumber(inputId, getPicker);
    });
    input.addEventListener("paste", () => {
      setTimeout(() => {
        sanitizePhoneInput(input);
        if (!input.value.trim()) {
          restorePreferredCountry(getPicker);
          return;
        }
        applyInternationalPhoneNumber(inputId, getPicker);
      }, 0);
    });
    return true;
  }

  function bindInternationalPhoneInputs() {
    let bound = false;
    if (bindInternationalPhoneInput("phone-number", () => phoneCountryPicker)) {
      bound = true;
    }
    if (bindInternationalPhoneInput("register-phone-required-input", () => registerPhoneRequiredCountryPicker)) {
      bound = true;
    }
    if (bindInternationalPhoneInput("security-phone-number", () => securityPhoneCountryPicker)) {
      bound = true;
    }
    return bound;
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
    const initialized = phoneCountryPicker.init();
    bindInternationalPhoneInputs();
    return initialized;
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
    const initialized = registerPhoneRequiredCountryPicker.init();
    bindInternationalPhoneInputs();
    return initialized;
  }

  function initSecurityPhoneCountryPicker() {
    securityPhoneCountryPicker = new CountryPicker({
      containerId: "security-phone-country-picker",
      hiddenInputId: "security-phone-country-code",
      triggerId: "security-phone-country-trigger",
      popoverId: "security-phone-country-popover",
      searchInputId: "security-phone-country-search",
      listId: "security-phone-country-list",
      triggerFlagId: "security-phone-country-flag",
      triggerNameId: "security-phone-country-name",
      triggerCodeId: "security-phone-country-code-label",
      optionIdPrefix: "security-phone-country-option"
    });
    const initialized = securityPhoneCountryPicker.init();
    bindInternationalPhoneInputs();
    return initialized;
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

  function applyPhoneInternationalNumber(options = {}) {
    return applyInternationalPhoneNumber("phone-number", () => phoneCountryPicker, options);
  }

  function applyRegisterPhoneRequiredInternationalNumber(options = {}) {
    return applyInternationalPhoneNumber("register-phone-required-input", () => registerPhoneRequiredCountryPicker, options);
  }

  function applySecurityPhoneInternationalNumber(options = {}) {
    return applyInternationalPhoneNumber("security-phone-number", () => securityPhoneCountryPicker, options);
  }

  function resolvePhoneNumberForSubmit(options = {}) {
    return resolvePhoneSubmissionParts(
      "phone-number",
      "phone-country-code",
      () => phoneCountryPicker,
      options
    );
  }

  function resolveRegisterPhoneRequiredForSubmit(options = {}) {
    return resolvePhoneSubmissionParts(
      "register-phone-required-input",
      "register-phone-country-code",
      () => registerPhoneRequiredCountryPicker,
      options
    );
  }

  function resolveSecurityPhoneForSubmit(options = {}) {
    return resolvePhoneSubmissionParts(
      "security-phone-number",
      "security-phone-country-code",
      () => securityPhoneCountryPicker,
      options
    );
  }

  const publicApi = {
    DEFAULT_PHONE_COUNTRY_CODE,
    initPhoneCountryPicker,
    initRegisterPhoneRequiredCountryPicker,
    initSecurityPhoneCountryPicker,
    bindInternationalPhoneInputs,
    autoDetectPhoneCountryCode,
    applyPhoneInternationalNumber,
    applyRegisterPhoneInternationalNumber: applyPhoneInternationalNumber,
    applyRegisterPhoneRequiredInternationalNumber,
    applySecurityPhoneInternationalNumber,
    resolvePhoneNumberForSubmit,
    resolveRegisterPhoneRequiredForSubmit,
    resolveSecurityPhoneForSubmit
  };

  if (typeof module !== "undefined" && module.exports) {
    publicApi.__test__ = {
      normalizeAndSortCountries,
      compareCountriesByDialSearch,
      normalizeInternationalPhoneNumber,
      findBestCountryForInternationalNumber,
      stripDialCodeFromInternationalNumber
    };
  }

  return publicApi;
});
