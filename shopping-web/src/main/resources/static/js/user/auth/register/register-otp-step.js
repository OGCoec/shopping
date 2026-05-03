(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterOtpStep = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const REGISTER_EMAIL_CODE_TYPE_PATH = "/shopping/user/register/email-code-type";
  const DEFAULT_WAF_REPLAY_EVENT_NAME = "shopping:preauth:waf-request-replayed";

  function resolveEmailCodeResendCooldownMs(payload) {
    const retryAfterMs = Number(payload?.emailCodeRetryAfterMs);
    if (Number.isFinite(retryAfterMs) && retryAfterMs > 0) {
      return Math.round(retryAfterMs);
    }
    const passedAt = Number(payload?.passedAt);
    if (Number.isFinite(passedAt) && passedAt > 0) {
      return Math.max(0, Math.round(passedAt - Date.now()));
    }
    return 0;
  }

  function createRegisterOtpStep(options = {}) {
    const getRegisterFormApi = typeof options.getRegisterFormApi === "function"
      ? options.getRegisterFormApi
      : () => options.registerFormApi || null;
    const fetchCurrentRegisterFlowState = typeof options.fetchCurrentRegisterFlowState === "function"
      ? options.fetchCurrentRegisterFlowState
      : async () => null;
    const navigateToRegisterStep = typeof options.navigateToRegisterStep === "function"
      ? options.navigateToRegisterStep
      : () => {};
    const buildRegisterDeviceFingerprint = typeof options.buildRegisterDeviceFingerprint === "function"
      ? options.buildRegisterDeviceFingerprint
      : () => "";
    const clearRegisterDraftState = typeof options.clearRegisterDraftState === "function"
      ? options.clearRegisterDraftState
      : () => {};
    const showRegisterError = typeof options.showRegisterError === "function"
      ? options.showRegisterError
      : () => {};
    const clearRegisterError = typeof options.clearRegisterError === "function"
      ? options.clearRegisterError
      : () => {};
    const registerStepPaths = options.registerStepPaths || {};
    const wafReplayEventName = typeof options.wafReplayEventName === "string" && options.wafReplayEventName
      ? options.wafReplayEventName
      : DEFAULT_WAF_REPLAY_EVENT_NAME;

    let registerWafReplayHandling = false;

    function isCurrentRoute(targetPath) {
      if (typeof window === "undefined" || !targetPath) {
        return false;
      }
      try {
        const targetUrl = new URL(targetPath, window.location.origin);
        return `${window.location.pathname}${window.location.search}` === `${targetUrl.pathname}${targetUrl.search}`;
      } catch (_) {
        return window.location.pathname === targetPath;
      }
    }

    function hydrateAndOpenRegisterOtpStep(registerFormApi, email, riskLevel, requirePhoneBinding, pendingRegisterPayload) {
      if (typeof window.openRegisterOtpStep !== "function") {
        showRegisterError("Email code sent, but OTP step is unavailable.", false);
        return false;
      }

      registerFormApi?.hydratePendingRegisterPayload?.({
        email,
        deviceFingerprint: pendingRegisterPayload?.deviceFingerprint || buildRegisterDeviceFingerprint(),
        riskLevel,
        requirePhoneBinding
      });
      window.openRegisterOtpStep(email, {
        riskLevel,
        requirePhoneBinding
      });
      return true;
    }

    function openRegisterOtpAfterEmailSent(deliveryPayload = null) {
      const registerFormApi = getRegisterFormApi();
      const registerEmailInput = document.getElementById("register-email");
      const pendingRegisterPayload = registerFormApi?.getPendingRegisterPayload?.() || null;
      const registerEmail = pendingRegisterPayload?.email || registerEmailInput?.value?.trim() || "";
      const registerRiskLevel = deliveryPayload?.riskLevel || pendingRegisterPayload?.riskLevel || "";
      const requirePhoneBinding = typeof deliveryPayload?.requirePhoneBinding === "boolean"
        ? deliveryPayload.requirePhoneBinding
        : Boolean(pendingRegisterPayload?.requirePhoneBinding);
      clearRegisterError();

      if (typeof window !== "undefined"
          && !isCurrentRoute(registerStepPaths.EMAIL_VERIFICATION)) {
        navigateToRegisterStep("otp", registerStepPaths.EMAIL_VERIFICATION);
      }

      const opened = hydrateAndOpenRegisterOtpStep(
        registerFormApi,
        registerEmail,
        registerRiskLevel,
        requirePhoneBinding,
        pendingRegisterPayload
      );
      const resendCooldownMs = resolveEmailCodeResendCooldownMs(deliveryPayload);
      if (opened
          && resendCooldownMs > 0
          && typeof window !== "undefined"
          && typeof window.startRegisterEmailResendCooldown === "function") {
        window.startRegisterEmailResendCooldown(resendCooldownMs);
      }
      return opened;
    }

    async function restoreRegisterOtpStepFromSession() {
      const registerFormApi = getRegisterFormApi();
      const pendingRegisterPayload = registerFormApi?.getPendingRegisterPayload?.() || null;
      if (pendingRegisterPayload?.email) {
        return openRegisterOtpAfterEmailSent({
          riskLevel: pendingRegisterPayload.riskLevel || "",
          requirePhoneBinding: Boolean(pendingRegisterPayload.requirePhoneBinding)
        });
      }

      const flowState = await fetchCurrentRegisterFlowState();
      if (!flowState?.email) {
        navigateToRegisterStep("register", registerStepPaths.CREATE_ACCOUNT, { replace: true });
        return false;
      }

      registerFormApi?.hydratePendingRegisterPayload?.({
        email: flowState.email,
        deviceFingerprint: buildRegisterDeviceFingerprint(),
        riskLevel: flowState.riskLevel || "",
        requirePhoneBinding: Boolean(flowState.requirePhoneBinding)
      });
      return openRegisterOtpAfterEmailSent({
        riskLevel: flowState.riskLevel || "",
        requirePhoneBinding: Boolean(flowState.requirePhoneBinding)
      });
    }

    async function resendRegisterEmailCodeFromOtp() {
      const registerFormApi = getRegisterFormApi();
      if (!registerFormApi || typeof registerFormApi.resendRegisterEmailCode !== "function") {
        return {
          success: false,
          message: "\u6ce8\u518c\u80fd\u529b\u5c1a\u672a\u5c31\u7eea\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002",
          submitCooldownMs: 0
        };
      }
      return registerFormApi.resendRegisterEmailCode();
    }

    async function verifyRegisterEmailCodeFromOtp(emailCode) {
      const registerFormApi = getRegisterFormApi();
      if (!registerFormApi || typeof registerFormApi.verifyRegisterEmailCode !== "function") {
        return {
          success: false,
          message: "Register verification is unavailable, please retry later.",
          requirePhoneBinding: false
        };
      }
      const verifyResult = await registerFormApi.verifyRegisterEmailCode(emailCode);
      if (verifyResult?.success) {
        clearRegisterDraftState();
      }
      return verifyResult;
    }

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
      const registerFormApi = getRegisterFormApi();
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
      window.addEventListener(wafReplayEventName, (event) => {
        handleRegisterWafReplay(event?.detail || {}).catch(() => {
          showRegisterError("Register request failed, please retry.");
        });
      });
    }

    return {
      openRegisterOtpAfterEmailSent,
      restoreRegisterOtpStepFromSession,
      resendRegisterEmailCodeFromOtp,
      verifyRegisterEmailCodeFromOtp,
      isRegisterEmailCodeTypeReplay,
      handleRegisterWafReplay,
      bindRegisterWafReplayHandler
    };
  }

  return {
    REGISTER_EMAIL_CODE_TYPE_PATH,
    DEFAULT_WAF_REPLAY_EVENT_NAME,
    createRegisterOtpStep
  };
});
