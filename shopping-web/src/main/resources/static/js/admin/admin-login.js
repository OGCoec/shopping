(function () {
  const api = window.AdminApi;
  const transition = window.AdminParticleTransition;
  const form = document.getElementById("admin-login-form");
  const identifierInput = document.getElementById("admin-login-identifier");
  const passwordInput = document.getElementById("admin-login-password");
  const submitButton = document.getElementById("admin-login-submit");
  const statusNode = document.getElementById("admin-login-status");

  transition?.prewarm?.(form);
  const enterPromise = transition?.playEnter?.(document.querySelectorAll("[data-admin-target]"));
  enterPromise?.finally?.(() => transition?.prewarm?.(form));

  let captureRefreshTimer = 0;

  function refreshCaptureSoon() {
    window.clearTimeout(captureRefreshTimer);
    captureRefreshTimer = window.setTimeout(() => {
      transition?.prewarm?.(form, { forceCapture: true });
    }, 650);
  }

  identifierInput?.addEventListener("input", refreshCaptureSoon);
  passwordInput?.addEventListener("input", refreshCaptureSoon);

  const CONSOLE_BASE_PATH = "/shopping/admin/console";
  const RETURN_TO_MAX_LENGTH = 512;

  function isAllowedConsoleReturnTo(value) {
    if (typeof value !== "string" || value.length === 0) {
      return false;
    }
    if (value.length > RETURN_TO_MAX_LENGTH) {
      return false;
    }
    if (!value.startsWith("/") || value.startsWith("//")) {
      return false;
    }
    const queryIndex = value.indexOf("?");
    const path = queryIndex >= 0 ? value.slice(0, queryIndex) : value;
    return path === CONSOLE_BASE_PATH || path.startsWith(CONSOLE_BASE_PATH + "/");
  }

  function resolveReturnTo() {
    try {
      const value = new URLSearchParams(window.location.search).get("returnTo");
      return isAllowedConsoleReturnTo(value) ? value : "";
    } catch (_) {
      return "";
    }
  }

  form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    submitButton.disabled = true;
    transition?.prewarm?.(form);
    api.setStatus(statusNode, "正在登录...");
    try {
      const encryptedPassword = await api.encryptPassword(passwordInput.value);
      const response = await api.request("/shopping/admin/login", {
        identifier: identifierInput.value.trim(),
        ...encryptedPassword
      });
      api.setStatus(statusNode, "登录成功。", "ok");
      const returnTo = resolveReturnTo();
      const redirectPath = returnTo || response.data?.redirectPath || "/shopping/admin/console";
      if (transition?.beginExit) {
        await transition.beginExit({ source: form, to: redirectPath });
        return;
      }
      window.location.assign(redirectPath);
    } catch (error) {
      if (error.payload?.code === "ADMIN_NOT_INITIALIZED") {
        window.location.assign("/shopping/admin/firstlogin");
        return;
      }
      api.setStatus(statusNode, error.message, "error");
      submitButton.disabled = false;
    }
  });
})();
