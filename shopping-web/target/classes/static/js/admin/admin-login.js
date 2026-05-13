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
      const redirectPath = response.data?.redirectPath || "/shopping/admin/console";
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
