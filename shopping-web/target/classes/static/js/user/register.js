// ============ REGISTER ENTRY ============
const registerTianaiTrackApi = typeof module !== "undefined" && module.exports
  ? require("./auth/register/tianai/register-tianai-track.js")
  : globalThis.ShoppingRegisterTianaiTrack;
const authUtilsModule = typeof module !== "undefined" && module.exports
  ? require("./auth/shared/auth-utils.js")
  : globalThis.ShoppingAuthUtils;
const registerFormModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-form.js")
  : globalThis.ShoppingRegisterForm;
const registerTianaiModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/tianai/register-tianai.js")
  : globalThis.ShoppingRegisterTianai;
const registerTurnstileModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-turnstile.js")
  : globalThis.ShoppingRegisterTurnstile;
const registerHCaptchaModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-hcaptcha.js")
  : globalThis.ShoppingRegisterHCaptcha;
const registerHutoolCaptchaModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-hutool-captcha.js")
  : globalThis.ShoppingRegisterHutoolCaptcha;
const preAuthClientModule = typeof module !== "undefined" && module.exports
  ? require("./auth/shared/preauth-client.js")
  : globalThis.ShoppingPreAuthClient;

if (
  !registerTianaiTrackApi
  || !authUtilsModule
  || !registerFormModule
  || !registerTianaiModule
  || !registerTurnstileModule
  || !registerHCaptchaModule
  || !registerHutoolCaptchaModule
  || !preAuthClientModule
) {
  throw new Error("register dependencies failed to load");
}

const {
  buildTianaiRotatePercentage,
  buildTianaiTrackPayload,
  buildTianaiWordClickPayload
} = registerTianaiTrackApi;
const {
  getCaptchaSuccessFeedbackDelay,
  waitForCaptchaSuccessFeedback,
  waitForNextPaint,
  getElementDisplaySize
} = authUtilsModule;
const { createRegisterForm } = registerFormModule;
const {
  getConcatRenderData,
  buildConcatLayerMarkup,
  resetCaptchaImageVisibility,
  createRegisterTianai
} = registerTianaiModule;
const { createRegisterTurnstile } = registerTurnstileModule;
const { createRegisterHCaptcha } = registerHCaptchaModule;
const { createRegisterHutoolCaptcha } = registerHutoolCaptchaModule;
const WAF_REPLAY_EVENT_NAME = typeof preAuthClientModule.WAF_REPLAY_EVENT_NAME === "string"
  ? preAuthClientModule.WAF_REPLAY_EVENT_NAME
  : "shopping:preauth:waf-request-replayed";
const REGISTER_EMAIL_CODE_TYPE_PATH = "/shopping/user/register/email-code-type";

const CAPTCHA_SUCCESS_FEEDBACK_MIN_MS = 1200;
const HCAPTCHA_AUTO_RETRY_DELAY_MS = 180;
const HCAPTCHA_AUTO_RETRY_LIMIT = 1;

let registerFormApi = null;
let registerTianaiApi = null;
let registerTurnstileApi = null;
let registerHCaptchaApi = null;
let registerHutoolCaptchaApi = null;

const REGISTER_ERROR_FALLBACK_MESSAGE = "注册请求失败，请稍后重试";

function isMojibakeLikeMessage(text) {
  if (!text) {
    return false;
  }
  if (text.includes("�")) {
    return true;
  }
  const suspiciousMatches = text.match(/[鍙锛鏄璇鐨鎴濡鏉娉缁閿锟]/g);
  return (suspiciousMatches?.length || 0) >= 2;
}

function normalizeRegisterErrorMessage(message) {
  const normalized = typeof message === "string" ? message.trim() : "";
  if (!normalized) {
    return REGISTER_ERROR_FALLBACK_MESSAGE;
  }
  if (isMojibakeLikeMessage(normalized)) {
    return REGISTER_ERROR_FALLBACK_MESSAGE;
  }
  return normalized;
}

function showRegisterError(message, triggerAnimation = true) {
  const registerErrorMessage = document.getElementById("register-error-msg");
  if (!registerErrorMessage) return;
  registerErrorMessage.textContent = normalizeRegisterErrorMessage(message);
  registerErrorMessage.style.display = "block";
  if (triggerAnimation) {
    triggerLoginError();
  }
}

function clearRegisterError() {
  const registerErrorMessage = document.getElementById("register-error-msg");
  if (!registerErrorMessage) return;
  registerErrorMessage.textContent = "";
  registerErrorMessage.style.display = "none";
}

function triggerRegisterCaptchaFailureAnimation() {
  if (typeof triggerLoginError === "function") {
    triggerLoginError();
  }
}

function openRegisterOtpAfterEmailSent(deliveryPayload = null) {
  const registerEmailInput = document.getElementById("register-email");
  const pendingRegisterPayload = registerFormApi?.getPendingRegisterPayload?.() || null;
  const registerEmail = pendingRegisterPayload?.email || registerEmailInput?.value?.trim() || "";
  const registerRiskLevel = deliveryPayload?.riskLevel || pendingRegisterPayload?.riskLevel || "";
  const requirePhoneBinding = typeof deliveryPayload?.requirePhoneBinding === "boolean"
    ? deliveryPayload.requirePhoneBinding
    : Boolean(pendingRegisterPayload?.requirePhoneBinding);
  clearRegisterError();

  if (typeof window.openRegisterOtpStep === "function") {
    window.openRegisterOtpStep(registerEmail, {
      riskLevel: registerRiskLevel,
      requirePhoneBinding
    });
    return true;
  }

  showRegisterError("Email code sent, but OTP step is unavailable.", false);
  return false;
}

async function resendRegisterEmailCodeFromOtp() {
  if (!registerFormApi || typeof registerFormApi.resendRegisterEmailCode !== "function") {
    return {
      success: false,
      message: "注册能力尚未就绪，请稍后重试。",
      submitCooldownMs: 0
    };
  }
  return registerFormApi.resendRegisterEmailCode();
}

async function verifyRegisterEmailCodeFromOtp(emailCode) {
  if (!registerFormApi || typeof registerFormApi.verifyRegisterEmailCode !== "function") {
    return {
      success: false,
      message: "Register verification is unavailable, please retry later.",
      requirePhoneBinding: false
    };
  }
  return registerFormApi.verifyRegisterEmailCode(emailCode);
}

let registerWafReplayHandling = false;

function isRegisterEmailCodeTypeReplay(detail = {}) {
  const replayUrl = detail?.url ? String(detail.url).trim() : "";
  if (!replayUrl) {
    return false;
  }
  if (replayUrl.startsWith(REGISTER_EMAIL_CODE_TYPE_PATH)) {
    return true;
  }
  if (typeof window === "undefined") {
    return replayUrl.includes(REGISTER_EMAIL_CODE_TYPE_PATH);
  }
  try {
    const parsed = new URL(replayUrl, window.location.origin);
    return parsed.pathname === REGISTER_EMAIL_CODE_TYPE_PATH;
  } catch (_) {
    return replayUrl.includes(REGISTER_EMAIL_CODE_TYPE_PATH);
  }
}

async function handleRegisterWafReplay(detail = {}) {
  if (!isRegisterEmailCodeTypeReplay(detail)) {
    return;
  }
  if (!registerFormApi || typeof registerFormApi.continueRegisterAfterWafReplay !== "function") {
    return;
  }
  if (registerWafReplayHandling) {
    return;
  }
  registerWafReplayHandling = true;
  try {
    await registerFormApi.continueRegisterAfterWafReplay(detail);
  } catch (_) {
    showRegisterError("Register request failed, please retry.");
  } finally {
    registerWafReplayHandling = false;
  }
}

function bindRegisterWafReplayHandler() {
  if (typeof window === "undefined" || typeof window.addEventListener !== "function") {
    return;
  }
  if (window.__shoppingRegisterWafReplayBound === true) {
    return;
  }
  window.__shoppingRegisterWafReplayBound = true;
  window.addEventListener(WAF_REPLAY_EVENT_NAME, (event) => {
    handleRegisterWafReplay(event?.detail || {}).catch(() => {
      showRegisterError("Register request failed, please retry.");
    });
  });
}

registerFormApi = createRegisterForm({
  showRegisterError,
  clearRegisterError,
  openRegisterOtpAfterEmailSent,
  loadRegisterCaptcha() {
    return registerHutoolCaptchaApi.loadRegisterCaptcha();
  },
  openRegisterCaptchaModal() {
    return registerHutoolCaptchaApi.openRegisterCaptchaModal();
  },
  renderTurnstileCaptcha(siteKey) {
    return registerTurnstileApi.renderTurnstileCaptcha(siteKey);
  },
  renderHCaptcha(siteKey) {
    return registerHCaptchaApi.renderHCaptcha(siteKey);
  },
  loadTianaiCaptcha(subType) {
    return registerTianaiApi.loadTianaiCaptcha(subType);
  },
  openTianaiModal() {
    return registerTianaiApi.openTianaiModal();
  }
});
bindRegisterWafReplayHandler();

registerTianaiApi = createRegisterTianai({
  getElementDisplaySize,
  triggerCaptchaFailureAnimation: triggerRegisterCaptchaFailureAnimation,
  requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed) {
    return registerFormApi.requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed);
  },
  openRegisterOtpAfterEmailSent,
  getPendingRegisterPayload() {
    return registerFormApi.getPendingRegisterPayload();
  }
});

registerHutoolCaptchaApi = createRegisterHutoolCaptcha({
  showRegisterError,
  triggerCaptchaFailureAnimation: triggerRegisterCaptchaFailureAnimation,
  requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed) {
    return registerFormApi.requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed);
  },
  openRegisterOtpAfterEmailSent,
  getPendingRegisterPayload() {
    return registerFormApi.getPendingRegisterPayload();
  }
});

registerTurnstileApi = createRegisterTurnstile({
  showRegisterError,
  triggerCaptchaFailureAnimation: triggerRegisterCaptchaFailureAnimation,
  requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed) {
    return registerFormApi.requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed);
  },
  waitForCaptchaSuccessFeedback(startedAt) {
    return waitForCaptchaSuccessFeedback(startedAt, CAPTCHA_SUCCESS_FEEDBACK_MIN_MS);
  },
  openRegisterOtpAfterEmailSent,
  getPendingRegisterPayload() {
    return registerFormApi.getPendingRegisterPayload();
  }
});

registerHCaptchaApi = createRegisterHCaptcha({
  autoRetryDelayMs: HCAPTCHA_AUTO_RETRY_DELAY_MS,
  autoRetryLimit: HCAPTCHA_AUTO_RETRY_LIMIT,
  showRegisterError,
  triggerCaptchaFailureAnimation: triggerRegisterCaptchaFailureAnimation,
  requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed) {
    return registerFormApi.requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed);
  },
  waitForCaptchaSuccessFeedback(startedAt) {
    return waitForCaptchaSuccessFeedback(startedAt, CAPTCHA_SUCCESS_FEEDBACK_MIN_MS);
  },
  waitForNextPaint,
  openRegisterOtpAfterEmailSent,
  getPendingRegisterPayload() {
    return registerFormApi.getPendingRegisterPayload();
  }
});

if (typeof window !== "undefined") {
  window.resendRegisterEmailCode = resendRegisterEmailCodeFromOtp;
  window.verifyRegisterEmailCode = verifyRegisterEmailCodeFromOtp;
}

function showRegisterCaptchaError(message) {
  return registerHutoolCaptchaApi.showRegisterCaptchaError(message);
}

function clearRegisterCaptchaError() {
  return registerHutoolCaptchaApi.clearRegisterCaptchaError();
}

function openRegisterCaptchaModal() {
  return registerHutoolCaptchaApi.openRegisterCaptchaModal();
}

function closeRegisterCaptchaModal() {
  return registerHutoolCaptchaApi.closeRegisterCaptchaModal();
}

function showTurnstileError(message) {
  return registerTurnstileApi.showTurnstileError(message);
}

function clearTurnstileError() {
  return registerTurnstileApi.clearTurnstileError();
}

function openTurnstileModal() {
  return registerTurnstileApi.openTurnstileModal();
}

function closeTurnstileModal() {
  return registerTurnstileApi.closeTurnstileModal();
}

function showHCaptchaError(message) {
  return registerHCaptchaApi.showHCaptchaError(message);
}

function clearHCaptchaError() {
  return registerHCaptchaApi.clearHCaptchaError();
}

function openHCaptchaModal() {
  return registerHCaptchaApi.openHCaptchaModal();
}

function closeHCaptchaModal() {
  return registerHCaptchaApi.closeHCaptchaModal();
}

function scheduleHCaptchaAutoRetry() {
  return registerHCaptchaApi.scheduleHCaptchaAutoRetry();
}

function loadTurnstileScript() {
  return registerTurnstileApi.loadTurnstileScript();
}

function loadHCaptchaScript() {
  return registerHCaptchaApi.loadHCaptchaScript();
}

function renderTurnstileCaptcha(siteKey) {
  return registerTurnstileApi.renderTurnstileCaptcha(siteKey);
}

function continueRegisterWithTurnstile(token, successFeedbackStartedAt = Date.now()) {
  return registerTurnstileApi.continueRegisterWithTurnstile(token, successFeedbackStartedAt);
}

function renderHCaptcha(siteKey) {
  return registerHCaptchaApi.renderHCaptcha(siteKey);
}

function continueRegisterWithHCaptcha(token, successFeedbackStartedAt = Date.now()) {
  return registerHCaptchaApi.continueRegisterWithHCaptcha(token, successFeedbackStartedAt);
}

function showTianaiError(message) {
  return registerTianaiApi.showTianaiError(message);
}

function clearTianaiError() {
  return registerTianaiApi.clearTianaiError();
}

function closeTianaiModal() {
  return registerTianaiApi.closeTianaiModal();
}

function openTianaiModal() {
  return registerTianaiApi.openTianaiModal();
}

function loadTianaiCaptcha(subType) {
  return registerTianaiApi.loadTianaiCaptcha(subType);
}

function continueRegisterWithTianai() {
  return registerTianaiApi.continueRegisterWithTianai();
}

function loadRegisterCaptcha(existingUuid = "") {
  return registerHutoolCaptchaApi.loadRegisterCaptcha(existingUuid);
}

function continueRegisterWithCaptcha() {
  return registerHutoolCaptchaApi.continueRegisterWithCaptcha();
}

const REGISTER_PASSWORD_HIDDEN_ICON = `
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M2 12s3.6-7 10-7c2.1 0 4 .55 5.62 1.47"></path>
    <path d="M22 12s-3.6 7-10 7c-2.1 0-4-.55-5.62-1.47"></path>
    <path d="M3 3l18 18"></path>
    <path d="M9.88 9.88A3 3 0 0 0 12 15a3 3 0 0 0 2.12-.88"></path>
  </svg>
`;

const REGISTER_PASSWORD_VISIBLE_ICON = `
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"></path>
    <circle cx="12" cy="12" r="3"></circle>
  </svg>
`;

function updateRegisterPasswordToggle(button, input, showLabel, hideLabel) {
  const visible = input?.type === "text";
  button.innerHTML = visible ? REGISTER_PASSWORD_VISIBLE_ICON : REGISTER_PASSWORD_HIDDEN_ICON;
  button.classList.toggle("is-visible", visible);
  button.setAttribute("aria-label", visible ? hideLabel : showLabel);
  button.setAttribute("title", visible ? hideLabel : showLabel);
}

function bindRegisterPasswordToggle(buttonId, inputId, showLabel, hideLabel) {
  const button = document.getElementById(buttonId);
  const input = document.getElementById(inputId);
  if (!button || !input) {
    return;
  }

  updateRegisterPasswordToggle(button, input, showLabel, hideLabel);

  if (button.dataset.bound === "true") {
    return;
  }

  button.dataset.bound = "true";
  button.addEventListener("click", () => {
    input.type = input.type === "password" ? "text" : "password";
    updateRegisterPasswordToggle(button, input, showLabel, hideLabel);
    input.focus({ preventScroll: true });
    try {
      const caretPosition = typeof input.value === "string" ? input.value.length : 0;
      input.setSelectionRange(caretPosition, caretPosition);
    } catch (_) {
      // Ignore caret restore failures on browsers that do not support it.
    }
  });
}

function initializeRegisterFragment() {
  bindRegisterPasswordToggle(
    "register-password-toggle",
    "register-password",
    "Show password",
    "Hide password"
  );
  bindRegisterPasswordToggle(
    "register-confirm-toggle",
    "register-confirm",
    "Show confirm password",
    "Hide confirm password"
  );

  const registerPasswordInput = document.getElementById("register-password");
  if (registerPasswordInput && registerPasswordInput.dataset.strengthBound !== "true") {
    registerPasswordInput.dataset.strengthBound = "true";
    registerPasswordInput.addEventListener("input", (event) => {
      registerFormApi.updateRegisterPasswordStrengthDisplay(event.target.value || "");
    });
  }
  if (registerPasswordInput) {
    registerFormApi.updateRegisterPasswordStrengthDisplay(registerPasswordInput.value || "");
  }

  const registerSubmitButton = document.getElementById("btn-register-submit");
  if (registerSubmitButton && registerSubmitButton.dataset.submitBound !== "true") {
    registerSubmitButton.dataset.submitBound = "true";
    let submitCooldownTimer = null;
    let submitCooldownUntil = 0;
    const buttonTextNode = registerSubmitButton.querySelector(".btn-text");
    const buttonHoverTextNode = registerSubmitButton.querySelector(".btn-hover-content span");
    const buttonDisabledTextNode = registerSubmitButton.querySelector(".btn-disabled-content span");
    const defaultButtonText = (buttonTextNode?.textContent || "Create account").trim() || "Create account";

    const applyButtonText = (text) => {
      if (buttonTextNode) {
        buttonTextNode.textContent = text;
      }
      if (buttonHoverTextNode) {
        buttonHoverTextNode.textContent = text;
      }
      if (buttonDisabledTextNode) {
        buttonDisabledTextNode.textContent = text;
      }
    };

    const clearSubmitCooldownTimer = () => {
      if (!submitCooldownTimer) {
        return;
      }
      clearInterval(submitCooldownTimer);
      submitCooldownTimer = null;
    };

    const isSubmitCoolingDown = () => Date.now() < submitCooldownUntil;

    const setRegisterSubmitLocked = (locked) => {
      const finalLocked = locked || isSubmitCoolingDown();
      registerSubmitButton.disabled = finalLocked;
      registerSubmitButton.classList.toggle("is-submitting", finalLocked);
      registerSubmitButton.setAttribute("aria-disabled", finalLocked ? "true" : "false");
    };

    const startSubmitCooldown = (cooldownMs) => {
      const safeCooldownMs = Math.max(0, Math.round(Number(cooldownMs) || 0));
      if (safeCooldownMs <= 0) {
        return;
      }

      submitCooldownUntil = Date.now() + safeCooldownMs;
      clearSubmitCooldownTimer();

      const updateCooldownView = () => {
        const remainingMs = Math.max(0, submitCooldownUntil - Date.now());
        if (remainingMs <= 0) {
          clearSubmitCooldownTimer();
          submitCooldownUntil = 0;
          applyButtonText(defaultButtonText);
          if (registerSubmitButton.dataset.submitting !== "true") {
            setRegisterSubmitLocked(false);
          }
          return;
        }
        const remainingSeconds = Math.max(1, Math.ceil(remainingMs / 1000));
        applyButtonText(`请稍后重试 ${remainingSeconds}s`);
        setRegisterSubmitLocked(true);
      };

      updateCooldownView();
      submitCooldownTimer = setInterval(updateCooldownView, 200);
    };
    registerSubmitButton.addEventListener("click", async () => {
      if (registerSubmitButton.dataset.submitting === "true") {
        return;
      }
      if (isSubmitCoolingDown()) {
        setRegisterSubmitLocked(true);
        return;
      }
      registerSubmitButton.dataset.submitting = "true";
      setRegisterSubmitLocked(true);
      try {
        const submitResult = await registerFormApi.submitRegisterEmailCode();
        const submitCooldownMs = Math.max(0, Math.round(Number(submitResult?.submitCooldownMs) || 0));
        if (submitCooldownMs > 0) {
          startSubmitCooldown(submitCooldownMs);
        } else if (!isSubmitCoolingDown()) {
          applyButtonText(defaultButtonText);
        }
      } catch (_) {
        showRegisterError("注册请求失败，请稍后重试");
      } finally {
        registerSubmitButton.dataset.submitting = "false";
        if (!isSubmitCoolingDown()) {
          applyButtonText(defaultButtonText);
          setRegisterSubmitLocked(false);
        } else {
          setRegisterSubmitLocked(true);
        }
      }
    });
  }

  const captchaRefreshButton = document.getElementById("register-captcha-refresh");
  if (captchaRefreshButton && captchaRefreshButton.dataset.refreshBound !== "true") {
    captchaRefreshButton.dataset.refreshBound = "true";
    captchaRefreshButton.addEventListener("click", () => {
      loadRegisterCaptcha(registerHutoolCaptchaApi.getCurrentCaptchaUuid()).catch(() => {
        showRegisterCaptchaError("Failed to refresh captcha, please try again.");
      });
    });
  }

  const captchaCancelButton = document.getElementById("register-captcha-cancel");
  if (captchaCancelButton && captchaCancelButton.dataset.cancelBound !== "true") {
    captchaCancelButton.dataset.cancelBound = "true";
    captchaCancelButton.addEventListener("click", closeRegisterCaptchaModal);
  }

  const captchaConfirmButton = document.getElementById("register-captcha-confirm");
  if (captchaConfirmButton && captchaConfirmButton.dataset.confirmBound !== "true") {
    captchaConfirmButton.dataset.confirmBound = "true";
    captchaConfirmButton.addEventListener("click", () => {
      continueRegisterWithCaptcha().catch(() => {
        showRegisterCaptchaError("Captcha verification failed, please try again.");
      });
    });
  }

  const tianaiCancel = document.getElementById("register-tianai-cancel");
  if (tianaiCancel && tianaiCancel.dataset.bound !== "true") {
    tianaiCancel.dataset.bound = "true";
    tianaiCancel.addEventListener("click", closeTianaiModal);
  }

  const tianaiRefresh = document.getElementById("register-tianai-refresh");
  if (tianaiRefresh && tianaiRefresh.dataset.bound !== "true") {
    tianaiRefresh.dataset.bound = "true";
    tianaiRefresh.addEventListener("click", () => {
      loadTianaiCaptcha(registerTianaiApi.getCurrentTianaiSubType());
    });
  }

  const tianaiConfirm = document.getElementById("register-tianai-confirm");
  if (tianaiConfirm && tianaiConfirm.dataset.bound !== "true") {
    tianaiConfirm.dataset.bound = "true";
    tianaiConfirm.addEventListener("click", continueRegisterWithTianai);
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    CAPTCHA_SUCCESS_FEEDBACK_MIN_MS,
    HCAPTCHA_AUTO_RETRY_DELAY_MS,
    buildTianaiRotatePercentage,
    buildTianaiTrackPayload,
    buildTianaiWordClickPayload,
    getCaptchaSuccessFeedbackDelay,
    getConcatRenderData,
    buildConcatLayerMarkup,
    resetCaptchaImageVisibility,
    renderHCaptcha
  };
}
