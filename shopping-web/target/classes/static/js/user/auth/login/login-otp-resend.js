(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingLoginOtpResend = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const DEFAULT_RESEND_EMAIL_TEXT = "\u91cd\u65b0\u53d1\u9001\u7535\u5b50\u90ae\u4ef6";

  function createLoginOtpResend(options = {}) {
    const otpResendBtn = options.otpResendBtn || null;
    const otpApi = options.otpApi || null;
    const resendLoginEmailCode = typeof options.resendLoginEmailCode === "function"
      ? options.resendLoginEmailCode
      : async () => ({ success: false, message: "Resend is unavailable." });
    const triggerLoginError = typeof options.triggerLoginError === "function"
      ? options.triggerLoginError
      : () => {};

    let registerResendCooldownUntil = 0;
    let registerResendCooldownTimer = null;

    function clearRegisterResendCooldownTimer() {
      if (!registerResendCooldownTimer) {
        return;
      }
      clearInterval(registerResendCooldownTimer);
      registerResendCooldownTimer = null;
    }

    function applyOtpResendButtonState(text, disabled) {
      if (!otpResendBtn) {
        return;
      }
      otpResendBtn.textContent = text;
      otpResendBtn.disabled = disabled;
      otpResendBtn.setAttribute("aria-disabled", disabled ? "true" : "false");
    }

    function restoreOtpResendCopy() {
      otpApi?.syncOtpViewCopy?.();
      applyOtpResendButtonState(otpResendBtn?.textContent || DEFAULT_RESEND_EMAIL_TEXT, false);
    }

    function refreshRegisterResendCooldown() {
      const remainingMs = Math.max(0, registerResendCooldownUntil - Date.now());
      if (remainingMs <= 0) {
        clearRegisterResendCooldownTimer();
        registerResendCooldownUntil = 0;
        restoreOtpResendCopy();
        return;
      }

      const remainingSeconds = Math.max(1, Math.ceil(remainingMs / 1000));
      applyOtpResendButtonState(`Resend email (${remainingSeconds}s)`, true);
    }

    function startRegisterResendCooldown(cooldownMs) {
      const safeCooldownMs = Math.max(0, Math.round(Number(cooldownMs) || 0));
      if (safeCooldownMs <= 0) {
        return;
      }
      registerResendCooldownUntil = Date.now() + safeCooldownMs;
      clearRegisterResendCooldownTimer();
      refreshRegisterResendCooldown();
      registerResendCooldownTimer = setInterval(refreshRegisterResendCooldown, 200);
    }

    async function handleOtpResendClick() {
      const otpErrorMessage = document.getElementById("otp-error-msg");
      if (!otpResendBtn || !otpErrorMessage) {
        return;
      }

      const isRegisterScenario = otpApi?.getCurrentOtpScenario?.() === "register";
      if (!isRegisterScenario) {
        if (otpApi?.getCurrentLoginFactor?.() === "TOTP") {
          return;
        }
        try {
          const resendResult = await resendLoginEmailCode();
          otpErrorMessage.textContent = resendResult?.message || otpApi?.getOtpResendMessage?.() || "";
          otpErrorMessage.style.display = "block";
          if (!resendResult?.success) {
            triggerLoginError();
          }
        } catch (_) {
          otpErrorMessage.textContent = "Failed to resend the verification code.";
          otpErrorMessage.style.display = "block";
          triggerLoginError();
        }
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
      applyOtpResendButtonState("\u53d1\u9001\u4e2d...", true);
      try {
        if (typeof window.resendRegisterEmailCode !== "function") {
          otpErrorMessage.textContent = "\u91cd\u53d1\u529f\u80fd\u6682\u672a\u5c31\u7eea\uff0c\u8bf7\u8fd4\u56de\u6ce8\u518c\u8868\u5355\u91cd\u65b0\u63d0\u4ea4\u3002";
          otpErrorMessage.style.display = "block";
          triggerLoginError();
          return;
        }

        const resendResult = await window.resendRegisterEmailCode();
        const submitCooldownMs = Math.max(0, Math.round(Number(resendResult?.submitCooldownMs) || 0));

        otpErrorMessage.textContent = resendResult?.message || (resendResult?.success
          ? "\u90ae\u7bb1\u9a8c\u8bc1\u7801\u5df2\u91cd\u65b0\u53d1\u9001"
          : "\u91cd\u65b0\u53d1\u9001\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002");
        otpErrorMessage.style.display = "block";
        if (!resendResult?.success) {
          triggerLoginError();
        }
        if (submitCooldownMs > 0) {
          startRegisterResendCooldown(submitCooldownMs);
        }
      } catch (_) {
        otpErrorMessage.textContent = "\u91cd\u65b0\u53d1\u9001\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
        otpErrorMessage.style.display = "block";
        triggerLoginError();
      } finally {
        otpResendBtn.dataset.submitting = "false";
        if (Date.now() >= registerResendCooldownUntil) {
          clearRegisterResendCooldownTimer();
          registerResendCooldownUntil = 0;
          restoreOtpResendCopy();
        }
      }
    }

    function bindOtpResend() {
      if (!otpResendBtn) {
        return;
      }
      otpResendBtn.addEventListener("click", handleOtpResendClick);
    }

    return {
      bindOtpResend,
      clearRegisterResendCooldownTimer,
      refreshRegisterResendCooldown,
      startRegisterResendCooldown,
      handleOtpResendClick
    };
  }

  return {
    createLoginOtpResend
  };
});
