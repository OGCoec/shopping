(function () {
  const ME_PATH = "/shopping/user/auth/me";
  const AVATAR_PATH = "/shopping/user/profile/avatar";
  const ACCOUNT_DELETION_PATH = "/shopping/user/profile/deletion";
  const LOGIN_PATH = "/shopping/user/log-in";
  const TOTP_STATUS_PATH = "/shopping/user/totp/status";
  const TOTP_SETUP_PATH = "/shopping/user/totp/setup";
  const TOTP_CONFIRM_PATH = "/shopping/user/totp/setup/confirm";
  const authClient = window.ShoppingAuthClient;

  const status = document.getElementById("profile-status");
  const grid = document.getElementById("profile-grid");
  const title = document.getElementById("profile-title");
  const reloadButton = document.getElementById("reload-profile");
  const logoutCurrentButton = document.getElementById("logout-current");
  const logoutAllButton = document.getElementById("logout-all");
  const deleteAccountButton = document.getElementById("delete-account");
  const avatarImage = document.getElementById("profile-avatar-image");
  const avatarFallback = document.getElementById("profile-avatar-fallback");
  const avatarTitle = document.getElementById("avatar-title");
  const avatarUploadTrigger = document.getElementById("avatar-upload-trigger");
  const avatarChangeButton = document.getElementById("avatar-change-button");
  const avatarDeleteButton = document.getElementById("avatar-delete-button");
  const avatarInput = document.getElementById("avatar-file-input");
  const totpStatus = document.getElementById("totp-status");
  const totpActionButton = document.getElementById("totp-action");
  const totpModal = document.getElementById("totp-modal");
  const totpModalTitle = document.getElementById("totp-modal-title");
  const totpCloseButton = document.getElementById("totp-close");
  const totpQr = document.getElementById("totp-qr");
  const totpManualToggle = document.getElementById("totp-manual-toggle");
  const totpManual = document.getElementById("totp-manual");
  const totpSecret = document.getElementById("totp-secret");
  const totpConfirmForm = document.getElementById("totp-confirm-form");
  const totpCode = document.getElementById("totp-code");
  const totpCountdown = document.getElementById("totp-countdown");
  const totpModalStatus = document.getElementById("totp-modal-status");
  const totpConfirmButton = document.getElementById("totp-confirm");
  let hasRenderedProfile = false;
  let currentAvatarUrl = "";
  let totpEnabled = false;
  let totpPeriodSeconds = 30;
  let totpCountdownTimer = null;

  const fields = {
    userId: document.getElementById("field-user-id"),
    username: document.getElementById("field-username"),
    name: document.getElementById("field-name"),
    account: document.getElementById("field-account"),
    email: document.getElementById("field-email"),
    phone: document.getElementById("field-phone"),
    status: document.getElementById("field-status"),
    gender: document.getElementById("field-gender"),
    riskLevel: document.getElementById("field-risk-level"),
    roles: document.getElementById("field-roles")
  };

  function text(value) {
    if (Array.isArray(value)) {
      return value.length ? value.join(", ") : "-";
    }
    const normalized = value === null || value === undefined ? "" : String(value).trim();
    return normalized || "-";
  }

  function fullName(user) {
    return [user?.firstName, user?.lastName]
      .map((item) => text(item))
      .filter((item) => item !== "-")
      .join(" ") || "-";
  }

  function avatarSeed(user) {
    const source = user?.username || user?.account || user?.email || user?.phone || "用户";
    return String(source).trim().slice(0, 2).toUpperCase() || "用户";
  }

  function normalizeAvatarUrl(value) {
    const normalized = value === null || value === undefined ? "" : String(value).trim();
    return /^https?:\/\//i.test(normalized) ? normalized : "";
  }

  function setStatus(message, isError) {
    status.textContent = message;
    status.classList.toggle("error", Boolean(isError));
  }

  function setReloading(isReloading) {
    reloadButton.disabled = isReloading;
    grid.setAttribute("aria-busy", String(isReloading));
  }

  function setLoggingOut(isLoggingOut) {
    logoutCurrentButton.disabled = isLoggingOut;
    logoutAllButton.disabled = isLoggingOut;
    deleteAccountButton.disabled = isLoggingOut;
  }

  function setAvatarWorking(isWorking) {
    avatarUploadTrigger.disabled = isWorking;
    avatarChangeButton.disabled = isWorking;
    avatarDeleteButton.disabled = isWorking;
    avatarInput.disabled = isWorking;
  }

  async function confirmAndLogout(message, action) {
    if (!window.confirm(message)) {
      return;
    }
    setLoggingOut(true);
    try {
      await action();
    } catch (_) {
      setStatus("退出失败，请稍后重试。", true);
      setLoggingOut(false);
    }
  }

  async function submitAccountDeletion() {
    if (!authClient?.fetchWithAuth) {
      setStatus("认证客户端未加载。", true);
      return;
    }
    if (!window.confirm("确定要注销账号吗？账号会先停用，7 天后完成注销。")) {
      return;
    }
    const reason = window.prompt("请输入注销原因：");
    if (reason === null) {
      return;
    }
    const normalizedReason = reason.trim();
    if (!normalizedReason) {
      setStatus("注销原因不能为空。", true);
      return;
    }
    if (!window.confirm("再次确认注销账号？确认后当前账号会立即不可用。")) {
      return;
    }

    setLoggingOut(true);
    setStatus("正在提交注销请求...", false);
    try {
      const response = await authClient.fetchWithAuth(ACCOUNT_DELETION_PATH, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ deletionReason: normalizedReason })
      });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) {
        setStatus(payload?.message || "注销请求提交失败。", true);
        setLoggingOut(false);
        return;
      }
      window.alert("账号注销请求已提交。");
      window.location.assign(LOGIN_PATH);
    } catch (_) {
      setStatus("注销请求提交失败。", true);
      setLoggingOut(false);
    }
  }

  function setTotpModalStatus(message, isError) {
    totpModalStatus.textContent = message || "";
    totpModalStatus.classList.toggle("error", Boolean(isError));
  }

  function updateTotpStatusView(enabled) {
    totpEnabled = Boolean(enabled);
    totpStatus.textContent = totpEnabled ? "已启用身份验证器" : "未启用身份验证器";
    totpActionButton.textContent = totpEnabled ? "修改身份验证器" : "启用身份验证器";
    totpActionButton.disabled = false;
  }

  async function loadTotpStatus() {
    if (!authClient?.fetchWithAuth) {
      return;
    }
    totpActionButton.disabled = true;
    totpStatus.textContent = "正在读取状态...";
    try {
      const response = await authClient.fetchWithAuth(TOTP_STATUS_PATH, { method: "GET" });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) {
        totpStatus.textContent = payload?.message || "身份验证器状态读取失败。";
        totpActionButton.disabled = false;
        return;
      }
      updateTotpStatusView(payload.enabled);
    } catch (_) {
      totpStatus.textContent = "身份验证器状态读取失败。";
      totpActionButton.disabled = false;
    }
  }

  function openTotpModal() {
    totpModal.hidden = false;
    startTotpCountdown();
    setTimeout(() => totpCode.focus(), 0);
  }

  function closeTotpModal() {
    totpModal.hidden = true;
    stopTotpCountdown();
    setTotpModalStatus("", false);
    totpCode.value = "";
  }

  function startTotpCountdown() {
    stopTotpCountdown();
    const update = () => {
      const nowSeconds = Math.floor(Date.now() / 1000);
      const elapsed = nowSeconds % totpPeriodSeconds;
      const remaining = elapsed === 0 ? totpPeriodSeconds : totpPeriodSeconds - elapsed;
      totpCountdown.textContent = `${remaining}s`;
    };
    update();
    totpCountdownTimer = window.setInterval(update, 250);
  }

  function stopTotpCountdown() {
    if (totpCountdownTimer !== null) {
      window.clearInterval(totpCountdownTimer);
      totpCountdownTimer = null;
    }
  }

  async function startTotpSetup() {
    if (!authClient?.fetchWithAuth) {
      setStatus("认证客户端未加载。", true);
      return;
    }
    const wasEnabled = totpEnabled;
    totpActionButton.disabled = true;
    setStatus(wasEnabled ? "正在生成新的身份验证器密钥..." : "正在生成身份验证器密钥...", false);
    try {
      const response = await authClient.fetchWithAuth(TOTP_SETUP_PATH, { method: "POST" });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success || !payload?.secret || !payload?.otpauthUri) {
        setStatus(payload?.message || "身份验证器密钥生成失败。", true);
        return;
      }

      totpPeriodSeconds = Number(payload.periodSeconds) || 30;
      totpModalTitle.textContent = wasEnabled ? "修改身份验证器" : "启用身份验证器";
      totpConfirmButton.textContent = wasEnabled ? "验证并修改" : "验证并启用";
      totpManual.hidden = true;
      totpManualToggle.textContent = "无法扫码？";
      totpSecret.textContent = payload.secret;
      totpCode.value = "";
      setTotpModalStatus("", false);

      try {
        window.ShoppingLocalQr.render(totpQr, payload.otpauthUri, { border: 4, scale: 4 });
      } catch (_) {
        totpQr.textContent = "二维码生成失败，请改用手动密钥。";
        totpManual.hidden = false;
      }
      openTotpModal();
      setStatus(wasEnabled ? "请验证新的身份验证器。" : "请验证身份验证器。", false);
    } catch (_) {
      setStatus("身份验证器密钥生成失败。", true);
    } finally {
      totpActionButton.disabled = false;
    }
  }

  async function confirmTotpSetup(event) {
    event.preventDefault();
    const code = totpCode.value.trim();
    if (!/^\d{6}$/.test(code)) {
      setTotpModalStatus("请输入 6 位数字验证码。", true);
      return;
    }

    totpConfirmButton.disabled = true;
    setTotpModalStatus("正在验证...", false);
    try {
      const response = await authClient.fetchWithAuth(TOTP_CONFIRM_PATH, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code })
      });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) {
        setTotpModalStatus(payload?.message || "验证码不正确。", true);
        return;
      }
      updateTotpStatusView(true);
      setStatus("身份验证器已启用。", false);
      closeTotpModal();
    } catch (_) {
      setTotpModalStatus("验证失败，请稍后重试。", true);
    } finally {
      totpConfirmButton.disabled = false;
    }
  }

  function renderAvatar(user) {
    currentAvatarUrl = normalizeAvatarUrl(user?.avatarUrl);
    avatarFallback.textContent = avatarSeed(user);
    avatarDeleteButton.hidden = !currentAvatarUrl;
    avatarChangeButton.textContent = currentAvatarUrl ? "更换头像" : "选择头像";
    avatarTitle.textContent = currentAvatarUrl ? "已上传头像" : "未上传头像";

    if (currentAvatarUrl) {
      avatarImage.hidden = true;
      avatarFallback.hidden = false;
      avatarImage.src = currentAvatarUrl;
      return;
    }

    avatarImage.hidden = true;
    avatarImage.removeAttribute("src");
    avatarFallback.hidden = false;
  }

  avatarImage?.addEventListener("load", () => {
    if (!currentAvatarUrl) {
      return;
    }
    avatarImage.hidden = false;
    avatarFallback.hidden = true;
  });

  avatarImage?.addEventListener("error", () => {
    avatarImage.hidden = true;
    avatarFallback.hidden = false;
  });

  function render(user) {
    title.textContent = text(user.username || user.account || user.email || user.phone || "用户详情");
    fields.userId.textContent = text(user.userId);
    fields.username.textContent = text(user.username);
    fields.name.textContent = fullName(user);
    fields.account.textContent = text(user.account);
    fields.email.textContent = text(user.email);
    fields.phone.textContent = text(user.phone);
    fields.status.textContent = text(user.status);
    fields.gender.textContent = text(user.gender);
    fields.riskLevel.textContent = text(user.riskLevel);
    fields.roles.textContent = text(user.roles);
    renderAvatar(user);
    grid.hidden = false;
    hasRenderedProfile = true;
  }

  async function loadProfile(options = {}) {
    if (!authClient?.fetchWithAuth) {
      setStatus("认证客户端未加载。", true);
      return { success: false, user: null };
    }
    const firstLoad = !hasRenderedProfile;
    if (firstLoad) {
      grid.hidden = true;
    }
    setReloading(true);
    setStatus(
      firstLoad ? (options.loadingMessage || "正在读取用户信息...") : (options.refreshingMessage || "正在刷新用户信息..."),
      false
    );
    try {
      const response = await authClient.fetchWithAuth(ME_PATH, { method: "GET" });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success || !payload?.user) {
        setStatus(payload?.message || "读取用户信息失败。", true);
        return { success: false, user: null };
      }
      render(payload.user);
      loadTotpStatus();
      setStatus(options.successMessage || "用户信息已加载。", false);
      return { success: true, user: payload.user };
    } catch (_) {
      setStatus("读取用户信息失败。", true);
      return { success: false, user: null };
    } finally {
      setReloading(false);
    }
  }

  function sleep(delayMs) {
    return new Promise((resolve) => {
      window.setTimeout(resolve, delayMs);
    });
  }

  async function waitForAvatarRefresh(previousAvatarUrl) {
    for (let attempt = 0; attempt < 6; attempt += 1) {
      await sleep(attempt === 0 ? 1200 : 1500);
      const result = await loadProfile({
        loadingMessage: "正在同步头像...",
        refreshingMessage: "正在同步头像...",
        successMessage: "头像已同步。"
      });
      if (!result.success) {
        continue;
      }
      const nextAvatarUrl = normalizeAvatarUrl(result.user?.avatarUrl);
      if (nextAvatarUrl && nextAvatarUrl !== previousAvatarUrl) {
        return true;
      }
    }
    return false;
  }

  async function handleAvatarSelection() {
    const files = Array.from(avatarInput.files || []);
    avatarInput.value = "";
    if (files.length !== 1) {
      setStatus("一次只能上传一张头像图片。", true);
      return;
    }

    const file = files[0];
    if (!file || !file.type.startsWith("image/")) {
      setStatus("请选择图片文件。", true);
      return;
    }

    const previousAvatarUrl = currentAvatarUrl;
    const formData = new FormData();
    formData.append("file", file);
    setAvatarWorking(true);
    setStatus("正在提交头像上传任务...", false);

    try {
      const response = await authClient.fetchWithAuth(AVATAR_PATH, {
        method: "POST",
        body: formData
      });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) {
        setStatus(payload?.message || "头像上传失败。", true);
        return;
      }

      setStatus(payload?.message || "头像上传任务已提交。", false);
      const refreshed = await waitForAvatarRefresh(previousAvatarUrl);
      if (!refreshed) {
        setStatus("头像上传任务已提交，稍后刷新页面即可看到最新头像。", false);
      }
    } catch (_) {
      setStatus("头像上传失败。", true);
    } finally {
      setAvatarWorking(false);
    }
  }

  async function deleteAvatar() {
    if (!currentAvatarUrl) {
      setStatus("当前没有可删除的头像。", false);
      return;
    }
    if (!window.confirm("确定删除当前头像吗？")) {
      return;
    }

    setAvatarWorking(true);
    setStatus("正在删除头像...", false);
    try {
      const response = await authClient.fetchWithAuth(AVATAR_PATH, {
        method: "DELETE"
      });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) {
        setStatus(payload?.message || "头像删除失败。", true);
        return;
      }
      await loadProfile({
        loadingMessage: "正在刷新头像...",
        refreshingMessage: "正在刷新头像...",
        successMessage: payload?.message || "头像已删除。"
      });
    } catch (_) {
      setStatus("头像删除失败。", true);
    } finally {
      setAvatarWorking(false);
    }
  }

  reloadButton?.addEventListener("click", () => {
    loadProfile();
  });
  logoutCurrentButton?.addEventListener("click", () => {
    confirmAndLogout("确定要退出当前设备吗？", () => authClient?.logout?.());
  });
  logoutAllButton?.addEventListener("click", () => {
    confirmAndLogout("确定要退出全部设备吗？其他设备也需要重新登录。", () => authClient?.logoutAll?.());
  });
  deleteAccountButton?.addEventListener("click", submitAccountDeletion);

  avatarUploadTrigger?.addEventListener("click", () => avatarInput?.click());
  avatarChangeButton?.addEventListener("click", () => avatarInput?.click());
  avatarInput?.addEventListener("change", handleAvatarSelection);
  avatarDeleteButton?.addEventListener("click", deleteAvatar);

  totpActionButton?.addEventListener("click", startTotpSetup);
  totpCloseButton?.addEventListener("click", closeTotpModal);
  totpModal?.addEventListener("click", (event) => {
    if (event.target === totpModal) {
      closeTotpModal();
    }
  });
  totpManualToggle?.addEventListener("click", () => {
    const shouldShow = totpManual.hidden;
    totpManual.hidden = !shouldShow;
    totpManualToggle.textContent = shouldShow ? "隐藏手动密钥" : "无法扫码？";
  });
  totpCode?.addEventListener("input", () => {
    totpCode.value = totpCode.value.replace(/\D/g, "").slice(0, 6);
  });
  totpConfirmForm?.addEventListener("submit", confirmTotpSetup);
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !totpModal.hidden) {
      closeTotpModal();
    }
  });

  async function startPage() {
    const pageGate = window.ShoppingPageAccessGate;
    if (pageGate?.ready) {
      const allowed = await pageGate.ready();
      if (!allowed) {
        return;
      }
    }
    loadProfile();
  }

  startPage();
})();
