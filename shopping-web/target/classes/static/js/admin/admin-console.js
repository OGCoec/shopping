(function () {
  const api = window.AdminApi;
  const dom = window.AdminDom;
  const modal = window.AdminModal;
  const router = window.AdminRouter;
  const transition = window.AdminParticleTransition;
  const accountNode = document.getElementById("admin-console-account");
  const emailNode = document.getElementById("admin-console-email");
  const phoneNode = document.getElementById("admin-console-phone");
  const logoutButton = document.getElementById("admin-console-logout");
  const transitionSource = document.querySelector(".admin-split-console") || document.querySelector(".admin-main");
  window.__ADMIN_CONSOLE_JS_VERSION__ = "modular-v29";

  function redirectToLogin() {
    window.location.replace("/shopping/admin/login");
  }

  function revealConsole() {
    document.documentElement.classList.remove("admin-session-checking");
  }

  function mountBusinessModules() {
    window.AdminOAuthConfigModule?.mount();
    window.AdminCaptchaConfigModule?.mount();
    window.AdminSmtpConfigModule?.mount();
    window.AdminSmsConfigModule?.mount();
    window.AdminOssConfigModule?.mount();
    window.AdminRiskApiConfigModule?.mount();
    window.AdminIp2LocationQuotaKeysModule?.mount();
    window.AdminIp2LocationMailToolModule?.mount();
    window.AdminRiskIpScoreModule?.mount();
    window.AdminRiskDeviceScoreModule?.mount();
  }

  function bindRiskShortcuts() {
    document.querySelectorAll("[data-shortcut-family][data-shortcut-level]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const family = btn.dataset.shortcutFamily;
        const level = btn.dataset.shortcutLevel;
        if (family && level) {
          window.AdminRiskIpScoreModule?.presetLevel(family, level);
        }
      });
    });
  }

  function bindSectionNavigation() {
    document.querySelectorAll("[data-section-target]").forEach((trigger) => {
      trigger.addEventListener("click", () => {
        dom.playPress(trigger);
        router.switchSection(trigger.dataset.sectionTarget);
      });
    });
  }

  function bindCardInteractions() {
    document.querySelectorAll(".admin-console-card").forEach((card) => {
      card.setAttribute("tabindex", "0");
      card.setAttribute("role", "button");
      card.addEventListener("click", () => {
        if (card.dataset.sectionTarget) {
          return;
        }
        dom.playPress(card);
        modal.openDetail(card);
      });
      card.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          dom.playPress(card);
          if (card.dataset.sectionTarget) {
            router.switchSection(card.dataset.sectionTarget);
            return;
          }
          modal.openDetail(card);
        }
      });
    });
  }

  function bindSpringButtons() {
    document.querySelectorAll(".admin-spring-button, .admin-side-item").forEach((button) => {
      button.addEventListener("pointerdown", () => button.classList.add("is-pressing"));
      button.addEventListener("pointerup", () => button.classList.remove("is-pressing"));
      button.addEventListener("pointerleave", () => button.classList.remove("is-pressing"));
      button.addEventListener("pointercancel", () => button.classList.remove("is-pressing"));
    });
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
      dom.setText(accountNode, user.username || "管理员");
      dom.setText(emailNode, user.email || "-");
      dom.setText(phoneNode, user.phone || "-");
      return true;
    } catch (_) {
      redirectToLogin();
      return false;
    }
  }

  function initializeRouting() {
    const initialSection = router.getSectionFromLocation();
    window.history?.replaceState?.({ adminSection: initialSection }, "", window.location.href);
    router.switchSection(initialSection, { replaceUrl: true });
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
    mountBusinessModules();
    bindRiskShortcuts();
    bindSectionNavigation();
    bindCardInteractions();
    bindSpringButtons();
    bindGlobalKeys();
    bindLogout();
    initializeRouting();
    revealConsole();
    playInitialTransition();
  }

  boot().catch(() => {
    redirectToLogin();
  });
})();
