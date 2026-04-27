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
const PHONE_VALIDATE_PATH = "/shopping/auth/preauth/phone-validate";

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
  let registerResendCooldownUntil = 0;
  let registerResendCooldownTimer = null;

  const clearRegisterResendCooldownTimer = () => {
    if (!registerResendCooldownTimer) {
      return;
    }
    clearInterval(registerResendCooldownTimer);
    registerResendCooldownTimer = null;
  };

  const applyOtpResendButtonState = (text, disabled) => {
    otpResendBtn.textContent = text;
    otpResendBtn.disabled = disabled;
    otpResendBtn.setAttribute("aria-disabled", disabled ? "true" : "false");
  };

  const refreshRegisterResendCooldown = () => {
    const remainingMs = Math.max(0, registerResendCooldownUntil - Date.now());
    if (remainingMs <= 0) {
      clearRegisterResendCooldownTimer();
      registerResendCooldownUntil = 0;
      otpApi.syncOtpViewCopy();
      applyOtpResendButtonState(otpResendBtn.textContent || "重新发送电子邮件", false);
      return;
    }
    const remainingSeconds = Math.max(1, Math.ceil(remainingMs / 1000));
    applyOtpResendButtonState(`${remainingSeconds}s 后可重发`, true);
  };

  const startRegisterResendCooldown = (cooldownMs) => {
    const safeCooldownMs = Math.max(0, Math.round(Number(cooldownMs) || 0));
    if (safeCooldownMs <= 0) {
      return;
    }
    registerResendCooldownUntil = Date.now() + safeCooldownMs;
    clearRegisterResendCooldownTimer();
    refreshRegisterResendCooldown();
    registerResendCooldownTimer = setInterval(refreshRegisterResendCooldown, 200);
  };

  otpResendBtn.addEventListener("click", async () => {
    const otpErrorMessage = document.getElementById("otp-error-msg");
    if (!otpErrorMessage) return;

    const isRegisterScenario = otpApi.getCurrentOtpScenario() === "register";
    if (!isRegisterScenario) {
      otpErrorMessage.textContent = otpApi.getOtpResendMessage();
      otpErrorMessage.style.display = "block";
      return;
    }

    if (otpResendBtn.dataset.submitting === "true") {
      return;
    }
    if (Date.now() < registerResendCooldownUntil) {
      refreshRegisterResendCooldown();
      return;
    }

    otpResendBtn.dataset.submitting = "true";
    applyOtpResendButtonState("发送中...", true);
    try {
      if (typeof window.resendRegisterEmailCode !== "function") {
        otpErrorMessage.textContent = "重发功能暂未就绪，请返回注册表单重新提交。";
        otpErrorMessage.style.display = "block";
        triggerLoginError();
        return;
      }

      const resendResult = await window.resendRegisterEmailCode();
      const submitCooldownMs = Math.max(0, Math.round(Number(resendResult?.submitCooldownMs) || 0));

      otpErrorMessage.textContent = resendResult?.message || (resendResult?.success
        ? "邮箱验证码已重新发送"
        : "重新发送失败，请稍后重试。");
      otpErrorMessage.style.display = "block";
      if (!resendResult?.success) {
        triggerLoginError();
      }
      if (submitCooldownMs > 0) {
        startRegisterResendCooldown(submitCooldownMs);
      }
    } catch (_) {
      otpErrorMessage.textContent = "重新发送失败，请稍后重试。";
      otpErrorMessage.style.display = "block";
      triggerLoginError();
    } finally {
      otpResendBtn.dataset.submitting = "false";
      if (Date.now() >= registerResendCooldownUntil) {
        clearRegisterResendCooldownTimer();
        registerResendCooldownUntil = 0;
        otpApi.syncOtpViewCopy();
        applyOtpResendButtonState(otpResendBtn.textContent || "重新发送电子邮件", false);
      }
    }
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

function resolvePhoneValidationMessage(payload) {
  const reasonCode = payload?.reasonCode || "";
  switch (reasonCode) {
    case "PHONE_VOIP_NOT_ALLOWED":
      return "Virtual or VoIP phone numbers are not allowed";
    case "PHONE_FIXED_LINE_NOT_ALLOWED":
      return "Landline phone numbers are not allowed";
    case "PHONE_TYPE_NOT_ALLOWED":
      return "Only mobile phone numbers are allowed";
    case "PHONE_INVALID_DIAL_CODE":
      return "Please choose a valid country or region";
    case "PHONE_INVALID":
      return "Please enter a valid mobile phone number";
    default:
      return payload?.message || "Phone number validation failed";
  }
}

async function validatePhoneNumberPolicy(dialCode, rawPhone) {
  const requestOptions = {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      dialCode,
      phoneNumber: rawPhone
    })
  };

  const response = preAuthClientApi?.fetchWithPreAuth
    ? await preAuthClientApi.fetchWithPreAuth(PHONE_VALIDATE_PATH, requestOptions)
    : await fetch(PHONE_VALIDATE_PATH, { ...requestOptions, credentials: "same-origin" });

  let payload = null;
  try {
    payload = await response.json();
  } catch (_) {
    payload = null;
  }

  if (!response.ok || !payload?.success) {
    return {
      success: false,
      message: resolvePhoneValidationMessage(payload),
      normalizedE164: payload?.normalizedE164 || ""
    };
  }

  return {
    success: true,
    message: payload.message || "ok",
    normalizedE164: payload.normalizedE164 || ""
  };
}

// ============ FORM VALIDATION ============
const loginForm = document.getElementById("login-form");
if (loginForm) {
  loginForm.addEventListener("submit", async (event) => {
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

      const registerPhoneValidationResult = await validatePhoneNumberPolicy(dialCode, rawPhone);
      if (!registerPhoneValidationResult.success) {
        showRegisterPhoneRequiredValidationError(registerPhoneValidationResult.message);
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

      const phoneValidationResult = await validatePhoneNumberPolicy(dialCode, rawPhone);
      if (!phoneValidationResult.success) {
        showPhoneValidationError(phoneValidationResult.message, phoneNumberInput, phoneNumberLabel);
        return;
      }

      lastIdentifierView = "phone";
      goToPasswordStep("phone", phoneValidationResult.normalizedE164 || `${dialCode} ${rawPhone}`);
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

      if (otpApi.getCurrentOtpScenario() === "register") {
        if (typeof window.verifyRegisterEmailCode !== "function") {
          if (otpErrorMessage) {
            otpErrorMessage.textContent = "注册验证功能暂不可用，请稍后重试。";
            otpErrorMessage.style.display = "block";
          }
          triggerLoginError();
          return;
        }

        try {
          const verifyResult = await window.verifyRegisterEmailCode(rawOtp);
          if (!verifyResult?.success) {
            if (otpErrorMessage) {
              otpErrorMessage.textContent = verifyResult?.message || "验证码校验失败，请重试。";
              otpErrorMessage.style.display = "block";
            }
            triggerLoginError();
            return;
          }

          if (verifyResult?.requirePhoneBinding) {
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
            otpErrorMessage.textContent = verifyResult?.message || "注册成功，账号已创建。";
            otpErrorMessage.style.display = "block";
          }
          return;
        } catch (_) {
          if (otpErrorMessage) {
            otpErrorMessage.textContent = "注册验证失败，请稍后重试。";
            otpErrorMessage.style.display = "block";
          }
          triggerLoginError();
          return;
        }
      }

      if (otpErrorMessage) {
        otpErrorMessage.textContent = "验证码校验通过（演示）";
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
