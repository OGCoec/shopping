(function () {
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
const registerRecaptchaModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-recaptcha.js")
  : globalThis.ShoppingRegisterRecaptcha;
const registerHutoolCaptchaModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-hutool-captcha.js")
  : globalThis.ShoppingRegisterHutoolCaptcha;
const preAuthClientModule = typeof module !== "undefined" && module.exports
  ? require("./auth/shared/preauth-client.js")
  : globalThis.ShoppingPreAuthClient;
const authRoutesModule = typeof module !== "undefined" && module.exports
  ? require("./auth/shared/auth-routes.js")
  : globalThis.ShoppingAuthRoutes;
const registerRouteStateModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-route-state.js")
  : globalThis.ShoppingRegisterRouteState;
const registerFlowModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-flow.js")
  : globalThis.ShoppingRegisterFlow;
const registerEntryModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-entry.js")
  : globalThis.ShoppingRegisterEntry;
const registerOtpStepModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-otp-step.js")
  : globalThis.ShoppingRegisterOtpStep;
const registerCaptchaCoordinatorModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-captcha-coordinator.js")
  : globalThis.ShoppingRegisterCaptchaCoordinator;
const registerPasswordStepModule = typeof module !== "undefined" && module.exports
  ? require("./auth/register/register-password-step.js")
  : globalThis.ShoppingRegisterPasswordStep;

if (
  !registerTianaiTrackApi
  || !authUtilsModule
  || !registerFormModule
  || !registerTianaiModule
  || !registerTurnstileModule
  || !registerHCaptchaModule
  || !registerRecaptchaModule
  || !registerHutoolCaptchaModule
  || !preAuthClientModule
  || !registerRouteStateModule
  || !registerFlowModule
  || !registerEntryModule
  || !registerOtpStepModule
  || !registerCaptchaCoordinatorModule
  || !registerPasswordStepModule
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
const { createRegisterForm, buildRegisterDeviceFingerprint } = registerFormModule;
const {
  getConcatRenderData,
  buildConcatLayerMarkup,
  resetCaptchaImageVisibility,
  createRegisterTianai
} = registerTianaiModule;
const { createRegisterTurnstile } = registerTurnstileModule;
const { createRegisterHCaptcha } = registerHCaptchaModule;
const { createRegisterRecaptcha } = registerRecaptchaModule;
const { createRegisterHutoolCaptcha } = registerHutoolCaptchaModule;
const {
  readRegisterDraftState,
  updateRegisterDraftState,
  clearRegisterDraftState
} = registerRouteStateModule;
const { createRegisterFlow } = registerFlowModule;
const { createRegisterEntry } = registerEntryModule;
const { createRegisterOtpStep } = registerOtpStepModule;
const { createRegisterCaptchaCoordinator } = registerCaptchaCoordinatorModule;
const { createRegisterPasswordStep } = registerPasswordStepModule;
const registerFlowApi = createRegisterFlow({
  preAuthClientApi: preAuthClientModule,
  authRoutesApi: authRoutesModule,
  buildRegisterDeviceFingerprint
});
const {
  REGISTER_STEP_PATHS,
  fetchCurrentRegisterFlowState,
  navigateToRegisterStep
} = registerFlowApi;

const CAPTCHA_SUCCESS_FEEDBACK_MIN_MS = 1200;
const HCAPTCHA_AUTO_RETRY_DELAY_MS = 180;
const HCAPTCHA_AUTO_RETRY_LIMIT = 1;

let registerFormApi = null;
const REGISTER_ERROR_FALLBACK_MESSAGE = "\u6ce8\u518c\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";

function isMojibakeLikeMessage(text) {
  if (!text) {
    return false;
  }
  if (text.includes("\ufffd") || text.includes("\u951f")) {
    return true;
  }
  return false;
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

function showRegisterEntryError(message, triggerAnimation = true) {
  const registerEntryErrorMessage = document.getElementById("register-entry-error-msg");
  if (!registerEntryErrorMessage) return;
  registerEntryErrorMessage.textContent = normalizeRegisterErrorMessage(message);
  registerEntryErrorMessage.style.display = "block";
  if (triggerAnimation) {
    triggerLoginError();
  }
}

function clearRegisterEntryError() {
  const registerEntryErrorMessage = document.getElementById("register-entry-error-msg");
  if (!registerEntryErrorMessage) return;
  registerEntryErrorMessage.textContent = "";
  registerEntryErrorMessage.style.display = "none";
}

function triggerRegisterCaptchaFailureAnimation() {
  if (typeof triggerLoginError === "function") {
    triggerLoginError();
  }
}

const registerEntryApi = createRegisterEntry({
  readRegisterDraftState,
  updateRegisterDraftState,
  startRegisterFlow: registerFlowApi.startRegisterFlow,
  navigateToRegisterStep,
  showRegisterEntryError,
  clearRegisterEntryError,
  registerStepPaths: REGISTER_STEP_PATHS,
  invalidEmailMessage: "\u8bf7\u8f93\u5165\u6709\u6548\u7684\u7535\u5b50\u90ae\u7bb1\u5730\u5740"
});
const {
  continueRegisterEntryStep,
  restoreRegisterEntryStepFromSession,
  initializeRegisterEntryFragment
} = registerEntryApi;
const registerOtpApi = createRegisterOtpStep({
  getRegisterFormApi: () => registerFormApi,
  fetchCurrentRegisterFlowState,
  navigateToRegisterStep,
  registerStepPaths: REGISTER_STEP_PATHS,
  buildRegisterDeviceFingerprint,
  clearRegisterDraftState,
  showRegisterError,
  clearRegisterError,
  wafReplayEventName: preAuthClientModule.WAF_REPLAY_EVENT_NAME
});
const {
  openRegisterOtpAfterEmailSent,
  restoreRegisterOtpStepFromSession,
  resendRegisterEmailCodeFromOtp,
  verifyRegisterEmailCodeFromOtp,
  bindRegisterWafReplayHandler
} = registerOtpApi;
const registerCaptchaApi = createRegisterCaptchaCoordinator({
  createRegisterTianai,
  createRegisterHutoolCaptcha,
  createRegisterTurnstile,
  createRegisterHCaptcha,
  createRegisterRecaptcha,
  getRegisterFormApi: () => registerFormApi,
  showRegisterError,
  triggerCaptchaFailureAnimation: triggerRegisterCaptchaFailureAnimation,
  openRegisterOtpAfterEmailSent,
  waitForCaptchaSuccessFeedback,
  waitForNextPaint,
  getElementDisplaySize,
  captchaSuccessFeedbackMinMs: CAPTCHA_SUCCESS_FEEDBACK_MIN_MS,
  hcaptchaAutoRetryDelayMs: HCAPTCHA_AUTO_RETRY_DELAY_MS,
  hcaptchaAutoRetryLimit: HCAPTCHA_AUTO_RETRY_LIMIT
});
const {
  showRegisterCaptchaError,
  clearRegisterCaptchaError,
  openRegisterCaptchaModal,
  closeRegisterCaptchaModal,
  showTurnstileError,
  clearTurnstileError,
  openTurnstileModal,
  closeTurnstileModal,
  showHCaptchaError,
  clearHCaptchaError,
  openHCaptchaModal,
  closeHCaptchaModal,
  scheduleHCaptchaAutoRetry,
  loadTurnstileScript,
  loadHCaptchaScript,
  renderTurnstileCaptcha,
  continueRegisterWithTurnstile,
  renderHCaptcha,
  continueRegisterWithHCaptcha,
  executeRecaptcha,
  showTianaiError,
  clearTianaiError,
  closeTianaiModal,
  openTianaiModal,
  loadTianaiCaptcha,
  continueRegisterWithTianai,
  loadRegisterCaptcha,
  continueRegisterWithCaptcha,
  bindRegisterCaptchaControls
} = registerCaptchaApi;
const registerPasswordApi = createRegisterPasswordStep({
  getRegisterFormApi: () => registerFormApi,
  readRegisterDraftState,
  updateRegisterDraftState,
  fetchCurrentRegisterFlowState,
  navigateToRegisterStep,
  registerStepPaths: REGISTER_STEP_PATHS,
  buildRegisterDeviceFingerprint,
  showRegisterError,
  clearRegisterError,
  bindRegisterCaptchaControls
});
const {
  restoreRegisterPasswordStepFromSession,
  submitRegisterPasswordStep,
  initializeRegisterPasswordFragment
} = registerPasswordApi;

registerFormApi = createRegisterForm({
  showRegisterError,
  clearRegisterError,
  openRegisterOtpAfterEmailSent,
  loadRegisterCaptcha() {
    return loadRegisterCaptcha();
  },
  openRegisterCaptchaModal() {
    return openRegisterCaptchaModal();
  },
  renderTurnstileCaptcha(siteKey) {
    return renderTurnstileCaptcha(siteKey);
  },
  renderHCaptcha(siteKey) {
    return renderHCaptcha(siteKey);
  },
  executeRecaptcha(siteKey) {
    return executeRecaptcha(siteKey);
  },
  loadTianaiCaptcha(subType) {
    return loadTianaiCaptcha(subType);
  },
  openTianaiModal() {
    return openTianaiModal();
  }
});
bindRegisterWafReplayHandler();

if (typeof window !== "undefined") {
  window.continueRegisterEntryStep = continueRegisterEntryStep;
  window.submitRegisterPasswordStep = submitRegisterPasswordStep;
  window.resendRegisterEmailCode = resendRegisterEmailCodeFromOtp;
  window.verifyRegisterEmailCode = verifyRegisterEmailCodeFromOtp;
  window.restoreRegisterEntryStepFromSession = restoreRegisterEntryStepFromSession;
  window.restoreRegisterPasswordStepFromSession = restoreRegisterPasswordStepFromSession;
  window.restoreRegisterOtpStepFromSession = restoreRegisterOtpStepFromSession;
  window.initializeRegisterRouteFragment = initializeRegisterRouteFragment;
  window.clearRegisterFlowLocalState = () => {
    registerFormApi?.clearPendingRegisterPayload?.();
  };
  window.clearRegisterDraftState = clearRegisterDraftState;
}

function initializeRegisterRouteFragment(view) {
  if (view === "register") {
    initializeRegisterEntryFragment();
    return;
  }
  if (view === "register-password") {
    initializeRegisterPasswordFragment();
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
})();
