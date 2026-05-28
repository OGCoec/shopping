(function () {
  const api = window.AdminApi;
  const dom = window.AdminDom;
  const modal = window.AdminModal;
  const router = window.AdminRouter;
  const transition = window.AdminParticleTransition;
  const accountNode = document.getElementById("admin-console-account");
  const logoutButton = document.getElementById("admin-console-logout");
  const transitionSource = document.querySelector(".admin-split-console") || document.querySelector(".admin-main");
  const NETWORK_CHECK_ERROR_CODES = new Set(["WEBRTC_SIGNAL_REQUIRED", "WEBRTC_IP_MISMATCH"]);
  let currentUser = {};
  window.__ADMIN_CONSOLE_JS_VERSION__ = "modular-v32";

  function redirectToLogin() {
    window.location.replace("/shopping/admin/login");
  }

  function isNetworkCheckError(error) {
    const status = Number(error?.status || error?.payload?.status || 0);
    const code = String(error?.payload?.error || error?.payload?.code || "");
    return status === 403 && NETWORK_CHECK_ERROR_CODES.has(code);
  }

  function revealConsole() {
    document.documentElement.classList.remove("admin-session-checking");
  }

  function renderSession(user = currentUser) {
    dom.setText(accountNode, user.username || "管理员");
    dom.setText(document.getElementById("admin-console-email"), user.email || "-");
    dom.setText(document.getElementById("admin-console-phone"), user.phone || "-");
  }

  function hydrateInteractiveCards(root = document) {
    root.querySelectorAll?.(".admin-console-card").forEach((card) => {
      card.setAttribute("tabindex", "0");
      card.setAttribute("role", "button");
    });
  }

  function bindSectionLifecycle() {
    Object.keys(window.AdminSections?.sections || {}).forEach((sectionName) => {
      router.register(sectionName, () => {
        hydrateInteractiveCards(router.getLoadedPanel(sectionName) || document);
      });
    });
    router.register("overview", () => renderSession());
  }

  function bindDelegatedNavigation() {
    document.addEventListener("click", async (event) => {
      const trigger = event.target.closest?.("[data-section-target]");
      if (trigger && document.contains(trigger)) {
        event.preventDefault();
        dom.playPress(trigger);
        const family = trigger.dataset.shortcutFamily;
        const level = trigger.dataset.shortcutLevel;
        if (family && level) {
          window.AdminRiskIpScoreModule?.presetLevel(family, level);
        }
        await router.switchSection(trigger.dataset.sectionTarget);
        return;
      }

      const card = event.target.closest?.(".admin-console-card");
      if (!card || !document.contains(card) || card.dataset.sectionTarget) {
        return;
      }
      dom.playPress(card);
      modal.openDetail(card);
    });

    document.addEventListener("keydown", async (event) => {
      if (event.key !== "Enter" && event.key !== " ") {
        return;
      }
      const card = event.target.closest?.(".admin-console-card");
      if (!card || !document.contains(card)) {
        return;
      }
      event.preventDefault();
      dom.playPress(card);
      if (card.dataset.sectionTarget) {
        await router.switchSection(card.dataset.sectionTarget);
        return;
      }
      modal.openDetail(card);
    });
  }

  function bindSpringButtons() {
    const setPressing = (event, pressing) => {
      const button = event.target.closest?.(".admin-spring-button, .admin-side-item");
      if (button && document.contains(button)) {
        button.classList.toggle("is-pressing", pressing);
      }
    };
    document.addEventListener("pointerdown", (event) => setPressing(event, true));
    document.addEventListener("pointerup", (event) => setPressing(event, false));
    document.addEventListener("pointerleave", (event) => setPressing(event, false), true);
    document.addEventListener("pointercancel", (event) => setPressing(event, false));
  }

  function bindGlobalKeys() {
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        window.AdminIp2LocationMailToolModule?.setOpen(false);
        modal.closeDetail();
      }
    });
  }

  function bindLogout() {
    logoutButton?.addEventListener("click", async () => {
      logoutButton.disabled = true;
      transition?.prewarm?.(transitionSource);
      try {
        const response = await api.request("/shopping/admin/logout", {});
        const redirectPath = response.data?.redirectPath || "/shopping/admin/login";
        if (transition?.beginExit) {
          await transition.beginExit({ source: transitionSource, to: redirectPath });
          return;
        }
        window.location.replace(redirectPath);
      } catch (_) {
        logoutButton.disabled = false;
      }
    });
  }

  async function loadSession() {
    try {
      const response = await api.get("/shopping/admin/session/me");
      const user = response.data || {};
      if (!user.authenticated) {
        redirectToLogin();
        return false;
      }
      currentUser = user;
      renderSession(user);
      return true;
    } catch (error) {
      if (isNetworkCheckError(error)) {
        return false;
      }
      redirectToLogin();
      return false;
    }
  }

  async function initializeRouting() {
    const initialSection = router.getSectionFromLocation();
    window.history?.replaceState?.({ adminSection: initialSection }, "", window.location.href);
    await router.switchSection(initialSection, { replaceUrl: true });
  }

  function playInitialTransition() {
    transition?.prewarm?.(transitionSource);
    const enterPromise = transition?.playEnter?.(document.querySelectorAll("[data-admin-target]"));
    enterPromise?.finally?.(() => transition?.prewarm?.(transitionSource));
  }

  async function boot() {
    const sessionLoaded = await loadSession();
    if (!sessionLoaded) {
      return;
    }
    window.AdminIp2LocationMailToolModule?.mount();
    bindSectionLifecycle();
    bindDelegatedNavigation();
    bindSpringButtons();
    bindGlobalKeys();
    bindLogout();
    await initializeRouting();
    revealConsole();
    playInitialTransition();
  }

  boot().catch(() => {
    redirectToLogin();
  });
})();
