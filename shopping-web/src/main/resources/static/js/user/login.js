// ============ UI & FORM STATE ============
const emailInput = document.getElementById("email");
const phoneNumberInput = document.getElementById("phone-number");
const passwordInput = document.getElementById("password-input");
const emailLoginView = document.getElementById("email-login-view");
const phoneLoginView = document.getElementById("phone-login-view");
const passwordLoginView = document.getElementById("password-login-view");
const otpLoginView = document.getElementById("otp-login-view");
const phoneBtn = document.getElementById("btn-phone");
const switchToEmailBtn = document.getElementById("switch-to-email");
const identifierLabel = document.getElementById("identifier-label");
const identifierValue = document.getElementById("identifier-value");
const editIdentifierBtn = document.getElementById("edit-identifier-btn");
const togglePasswordVisibilityBtn = document.getElementById("toggle-password-visibility");
const otpLoginBtn = document.getElementById("otp-login-btn");
const githubBtn = document.getElementById("btn-github");
const googleBtn = document.getElementById("btn-google");
const microsoftBtn = document.getElementById("btn-microsoft");
const otpCodeInput = document.getElementById("otp-code-input");
const otpStepSubtitle = document.getElementById("otp-step-subtitle");
const otpResendBtn = document.getElementById("otp-resend-btn");
const backToPasswordBtn = document.getElementById("back-to-password-btn");
const otpStepTitle = document.querySelector("#otp-login-view .otp-step-title");
const otpCodeLabel = document.getElementById("otp-code-label");
const otpSecondaryDivider = document.querySelector("#otp-login-view .password-alt-divider");
const formContainer = document.querySelector(".form-container");
const registerView = document.getElementById("register-view");
const registerPasswordView = document.getElementById("register-password-view");
const registerPhoneRequiredView = document.getElementById("register-phone-required-view");
const sessionEndedView = document.getElementById("session-ended-view");
const forgotPasswordView = document.getElementById("forgot-password-view");
const signupLinkWrap = document.getElementById("signup-link-wrap");
const routeFragments = {
  register: {
    container: registerView,
    path: "/fragments/register-view.html",
    loaded: false,
    loadingTask: null
  },
  "register-password": {
    container: registerPasswordView,
    path: "/fragments/register-password-view.html",
    loaded: false,
    loadingTask: null
  },
  "forgot-password": {
    container: forgotPasswordView,
    path: "/fragments/forgot-password-view.html",
    loaded: false,
    loadingTask: null
  }
};

const loginVisualsApi = globalThis.ShoppingLoginVisuals;
const loginCountryPickerApi = globalThis.ShoppingLoginCountryPicker;
const preAuthClientApi = globalThis.ShoppingPreAuthClient;
const authRoutesApi = globalThis.ShoppingAuthRoutes;
const loginShellModule = globalThis.ShoppingLoginShell;
const loginOtpModule = globalThis.ShoppingLoginOtp;
const loginOtpResendModule = globalThis.ShoppingLoginOtpResend;
const loginRouteNoticeModule = globalThis.ShoppingLoginRouteNotice;
const loginSubmitHandlersModule = globalThis.ShoppingLoginSubmitHandlers;
const loginRegisterBridgeModule = globalThis.ShoppingLoginRegisterBridge;
const loginCaptchaCoordinatorModule = globalThis.ShoppingLoginCaptchaCoordinator;
const authUtilsModule = globalThis.ShoppingAuthUtils;
const registerTianaiModule = globalThis.ShoppingRegisterTianai;
const registerTurnstileModule = globalThis.ShoppingRegisterTurnstile;
const registerHCaptchaModule = globalThis.ShoppingRegisterHCaptcha;
const registerRecaptchaModule = globalThis.ShoppingRegisterRecaptcha;
const registerHutoolCaptchaModule = globalThis.ShoppingRegisterHutoolCaptcha;
const registerCaptchaCoordinatorModule = globalThis.ShoppingRegisterCaptchaCoordinator;
const passwordResetModule = globalThis.ShoppingPasswordReset;
const PHONE_VALIDATE_PATH = "/shopping/auth/preauth/phone-validate";
const AUTH_PATHS = authRoutesApi?.PATHS || {
  LOGIN: "/shopping/user/log-in",
  LOGIN_PASSWORD: "/shopping/user/log-in/password",
  CREATE_ACCOUNT: "/shopping/user/create-account",
  CREATE_ACCOUNT_PASSWORD: "/shopping/user/create-account/password",
  EMAIL_VERIFICATION: "/shopping/user/email-verification",
  TOTP_VERIFICATION: "/shopping/user/totp-verification",
  ADD_PHONE: "/shopping/user/add-phone",
  REGISTER_EMAIL_VERIFICATION: "/shopping/user/email-verification?mode=register",
  LOGIN_EMAIL_VERIFICATION: "/shopping/user/email-verification?mode=login",
  LOGIN_TOTP_VERIFICATION: "/shopping/user/totp-verification?mode=login",
  REGISTER_ADD_PHONE: "/shopping/user/add-phone?mode=register",
  LOGIN_ADD_PHONE: "/shopping/user/add-phone?mode=login",
  SESSION_ENDED: "/shopping/user/session-ended",
  RESET_PASSWORD_URL: "/shopping/user/reset-password-url",
  RESET_PASSWORD_CODE: "/shopping/user/reset-password-code"
};

if (!loginVisualsApi || !loginCountryPickerApi || !loginShellModule || !loginOtpModule
    || !loginOtpResendModule || !loginRouteNoticeModule || !loginSubmitHandlersModule
    || !loginRegisterBridgeModule || !loginCaptchaCoordinatorModule || !authUtilsModule
    || !registerTianaiModule || !registerTurnstileModule || !registerHCaptchaModule
    || !registerRecaptchaModule
    || !registerHutoolCaptchaModule || !registerCaptchaCoordinatorModule || !passwordResetModule) {
  throw new Error("login dependencies failed to load");
}

const {
  initializeVisuals,
  handleVisualResize,
  handleVisualVisibilityChange,
  triggerLoginError,
  updateCharacters,
  applyFocusModeFromActiveElement
} = loginVisualsApi;

const {
  initPhoneCountryPicker,
  initRegisterPhoneRequiredCountryPicker,
  autoDetectPhoneCountryCode
} = loginCountryPickerApi;
const {
  waitForCaptchaSuccessFeedback,
  waitForNextPaint,
  getElementDisplaySize
} = authUtilsModule;

const { createLoginShell } = loginShellModule;
const { createLoginOtp } = loginOtpModule;
const { createLoginOtpResend } = loginOtpResendModule;
const { createLoginRouteNotice } = loginRouteNoticeModule;
const { createLoginSubmitHandlers } = loginSubmitHandlersModule;
const { createLoginRegisterBridge } = loginRegisterBridgeModule;
const { createLoginCaptchaCoordinator } = loginCaptchaCoordinatorModule;
const { createRegisterTianai } = registerTianaiModule;
const { createRegisterTurnstile } = registerTurnstileModule;
const { createRegisterHCaptcha } = registerHCaptchaModule;
const { createRegisterRecaptcha } = registerRecaptchaModule;
const { createRegisterHutoolCaptcha } = registerHutoolCaptchaModule;
const { createRegisterCaptchaCoordinator } = registerCaptchaCoordinatorModule;
const { initializePasswordResetFragment } = passwordResetModule;

let lastIdentifierView = "email";

const shellApi = createLoginShell({
  emailLoginView,
  phoneLoginView,
  passwordLoginView,
  otpLoginView,
  registerView,
  registerPasswordView,
  registerPhoneRequiredView,
  sessionEndedView,
  forgotPasswordView,
  signupLinkWrap,
  formContainer,
  routeFragments,
  initializeRouteFragment(view) {
    if (typeof globalThis.initializeRegisterRouteFragment === "function") {
      globalThis.initializeRegisterRouteFragment(view);
    }
    if (view === "forgot-password") {
      initializePasswordResetFragment({
        shellApi,
        preAuthClientApi
      });
    }
  }
});
window.ShoppingAuthShellApi = shellApi;

const otpApi = createLoginOtp({
  otpStepTitle,
  otpCodeLabel,
  otpResendBtn,
  backToPasswordBtn,
  otpSecondaryDivider,
  otpStepSubtitle,
  otpCodeInput,
  identifierValueNode: identifierValue,
  setAuthView(view) {
    shellApi.setAuthView(view);
  }
});

window.openRegisterOtpStep = otpApi.openRegisterOtpStep;

const routeNoticeApi = createLoginRouteNotice({
  authRoutesApi,
  authPaths: AUTH_PATHS,
  triggerLoginError
});
const {
  consumeRegisterRouteNotice,
  getRegisterNoticeMessage,
  showInlineRouteNotice
} = routeNoticeApi;
window.ShoppingLoginRouteNoticeApi = routeNoticeApi;

if (phoneBtn) {
  phoneBtn.addEventListener("click", () => shellApi.setAuthView("phone"));
}

function bindOAuthLogin(button, provider) {
  if (!button) return;

  button.addEventListener("click", () => {
    window.location.href = `/oauth2/${provider}/login`;
  });
}

bindOAuthLogin(githubBtn, "github");
bindOAuthLogin(googleBtn, "google");
bindOAuthLogin(microsoftBtn, "microsoft");

if (switchToEmailBtn) {
  switchToEmailBtn.addEventListener("click", (event) => {
    event.preventDefault();
    shellApi.navigateToView("email");
  });
}

if (editIdentifierBtn) {
  editIdentifierBtn.addEventListener("click", () => {
    shellApi.setAuthView(lastIdentifierView);
    if (lastIdentifierView === "phone" && phoneNumberInput) {
      phoneNumberInput.focus();
      return;
    }
    if (emailInput) {
      emailInput.focus();
    }
  });
}

if (togglePasswordVisibilityBtn && passwordInput) {
  togglePasswordVisibilityBtn.addEventListener("click", () => {
    passwordInput.type = passwordInput.type === "password" ? "text" : "password";
    passwordInput.focus({ preventScroll: true });
    applyFocusModeFromActiveElement();
  });
}

if (backToPasswordBtn) {
  backToPasswordBtn.addEventListener("click", () => {
    handleBackToPassword();
  });
}

function getCurrentRouteTarget() {
  return `${window.location.pathname}${window.location.search}${window.location.hash}`;
}

async function restoreAuthRouteState(routeTarget) {
  const currentRoute = routeTarget || getCurrentRouteTarget();
  const currentPath = authRoutesApi?.canonicalPathForPathname?.(window.location.pathname) || window.location.pathname;
  const currentMode = authRoutesApi?.modeForRoute?.(currentRoute) || "";

  if (currentMode === "register") {
    await restoreCurrentRegisterRoute(currentRoute);
    return;
  }
  if (currentMode === "login") {
    await restoreLoginFlowState(currentRoute);
    return;
  }

  const restoredLoginFlow = await restoreLoginFlowState(currentPath);
  if (!restoredLoginFlow) {
    await restoreCurrentRegisterRoute(currentPath);
  }
}

// ============ LOGIN COUNTRY PICKER MOVED TO auth/login/login-country-picker.js ============
// ============ LOGIN SHELL MOVED TO auth/login/login-shell.js ============
// ============ LOGIN OTP MOVED TO auth/login/login-otp.js ============
// ============ LOGIN VISUALS MOVED TO auth/login/login-visuals.js ============

const phoneErrorMessage = document.getElementById("phone-error-msg");
const phoneNumberLabel = document.getElementById("phone-number-label");
const registerPhoneRequiredInput = document.getElementById("register-phone-required-input");
const registerPhoneRequiredLabel = document.getElementById("register-phone-required-label");
const registerPhoneRequiredCodeGroup = document.getElementById("register-phone-required-code-group");
const registerPhoneRequiredCodeInput = document.getElementById("register-phone-required-code-input");
const registerPhoneRequiredSendCodeButton = document.getElementById("register-phone-required-send-code");
const registerPhoneRequiredErrorMessage = document.getElementById("register-phone-required-error-msg");
const loginForm = document.getElementById("login-form");

const loginSubmitHandlersApi = createLoginSubmitHandlers({
  shellApi,
  otpApi,
  preAuthClientApi,
  phoneValidatePath: PHONE_VALIDATE_PATH,
  authPaths: AUTH_PATHS,
  loginForm,
  emailInput,
  phoneNumberInput,
  passwordInput,
  otpCodeInput,
  phoneErrorMessage,
  phoneNumberLabel,
  identifierLabel,
  identifierValue,
  setLastIdentifierView(view) {
    lastIdentifierView = view;
  },
  triggerLoginError
});
const {
  bindLoginFormSubmit,
  resolveDialCodeValue,
  validatePhoneNumberPolicy,
  setHandleRegisterSubmit,
  setHandleLoginChallenge,
  showEmailError,
  clearEmailError,
  requestLoginEmailCode,
  submitPendingLoginChallenge,
  resolveSuccessfulLoginStart,
  getPendingLoginStartPayload,
  restoreLoginFlowState,
  submitLoginPhoneCode,
  submitLoginPhoneBinding,
  submitRegisterPhoneCode,
  submitRegisterPhoneBinding
} = loginSubmitHandlersApi;

const loginCaptchaCoordinatorApi = createLoginCaptchaCoordinator({
  createRegisterCaptchaCoordinator,
  createRegisterTianai,
  createRegisterHutoolCaptcha,
  createRegisterTurnstile,
  createRegisterHCaptcha,
  createRegisterRecaptcha,
  submitPendingLoginChallenge,
  getPendingLoginStartPayload,
  resolveLoginChallengeSuccess: resolveSuccessfulLoginStart,
  showLoginError: showEmailError,
  clearLoginError: clearEmailError,
  triggerCaptchaFailureAnimation: triggerLoginError,
  waitForCaptchaSuccessFeedback,
  waitForNextPaint,
  getElementDisplaySize,
  captchaSuccessFeedbackMinMs: 1200,
  hcaptchaAutoRetryDelayMs: 180,
  hcaptchaAutoRetryLimit: 1
});
setHandleLoginChallenge((payload, context) =>
  loginCaptchaCoordinatorApi.handleLoginChallengeRequirement(payload, context));

if (otpLoginBtn) {
  otpLoginBtn.addEventListener("click", () => {
    requestLoginEmailCode();
  });
}

const otpResendApi = createLoginOtpResend({
  otpResendBtn,
  otpApi,
  resendLoginEmailCode: requestLoginEmailCode,
  triggerLoginError
});
otpResendApi.bindOtpResend();
window.startRegisterEmailResendCooldown = otpResendApi.startRegisterResendCooldown;

const registerBridgeApi = createLoginRegisterBridge({
  shellApi,
  otpApi,
  authPaths: AUTH_PATHS,
  passwordInput,
  otpCodeInput,
  registerPhoneRequiredInput,
  registerPhoneRequiredLabel,
  registerPhoneRequiredCodeGroup,
  registerPhoneRequiredCodeInput,
  registerPhoneRequiredSendCodeButton,
  registerPhoneRequiredErrorMessage,
  resolveDialCodeValue,
  validatePhoneNumberPolicy,
  submitLoginPhoneCode,
  submitLoginPhoneBinding,
  submitRegisterPhoneCode,
  submitRegisterPhoneBinding,
  handlePhoneSmsChallenge(payload, context) {
    return loginCaptchaCoordinatorApi.handleLoginChallengeRequirement(payload, context);
  },
  triggerLoginError
});
const {
  handleRegisterSubmit,
  handleBackToPassword,
  restoreCurrentRegisterRoute,
  applyRegisterRouteNoticeEffects
} = registerBridgeApi;

setHandleRegisterSubmit(handleRegisterSubmit);
bindLoginFormSubmit();

window.addEventListener("popstate", () => {
  const currentRoute = getCurrentRouteTarget();
  shellApi.applyRoute(currentRoute).then(async () => {
    await restoreAuthRouteState(currentRoute);
  }).catch(() => {
  });
});

// ============ INITIALIZATION ============
window.addEventListener("load", () => {
  const routeNotice = consumeRegisterRouteNotice();
  const revealResolvedAuthRoute = () => {
    document.body?.classList.remove("auth-route-pending");
  };
  preAuthClientApi?.bootstrapPreAuthToken?.().catch(() => {
  });
  initializeVisuals({ emailInput, phoneNumberInput });
  initPhoneCountryPicker();
  initRegisterPhoneRequiredCountryPicker();
  autoDetectPhoneCountryCode();
  shellApi.bindSpaRouteLinks();
  shellApi.applyRoute(getCurrentRouteTarget()).then(async () => {
    const currentPath = authRoutesApi?.canonicalPathForPathname?.(window.location.pathname) || window.location.pathname;
    await restoreAuthRouteState(getCurrentRouteTarget());
    applyRegisterRouteNoticeEffects(routeNotice);
    showInlineRouteNotice(currentPath, getRegisterNoticeMessage(routeNotice));
  }).catch(() => {
  }).finally(revealResolvedAuthRoute);
  updateCharacters();
});

document.addEventListener("visibilitychange", () => {
  handleVisualVisibilityChange(Boolean(document.hidden));
});

window.addEventListener("resize", () => {
  handleVisualResize();
});
