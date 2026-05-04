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
      handleCaptchaDeliveryFailure,
      requestRegisterEmailCodeDelivery,
      waitForCaptchaSuccessFeedback,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    } = options || {};
    const domIdPrefix = (typeof idPrefix === "string" && idPrefix.trim()) || "register";
    const recaptchaCallbackPrefix = domIdPrefix.replace(/[^a-zA-Z0-9_$]/g, "") || "Register";
    const recaptchaOnloadName = `onload${recaptchaCallbackPrefix}Recaptcha`;

    let recaptchaScriptPromise = null;
    let currentRecaptchaSiteKey = "";
    let currentRecaptchaWidgetId = null;
    let recaptchaSubmissionInFlight = false;

    function getDomId(suffix) {
      return `${domIdPrefix}-${suffix}`;
    }

    function showRecaptchaError(message) {
      const errorNode = document.getElementById(getDomId("recaptcha-error-msg"));
      if (errorNode) {
        errorNode.textContent = message || "Google reCAPTCHA verification failed.";
        errorNode.style.display = "block";
      }
      showRegisterError?.(message || "Google reCAPTCHA verification failed.");
    }

    function clearRecaptchaError() {
      const errorNode = document.getElementById(getDomId("recaptcha-error-msg"));
      if (errorNode) {
        errorNode.textContent = "";
        errorNode.style.display = "none";
      }
    }

    function openRecaptchaModal() {
      const modal = document.getElementById(getDomId("recaptcha-modal"));
      if (modal) {
        modal.style.display = "flex";
      }
    }

    function closeRecaptchaModal() {
      const modal = document.getElementById(getDomId("recaptcha-modal"));
      if (modal) {
        modal.style.display = "none";
      }
      recaptchaSubmissionInFlight = false;
      clearRecaptchaError();
      if (window.grecaptcha && currentRecaptchaWidgetId !== null) {
        window.grecaptcha.reset(currentRecaptchaWidgetId);
      }
    }

    function loadRecaptchaScript(siteKey) {
      if (!siteKey) {
        return Promise.reject(new Error("Google reCAPTCHA site key is not configured."));
      }
      if (window.grecaptcha && typeof window.grecaptcha.render === "function") {
        return Promise.resolve();
      }
      if (recaptchaScriptPromise && currentRecaptchaSiteKey === siteKey) {
        return recaptchaScriptPromise;
      }

      currentRecaptchaSiteKey = siteKey;
      recaptchaScriptPromise = new Promise((resolve, reject) => {
        let settled = false;
        const settle = (callback) => {
          if (settled) {
            return;
          }
          settled = true;
          window.clearTimeout(timeoutId);
          callback();
        };
        const timeoutId = window.setTimeout(() => {
          settle(() => {
            window[recaptchaOnloadName] = undefined;
            recaptchaScriptPromise = null;
            reject(new Error("Google reCAPTCHA script timed out"));
          });
        }, 15000);
        window[recaptchaOnloadName] = () => {
          settle(() => {
            if (window.grecaptcha && typeof window.grecaptcha.render === "function") {
              resolve();
              return;
            }
            recaptchaScriptPromise = null;
            reject(new Error("Google reCAPTCHA initialization failed"));
          });
        };

        const script = document.createElement("script");
        script.src = `https://www.google.com/recaptcha/api.js?onload=${encodeURIComponent(recaptchaOnloadName)}&render=explicit`;
        script.async = true;
        script.defer = true;
        script.onerror = () => {
          settle(() => {
            window[recaptchaOnloadName] = undefined;
            recaptchaScriptPromise = null;
            reject(new Error("Google reCAPTCHA script failed to load"));
          });
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

    async function continueRegisterWithRecaptcha(token, successFeedbackStartedAt = Date.now()) {
      if (!getPendingRegisterPayload?.()) {
        showRecaptchaError("Security verification context expired. Please submit again.");
        return;
      }

      if (!token) {
        showRecaptchaError("Google reCAPTCHA token is empty.");
        triggerCaptchaFailureAnimation?.();
        return;
      }

      if (recaptchaSubmissionInFlight) {
        return;
      }
      recaptchaSubmissionInFlight = true;

      try {
        const deliveryResult = await requestRegisterEmailCodeDelivery("", token, false);
        const { ok, payload } = await resolveDeliveryResult(deliveryResult);
        if (!ok || !payload.success) {
          if (typeof handleCaptchaDeliveryFailure === "function") {
            const handled = await handleCaptchaDeliveryFailure(payload, {
              defaultMessage: "Google reCAPTCHA verification failed. Please retry.",
              closeModal: closeRecaptchaModal,
              showCaptchaError: showRecaptchaError
            });
            if (handled) {
              return;
            }
          }
          showRecaptchaError(payload.message || "Google reCAPTCHA verification failed. Please retry.");
          triggerCaptchaFailureAnimation?.();
          if (window.grecaptcha && currentRecaptchaWidgetId !== null) {
            window.grecaptcha.reset(currentRecaptchaWidgetId);
          }
          return;
        }

        await waitForCaptchaSuccessFeedback?.(successFeedbackStartedAt);
        const pendingRegisterPayload = getPendingRegisterPayload?.() || null;
        if (pendingRegisterPayload) {
          pendingRegisterPayload.riskLevel = payload.riskLevel || pendingRegisterPayload.riskLevel || "";
          pendingRegisterPayload.requirePhoneBinding = Boolean(payload.requirePhoneBinding);
        }
        closeRecaptchaModal();
        openRegisterOtpAfterEmailSent?.(payload);
      } catch (_) {
        showRecaptchaError("Google reCAPTCHA verification failed. Please retry.");
        triggerCaptchaFailureAnimation?.();
        if (window.grecaptcha && currentRecaptchaWidgetId !== null) {
          window.grecaptcha.reset(currentRecaptchaWidgetId);
        }
      } finally {
        recaptchaSubmissionInFlight = false;
      }
    }

    async function renderRecaptcha(siteKey) {
      currentRecaptchaSiteKey = siteKey || "";
      openRecaptchaModal();
      clearRecaptchaError();
      if (!currentRecaptchaSiteKey) {
        showRecaptchaError("Google reCAPTCHA site key is not configured.");
        return;
      }

      try {
        await loadRecaptchaScript(currentRecaptchaSiteKey);
      } catch (error) {
        showRecaptchaError(error?.message || "Google reCAPTCHA failed to load. Please retry.");
        return;
      }

      const container = document.getElementById(getDomId("recaptcha-container"));
      if (!container || !window.grecaptcha || typeof window.grecaptcha.render !== "function") {
        showRecaptchaError("Google reCAPTCHA initialization failed.");
        return;
      }

      container.innerHTML = "";
      currentRecaptchaWidgetId = window.grecaptcha.render(container, {
        sitekey: currentRecaptchaSiteKey,
        callback(token) {
          continueRegisterWithRecaptcha(token, Date.now()).catch(() => {
            showRecaptchaError("Google reCAPTCHA verification failed. Please retry.");
          });
        },
        "expired-callback"() {
          showRecaptchaError("Google reCAPTCHA expired. Please retry.");
          if (window.grecaptcha && currentRecaptchaWidgetId !== null) {
            window.grecaptcha.reset(currentRecaptchaWidgetId);
          }
        },
        "error-callback"() {
          showRecaptchaError("Google reCAPTCHA failed to load. Please retry.");
        }
      });
    }

    return {
      showRecaptchaError,
      clearRecaptchaError,
      openRecaptchaModal,
      closeRecaptchaModal,
      loadRecaptchaScript,
      renderRecaptcha,
      executeRecaptcha: renderRecaptcha,
      continueRegisterWithRecaptcha,
      getRecaptchaDomPrefix() {
        return domIdPrefix;
      }
    };
  }

  return {
    createRegisterRecaptcha
  };
});
