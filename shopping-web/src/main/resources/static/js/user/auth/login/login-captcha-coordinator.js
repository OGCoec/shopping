(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingLoginCaptchaCoordinator = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const DEFAULT_LOGIN_TIANAI_PATH_MAP = {
    SLIDER: "/shopping/user/login/tianai/slider",
    ROTATE: "/shopping/user/login/tianai/rotate",
    CONCAT: "/shopping/user/login/tianai/concat",
    WORD_IMAGE_CLICK: "/shopping/user/login/tianai/word-click"
  };

  function createLoginCaptchaCoordinator(options = {}) {
    const createRegisterCaptchaCoordinator = options.createRegisterCaptchaCoordinator;
    const createRegisterTianai = options.createRegisterTianai;
    const createRegisterHutoolCaptcha = options.createRegisterHutoolCaptcha;
    const createRegisterTurnstile = options.createRegisterTurnstile;
    const createRegisterHCaptcha = options.createRegisterHCaptcha;
    const createRegisterRecaptcha = options.createRegisterRecaptcha;
    const submitPendingLoginChallenge = typeof options.submitPendingLoginChallenge === "function"
      ? options.submitPendingLoginChallenge
      : async () => {
        throw new Error("login challenge submitter is unavailable");
      };
    const getPendingLoginStartPayload = typeof options.getPendingLoginStartPayload === "function"
      ? options.getPendingLoginStartPayload
      : () => null;
    const resolveLoginChallengeSuccess = typeof options.resolveLoginChallengeSuccess === "function"
      ? options.resolveLoginChallengeSuccess
      : async () => false;
    const showLoginError = typeof options.showLoginError === "function"
      ? options.showLoginError
      : () => {};
    const clearLoginError = typeof options.clearLoginError === "function"
      ? options.clearLoginError
      : () => {};
    const triggerCaptchaFailureAnimation = typeof options.triggerCaptchaFailureAnimation === "function"
      ? options.triggerCaptchaFailureAnimation
      : () => {};
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
    let activeSubmitChallenge = submitPendingLoginChallenge;
    let activePendingChallengePayloadResolver = getPendingLoginStartPayload;
    let activeChallengeSuccessResolver = resolveLoginChallengeSuccess;
    let activeChallengeFailureResolver = showLoginError;

    if (typeof createRegisterCaptchaCoordinator !== "function"
        || typeof createRegisterTianai !== "function"
        || typeof createRegisterHutoolCaptcha !== "function"
        || typeof createRegisterTurnstile !== "function"
        || typeof createRegisterHCaptcha !== "function"
        || typeof createRegisterRecaptcha !== "function") {
      throw new Error("login captcha dependencies failed to load");
    }

    const loginChallengeBridgeApi = {
      requestRegisterEmailCodeDelivery(captchaUuid, captchaCode) {
        return activeSubmitChallenge(captchaUuid, captchaCode);
      },
      getPendingRegisterPayload() {
        return activePendingChallengePayloadResolver();
      }
    };

    const sharedCaptchaApi = createRegisterCaptchaCoordinator({
      idPrefix: "login",
      createRegisterTianai,
      createRegisterHutoolCaptcha,
      createRegisterTurnstile,
      createRegisterHCaptcha,
      createRegisterRecaptcha,
      getRegisterFormApi() {
        return loginChallengeBridgeApi;
      },
      showRegisterError: showLoginError,
      triggerCaptchaFailureAnimation,
      openRegisterOtpAfterEmailSent(payload) {
        clearLoginError();
        return activeChallengeSuccessResolver(payload);
      },
      handleCaptchaDeliveryFailure(payload, controls = {}) {
        if (payload?.challengeType) {
          return false;
        }
        const message = payload?.message || controls.defaultMessage || "Verification failed. Please retry.";
        if (typeof activeChallengeFailureResolver === "function") {
          controls.closeModal?.();
          activeChallengeFailureResolver(message, payload);
          return true;
        }
        return false;
      },
      waitForCaptchaSuccessFeedback,
      waitForNextPaint,
      getElementDisplaySize,
      captchaSuccessFeedbackMinMs,
      hcaptchaAutoRetryDelayMs,
      hcaptchaAutoRetryLimit,
      hutoolCaptchaPath: "/shopping/user/login/hutoolcaptcha",
      tianaiCaptchaPathMap: options.tianaiCaptchaPathMap || DEFAULT_LOGIN_TIANAI_PATH_MAP,
      hcaptchaScriptOnloadCallbackName: "onloadLoginHCaptcha"
    });

    sharedCaptchaApi.bindRegisterCaptchaControls();

    function rememberPendingChallenge(payload) {
      const pending = activePendingChallengePayloadResolver();
      if (!pending || typeof pending !== "object") {
        return null;
      }
      pending.challengeType = payload?.challengeType || "";
      pending.challengeSubType = payload?.challengeSubType || "";
      pending.riskLevel = payload?.riskLevel || pending.riskLevel || "";
      return pending;
    }

    async function handleLoginChallengeRequirement(payload, context = {}) {
      const challengeType = (payload?.challengeType || "").trim().toUpperCase();
      const challengeSubType = payload?.challengeSubType || "";
      const errorTarget = typeof context.errorTarget === "function"
        ? context.errorTarget
        : showLoginError;
      activeSubmitChallenge = typeof context.submitChallenge === "function"
        ? context.submitChallenge
        : submitPendingLoginChallenge;
      activePendingChallengePayloadResolver = typeof context.getPendingChallengePayload === "function"
        ? context.getPendingChallengePayload
        : getPendingLoginStartPayload;
      activeChallengeSuccessResolver = typeof context.resolveChallengeSuccess === "function"
        ? context.resolveChallengeSuccess
        : resolveLoginChallengeSuccess;
      activeChallengeFailureResolver = errorTarget;

      if (!challengeType) {
        return false;
      }

      const pending = rememberPendingChallenge(payload);
      if (!pending?.email) {
        errorTarget("Login request expired. Please enter your email again.");
        return true;
      }
      pending.challengeType = challengeType;
      clearLoginError();

      if (challengeType === "HUTOOL_SHEAR_CAPTCHA") {
        sharedCaptchaApi.openRegisterCaptchaModal();
        try {
          await sharedCaptchaApi.loadRegisterCaptcha();
        } catch (_) {
          sharedCaptchaApi.showRegisterCaptchaError("Captcha image failed to load. Please refresh and try again.");
        }
        return true;
      }

      if (challengeType === "TIANAI_CAPTCHA") {
        sharedCaptchaApi.openTianaiModal();
        try {
          await sharedCaptchaApi.loadTianaiCaptcha(challengeSubType);
        } catch (_) {
          sharedCaptchaApi.showTianaiError("Security challenge failed to load. Please refresh and try again.");
        }
        return true;
      }

      if (challengeType === "CLOUDFLARE_TURNSTILE") {
        try {
          await sharedCaptchaApi.renderTurnstileCaptcha(payload?.challengeSiteKey || "");
        } catch (_) {
          sharedCaptchaApi.openTurnstileModal();
          sharedCaptchaApi.showTurnstileError("Cloudflare Turnstile failed to load. Check the site key or network.");
        }
        return true;
      }

      if (challengeType === "HCAPTCHA") {
        try {
          await sharedCaptchaApi.renderHCaptcha(payload?.challengeSiteKey || "");
        } catch (_) {
          sharedCaptchaApi.openHCaptchaModal();
          sharedCaptchaApi.showHCaptchaError("hCaptcha failed to load. Check the site key or network.");
        }
        return true;
      }

      if (challengeType === "GOOGLE_RECAPTCHA_V3") {
        try {
          await sharedCaptchaApi.executeRecaptcha(payload?.challengeSiteKey || "");
        } catch (_) {
          showLoginError("Google reCAPTCHA failed to load. Please retry.");
        }
        return true;
      }

      return false;
    }

    return {
      ...sharedCaptchaApi,
      handleLoginChallengeRequirement
    };
  }

  return {
    DEFAULT_LOGIN_TIANAI_PATH_MAP,
    createLoginCaptchaCoordinator
  };
});
