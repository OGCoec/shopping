(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterRecaptcha = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function createRegisterRecaptcha(options) {
    const {
      idPrefix,
      showRegisterError,
      triggerCaptchaFailureAnimation,
      requestRegisterEmailCodeDelivery,
      waitForCaptchaSuccessFeedback,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload,
      action = "shopping_auth"
    } = options || {};
    const domIdPrefix = (typeof idPrefix === "string" && idPrefix.trim()) || "register";

    let recaptchaScriptPromise = null;
    let currentRecaptchaSiteKey = "";
    let recaptchaSubmissionInFlight = false;

    function showRecaptchaError(message) {
      showRegisterError?.(message || "Google reCAPTCHA verification failed.");
    }

    function loadRecaptchaScript(siteKey) {
      if (!siteKey) {
        return Promise.reject(new Error("Google reCAPTCHA site key is not configured."));
      }
      if (window.grecaptcha && currentRecaptchaSiteKey === siteKey) {
        return Promise.resolve();
      }
      if (recaptchaScriptPromise && currentRecaptchaSiteKey === siteKey) {
        return recaptchaScriptPromise;
      }

      currentRecaptchaSiteKey = siteKey;
      recaptchaScriptPromise = new Promise((resolve, reject) => {
        const script = document.createElement("script");
        script.src = `https://www.google.com/recaptcha/api.js?render=${encodeURIComponent(siteKey)}`;
        script.async = true;
        script.defer = true;
        script.onload = () => resolve();
        script.onerror = () => {
          recaptchaScriptPromise = null;
          reject(new Error("Google reCAPTCHA script failed to load"));
        };
        document.head.appendChild(script);
      });
      return recaptchaScriptPromise;
    }

    async function resolveDeliveryResult(deliveryResult) {
      if (deliveryResult && typeof deliveryResult.json === "function") {
        const payload = await deliveryResult.json();
        return {
          ok: Boolean(deliveryResult.ok),
          payload: payload || {}
        };
      }
      const payload = deliveryResult && typeof deliveryResult === "object" ? deliveryResult : {};
      return {
        ok: Boolean(payload.success),
        payload
      };
    }

    async function executeRecaptcha(siteKey) {
      if (!getPendingRegisterPayload?.()) {
        showRecaptchaError("Security verification context expired. Please submit again.");
        return;
      }

      try {
        await loadRecaptchaScript(siteKey || "");
      } catch (error) {
        showRecaptchaError(error?.message || "Google reCAPTCHA failed to load. Please retry.");
        return;
      }

      if (!window.grecaptcha || typeof window.grecaptcha.ready !== "function") {
        showRecaptchaError("Google reCAPTCHA is unavailable. Please retry.");
        return;
      }
      if (recaptchaSubmissionInFlight) {
        return;
      }
      recaptchaSubmissionInFlight = true;

      window.grecaptcha.ready(async () => {
        const successFeedbackStartedAt = Date.now();
        try {
          const token = await window.grecaptcha.execute(siteKey, { action });
          if (!token) {
            showRecaptchaError("Google reCAPTCHA token is empty.");
            triggerCaptchaFailureAnimation?.();
            return;
          }

          const deliveryResult = await requestRegisterEmailCodeDelivery("", token, false);
          const { ok, payload } = await resolveDeliveryResult(deliveryResult);
          if (!ok || !payload.success) {
            showRecaptchaError(payload.message || "Google reCAPTCHA verification failed. Please retry.");
            triggerCaptchaFailureAnimation?.();
            return;
          }

          await waitForCaptchaSuccessFeedback?.(successFeedbackStartedAt);
          const pendingRegisterPayload = getPendingRegisterPayload?.() || null;
          if (pendingRegisterPayload) {
            pendingRegisterPayload.riskLevel = payload.riskLevel || pendingRegisterPayload.riskLevel || "";
            pendingRegisterPayload.requirePhoneBinding = Boolean(payload.requirePhoneBinding);
          }
          openRegisterOtpAfterEmailSent?.(payload);
        } catch (_) {
          showRecaptchaError("Google reCAPTCHA verification failed. Please retry.");
          triggerCaptchaFailureAnimation?.();
        } finally {
          recaptchaSubmissionInFlight = false;
        }
      });
    }

    return {
      showRecaptchaError,
      loadRecaptchaScript,
      executeRecaptcha,
      getRecaptchaDomPrefix() {
        return domIdPrefix;
      }
    };
  }

  return {
    createRegisterRecaptcha
  };
});
