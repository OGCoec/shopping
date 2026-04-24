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
    let currentRegisterRiskLevel = "";
    let currentRegisterRequirePhoneBinding = false;

    function syncOtpViewCopy() {
      const shouldShowPasswordFallback = currentOtpScenario !== "register";

      if (otpStepTitle) {
        otpStepTitle.textContent = "检查您的收件箱";
      }
      if (otpCodeLabel) {
        otpCodeLabel.textContent = "验证码";
      }
      if (otpResendBtn) {
        otpResendBtn.textContent = currentOtpScenario === "register" ? "重新发送电子邮件" : "重新发送验证码";
      }
      if (backToPasswordBtn) {
        backToPasswordBtn.textContent = "使用密码继续";
        backToPasswordBtn.style.display = shouldShowPasswordFallback ? "block" : "none";
      }
      if (otpSecondaryDivider) {
        otpSecondaryDivider.style.display = shouldShowPasswordFallback ? "flex" : "none";
      }
    }

    function getOtpDeliveryText(destination) {
      if (currentOtpScenario === "register") {
        return `输入我们刚刚向 ${destination} 发送的验证码`;
      }

      if (currentIdentifierType === "phone") {
        return `输入我们刚刚向 ${destination} 发送的短信验证码`;
      }

      return `输入我们刚刚向 ${destination} 发送的邮箱验证码`;
    }

    function openOtpStep() {
      if (!otpStepSubtitle || !otpCodeInput) return;

      currentOtpScenario = "login";
      currentRegisterRiskLevel = "";
      currentRegisterRequirePhoneBinding = false;
      syncOtpViewCopy();
      const destination = currentIdentifierValue || identifierValueNode?.textContent || "";
      otpStepSubtitle.textContent = getOtpDeliveryText(destination);

      const otpErrorMessage = document.getElementById("otp-error-msg");
      if (otpErrorMessage) otpErrorMessage.style.display = "none";

      otpCodeInput.value = "";
      if (typeof setAuthView === "function") {
        setAuthView("otp");
      }
      otpCodeInput.focus();
    }

    function openRegisterOtpStep(email, options = {}) {
      if (!otpStepSubtitle || !otpCodeInput) return;

      currentOtpScenario = "register";
      currentIdentifierType = "email";
      currentIdentifierValue = email || "";
      currentRegisterRiskLevel = (options?.riskLevel || "").toString().trim().toUpperCase();
      if (typeof options?.requirePhoneBinding === "boolean") {
        currentRegisterRequirePhoneBinding = options.requirePhoneBinding;
      } else {
        currentRegisterRequirePhoneBinding = ["L3", "L4", "L5"].includes(currentRegisterRiskLevel);
      }
      syncOtpViewCopy();

      const destination = currentIdentifierValue || "当前邮箱";
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
      if (currentIdentifierType === "phone") {
        return "短信验证码已重新发送";
      }

      return "邮箱验证码已重新发送";
    }

    function setIdentifierContext(identifierType, identifierText) {
      currentIdentifierType = identifierType;
      currentIdentifierValue = identifierText;
    }

    return {
      syncOtpViewCopy,
      getOtpDeliveryText,
      openOtpStep,
      openRegisterOtpStep,
      getOtpResendMessage,
      setIdentifierContext,
      getCurrentIdentifierType() {
        return currentIdentifierType;
      },
      getCurrentIdentifierValue() {
        return currentIdentifierValue;
      },
      getCurrentOtpScenario() {
        return currentOtpScenario;
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
