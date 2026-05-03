(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterHCaptcha = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function createRegisterHCaptcha(options) {
    const {
      idPrefix,
      scriptOnloadCallbackName,
      autoRetryDelayMs,
      autoRetryLimit,
      showRegisterError,
      triggerCaptchaFailureAnimation,
      handleCaptchaDeliveryFailure,
      requestRegisterEmailCodeDelivery,
      waitForCaptchaSuccessFeedback,
      waitForNextPaint,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    } = options || {};
    const domIdPrefix = (typeof idPrefix === "string" && idPrefix.trim()) || "register";
    const hcaptchaOnloadName = (typeof scriptOnloadCallbackName === "string" && scriptOnloadCallbackName.trim())
      || `onload${domIdPrefix.replace(/[^a-zA-Z0-9]/g, "") || "Register"}HCaptcha`;

    let currentHCaptchaWidgetId = null;
    let currentHCaptchaSiteKey = "";
    let hcaptchaScriptPromise = null;
    let currentHCaptchaAutoRetryCount = 0;
    let hcaptchaAutoRetryTimer = null;
    let hcaptchaSubmissionInFlight = false;

    function getDomId(suffix) {
      return `${domIdPrefix}-${suffix}`;
    }

    function showHCaptchaError(message) {
      const errorNode = document.getElementById(getDomId("hcaptcha-error-msg"));
      if (!errorNode) return;
      errorNode.textContent = message;
      errorNode.style.display = "block";
    }

    function clearHCaptchaError() {
      const errorNode = document.getElementById(getDomId("hcaptcha-error-msg"));
      if (!errorNode) return;
      errorNode.textContent = "";
      errorNode.style.display = "none";
    }

    function openHCaptchaModal() {
      const modal = document.getElementById(getDomId("hcaptcha-modal"));
      if (modal) modal.style.display = "flex";
    }

    function closeHCaptchaModal() {
      const modal = document.getElementById(getDomId("hcaptcha-modal"));
      if (modal) modal.style.display = "none";
      if (hcaptchaAutoRetryTimer !== null) {
        clearTimeout(hcaptchaAutoRetryTimer);
        hcaptchaAutoRetryTimer = null;
      }
      currentHCaptchaAutoRetryCount = 0;
      hcaptchaSubmissionInFlight = false;
      clearHCaptchaError();
    }

    function scheduleHCaptchaAutoRetry() {
      if (!window.hcaptcha || currentHCaptchaWidgetId === null) {
        return false;
      }
      if (currentHCaptchaAutoRetryCount >= autoRetryLimit) {
        return false;
      }

      currentHCaptchaAutoRetryCount += 1;
      clearHCaptchaError();

      if (hcaptchaAutoRetryTimer !== null) {
        clearTimeout(hcaptchaAutoRetryTimer);
      }

      const widgetId = currentHCaptchaWidgetId;
      hcaptchaAutoRetryTimer = setTimeout(() => {
        hcaptchaAutoRetryTimer = null;
        const modal = document.getElementById(getDomId("hcaptcha-modal"));
        if (!window.hcaptcha || currentHCaptchaWidgetId === null || currentHCaptchaWidgetId !== widgetId) {
          return;
        }
        if (modal && modal.style && modal.style.display === "none") {
          return;
        }
        window.hcaptcha.reset(widgetId);
      }, autoRetryDelayMs);

      return true;
    }

    function loadHCaptchaScript() {
      if (window.hcaptcha) {
        return Promise.resolve();
      }
      if (hcaptchaScriptPromise) {
        return hcaptchaScriptPromise;
      }
      hcaptchaScriptPromise = new Promise((resolve, reject) => {
        window[hcaptchaOnloadName] = () => {
          resolve();
        };
        const script = document.createElement("script");
        script.src = `https://js.hcaptcha.com/1/api.js?onload=${hcaptchaOnloadName}&render=explicit`;
        script.async = true;
        script.defer = true;
        script.onerror = () => {
          hcaptchaScriptPromise = null;
          reject(new Error("hCaptcha script failed to load"));
        };
        document.head.appendChild(script);
      });
      return hcaptchaScriptPromise;
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

    async function continueRegisterWithHCaptcha(token, successFeedbackStartedAt = Date.now()) {
      if (!getPendingRegisterPayload?.()) {
        showHCaptchaError("注册上下文已失效，请重新提交");
        return;
      }
      if (!token) {
        showHCaptchaError("hCaptcha token 为空");
        triggerCaptchaFailureAnimation?.();
        return;
      }
      if (hcaptchaSubmissionInFlight) {
        return;
      }

      hcaptchaSubmissionInFlight = true;
      try {
        const deliveryResult = await requestRegisterEmailCodeDelivery("", token, false);
        const { ok, payload } = await resolveDeliveryResult(deliveryResult);
        if (!ok || !payload.success) {
          if (typeof handleCaptchaDeliveryFailure === "function") {
            const handled = await handleCaptchaDeliveryFailure(payload, {
              defaultMessage: "Captcha verification failed. Please retry.",
              closeModal: closeHCaptchaModal,
              showCaptchaError: showHCaptchaError
            });
            if (handled) {
              return;
            }
          }
          showHCaptchaError(payload.message || "hCaptcha 校验失败，请重试");
          triggerCaptchaFailureAnimation?.();
          if (window.hcaptcha && currentHCaptchaWidgetId !== null) {
            window.hcaptcha.reset(currentHCaptchaWidgetId);
          }
          return;
        }

        await waitForCaptchaSuccessFeedback(successFeedbackStartedAt);
        closeHCaptchaModal();
        const pendingRegisterPayload = getPendingRegisterPayload?.() || null;
        if (pendingRegisterPayload) {
          pendingRegisterPayload.riskLevel = payload.riskLevel || pendingRegisterPayload.riskLevel || "";
          pendingRegisterPayload.requirePhoneBinding = Boolean(payload.requirePhoneBinding);
        }
        openRegisterOtpAfterEmailSent(payload);
      } finally {
        hcaptchaSubmissionInFlight = false;
      }
    }

    async function renderHCaptcha(siteKey) {
      currentHCaptchaSiteKey = siteKey || "";
      openHCaptchaModal();
      clearHCaptchaError();
      if (!currentHCaptchaSiteKey) {
        showHCaptchaError("hCaptcha site key is not configured.");
        showRegisterError("hCaptcha siteKey 未配置");
        return;
      }
      openHCaptchaModal();
      clearHCaptchaError();
      try {
        await loadHCaptchaScript();
      } catch (_) {
        showHCaptchaError("hCaptcha script failed to load. Please retry.");
        return;
      }
      await waitForNextPaint(2);

      const container = document.getElementById(getDomId("hcaptcha-container"));
      if (!container || !window.hcaptcha) {
        showHCaptchaError("hCaptcha 初始化失败");
        return;
      }

      if (currentHCaptchaWidgetId !== null) {
        currentHCaptchaAutoRetryCount = 0;
        window.hcaptcha.reset(currentHCaptchaWidgetId);
        return;
      }

      currentHCaptchaAutoRetryCount = 0;
      container.innerHTML = "";
      currentHCaptchaWidgetId = window.hcaptcha.render(container, {
        sitekey: currentHCaptchaSiteKey,
        callback: (token) => {
          const successFeedbackStartedAt = Date.now();
          continueRegisterWithHCaptcha(token, successFeedbackStartedAt).catch(() => {
            showHCaptchaError("hCaptcha 校验失败，请重试");
            triggerCaptchaFailureAnimation?.();
            if (window.hcaptcha && currentHCaptchaWidgetId !== null) {
              window.hcaptcha.reset(currentHCaptchaWidgetId);
            }
          });
        },
        "error-callback": () => {
          if (scheduleHCaptchaAutoRetry()) {
            return;
          }
          showHCaptchaError("hCaptcha 加载失败，请刷新重试");
        },
        "expired-callback": () => {
          showHCaptchaError("hCaptcha 已过期，请刷新重试");
          if (window.hcaptcha && currentHCaptchaWidgetId !== null) {
            currentHCaptchaAutoRetryCount = 0;
            window.hcaptcha.reset(currentHCaptchaWidgetId);
          }
        }
      });
    }

    return {
      showHCaptchaError,
      clearHCaptchaError,
      openHCaptchaModal,
      closeHCaptchaModal,
      scheduleHCaptchaAutoRetry,
      loadHCaptchaScript,
      renderHCaptcha,
      continueRegisterWithHCaptcha
    };
  }

  return {
    createRegisterHCaptcha
  };
});
