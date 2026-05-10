(function () {
  const api = window.AdminApi;
  const transition = window.AdminParticleTransition;
  const form = document.getElementById("admin-firstlogin-form");
  const usernameInput = document.getElementById("admin-firstlogin-username");
  const phoneInput = document.getElementById("admin-firstlogin-phone");
  const emailInput = document.getElementById("admin-firstlogin-email");
  const codeInput = document.getElementById("admin-firstlogin-code");
  const passwordInput = document.getElementById("admin-firstlogin-password");
  const confirmInput = document.getElementById("admin-firstlogin-confirm");
  const sendCodeButton = document.getElementById("admin-firstlogin-send-code");
  const submitButton = document.getElementById("admin-firstlogin-submit");
  const statusNode = document.getElementById("admin-firstlogin-status");

  let cooldownTimer = null;

  transition?.prewarm?.(form);
  const enterPromise = transition?.playEnter?.(document.querySelectorAll("[data-admin-target]"));
  enterPromise?.finally?.(() => transition?.prewarm?.(form));

  function setBusy(button, busy) {
    if (button) {
      button.disabled = Boolean(busy);
    }
  }

  let captureRefreshTimer = 0;

  function refreshCaptureSoon() {
    window.clearTimeout(captureRefreshTimer);
    captureRefreshTimer = window.setTimeout(() => {
      transition?.prewarm?.(form, { forceCapture: true });
    }, 650);
  }

  [usernameInput, phoneInput, emailInput, codeInput, passwordInput, confirmInput].forEach((input) => {
    input?.addEventListener("input", refreshCaptureSoon);
  });

  function startCooldown(seconds) {
    clearInterval(cooldownTimer);
    let remaining = Math.max(1, Math.round(Number(seconds) || 60));
    sendCodeButton.disabled = true;
    sendCodeButton.textContent = `${remaining}s`;
    cooldownTimer = setInterval(() => {
      remaining -= 1;
      if (remaining <= 0) {
        clearInterval(cooldownTimer);
        sendCodeButton.disabled = false;
        sendCodeButton.textContent = "获取验证码";
        return;
      }
      sendCodeButton.textContent = `${remaining}s`;
    }, 1000);
  }

  function validateBeforeSubmit() {
    if (passwordInput.value !== confirmInput.value) {
      api.setStatus(statusNode, "两次输入的密码不一致。", "error");
      return false;
    }
    if (!/^\d{6}$/.test(codeInput.value.trim())) {
      api.setStatus(statusNode, "请输入 6 位数字邮箱验证码。", "error");
      return false;
    }
    return true;
  }

  sendCodeButton?.addEventListener("click", async () => {
    const email = emailInput.value.trim();
    if (!email) {
      api.setStatus(statusNode, "请输入邮箱地址。", "error");
      return;
    }
    setBusy(sendCodeButton, true);
    api.setStatus(statusNode, "正在发送验证码...");
    try {
      const response = await api.request("/shopping/admin/firstlogin/email-code", { email });
      api.setStatus(statusNode, "验证码已发送，请检查邮箱。", "ok");
      startCooldown(response.data?.cooldownSeconds || 60);
    } catch (error) {
      api.setStatus(statusNode, error.message, "error");
      setBusy(sendCodeButton, false);
    }
  });

  form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!validateBeforeSubmit()) {
      return;
    }
    setBusy(submitButton, true);
    transition?.prewarm?.(form);
    api.setStatus(statusNode, "正在完成初始化...");
    try {
      const response = await api.request("/shopping/admin/firstlogin/complete", {
        username: usernameInput.value.trim(),
        phone: phoneInput.value.trim(),
        email: emailInput.value.trim(),
        password: passwordInput.value,
        emailCode: codeInput.value.trim()
      });
      api.setStatus(statusNode, "初始化成功。", "ok");
      const redirectPath = response.data?.redirectPath || "/shopping/admin/login";
      if (transition?.beginExit) {
        await transition.beginExit({ source: form, to: redirectPath });
        return;
      }
      window.location.assign(redirectPath);
    } catch (error) {
      api.setStatus(statusNode, error.message, "error");
      setBusy(submitButton, false);
    }
  });
})();
