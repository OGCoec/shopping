(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingLoginOtp = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function createLoginOtp(options) {
    const {
      otpStepTitle,
      otpCodeLabel,
      otpResendBtn,
      backToPasswordBtn,
      otpSecondaryDivider,
      otpStepSubtitle,
      otpCodeInput,
      identifierValueNode,
      setAuthView
    } = options || {};

    let currentIdentifierType = "email";
    let currentIdentifierValue = "";
    let currentOtpScenario = "login";
    let currentLoginFactor = "EMAIL_OTP";
    let currentRegisterRiskLevel = "";
    let currentRegisterRequirePhoneBinding = false;

    function isRegisterEmailVerificationRoute() {
      if (typeof window === "undefined") {
        return false;
      }
      const pathname = window.location?.pathname || "";
      return pathname.replace(/\/+$/g, "") === "/shopping/user/email-verification"
        && currentOtpScenario === "register";
    }

    function isLoginTotpFactor() {
      return currentOtpScenario === "login" && currentLoginFactor === "TOTP";
    }

    function syncOtpViewCopy() {
      const shouldShowPasswordFallback = currentOtpScenario !== "register"
        && !isRegisterEmailVerificationRoute();
      const isTotp = isLoginTotpFactor();

      if (otpStepTitle) {
        otpStepTitle.textContent = isTotp ? "Enter authenticator code" : "Check your inbox";
      }
      if (otpCodeLabel) {
        otpCodeLabel.textContent = isTotp ? "Authenticator code" : "Verification code";
      }
      if (otpResendBtn) {
        if (currentOtpScenario === "register") {
          otpResendBtn.textContent = "Resend email";
          otpResendBtn.style.display = "inline-flex";
        } else if (isTotp) {
          otpResendBtn.textContent = "Use email code";
          otpResendBtn.style.display = "none";
        } else {
          otpResendBtn.textContent = "Resend code";
          otpResendBtn.style.display = "inline-flex";
        }
      }
      if (backToPasswordBtn) {
        backToPasswordBtn.textContent = "Use password";
        backToPasswordBtn.style.display = shouldShowPasswordFallback ? "block" : "none";
      }
      if (otpSecondaryDivider) {
        otpSecondaryDivider.style.display = shouldShowPasswordFallback ? "flex" : "none";
      }
    }

    function getOtpDeliveryText(destination) {
      if (currentOtpScenario === "register") {
        return `Enter the code we sent to ${destination}.`;
      }
      if (isLoginTotpFactor()) {
        return "Enter the 6-digit code from your authenticator app.";
      }
      if (currentIdentifierType === "phone") {
        return `Enter the code we sent to ${destination}.`;
      }
      return `Enter the code we sent to ${destination}.`;
    }

    function openOtpStep() {
      openLoginOtpStep("EMAIL_OTP");
    }

    function openLoginOtpStep(factor) {
      if (!otpStepSubtitle || !otpCodeInput) {
        return;
      }
      currentOtpScenario = "login";
      currentLoginFactor = factor === "TOTP" ? "TOTP" : "EMAIL_OTP";
      currentRegisterRiskLevel = "";
      currentRegisterRequirePhoneBinding = false;
      syncOtpViewCopy();
      const destination = currentIdentifierValue || identifierValueNode?.textContent || "";
      otpStepSubtitle.textContent = getOtpDeliveryText(destination);

      const otpErrorMessage = document.getElementById("otp-error-msg");
      if (otpErrorMessage) {
        otpErrorMessage.textContent = "";
        otpErrorMessage.style.display = "none";
      }

      otpCodeInput.value = "";
      if (typeof setAuthView === "function") {
        setAuthView("otp");
      }
      otpCodeInput.focus();
    }

    function openRegisterOtpStep(email, options = {}) {
      if (!otpStepSubtitle || !otpCodeInput) {
        return;
      }

      currentOtpScenario = "register";
      currentLoginFactor = "EMAIL_OTP";
      currentIdentifierType = "email";
      currentIdentifierValue = email || "";
      currentRegisterRiskLevel = (options?.riskLevel || "").toString().trim().toUpperCase();
      if (typeof options?.requirePhoneBinding === "boolean") {
        currentRegisterRequirePhoneBinding = options.requirePhoneBinding;
      } else {
        currentRegisterRequirePhoneBinding = ["L3", "L4", "L5"].includes(currentRegisterRiskLevel);
      }
      syncOtpViewCopy();

      const destination = currentIdentifierValue || "your email";
      otpStepSubtitle.textContent = getOtpDeliveryText(destination);

      const otpErrorMessage = document.getElementById("otp-error-msg");
      if (otpErrorMessage) {
        otpErrorMessage.textContent = "";
        otpErrorMessage.style.display = "none";
      }

      otpCodeInput.value = "";
      if (typeof setAuthView === "function") {
        setAuthView("otp");
      }
      otpCodeInput.focus();
    }

    function getOtpResendMessage() {
      if (isLoginTotpFactor()) {
        return "";
      }
      if (currentIdentifierType === "phone") {
        return "SMS code sent again.";
      }
      return "Email code sent again.";
    }

    function setIdentifierContext(identifierType, identifierText) {
      currentIdentifierType = identifierType;
      currentIdentifierValue = identifierText;
    }

    return {
      syncOtpViewCopy,
      getOtpDeliveryText,
      openOtpStep,
      openLoginOtpStep,
      openRegisterOtpStep,
      getOtpResendMessage,
      setIdentifierContext,
      setLoginFactorContext(factor, identifierType, identifierText) {
        currentOtpScenario = "login";
        currentLoginFactor = factor === "TOTP" ? "TOTP" : "EMAIL_OTP";
        if (identifierType) {
          currentIdentifierType = identifierType;
        }
        if (typeof identifierText === "string") {
          currentIdentifierValue = identifierText;
        }
        syncOtpViewCopy();
      },
      getCurrentIdentifierType() {
        return currentIdentifierType;
      },
      getCurrentIdentifierValue() {
        return currentIdentifierValue;
      },
      getCurrentOtpScenario() {
        return currentOtpScenario;
      },
      getCurrentLoginFactor() {
        return currentLoginFactor;
      },
      shouldRequirePhoneBinding() {
        return currentOtpScenario === "register" && Boolean(currentRegisterRequirePhoneBinding);
      },
      getCurrentRegisterRiskLevel() {
        return currentRegisterRiskLevel;
      }
    };
  }

  return {
    createLoginOtp
  };
});
