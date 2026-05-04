(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterCaptchaCoordinator = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function createRegisterCaptchaCoordinator(options = {}) {
    const createRegisterTianai = options.createRegisterTianai;
    const createRegisterHutoolCaptcha = options.createRegisterHutoolCaptcha;
    const createRegisterTurnstile = options.createRegisterTurnstile;
    const createRegisterHCaptcha = options.createRegisterHCaptcha;
    const createRegisterRecaptcha = options.createRegisterRecaptcha;
    const domIdPrefix = (typeof options.idPrefix === "string" && options.idPrefix.trim()) || "register";
    const hutoolCaptchaPath = (typeof options.hutoolCaptchaPath === "string" && options.hutoolCaptchaPath.trim())
      || "/shopping/user/register/hutoolcaptcha";
    const tianaiCaptchaPathMap = options.tianaiCaptchaPathMap || null;
    const hcaptchaScriptOnloadCallbackName = (typeof options.hcaptchaScriptOnloadCallbackName === "string"
      && options.hcaptchaScriptOnloadCallbackName.trim())
      || "";
    const getRegisterFormApi = typeof options.getRegisterFormApi === "function"
      ? options.getRegisterFormApi
      : () => options.registerFormApi || null;
    const showRegisterError = typeof options.showRegisterError === "function"
      ? options.showRegisterError
      : () => {};
    const triggerCaptchaFailureAnimation = typeof options.triggerCaptchaFailureAnimation === "function"
      ? options.triggerCaptchaFailureAnimation
      : () => {};
    const handleCaptchaDeliveryFailure = typeof options.handleCaptchaDeliveryFailure === "function"
      ? options.handleCaptchaDeliveryFailure
      : async (payload, controls = {}) => {
        if (payload?.challengeType) {
          return false;
        }
        const message = payload?.message || controls.defaultMessage || "Verification failed. Please retry.";
        controls.closeModal?.();
        showRegisterError(message);
        return true;
      };
    const openRegisterOtpAfterEmailSent = typeof options.openRegisterOtpAfterEmailSent === "function"
      ? options.openRegisterOtpAfterEmailSent
      : () => false;
    const waitForCaptchaSuccessFeedback = typeof options.waitForCaptchaSuccessFeedback === "function"
      ? options.waitForCaptchaSuccessFeedback
      : async () => {};
    const waitForNextPaint = typeof options.waitForNextPaint === "function"
      ? options.waitForNextPaint
      : async () => {};
    const getElementDisplaySize = typeof options.getElementDisplaySize === "function"
      ? options.getElementDisplaySize
      : () => ({ width: 0, height: 0 });
    const captchaSuccessFeedbackMinMs = Math.max(0, Number(options.captchaSuccessFeedbackMinMs) || 0);
    const hcaptchaAutoRetryDelayMs = Math.max(0, Number(options.hcaptchaAutoRetryDelayMs) || 0);
    const hcaptchaAutoRetryLimit = Math.max(0, Number(options.hcaptchaAutoRetryLimit) || 0);

    function getDomId(suffix) {
      return `${domIdPrefix}-${suffix}`;
    }

    function requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed) {
      const registerFormApi = getRegisterFormApi();
      if (!registerFormApi || typeof registerFormApi.requestRegisterEmailCodeDelivery !== "function") {
        throw new Error("register form api is unavailable");
      }
      return registerFormApi.requestRegisterEmailCodeDelivery(captchaUuid, captchaCode, captchaPassed);
    }

    function getPendingRegisterPayload() {
      return getRegisterFormApi()?.getPendingRegisterPayload?.() || null;
    }

    const registerTianaiApi = createRegisterTianai({
      idPrefix: domIdPrefix,
      captchaPathMap: tianaiCaptchaPathMap,
      getElementDisplaySize,
      triggerCaptchaFailureAnimation,
      handleCaptchaDeliveryFailure,
      requestRegisterEmailCodeDelivery,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    });

    const registerHutoolCaptchaApi = createRegisterHutoolCaptcha({
      idPrefix: domIdPrefix,
      captchaPath: hutoolCaptchaPath,
      showRegisterError,
      triggerCaptchaFailureAnimation,
      handleCaptchaDeliveryFailure,
      requestRegisterEmailCodeDelivery,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    });

    const registerTurnstileApi = createRegisterTurnstile({
      idPrefix: domIdPrefix,
      showRegisterError,
      triggerCaptchaFailureAnimation,
      handleCaptchaDeliveryFailure,
      requestRegisterEmailCodeDelivery,
      waitForCaptchaSuccessFeedback(startedAt) {
        return waitForCaptchaSuccessFeedback(startedAt, captchaSuccessFeedbackMinMs);
      },
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    });

    const registerHCaptchaApi = createRegisterHCaptcha({
      idPrefix: domIdPrefix,
      scriptOnloadCallbackName: hcaptchaScriptOnloadCallbackName,
      autoRetryDelayMs: hcaptchaAutoRetryDelayMs,
      autoRetryLimit: hcaptchaAutoRetryLimit,
      showRegisterError,
      triggerCaptchaFailureAnimation,
      handleCaptchaDeliveryFailure,
      requestRegisterEmailCodeDelivery,
      waitForCaptchaSuccessFeedback(startedAt) {
        return waitForCaptchaSuccessFeedback(startedAt, captchaSuccessFeedbackMinMs);
      },
      waitForNextPaint,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    });

    const registerRecaptchaApi = typeof createRegisterRecaptcha === "function"
      ? createRegisterRecaptcha({
        idPrefix: domIdPrefix,
        showRegisterError,
        triggerCaptchaFailureAnimation,
        handleCaptchaDeliveryFailure,
        requestRegisterEmailCodeDelivery,
        waitForCaptchaSuccessFeedback(startedAt) {
          return waitForCaptchaSuccessFeedback(startedAt, captchaSuccessFeedbackMinMs);
        },
        openRegisterOtpAfterEmailSent,
        getPendingRegisterPayload
      })
      : null;

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

    function executeRecaptcha(siteKey) {
      if (!registerRecaptchaApi) {
        showRegisterError("Google reCAPTCHA is unavailable.");
        return Promise.resolve();
      }
      return registerRecaptchaApi.executeRecaptcha(siteKey);
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

    function getCurrentCaptchaUuid() {
      return registerHutoolCaptchaApi.getCurrentCaptchaUuid();
    }

    function getCurrentTianaiSubType() {
      return registerTianaiApi.getCurrentTianaiSubType();
    }

    function bindRegisterCaptchaControls() {
      const captchaRefreshButton = document.getElementById(getDomId("captcha-refresh"));
      if (captchaRefreshButton && captchaRefreshButton.dataset.refreshBound !== "true") {
        captchaRefreshButton.dataset.refreshBound = "true";
        captchaRefreshButton.addEventListener("click", () => {
          loadRegisterCaptcha(getCurrentCaptchaUuid()).catch(() => {
            showRegisterCaptchaError("Failed to refresh captcha, please try again.");
          });
        });
      }

      const captchaCancelButton = document.getElementById(getDomId("captcha-cancel"));
      if (captchaCancelButton && captchaCancelButton.dataset.cancelBound !== "true") {
        captchaCancelButton.dataset.cancelBound = "true";
        captchaCancelButton.addEventListener("click", closeRegisterCaptchaModal);
      }

      const captchaConfirmButton = document.getElementById(getDomId("captcha-confirm"));
      if (captchaConfirmButton && captchaConfirmButton.dataset.confirmBound !== "true") {
        captchaConfirmButton.dataset.confirmBound = "true";
        captchaConfirmButton.addEventListener("click", () => {
          continueRegisterWithCaptcha().catch(() => {
            showRegisterCaptchaError("Captcha verification failed, please try again.");
          });
        });
      }

      const tianaiCancel = document.getElementById(getDomId("tianai-cancel"));
      if (tianaiCancel && tianaiCancel.dataset.bound !== "true") {
        tianaiCancel.dataset.bound = "true";
        tianaiCancel.addEventListener("click", closeTianaiModal);
      }

      const tianaiRefresh = document.getElementById(getDomId("tianai-refresh"));
      if (tianaiRefresh && tianaiRefresh.dataset.bound !== "true") {
        tianaiRefresh.dataset.bound = "true";
        tianaiRefresh.addEventListener("click", () => {
          loadTianaiCaptcha(getCurrentTianaiSubType());
        });
      }

      const tianaiConfirm = document.getElementById(getDomId("tianai-confirm"));
      if (tianaiConfirm && tianaiConfirm.dataset.bound !== "true") {
        tianaiConfirm.dataset.bound = "true";
        tianaiConfirm.addEventListener("click", continueRegisterWithTianai);
      }
    }

    return {
      registerTianaiApi,
      registerHutoolCaptchaApi,
      registerTurnstileApi,
      registerHCaptchaApi,
      registerRecaptchaApi,
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
      getCurrentCaptchaUuid,
      getCurrentTianaiSubType,
      bindRegisterCaptchaControls
    };
  }

  return {
    createRegisterCaptchaCoordinator
  };
});
