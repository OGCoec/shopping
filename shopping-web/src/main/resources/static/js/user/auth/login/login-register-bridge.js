(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingLoginRegisterBridge = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const DEFAULT_AUTH_PATHS = {
    CREATE_ACCOUNT: "/shopping/user/create-account",
    CREATE_ACCOUNT_PASSWORD: "/shopping/user/create-account/password",
    EMAIL_VERIFICATION: "/shopping/user/email-verification",
    ADD_PHONE: "/shopping/user/add-phone",
    REGISTER_EMAIL_VERIFICATION: "/shopping/user/email-verification?mode=register",
    REGISTER_ADD_PHONE: "/shopping/user/add-phone?mode=register",
    SESSION_ENDED: "/shopping/user/session-ended"
  };

  function createLoginRegisterBridge(options = {}) {
    const PHONE_VALIDATION_PURPOSE_BIND_PHONE = "BIND_PHONE";
    const shellApi = options.shellApi || null;
    const otpApi = options.otpApi || null;
    const authPaths = options.authPaths || DEFAULT_AUTH_PATHS;
    const passwordInput = options.passwordInput || null;
    const otpCodeInput = options.otpCodeInput || null;
    const registerPhoneRequiredInput = options.registerPhoneRequiredInput || null;
    const registerPhoneRequiredLabel = options.registerPhoneRequiredLabel || null;
    const registerPhoneRequiredCodeGroup = options.registerPhoneRequiredCodeGroup || null;
    const registerPhoneRequiredCodeInput = options.registerPhoneRequiredCodeInput || null;
    const registerPhoneRequiredSendCodeButton = options.registerPhoneRequiredSendCodeButton || null;
    const registerPhoneRequiredErrorMessage = options.registerPhoneRequiredErrorMessage || null;
    const resolveDialCodeValue = typeof options.resolveDialCodeValue === "function"
      ? options.resolveDialCodeValue
      : () => "";
    const validatePhoneNumberPolicy = typeof options.validatePhoneNumberPolicy === "function"
      ? options.validatePhoneNumberPolicy
      : async () => ({ success: false, message: "Phone number validation failed" });
    const submitLoginPhoneBinding = typeof options.submitLoginPhoneBinding === "function"
      ? options.submitLoginPhoneBinding
      : null;
    const submitLoginPhoneCode = typeof options.submitLoginPhoneCode === "function"
      ? options.submitLoginPhoneCode
      : null;
    const submitRegisterPhoneBinding = typeof options.submitRegisterPhoneBinding === "function"
      ? options.submitRegisterPhoneBinding
      : null;
    const submitRegisterPhoneCode = typeof options.submitRegisterPhoneCode === "function"
      ? options.submitRegisterPhoneCode
      : null;
    const handlePhoneSmsChallenge = typeof options.handlePhoneSmsChallenge === "function"
      ? options.handlePhoneSmsChallenge
      : async () => false;
    const triggerLoginError = typeof options.triggerLoginError === "function"
      ? options.triggerLoginError
      : () => {};
    let registerPhoneCodeSent = false;
    let registerPhoneCodeDialCode = "";
    let registerPhoneCodeRawPhone = "";
    let pendingRegisterPhoneSmsChallenge = null;
    let registerPhoneSendCooldownUntil = 0;
    let registerPhoneSendCooldownTimer = null;
    const registerPhoneSendCodeDefaultText = registerPhoneRequiredSendCodeButton?.textContent || "Send code";
    const registerPhoneCodeSentMessage = "\u77ed\u4fe1\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\uff0c\u8bf7\u8f93\u5165\u77ed\u4fe1\u9a8c\u8bc1\u7801\u3002";

    function resolveRetryAfterMs(payload, fallbackMs = 0) {
      const retryAfterMs = Number(payload?.retryAfterMs);
      if (Number.isFinite(retryAfterMs) && retryAfterMs > 0) {
        return Math.round(retryAfterMs);
      }
      return Math.max(0, Math.round(Number(fallbackMs) || 0));
    }

    function clearRegisterPhoneSendCooldownTimer() {
      if (!registerPhoneSendCooldownTimer) {
        return;
      }
      clearInterval(registerPhoneSendCooldownTimer);
      registerPhoneSendCooldownTimer = null;
    }

    function applyRegisterPhoneSendCodeButton(text, disabled) {
      if (!registerPhoneRequiredSendCodeButton) {
        return;
      }
      registerPhoneRequiredSendCodeButton.textContent = text;
      registerPhoneRequiredSendCodeButton.disabled = disabled;
      registerPhoneRequiredSendCodeButton.setAttribute("aria-disabled", disabled ? "true" : "false");
    }

    function resetRegisterPhoneSendCooldown() {
      clearRegisterPhoneSendCooldownTimer();
      registerPhoneSendCooldownUntil = 0;
      applyRegisterPhoneSendCodeButton(registerPhoneSendCodeDefaultText, false);
    }

    function refreshRegisterPhoneSendCooldown() {
      const remainingMs = Math.max(0, registerPhoneSendCooldownUntil - Date.now());
      if (remainingMs <= 0) {
        resetRegisterPhoneSendCooldown();
        return;
      }
      const remainingSeconds = Math.max(1, Math.ceil(remainingMs / 1000));
      applyRegisterPhoneSendCodeButton(`${registerPhoneSendCodeDefaultText} (${remainingSeconds}s)`, true);
    }

    function startRegisterPhoneSendCooldown(cooldownMs) {
      const safeCooldownMs = Math.max(0, Math.round(Number(cooldownMs) || 0));
      if (safeCooldownMs <= 0) {
        return;
      }
      registerPhoneSendCooldownUntil = Date.now() + safeCooldownMs;
      clearRegisterPhoneSendCooldownTimer();
      refreshRegisterPhoneSendCooldown();
      registerPhoneSendCooldownTimer = setInterval(refreshRegisterPhoneSendCooldown, 200);
    }

    function normalizeRoutePath(routeTarget) {
      const rawRouteTarget = typeof routeTarget === "string" ? routeTarget.trim() : "";
      if (!rawRouteTarget) {
        return "";
      }
      try {
        return new URL(rawRouteTarget, window.location.origin).pathname.replace(/\/+$/g, "");
      } catch (_) {
        return rawRouteTarget.split(/[?#]/)[0].replace(/\/+$/g, "");
      }
    }

    function modeForRoute(routeTarget) {
      try {
        return new URL(routeTarget || window.location.href, window.location.origin).searchParams.get("mode") || "";
      } catch (_) {
        return "";
      }
    }

    function isRegisterModeRoute() {
      return modeForRoute(typeof window !== "undefined" ? window.location.href : "") === "register";
    }

    function registerModePath(basePathKey, modePathKey) {
      const modePath = authPaths[modePathKey];
      if (typeof modePath === "string" && modePath) {
        return modePath;
      }
      const basePath = authPaths[basePathKey] || "";
      return basePath ? `${basePath}?mode=register` : "";
    }

    function resetRegisterPhoneRequiredValidationState() {
      if (registerPhoneRequiredErrorMessage) registerPhoneRequiredErrorMessage.style.display = "none";
      if (registerPhoneRequiredInput) registerPhoneRequiredInput.classList.remove("error");
      if (registerPhoneRequiredLabel) registerPhoneRequiredLabel.classList.remove("error-label");
      if (registerPhoneRequiredCodeInput) registerPhoneRequiredCodeInput.classList.remove("error");
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

    function resetRegisterPhoneCodeState() {
      registerPhoneCodeSent = false;
      registerPhoneCodeDialCode = "";
      registerPhoneCodeRawPhone = "";
      if (registerPhoneRequiredCodeInput) {
        registerPhoneRequiredCodeInput.value = "";
        registerPhoneRequiredCodeInput.classList.remove("error");
      }
      if (registerPhoneRequiredCodeGroup) {
        registerPhoneRequiredCodeGroup.hidden = true;
      }
    }

    function showRegisterPhoneCodeStep() {
      registerPhoneCodeSent = true;
      if (registerPhoneRequiredCodeGroup) {
        registerPhoneRequiredCodeGroup.hidden = false;
      }
      if (registerPhoneRequiredCodeInput) {
        registerPhoneRequiredCodeInput.focus();
      }
    }

    function showRegisterPhoneCodeSentStatus() {
      if (registerPhoneRequiredErrorMessage) {
        registerPhoneRequiredErrorMessage.textContent = registerPhoneCodeSentMessage;
        registerPhoneRequiredErrorMessage.style.display = "block";
      }
    }

    async function sendRegisterPhoneRequiredCode(dialCode, rawPhone) {
      if (Date.now() < registerPhoneSendCooldownUntil) {
        refreshRegisterPhoneSendCooldown();
        return false;
      }
      const submitPhoneCode = isRegisterModeRoute() ? submitRegisterPhoneCode : submitLoginPhoneCode;
      if (!submitPhoneCode) {
        showRegisterPhoneRequiredValidationError("SMS verification is not available.");
        return false;
      }
      const validationResult = await validatePhoneNumberPolicy(dialCode, rawPhone, PHONE_VALIDATION_PURPOSE_BIND_PHONE);
      if (!validationResult.success) {
        showRegisterPhoneRequiredValidationError(validationResult.message);
        return false;
      }

      const showCodeSentState = (payload) => {
        registerPhoneCodeDialCode = dialCode;
        registerPhoneCodeRawPhone = rawPhone;
        showRegisterPhoneCodeStep();
        startRegisterPhoneSendCooldown(resolveRetryAfterMs(payload, 60_000));
        showRegisterPhoneCodeSentStatus();
      };

      const sendResult = await submitPhoneCode(dialCode, rawPhone);
      if (!sendResult?.success) {
        if (sendResult?.challengeType) {
          pendingRegisterPhoneSmsChallenge = {
            email: sendResult.email || "",
            deviceFingerprint: sendResult.deviceFingerprint || "",
            challengeType: sendResult.challengeType || "",
            challengeSubType: sendResult.challengeSubType || "",
            riskLevel: sendResult.riskLevel || ""
          };
          const handled = await handlePhoneSmsChallenge(sendResult, {
            errorTarget: showRegisterPhoneRequiredValidationError,
            submitChallenge(captchaUuid = "", captchaCode = "") {
              return submitPhoneCode(dialCode, rawPhone, captchaUuid, captchaCode);
            },
            getPendingChallengePayload() {
              return pendingRegisterPhoneSmsChallenge;
            },
            resolveChallengeSuccess(payload) {
              if (!payload?.success) {
                showRegisterPhoneRequiredValidationError(payload?.message || "Failed to send SMS code.");
                return false;
              }
              showCodeSentState(payload);
              return true;
            }
          });
          if (handled) {
            return false;
          }
        }

        const retryAfterMs = resolveRetryAfterMs(sendResult);
        if (retryAfterMs > 0) {
          startRegisterPhoneSendCooldown(retryAfterMs);
        }
        showRegisterPhoneRequiredValidationError(sendResult?.message || "Failed to send SMS code.");
        return false;
      }

      showCodeSentState(sendResult);
      return true;
    }

    async function handleRegisterEntrySubmit() {
      if (typeof window.continueRegisterEntryStep === "function") {
        await window.continueRegisterEntryStep();
      }
      return true;
    }

    async function handleRegisterPasswordSubmit() {
      if (typeof window.submitRegisterPasswordStep === "function") {
        await window.submitRegisterPasswordStep();
      }
      return true;
    }

    async function handleRegisterPhoneRequiredSubmit() {
      const phoneParts = window.ShoppingLoginCountryPicker?.resolveRegisterPhoneRequiredForSubmit?.()
        || {
          dialCode: resolveDialCodeValue("register-phone-country-code"),
          phoneNumber: registerPhoneRequiredInput ? registerPhoneRequiredInput.value.trim().replace(/\D/g, "") : ""
        };
      const rawPhone = phoneParts.phoneNumber || "";
      const dialCode = phoneParts.dialCode || "";
      resetRegisterPhoneRequiredValidationState();

      if (!dialCode) {
        showRegisterPhoneRequiredValidationError("\u8bf7\u9009\u62e9\u56fd\u5bb6/\u5730\u533a");
        return true;
      }

      if (!/^\d{6,15}$/.test(rawPhone)) {
        showRegisterPhoneRequiredValidationError("\u8bf7\u8f93\u5165\u6709\u6548\u7684\u624b\u673a\u53f7\u7801");
        return true;
      }

      const validationResult = await validatePhoneNumberPolicy(dialCode, rawPhone, PHONE_VALIDATION_PURPOSE_BIND_PHONE);
      if (!validationResult.success) {
        showRegisterPhoneRequiredValidationError(validationResult.message);
        return true;
      }

      const submitPhoneBinding = isRegisterModeRoute() ? submitRegisterPhoneBinding : submitLoginPhoneBinding;
      if (submitPhoneBinding && typeof window !== "undefined"
          && window.location?.pathname === authPaths.ADD_PHONE) {
        if (!registerPhoneCodeSent
            || registerPhoneCodeDialCode !== dialCode
            || registerPhoneCodeRawPhone !== rawPhone) {
          await sendRegisterPhoneRequiredCode(dialCode, rawPhone);
          return true;
        }

        const rawCode = registerPhoneRequiredCodeInput ? registerPhoneRequiredCodeInput.value.trim() : "";
        if (!/^\d{6}$/.test(rawCode)) {
          if (registerPhoneRequiredCodeInput) {
            registerPhoneRequiredCodeInput.classList.add("error");
          }
          showRegisterPhoneRequiredValidationError("\u8bf7\u8f93\u5165 6 \u4f4d\u77ed\u4fe1\u9a8c\u8bc1\u7801");
          return true;
        }

        const loginResult = await submitPhoneBinding(dialCode, rawPhone, rawCode);
        if (!loginResult?.success) {
          showRegisterPhoneRequiredValidationError(loginResult?.message || "Failed to bind phone number.");
          return true;
        }
        if (loginResult?.redirectPath) {
          window.location.assign(loginResult.redirectPath);
        }
        return true;
      }

      if (registerPhoneRequiredErrorMessage) {
        registerPhoneRequiredErrorMessage.textContent =
          `\u5df2\u8bb0\u5f55\u624b\u673a\u53f7 ${dialCode} ${rawPhone}\uff0c\u77ed\u4fe1\u9a8c\u8bc1\u6d41\u7a0b\u5f85\u63a5\u5165`;
        registerPhoneRequiredErrorMessage.style.display = "block";
      }
      return true;
    }

    async function handleRegisterOtpSubmit() {
      if (otpApi?.getCurrentOtpScenario?.() !== "register" && !isRegisterModeRoute()) {
        return false;
      }

      const otpErrorMessage = document.getElementById("otp-error-msg");
      const rawOtp = otpCodeInput ? otpCodeInput.value.trim() : "";

      if (!/^\d{4,8}$/.test(rawOtp)) {
        if (otpErrorMessage) {
          otpErrorMessage.textContent = "\u8bf7\u8f93\u5165\u6709\u6548\u9a8c\u8bc1\u7801";
          otpErrorMessage.style.display = "block";
        }
        triggerLoginError();
        return true;
      }

      if (typeof window.verifyRegisterEmailCode !== "function") {
        if (otpErrorMessage) {
          otpErrorMessage.textContent = "\u6ce8\u518c\u9a8c\u8bc1\u529f\u80fd\u6682\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
          otpErrorMessage.style.display = "block";
        }
        triggerLoginError();
        return true;
      }

      try {
        const verifyResult = await window.verifyRegisterEmailCode(rawOtp);
        if (!verifyResult?.success) {
          if (otpErrorMessage) {
            otpErrorMessage.textContent = verifyResult?.message || "\u9a8c\u8bc1\u7801\u6821\u9a8c\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002";
            otpErrorMessage.style.display = "block";
          }
          triggerLoginError();
          return true;
        }

        if (verifyResult?.requirePhoneBinding) {
          if (registerPhoneRequiredInput) {
            registerPhoneRequiredInput.value = "";
          }
          resetRegisterPhoneCodeState();
          resetRegisterPhoneRequiredValidationState();
          resetRegisterPhoneSendCooldown();
          window.location.assign(registerModePath("ADD_PHONE", "REGISTER_ADD_PHONE"));
          return true;
        }

        if (verifyResult?.authenticated && verifyResult?.redirectPath) {
          window.location.assign(verifyResult.redirectPath);
          return true;
        }

        if (otpErrorMessage) {
          otpErrorMessage.textContent = verifyResult?.message || "\u6ce8\u518c\u6210\u529f\uff0c\u8d26\u53f7\u5df2\u521b\u5efa\u3002";
          otpErrorMessage.style.display = "block";
        }
        return true;
      } catch (_) {
        if (otpErrorMessage) {
          otpErrorMessage.textContent = "\u6ce8\u518c\u9a8c\u8bc1\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
          otpErrorMessage.style.display = "block";
        }
        triggerLoginError();
        return true;
      }
    }

    async function handleRegisterSubmit(authView) {
      if (authView === "register") {
        return handleRegisterEntrySubmit();
      }
      if (authView === "register-password") {
        return handleRegisterPasswordSubmit();
      }
      if (authView === "register-phone-required") {
        return handleRegisterPhoneRequiredSubmit();
      }
      if (authView === "otp") {
        return handleRegisterOtpSubmit();
      }
      return false;
    }

    async function handleBackToPassword() {
      if (otpApi?.getCurrentOtpScenario?.() === "register") {
        await shellApi?.navigateToView?.("register-password");
        if (typeof window.restoreRegisterPasswordStepFromSession === "function") {
          window.restoreRegisterPasswordStepFromSession();
        }
        return;
      }
      shellApi?.setAuthView?.("password");
      if (passwordInput) {
        passwordInput.focus();
      }
    }

    async function restoreCurrentRegisterRoute(currentPath) {
      const currentMode = modeForRoute(currentPath);
      if (currentMode && currentMode !== "register") {
        return false;
      }
      const currentPathname = normalizeRoutePath(currentPath);
      if (currentPathname === authPaths.CREATE_ACCOUNT
          && typeof window.restoreRegisterEntryStepFromSession === "function") {
        window.restoreRegisterEntryStepFromSession();
        return true;
      }
      if (currentPathname === authPaths.CREATE_ACCOUNT_PASSWORD
          && typeof window.restoreRegisterPasswordStepFromSession === "function") {
        await window.restoreRegisterPasswordStepFromSession();
        return true;
      }
      if (currentPathname === authPaths.EMAIL_VERIFICATION
          && typeof window.restoreRegisterOtpStepFromSession === "function") {
        await window.restoreRegisterOtpStepFromSession();
        return true;
      }
      return false;
    }

    function applyRegisterRouteNoticeEffects(routeNotice) {
      if (routeNotice === "flow-expired" || routeNotice === "register-completed") {
        window.clearRegisterFlowLocalState?.();
      }
      if (routeNotice === "flow-expired" || routeNotice === "register-completed") {
        window.clearRegisterDraftState?.();
      }
    }

    if (registerPhoneRequiredInput) {
      registerPhoneRequiredInput.addEventListener("input", () => {
        resetRegisterPhoneCodeState();
        resetRegisterPhoneSendCooldown();
      });
    }
    if (registerPhoneRequiredSendCodeButton) {
      registerPhoneRequiredSendCodeButton.addEventListener("click", async () => {
        const phoneParts = window.ShoppingLoginCountryPicker?.resolveRegisterPhoneRequiredForSubmit?.()
          || {
            dialCode: resolveDialCodeValue("register-phone-country-code"),
            phoneNumber: registerPhoneRequiredInput ? registerPhoneRequiredInput.value.trim().replace(/\D/g, "") : ""
          };
        const rawPhone = phoneParts.phoneNumber || "";
        const dialCode = phoneParts.dialCode || "";
        resetRegisterPhoneRequiredValidationState();
        if (!dialCode || !/^\d{6,15}$/.test(rawPhone)) {
          showRegisterPhoneRequiredValidationError("\u8bf7\u8f93\u5165\u6709\u6548\u7684\u624b\u673a\u53f7\u7801");
          return;
        }
        await sendRegisterPhoneRequiredCode(dialCode, rawPhone);
      });
    }

    return {
      resetRegisterPhoneRequiredValidationState,
      showRegisterPhoneRequiredValidationError,
      handleRegisterSubmit,
      handleBackToPassword,
      restoreCurrentRegisterRoute,
      applyRegisterRouteNoticeEffects
    };
  }

  return {
    createLoginRegisterBridge
  };
});
