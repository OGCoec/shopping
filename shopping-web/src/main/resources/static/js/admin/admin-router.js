(function (root) {
  const dom = root.AdminDom;
  const modal = root.AdminModal;
  const registry = root.AdminSections;
  const eyebrowNode = document.getElementById("admin-section-eyebrow");
  const titleNode = document.getElementById("admin-section-title");
  const copyNode = document.getElementById("admin-section-copy");
  const enterCallbacks = {};
  const loadedPanels = {};
  const loadingPanels = {};
  const mountedModules = {};
  let currentSection = "";
  let switchToken = 0;

  const defaultSection = registry?.defaultSection || "overview";
  const consoleBasePath = registry?.consoleBasePath || "/shopping/admin/console";

  function outlet() {
    return document.getElementById(registry?.panelOutletId || "admin-panel-outlet");
  }

  function getMeta(sectionName) {
    return registry?.get?.(sectionName) || registry?.sections?.[sectionName] || null;
  }

  function register(sectionName, callback) {
    if (!enterCallbacks[sectionName]) {
      enterCallbacks[sectionName] = [];
    }
    enterCallbacks[sectionName].push(callback);
  }

  function buildPanel(sectionName, html) {
    const template = document.createElement("template");
    template.innerHTML = String(html || "").trim();
    const panel = template.content.firstElementChild;
    if (!panel || panel.dataset.adminPanel !== sectionName) {
      throw new Error(`Admin panel fragment mismatch: ${sectionName}`);
    }
    panel.classList.remove("is-active");
    return panel;
  }

  async function ensurePanel(sectionName) {
    if (loadedPanels[sectionName]) {
      return loadedPanels[sectionName];
    }
    if (loadingPanels[sectionName]) {
      return loadingPanels[sectionName];
    }

    const meta = getMeta(sectionName);
    const target = outlet();
    if (!meta?.fragment || !target) {
      throw new Error(`Admin panel is not configured: ${sectionName}`);
    }

    loadingPanels[sectionName] = fetch(meta.fragment, {
      credentials: "same-origin",
      headers: { "X-Requested-With": "XMLHttpRequest" }
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Failed to load admin panel ${sectionName}: ${response.status}`);
        }
        return response.text();
      })
      .then((html) => {
        const panel = buildPanel(sectionName, html);
        target.append(panel);
        loadedPanels[sectionName] = panel;
        return panel;
      })
      .finally(() => {
        delete loadingPanels[sectionName];
      });

    return loadingPanels[sectionName];
  }

  async function ensureModuleFragments(meta) {
    const modules = meta?.modules || (meta?.module ? [meta.module] : []);
    if (!modules.length) {
      return;
    }
    const sectionNames = registry?.getSectionsForModules?.(modules) || [];
    await Promise.all(sectionNames.map((sectionName) => ensurePanel(sectionName)));
  }

  function mountModule(moduleName, panel) {
    if (!moduleName || mountedModules[moduleName]) {
      return;
    }
    const moduleApi = root[moduleName];
    if (!moduleApi?.mount) {
      console.warn("Admin module is not available:", moduleName);
      return;
    }
    moduleApi.mount(panel);
    mountedModules[moduleName] = true;
  }

  async function ensureSectionReady(sectionName) {
    const meta = getMeta(sectionName);
    if (!meta) {
      return null;
    }
    const panel = await ensurePanel(sectionName);
    await ensureModuleFragments(meta);
    (meta.modules || (meta.module ? [meta.module] : [])).forEach((moduleName) => {
      mountModule(moduleName, panel);
    });
    return panel;
  }

  function getSectionFromLocation() {
    const normalizedPath = window.location.pathname.replace(/\/+$/, "");
    if (normalizedPath === consoleBasePath) {
      return defaultSection;
    }
    if (normalizedPath.startsWith(consoleBasePath + "/")) {
      const routeValue = decodeURIComponent(normalizedPath.slice(consoleBasePath.length + 1));
      if (routeValue === "products" || routeValue.startsWith("products/")) {
        return "products";
      }
      return registry?.routeSectionMap?.[routeValue] || defaultSection;
    }
    const params = new URLSearchParams(window.location.search);
    const routeValue = params.get("section");
    return registry?.routeSectionMap?.[routeValue] || defaultSection;
  }

  function updateSectionUrl(sectionName, replace) {
    if (!window.history?.pushState) {
      return;
    }
    const routeValue = registry?.sectionRouteMap?.[sectionName];
    if (!routeValue) {
      return;
    }
    if (sectionName === "products" && replace && window.location.pathname.startsWith(`${consoleBasePath}/products/`)) {
      return;
    }
    const url = new URL(window.location.href);
    url.pathname = `${consoleBasePath}/${routeValue}`;
    url.searchParams.delete("section");

    const nextUrl = url.pathname + url.search + url.hash;
    const currentUrl = window.location.pathname + window.location.search + window.location.hash;
    if (nextUrl === currentUrl) {
      return;
    }

    const method = replace ? "replaceState" : "pushState";
    window.history[method]({ adminSection: sectionName }, "", nextUrl);
  }

  function updateChrome(sectionName, meta) {
    const navSection = meta.navTarget || (sectionName === "overview" ? "overview" : "externalInterfaces");
    document.querySelectorAll(".admin-side-item").forEach((item) => {
      item.classList.toggle("is-active", item.dataset.sectionTarget === navSection);
    });
    outlet()?.querySelectorAll(".admin-panel").forEach((panel) => {
      panel.classList.toggle("is-active", panel.dataset.adminPanel === sectionName);
    });
    dom.setText(eyebrowNode, meta.eyebrow);
    dom.setText(titleNode, meta.title);
    dom.setText(copyNode, meta.copy);
    document.title = `${meta.title} - Shopping System`;
    modal.closeDetail();
  }

  function runEnterCallbacks(sectionName) {
    const callbacks = enterCallbacks[sectionName];
    if (!callbacks) {
      return;
    }
    callbacks.forEach((callback) => {
      try {
        const result = callback();
        result?.catch?.((error) => console.error("Admin section callback failed:", error));
      } catch (error) {
        console.error("Admin section callback failed:", error);
      }
    });
  }

  async function switchSection(sectionName, options = {}) {
    const meta = getMeta(sectionName);
    if (!meta) {
      console.warn("Unknown admin console section:", sectionName);
      return false;
    }
    const token = ++switchToken;
    try {
      await ensureSectionReady(sectionName);
      if (token !== switchToken) {
        return false;
      }
      updateChrome(sectionName, meta);
      currentSection = sectionName;
      if (options.updateUrl !== false) {
        updateSectionUrl(sectionName, Boolean(options.replaceUrl));
      }
      runEnterCallbacks(sectionName);
      return true;
    } catch (error) {
      console.error(error);
      dom.setText(copyNode, "控制台面板加载失败，请刷新后重试。");
      return false;
    }
  }

  window.addEventListener("popstate", () => {
    switchSection(getSectionFromLocation(), { updateUrl: false });
  });

  root.AdminRouter = {
    switchSection,
    register,
    getSectionFromLocation,
    getCurrentSection: () => currentSection,
    getLoadedPanel: (sectionName) => loadedPanels[sectionName] || null,
    sectionMeta: registry?.sections || {},
    defaultSection
  };
})(window);
