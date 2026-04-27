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
  const REGISTER_PENDING_CONTEXT_KEY = "shopping:register:pending-context:v1";
  const REGISTER_EMAIL_CODE_TYPE_PATH = "/shopping/user/register/email-code-type";
  const REGISTER_EMAIL_CODE_VERIFY_PATH = "/shopping/user/register/email-code/verify";
  const REGISTER_CRYPTO_ERROR_MESSAGE = "密码加密不可用，请刷新后重试";
  const preAuthFetch = preAuthClientApi && typeof preAuthClientApi.fetchWithPreAuth === "function"
    ? preAuthClientApi.fetchWithPreAuth
    : fetch;

  async function parseJsonSafely(response) {
    try {
      return await response.json();
    } catch (_) {
      return {};
    }
  }

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

  function isBrowserRuntime() {
    return typeof window !== "undefined" && typeof sessionStorage !== "undefined";
  }

  function buildPendingRegisterPayloadSnapshot(payload) {
    if (!payload || typeof payload !== "object") {
      return null;
    }
    return {
      email: payload.email || "",
      username: payload.username || "",
      passwordCipher: payload.passwordCipher || "",
      kid: payload.kid || "",
      deviceFingerprint: payload.deviceFingerprint || "",
      challengeType: payload.challengeType || "",
      challengeSubType: payload.challengeSubType || "",
      riskLevel: payload.riskLevel || "",
      requirePhoneBinding: Boolean(payload.requirePhoneBinding)
    };
  }

  function isWebCryptoAvailable() {
    return typeof globalThis !== "undefined"
      && !!globalThis.crypto
      && !!globalThis.crypto.subtle
      && typeof TextEncoder === "function";
  }

  function encodeBase64Url(bytes) {
    if (!bytes) {
      return "";
    }
    if (typeof Buffer !== "undefined") {
      return Buffer.from(bytes)
        .toString("base64")
        .replace(/\+/g, "-")
        .replace(/\//g, "_")
        .replace(/=+$/g, "");
    }

    let binary = "";
    const chunkSize = 0x8000;
    for (let offset = 0; offset < bytes.length; offset += chunkSize) {
      const chunk = bytes.subarray(offset, offset + chunkSize);
      binary += String.fromCharCode.apply(null, chunk);
    }
    if (typeof btoa !== "function") {
      throw new Error(REGISTER_CRYPTO_ERROR_MESSAGE);
    }
    return btoa(binary)
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/g, "");
  }

  function buildRegisterRequestNonce() {
    if (!isWebCryptoAvailable()) {
      throw new Error(REGISTER_CRYPTO_ERROR_MESSAGE);
    }
    const nonceBytes = new Uint8Array(18);
    globalThis.crypto.getRandomValues(nonceBytes);
    return encodeBase64Url(nonceBytes);
  }

  function resolvePasswordCryptoKeyFromBootstrap(payload) {
    const cryptoPayload = payload && typeof payload === "object" ? payload.passwordCrypto : null;
    if (!cryptoPayload || typeof cryptoPayload !== "object") {
      return null;
    }

    const kid = cryptoPayload.kid ? String(cryptoPayload.kid).trim() : "";
    const publicKeyJwk = cryptoPayload.publicKeyJwk;
    if (!kid || !publicKeyJwk || typeof publicKeyJwk !== "object") {
      return null;
    }

    return {
      kid,
      publicKeyJwk,
      alg: cryptoPayload.alg ? String(cryptoPayload.alg) : "",
      expiresAtEpochMillis: Number(cryptoPayload.expiresAtEpochMillis || 0)
    };
  }

  async function fetchRegisterPasswordCryptoKey() {
    if (preAuthClientApi && typeof preAuthClientApi.fetchRegisterPasswordCryptoKey === "function") {
      return preAuthClientApi.fetchRegisterPasswordCryptoKey(true);
    }

    if (preAuthClientApi && typeof preAuthClientApi.bootstrapPreAuthToken === "function") {
      const bootstrapPayload = await preAuthClientApi.bootstrapPreAuthToken(true);
      const key = resolvePasswordCryptoKeyFromBootstrap(bootstrapPayload);
      if (key) {
        return key;
      }
    }

    const bootstrapResponse = await preAuthFetch("/shopping/auth/preauth/bootstrap", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: "{}"
    });
    if (!bootstrapResponse.ok) {
      throw new Error(REGISTER_CRYPTO_ERROR_MESSAGE);
    }
    const bootstrapPayload = await parseJsonSafely(bootstrapResponse);
    const key = resolvePasswordCryptoKeyFromBootstrap(bootstrapPayload);
    if (!key) {
      throw new Error(REGISTER_CRYPTO_ERROR_MESSAGE);
    }
    return key;
  }

  async function encryptRegisterPassword(rawPassword) {
    if (!isWebCryptoAvailable()) {
      throw new Error(REGISTER_CRYPTO_ERROR_MESSAGE);
    }
    const passwordCryptoKey = await fetchRegisterPasswordCryptoKey();
    const publicKeyJwk = passwordCryptoKey.publicKeyJwk;
    const kid = passwordCryptoKey.kid;
    if (!kid || !publicKeyJwk || typeof publicKeyJwk !== "object") {
      throw new Error(REGISTER_CRYPTO_ERROR_MESSAGE);
    }

    const cryptoKey = await globalThis.crypto.subtle.importKey(
      "jwk",
      publicKeyJwk,
      {
        name: "RSA-OAEP",
        hash: "SHA-256"
      },
      false,
      ["encrypt"]
    );
    const rawBytes = new TextEncoder().encode(String(rawPassword || ""));
    const encryptedBuffer = await globalThis.crypto.subtle.encrypt({ name: "RSA-OAEP" }, cryptoKey, rawBytes);
    return {
      kid,
      passwordCipher: encodeBase64Url(new Uint8Array(encryptedBuffer))
    };
  }

  function buildRegisterRequestPayload(pendingPayload, captchaUuid = "", captchaCode = "") {
    return {
      email: pendingPayload?.email || "",
      username: pendingPayload?.username || "",
      passwordCipher: pendingPayload?.passwordCipher || "",
      kid: pendingPayload?.kid || "",
      nonce: buildRegisterRequestNonce(),
      timestamp: Date.now(),
      deviceFingerprint: pendingPayload?.deviceFingerprint || "",
      captchaUuid,
      captchaCode
    };
  }

  function isRegisterEmailCodeTypeReplayUrl(url) {
    if (!url) {
      return false;
    }
    const normalized = String(url).trim();
    if (!normalized) {
      return false;
    }
    if (normalized.startsWith(REGISTER_EMAIL_CODE_TYPE_PATH)) {
      return true;
    }
    if (!isBrowserRuntime()) {
      return normalized.includes(REGISTER_EMAIL_CODE_TYPE_PATH);
    }
    try {
      const parsed = new URL(normalized, window.location.origin);
      return parsed.pathname === REGISTER_EMAIL_CODE_TYPE_PATH;
    } catch (_) {
      return normalized.includes(REGISTER_EMAIL_CODE_TYPE_PATH);
    }
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

    function persistPendingRegisterPayload() {
      if (!isBrowserRuntime()) {
        return;
      }
      const snapshot = buildPendingRegisterPayloadSnapshot(pendingRegisterPayload);
      if (!snapshot) {
        return;
      }
      try {
        sessionStorage.setItem(REGISTER_PENDING_CONTEXT_KEY, JSON.stringify({
          payload: snapshot,
          savedAt: Date.now()
        }));
      } catch (_) {
      }
    }

    function restorePendingRegisterPayloadFromSession() {
      if (pendingRegisterPayload) {
        return pendingRegisterPayload;
      }
      if (!isBrowserRuntime()) {
        return null;
      }
      try {
        const raw = sessionStorage.getItem(REGISTER_PENDING_CONTEXT_KEY);
        if (!raw) {
          return null;
        }
        const parsed = JSON.parse(raw);
        const snapshot = buildPendingRegisterPayloadSnapshot(parsed?.payload);
        if (!snapshot) {
          return null;
        }
        if (!snapshot.email || !snapshot.username || !snapshot.passwordCipher || !snapshot.kid || !snapshot.deviceFingerprint) {
          return null;
        }
        pendingRegisterPayload = snapshot;
        return pendingRegisterPayload;
      } catch (_) {
        return null;
      }
    }

    function clearPendingRegisterPayload() {
      pendingRegisterPayload = null;
      if (!isBrowserRuntime()) {
        return;
      }
      try {
        sessionStorage.removeItem(REGISTER_PENDING_CONTEXT_KEY);
      } catch (_) {
      }
    }

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
        restorePendingRegisterPayloadFromSession();
      }
      if (!pendingRegisterPayload) {
        throw new Error("register payload missing");
      }
      const requestPayload = buildRegisterRequestPayload(pendingRegisterPayload, captchaUuid, captchaCode);

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

      let encryptedPasswordPayload = null;
      try {
        encryptedPasswordPayload = await encryptRegisterPassword(password);
      } catch (error) {
        showRegisterError?.(error?.message || REGISTER_CRYPTO_ERROR_MESSAGE);
        return { submitCooldownMs: 0 };
      }

      pendingRegisterPayload = {
        email,
        username,
        passwordCipher: encryptedPasswordPayload.passwordCipher || "",
        kid: encryptedPasswordPayload.kid || "",
        deviceFingerprint: buildRegisterDeviceFingerprint()
      };
      persistPendingRegisterPayload();

      const response = await preAuthFetch("/shopping/user/register/email-code-type", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(buildRegisterRequestPayload(pendingRegisterPayload, "", ""))
      });

      const payload = await parseJsonSafely(response);
      if (!response.ok) {
        showRegisterError?.(payload.message || "注册请求失败");
        return { submitCooldownMs: 0 };
      }

      if (!payload.success) {
        persistPendingRegisterPayload();
        const challengeResult = await handleChallengeRequirement(payload);
        if (challengeResult.handled) {
          return { submitCooldownMs: challengeResult.submitCooldownMs };
        }

        showRegisterError?.(payload.message || "注册请求失败");
        return { submitCooldownMs: 0 };
      }

      pendingRegisterPayload.challengeType = "";
      pendingRegisterPayload.challengeSubType = "";
      persistPendingRegisterPayload();

      const deliveryResponse = await requestRegisterEmailCodeDelivery("", "");
      const deliveryPayload = await parseJsonSafely(deliveryResponse);
      if (!deliveryResponse.ok || !deliveryPayload.success) {
        persistPendingRegisterPayload();
        const challengeResult = await handleChallengeRequirement(deliveryPayload);
        if (challengeResult.handled) {
          return { submitCooldownMs: challengeResult.submitCooldownMs };
        }
        showRegisterError?.(deliveryPayload.message || "注册请求失败");
        return { submitCooldownMs: 0 };
      }

      pendingRegisterPayload.riskLevel = deliveryPayload.riskLevel || payload.riskLevel || "";
      pendingRegisterPayload.requirePhoneBinding = Boolean(deliveryPayload.requirePhoneBinding);
      persistPendingRegisterPayload();
      openRegisterOtpAfterEmailSent?.(deliveryPayload);
      return { submitCooldownMs: 0 };
    }

    async function resendRegisterEmailCode() {
      if (!pendingRegisterPayload) {
        restorePendingRegisterPayloadFromSession();
      }
      if (!pendingRegisterPayload) {
        return {
          success: false,
          message: "注册上下文已失效，请返回注册表单重新提交。",
          submitCooldownMs: 0
        };
      }

      const deliveryResponse = await requestRegisterEmailCodeDelivery("", "");
      const deliveryPayload = await parseJsonSafely(deliveryResponse);
      if (!deliveryResponse.ok || !deliveryPayload.success) {
        const challengeResult = await handleChallengeRequirement(deliveryPayload);
        if (challengeResult.handled) {
          const timeoutMessage = challengeResult.submitCooldownMs > 0
            ? buildOperationTimeoutMessage(deliveryPayload).message
            : "";
          return {
            success: false,
            message: timeoutMessage || deliveryPayload.message || "请先完成安全验证。",
            submitCooldownMs: challengeResult.submitCooldownMs
          };
        }
        return {
          success: false,
          message: deliveryPayload.message || "重新发送失败，请稍后重试。",
          submitCooldownMs: 0
        };
      }

      pendingRegisterPayload.riskLevel = deliveryPayload.riskLevel || pendingRegisterPayload.riskLevel || "";
      pendingRegisterPayload.requirePhoneBinding = Boolean(deliveryPayload.requirePhoneBinding);
      persistPendingRegisterPayload();
      openRegisterOtpAfterEmailSent?.(deliveryPayload);
      return {
        success: true,
        message: "邮箱验证码已重新发送",
        submitCooldownMs: 0
      };
    }

    async function verifyRegisterEmailCode(emailCode) {
      if (!pendingRegisterPayload) {
        restorePendingRegisterPayloadFromSession();
      }
      if (!pendingRegisterPayload) {
        return {
          success: false,
          message: "Register context expired, please submit register form again.",
          requirePhoneBinding: false
        };
      }

      const normalizedCode = typeof emailCode === "string" ? emailCode.trim() : "";
      if (!/^\d{4,8}$/.test(normalizedCode)) {
        return {
          success: false,
          message: "Please enter a valid email code.",
          requirePhoneBinding: false
        };
      }

      const response = await preAuthFetch(REGISTER_EMAIL_CODE_VERIFY_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          email: pendingRegisterPayload.email || "",
          emailCode: normalizedCode,
          deviceFingerprint: pendingRegisterPayload.deviceFingerprint || ""
        })
      });
      const payload = await parseJsonSafely(response);

      if (!response.ok) {
        return {
          success: false,
          message: payload?.message || "Register verification failed, please retry.",
          requirePhoneBinding: false
        };
      }
      if (!payload?.success) {
        return {
          success: false,
          message: payload?.message || "Email code verification failed.",
          requirePhoneBinding: false
        };
      }

      clearPendingRegisterPayload();
      return {
        success: true,
        message: payload?.message || "Register completed.",
        requirePhoneBinding: Boolean(payload?.requirePhoneBinding),
        userId: Number(payload?.userId || 0) || null
      };
    }

    async function continueRegisterAfterWafReplay(replayDetail = {}) {
      if (!isRegisterEmailCodeTypeReplayUrl(replayDetail?.url)) {
        return { handled: false, submitCooldownMs: 0 };
      }

      restorePendingRegisterPayloadFromSession();
      if (!pendingRegisterPayload) {
        showRegisterError?.("Register context expired, please submit the register form again.");
        return { handled: true, submitCooldownMs: 0 };
      }

      const replayPayload = replayDetail?.payload && typeof replayDetail.payload === "object"
        ? replayDetail.payload
        : {};
      const replayStatus = Number(replayDetail?.status || 0);
      const replayOk = replayDetail?.ok !== false && replayStatus >= 200 && replayStatus < 300;
      if (!replayOk) {
        showRegisterError?.(replayPayload.message || "Register request failed, please retry.");
        return { handled: true, submitCooldownMs: 0 };
      }

      if (!replayPayload.success) {
        persistPendingRegisterPayload();
        const challengeResult = await handleChallengeRequirement(replayPayload);
        if (challengeResult.handled) {
          return { handled: true, submitCooldownMs: challengeResult.submitCooldownMs };
        }
        showRegisterError?.(replayPayload.message || "Register request failed, please retry.");
        return { handled: true, submitCooldownMs: 0 };
      }

      pendingRegisterPayload.challengeType = "";
      pendingRegisterPayload.challengeSubType = "";
      persistPendingRegisterPayload();

      const deliveryResponse = await requestRegisterEmailCodeDelivery("", "");
      const deliveryPayload = await parseJsonSafely(deliveryResponse);
      if (!deliveryResponse.ok || !deliveryPayload.success) {
        persistPendingRegisterPayload();
        const challengeResult = await handleChallengeRequirement(deliveryPayload);
        if (challengeResult.handled) {
          return { handled: true, submitCooldownMs: challengeResult.submitCooldownMs };
        }
        showRegisterError?.(deliveryPayload.message || "Register request failed, please retry.");
        return { handled: true, submitCooldownMs: 0 };
      }

      pendingRegisterPayload.riskLevel = deliveryPayload.riskLevel || replayPayload.riskLevel || "";
      pendingRegisterPayload.requirePhoneBinding = Boolean(deliveryPayload.requirePhoneBinding);
      persistPendingRegisterPayload();
      openRegisterOtpAfterEmailSent?.(deliveryPayload);
      return { handled: true, submitCooldownMs: 0 };
    }

    return {
      checkRegisterPasswordStrength,
      getRegisterPasswordStrengthWidth,
      updateRegisterPasswordStrengthDisplay,
      buildRegisterDeviceFingerprint,
      submitRegisterEmailCode,
      resendRegisterEmailCode,
      verifyRegisterEmailCode,
      continueRegisterAfterWafReplay,
      requestRegisterEmailCodeDelivery,
      clearPendingRegisterPayload,
      getPendingRegisterPayload() {
        if (!pendingRegisterPayload) {
          restorePendingRegisterPayloadFromSession();
        }
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
