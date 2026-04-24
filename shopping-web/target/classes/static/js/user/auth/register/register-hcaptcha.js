(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterHCaptcha = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function createRegisterHCaptcha(options) {
    const {
      autoRetryDelayMs,
      autoRetryLimit,
      showRegisterError,
      triggerCaptchaFailureAnimation,
      requestRegisterEmailCodeDelivery,
      waitForCaptchaSuccessFeedback,
      waitForNextPaint,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    } = options || {};

    let currentHCaptchaWidgetId = null;
    let currentHCaptchaSiteKey = "";
    let hcaptchaScriptPromise = null;
    let currentHCaptchaAutoRetryCount = 0;
    let hcaptchaAutoRetryTimer = null;

    function showHCaptchaError(message) {
      const errorNode = document.getElementById("register-hcaptcha-error-msg");
      if (!errorNode) return;
      errorNode.textContent = message;
      errorNode.style.display = "block";
    }

    function clearHCaptchaError() {
      const errorNode = document.getElementById("register-hcaptcha-error-msg");
      if (!errorNode) return;
      errorNode.textContent = "";
      errorNode.style.display = "none";
    }

    function openHCaptchaModal() {
      const modal = document.getElementById("register-hcaptcha-modal");
      if (modal) modal.style.display = "flex";
    }

    function closeHCaptchaModal() {
      const modal = document.getElementById("register-hcaptcha-modal");
      if (modal) modal.style.display = "none";
      if (hcaptchaAutoRetryTimer !== null) {
        clearTimeout(hcaptchaAutoRetryTimer);
        hcaptchaAutoRetryTimer = null;
      }
      currentHCaptchaAutoRetryCount = 0;
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
        const modal = document.getElementById("register-hcaptcha-modal");
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
        window.onloadRegisterHCaptcha = () => {
          resolve();
        };
        const script = document.createElement("script");
        script.src = "https://js.hcaptcha.com/1/api.js?onload=onloadRegisterHCaptcha&render=explicit";
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

      const response = await requestRegisterEmailCodeDelivery("", token, false);
      const payload = await response.json();
      if (!response.ok || !payload.success) {
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
    }

    async function renderHCaptcha(siteKey) {
      currentHCaptchaSiteKey = siteKey || "";
      if (!currentHCaptchaSiteKey) {
        showRegisterError("hCaptcha siteKey 未配置");
        return;
      }
      openHCaptchaModal();
      clearHCaptchaError();
      await loadHCaptchaScript();
      await waitForNextPaint(2);

      const container = document.getElementById("register-hcaptcha-container");
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
