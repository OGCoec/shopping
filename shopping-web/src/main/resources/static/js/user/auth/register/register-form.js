(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory(require("../shared/preauth-client.js"));
    return;
  }
  root.ShoppingRegisterForm = factory(root.ShoppingPreAuthClient);
})(typeof globalThis !== "undefined" ? globalThis : this, function (preAuthClientApi) {
  const DEFAULT_PASSWORD_STRENGTH_COLORS = ["#ccc", "red", "orange", "yellowgreen", "green"];
  const DEFAULT_PASSWORD_STRENGTH_LABELS = ["太短", "极弱", "中等", "强", "极强"];
  const DEFAULT_PASSWORD_STRENGTH_BASE_WIDTH = 40;
  const DEFAULT_PASSWORD_STRENGTH_STEP_WIDTH = 30;
  const preAuthFetch = preAuthClientApi && typeof preAuthClientApi.fetchWithPreAuth === "function"
    ? preAuthClientApi.fetchWithPreAuth
    : fetch;

  function checkRegisterPasswordStrength(password) {
    if (!password || password.length <= 6) return 0;

    const isSingleCharacterTypePassword =
      /^[0-9]{7,}$/.test(password) || /^[a-z]{7,}$/.test(password) || /^[A-Z]{7,}$/.test(password);

    if (isSingleCharacterTypePassword) {
      return 1;
    }

    let score = 0;
    if (/[a-z]/.test(password)) score += 1;
    if (/[A-Z]/.test(password)) score += 1;
    if (/[0-9]/.test(password)) score += 1;
    if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) score += 1;

    if (password.length >= 9 && score === 4) return 4;
    if (password.length >= 9 && score === 3) return 3;
    return 2;
  }

  function buildRegisterDeviceFingerprint() {
    const userAgent = typeof navigator !== "undefined" ? navigator.userAgent || "unknown" : "unknown";
    const language = typeof navigator !== "undefined" ? navigator.language || "unknown" : "unknown";
    const platform = typeof navigator !== "undefined" ? navigator.platform || "unknown" : "unknown";
    const screenWidth = typeof screen !== "undefined" ? String(screen.width || 0) : "0";
    const screenHeight = typeof screen !== "undefined" ? String(screen.height || 0) : "0";
    const timeZone = typeof Intl !== "undefined"
      ? Intl.DateTimeFormat().resolvedOptions().timeZone || "unknown"
      : "unknown";

    return [userAgent, language, platform, screenWidth, screenHeight, timeZone].join("|");
  }

  function resolveOperationTimeoutRemainingMs(payload) {
    const retryAfterMs = Number(payload?.retryAfterMs);
    if (Number.isFinite(retryAfterMs) && retryAfterMs > 0) {
      return Math.round(retryAfterMs);
    }
    const waitUntilEpochMs = Number(payload?.waitUntilEpochMs);
    if (Number.isFinite(waitUntilEpochMs)) {
      return Math.max(0, Math.round(waitUntilEpochMs - Date.now()));
    }
    return 0;
  }

  function buildOperationTimeoutMessage(payload) {
    const remainingMs = resolveOperationTimeoutRemainingMs(payload);
    const waitSeconds = Math.max(1, Math.ceil(remainingMs / 1000));
    return {
      remainingMs,
      message: remainingMs > 0
        ? `当前操作过于频繁，请在 ${waitSeconds} 秒后重试`
        : (payload?.message || "当前操作过于频繁，请稍后重试")
    };
  }

  function createRegisterForm(options) {
    const {
      showRegisterError,
      clearRegisterError,
      openRegisterOtpAfterEmailSent,
      loadRegisterCaptcha,
      openRegisterCaptchaModal,
      renderTurnstileCaptcha,
      renderHCaptcha,
      loadTianaiCaptcha,
      openTianaiModal,
      passwordStrengthColors = DEFAULT_PASSWORD_STRENGTH_COLORS,
      passwordStrengthLabels = DEFAULT_PASSWORD_STRENGTH_LABELS,
      passwordStrengthBaseWidth = DEFAULT_PASSWORD_STRENGTH_BASE_WIDTH,
      passwordStrengthStepWidth = DEFAULT_PASSWORD_STRENGTH_STEP_WIDTH
    } = options || {};

    let pendingRegisterPayload = null;

    async function handleChallengeRequirement(payload) {
      const challengeType = payload?.challengeType || "";
      const challengeSubType = payload?.challengeSubType || "";
      const riskLevel = payload?.riskLevel || "";

      if (pendingRegisterPayload) {
        pendingRegisterPayload.challengeType = challengeType;
        pendingRegisterPayload.challengeSubType = challengeSubType;
      }

      if (!challengeType) {
        return { handled: false, submitCooldownMs: 0 };
      }

      if (challengeType === "OPERATION_TIMEOUT") {
        const timeoutInfo = buildOperationTimeoutMessage(payload);
        showRegisterError?.(timeoutInfo.message, false);
        return { handled: true, submitCooldownMs: timeoutInfo.remainingMs };
      }

      if (challengeType === "HUTOOL_SHEAR_CAPTCHA") {
        await loadRegisterCaptcha?.();
        openRegisterCaptchaModal?.();
        showRegisterError?.(`Risk ${riskLevel}: complete captcha ${challengeType} first`, false);
        return { handled: true, submitCooldownMs: 0 };
      }
      if (challengeType === "CLOUDFLARE_TURNSTILE") {
        await renderTurnstileCaptcha?.(payload.challengeSiteKey);
        showRegisterError?.(`Risk ${riskLevel}: complete Cloudflare Turnstile first`, false);
        return { handled: true, submitCooldownMs: 0 };
      }
      if (challengeType === "HCAPTCHA") {
        await renderHCaptcha?.(payload.challengeSiteKey);
        showRegisterError?.(`Risk ${riskLevel}: complete hCaptcha first`, false);
        return { handled: true, submitCooldownMs: 0 };
      }
      if (challengeType === "TIANAI_CAPTCHA") {
        await loadTianaiCaptcha?.(challengeSubType);
        openTianaiModal?.();
        showRegisterError?.(`Risk ${riskLevel}: complete security challenge ${challengeSubType} first`, false);
        return { handled: true, submitCooldownMs: 0 };
      }

      const challengeLabel = challengeSubType
        ? `${challengeType}(${challengeSubType})`
        : challengeType;
      showRegisterError?.(`Risk ${riskLevel}: complete captcha ${challengeLabel} first`, false);
      return { handled: true, submitCooldownMs: 0 };
    }
    function getRegisterPasswordStrengthWidth(level) {
      if (level === 0) {
        return passwordStrengthBaseWidth;
      }

      return passwordStrengthBaseWidth + level * passwordStrengthStepWidth;
    }

    function updateRegisterPasswordStrengthDisplay(password) {
      const passwordStrengthBar = document.getElementById("pwdStrengthBar");
      const passwordStrengthText = document.getElementById("pwdStrengthText");
      if (!passwordStrengthBar || !passwordStrengthText) return;

      if (!password) {
        passwordStrengthBar.style.width = "0";
        passwordStrengthBar.style.background = "transparent";
        passwordStrengthText.innerText = "";
        return;
      }

      const level = checkRegisterPasswordStrength(password);
      const color = passwordStrengthColors[level];

      passwordStrengthBar.style.background = color;
      passwordStrengthBar.style.width = `${getRegisterPasswordStrengthWidth(level)}px`;
      passwordStrengthText.innerText = passwordStrengthLabels[level];
      passwordStrengthText.style.color = color;
    }

    async function requestRegisterEmailCodeDelivery(captchaUuid = "", captchaCode = "") {
      if (!pendingRegisterPayload) {
        throw new Error("register payload missing");
      }
      const requestPayload = {
        email: pendingRegisterPayload.email || "",
        username: pendingRegisterPayload.username || "",
        password: pendingRegisterPayload.password || "",
        deviceFingerprint: pendingRegisterPayload.deviceFingerprint || "",
        captchaUuid,
        captchaCode
      };

      return preAuthFetch("/shopping/user/register/email-code", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(requestPayload)
      });
    }

    async function submitRegisterEmailCode() {
      const registerEmailInput = document.getElementById("register-email");
      const registerUsernameInput = document.getElementById("register-username");
      const registerPasswordInput = document.getElementById("register-password");
      const registerConfirmInput = document.getElementById("register-confirm");

      if (!registerEmailInput || !registerUsernameInput || !registerPasswordInput || !registerConfirmInput) {
        return { submitCooldownMs: 0 };
      }

      clearRegisterError?.();

      const email = registerEmailInput.value.trim();
      const username = registerUsernameInput.value.trim();
      const password = registerPasswordInput.value;
      const confirmPassword = registerConfirmInput.value;
      const passwordStrengthLevel = checkRegisterPasswordStrength(password);

      if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showRegisterError?.("请输入有效的电子邮箱地址");
        return { submitCooldownMs: 0 };
      }
      if (!username) {
        showRegisterError?.("用户名不能为空");
        return { submitCooldownMs: 0 };
      }
      if (!password || password.length < 6) {
        showRegisterError?.("密码至少 6 位");
        return { submitCooldownMs: 0 };
      }
      if (passwordStrengthLevel < 2) {
        showRegisterError?.("密码强度至少达到中等");
        return { submitCooldownMs: 0 };
      }
      if (password !== confirmPassword) {
        showRegisterError?.("两次输入的密码不一致");
        return { submitCooldownMs: 0 };
      }

      pendingRegisterPayload = {
        email,
        username,
        password,
        deviceFingerprint: buildRegisterDeviceFingerprint()
      };

      const response = await preAuthFetch("/shopping/user/register/email-code-type", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(pendingRegisterPayload)
      });

      const payload = await response.json();
      if (!response.ok) {
        showRegisterError?.(payload.message || "注册请求失败");
        return { submitCooldownMs: 0 };
      }

      if (!payload.success) {
        const challengeResult = await handleChallengeRequirement(payload);
        if (challengeResult.handled) {
          return { submitCooldownMs: challengeResult.submitCooldownMs };
        }

        showRegisterError?.(payload.message || "注册请求失败");
        return { submitCooldownMs: 0 };
      }

      pendingRegisterPayload.challengeType = "";
      pendingRegisterPayload.challengeSubType = "";

      const deliveryResponse = await requestRegisterEmailCodeDelivery("", "");
      const deliveryPayload = await deliveryResponse.json();
      if (!deliveryResponse.ok || !deliveryPayload.success) {
        const challengeResult = await handleChallengeRequirement(deliveryPayload);
        if (challengeResult.handled) {
          return { submitCooldownMs: challengeResult.submitCooldownMs };
        }
        showRegisterError?.(deliveryPayload.message || "注册请求失败");
        return { submitCooldownMs: 0 };
      }

      pendingRegisterPayload.riskLevel = deliveryPayload.riskLevel || payload.riskLevel || "";
      pendingRegisterPayload.requirePhoneBinding = Boolean(deliveryPayload.requirePhoneBinding);
      openRegisterOtpAfterEmailSent?.(deliveryPayload);
      return { submitCooldownMs: 0 };
    }

    return {
      checkRegisterPasswordStrength,
      getRegisterPasswordStrengthWidth,
      updateRegisterPasswordStrengthDisplay,
      buildRegisterDeviceFingerprint,
      submitRegisterEmailCode,
      requestRegisterEmailCodeDelivery,
      getPendingRegisterPayload() {
        return pendingRegisterPayload;
      }
    };
  }

  return {
    DEFAULT_PASSWORD_STRENGTH_COLORS,
    DEFAULT_PASSWORD_STRENGTH_LABELS,
    DEFAULT_PASSWORD_STRENGTH_BASE_WIDTH,
    DEFAULT_PASSWORD_STRENGTH_STEP_WIDTH,
    checkRegisterPasswordStrength,
    buildRegisterDeviceFingerprint,
    createRegisterForm
  };
});
