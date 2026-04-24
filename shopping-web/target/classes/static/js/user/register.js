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

if (
  !registerTianaiTrackApi
  || !authUtilsModule
  || !registerFormModule
  || !registerTianaiModule
  || !registerTurnstileModule
  || !registerHCaptchaModule
  || !registerHutoolCaptchaModule
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

const CAPTCHA_SUCCESS_FEEDBACK_MIN_MS = 1200;
const HCAPTCHA_AUTO_RETRY_DELAY_MS = 180;
const HCAPTCHA_AUTO_RETRY_LIMIT = 1;

let registerFormApi = null;
let registerTianaiApi = null;
let registerTurnstileApi = null;
let registerHCaptchaApi = null;
let registerHutoolCaptchaApi = null;

function showRegisterError(message, triggerAnimation = true) {
  const registerErrorMessage = document.getElementById("register-error-msg");
  if (!registerErrorMessage) return;
  registerErrorMessage.textContent = message;
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

function initializeRegisterFragment() {
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
        applyButtonText(`鐠囬鐡戝?${remainingSeconds}s`);
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
        showRegisterError("濞夈劌鍞界拠閿嬬湴婢惰精瑙﹂敍宀冾嚞缁嬪秴鎮楅柌宥堢槸");
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



