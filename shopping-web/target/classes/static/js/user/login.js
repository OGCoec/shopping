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
const registerPhoneRequiredView = document.getElementById("register-phone-required-view");
const forgotPasswordView = document.getElementById("forgot-password-view");
const signupLinkWrap = document.getElementById("signup-link-wrap");
const routeFragments = {
  register: {
    container: registerView,
    path: "/fragments/register-view.html",
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
const loginShellModule = globalThis.ShoppingLoginShell;
const loginOtpModule = globalThis.ShoppingLoginOtp;

if (!loginVisualsApi || !loginCountryPickerApi || !loginShellModule || !loginOtpModule) {
  throw new Error("login dependencies failed to load");
}

const {
  initializeVisuals,
  handleVisualResize,
  handleVisualVisibilityChange,
  triggerLoginError,
  updateCharacters
} = loginVisualsApi;

const {
  initPhoneCountryPicker,
  initRegisterPhoneRequiredCountryPicker,
  autoDetectPhoneCountryCode
} = loginCountryPickerApi;

const { createLoginShell } = loginShellModule;
const { createLoginOtp } = loginOtpModule;

let lastIdentifierView = "email";

const shellApi = createLoginShell({
  emailLoginView,
  phoneLoginView,
  passwordLoginView,
  otpLoginView,
  registerView,
  registerPhoneRequiredView,
  forgotPasswordView,
  signupLinkWrap,
  formContainer,
  routeFragments,
  initializeRegisterFragment() {
    if (typeof globalThis.initializeRegisterFragment === "function") {
      globalThis.initializeRegisterFragment();
    }
  }
});

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

window.addEventListener("popstate", () => {
  shellApi.applyRoute(window.location.pathname);
});

if (switchToEmailBtn) {
  switchToEmailBtn.addEventListener("click", (event) => {
    event.preventDefault();
    shellApi.navigateTo("/shopping/user/login");
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
  });
}

if (otpLoginBtn) {
  otpLoginBtn.addEventListener("click", otpApi.openOtpStep);
}

if (backToPasswordBtn) {
  backToPasswordBtn.addEventListener("click", () => {
    if (otpApi.getCurrentOtpScenario() === "register") {
      shellApi.setAuthView("register");
      return;
    }
    shellApi.setAuthView("password");
    if (passwordInput) {
      passwordInput.focus();
    }
  });
}

if (otpResendBtn) {
  otpResendBtn.addEventListener("click", () => {
    const otpErrorMessage = document.getElementById("otp-error-msg");
    if (!otpErrorMessage) return;

    otpErrorMessage.textContent = otpApi.getCurrentOtpScenario() === "register"
      ? "如需重新发送，请返回注册表单重新提交。"
      : otpApi.getOtpResendMessage();
    otpErrorMessage.style.display = "block";
  });
}

// ============ LOGIN COUNTRY PICKER MOVED TO auth/login/login-country-picker.js ============
// ============ LOGIN SHELL MOVED TO auth/login/login-shell.js ============
// ============ LOGIN OTP MOVED TO auth/login/login-otp.js ============
// ============ LOGIN VISUALS MOVED TO auth/login/login-visuals.js ============

const phoneErrorMessage = document.getElementById("phone-error-msg");
const phoneNumberLabel = document.getElementById("phone-number-label");
const registerPhoneRequiredInput = document.getElementById("register-phone-required-input");
const registerPhoneRequiredLabel = document.getElementById("register-phone-required-label");
const registerPhoneRequiredErrorMessage = document.getElementById("register-phone-required-error-msg");

function resetRegisterPhoneRequiredValidationState() {
  if (registerPhoneRequiredErrorMessage) registerPhoneRequiredErrorMessage.style.display = "none";
  if (registerPhoneRequiredInput) registerPhoneRequiredInput.classList.remove("error");
  if (registerPhoneRequiredLabel) registerPhoneRequiredLabel.classList.remove("error-label");
}

function showRegisterPhoneRequiredValidationError(message) {
  if (registerPhoneRequiredInput) registerPhoneRequiredInput.classList.add("error");
  if (registerPhoneRequiredLabel) registerPhoneRequiredLabel.classList.add("error-label");
  if (registerPhoneRequiredErrorMessage) {
    registerPhoneRequiredErrorMessage.textContent = message;
    registerPhoneRequiredErrorMessage.style.display = "block";
  }
  triggerLoginError();
}

function resetPhoneValidationState() {
  if (phoneErrorMessage) phoneErrorMessage.style.display = "none";
  if (phoneNumberInput) phoneNumberInput.classList.remove("error");
  if (phoneNumberLabel) phoneNumberLabel.classList.remove("error-label");
}

function showPhoneValidationError(message, input, label) {
  if (input) input.classList.add("error");
  if (label) label.classList.add("error-label");
  if (phoneErrorMessage) {
    phoneErrorMessage.textContent = message;
    phoneErrorMessage.style.display = "block";
  }
  triggerLoginError();
}

function resolveDialCodeValue(inputId) {
  return (document.getElementById(inputId)?.value || "").trim();
}

function goToPasswordStep(identifierType, identifierText) {
  if (!identifierLabel || !identifierValue || !passwordInput) return;

  otpApi.setIdentifierContext(identifierType, identifierText);
  identifierLabel.textContent = identifierType === "phone" ? "手机号码" : "电子邮箱地址";
  identifierValue.textContent = identifierText;
  passwordInput.value = "";
  passwordInput.type = "password";
  shellApi.setAuthView("password");
  passwordInput.focus();
}

// ============ FORM VALIDATION ============
const loginForm = document.getElementById("login-form");
if (loginForm) {
  loginForm.addEventListener("submit", (event) => {
    event.preventDefault();

    const authView = shellApi.getAuthView();

    if (authView === "register-phone-required") {
      const rawPhone = registerPhoneRequiredInput ? registerPhoneRequiredInput.value.trim() : "";
      const dialCode = resolveDialCodeValue("register-phone-country-code");
      resetRegisterPhoneRequiredValidationState();

      if (!dialCode) {
        showRegisterPhoneRequiredValidationError("请选择国家/地区");
        return;
      }

      if (!/^\d{6,15}$/.test(rawPhone)) {
        showRegisterPhoneRequiredValidationError("请输入有效的手机号码");
        return;
      }

      if (registerPhoneRequiredErrorMessage) {
        registerPhoneRequiredErrorMessage.textContent = `已记录手机号 ${dialCode} ${rawPhone}，短信验证流程待接入`;
        registerPhoneRequiredErrorMessage.style.display = "block";
      }
      return;
    }

    if (authView === "phone") {
      const rawPhone = phoneNumberInput ? phoneNumberInput.value.trim() : "";

      resetPhoneValidationState();

      const dialCode = resolveDialCodeValue("phone-country-code");
      if (!dialCode) {
        showPhoneValidationError("请选择国家/地区", phoneNumberInput, phoneNumberLabel);
        return;
      }

      if (!/^\d{6,15}$/.test(rawPhone)) {
        showPhoneValidationError("请输入有效的手机号码", phoneNumberInput, phoneNumberLabel);
        return;
      }

      lastIdentifierView = "phone";
      goToPasswordStep("phone", `${dialCode} ${rawPhone}`);
      return;
    }

    if (authView === "password") {
      const passwordErrorMessage = document.getElementById("password-error-msg");
      const rawPassword = passwordInput ? passwordInput.value : "";

      if (passwordErrorMessage) passwordErrorMessage.style.display = "none";

      if (!rawPassword || rawPassword.length < 6) {
        if (passwordErrorMessage) {
          passwordErrorMessage.textContent = "请输入至少 6 位密码";
          passwordErrorMessage.style.display = "block";
        }
        triggerLoginError();
        return;
      }

      if (passwordErrorMessage) {
        passwordErrorMessage.textContent = "密码校验通过（演示）";
        passwordErrorMessage.style.display = "block";
      }
      return;
    }

    if (authView === "otp") {
      const otpErrorMessage = document.getElementById("otp-error-msg");
      const rawOtp = otpCodeInput ? otpCodeInput.value.trim() : "";

      if (otpErrorMessage) otpErrorMessage.style.display = "none";

      if (!/^\d{4,8}$/.test(rawOtp)) {
        if (otpErrorMessage) {
          otpErrorMessage.textContent = "请输入有效验证码";
          otpErrorMessage.style.display = "block";
        }
        triggerLoginError();
        return;
      }

      if (otpApi.getCurrentOtpScenario() === "register" && otpApi.shouldRequirePhoneBinding()) {
        if (registerPhoneRequiredInput) {
          registerPhoneRequiredInput.value = "";
        }
        resetRegisterPhoneRequiredValidationState();
        shellApi.setAuthView("register-phone-required");
        if (registerPhoneRequiredInput) {
          registerPhoneRequiredInput.focus();
        }
        return;
      }

      if (otpErrorMessage) {
        otpErrorMessage.textContent = otpApi.getCurrentOtpScenario() === "register"
          ? "邮箱验证码输入已接入前端页面，后端创建账户流程待继续接入。"
          : "验证码校验通过（演示）";
        otpErrorMessage.style.display = "block";
      }
      return;
    }

    const email = emailInput.value.trim();
    const errEl = document.getElementById("error-msg");
    const emailLabel = document.getElementById("email-label");

    errEl.style.display = "none";
    emailInput.classList.remove("error");
    emailLabel.classList.remove("error-label");

    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      emailInput.classList.add("error");
      emailLabel.classList.add("error-label");
      errEl.textContent = "请输入有效的电子邮箱地址。";
      errEl.style.display = "block";
      triggerLoginError();
      return;
    }

    lastIdentifierView = "email";
    goToPasswordStep("email", email);
  });
}

// ============ INITIALIZATION ============
window.addEventListener("load", () => {
  preAuthClientApi?.bootstrapPreAuthToken?.().catch(() => {
  });
  initializeVisuals({ emailInput, phoneNumberInput });
  initPhoneCountryPicker();
  initRegisterPhoneRequiredCountryPicker();
  autoDetectPhoneCountryCode();
  shellApi.bindSpaRouteLinks();
  shellApi.applyRoute(window.location.pathname);
  updateCharacters();
});

document.addEventListener("visibilitychange", () => {
  handleVisualVisibilityChange(Boolean(document.hidden));
});

window.addEventListener("resize", () => {
  handleVisualResize();
});
