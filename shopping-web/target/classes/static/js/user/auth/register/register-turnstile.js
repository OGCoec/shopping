(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterTurnstile = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function createRegisterTurnstile(options) {
    const {
      showRegisterError,
      triggerCaptchaFailureAnimation,
      requestRegisterEmailCodeDelivery,
      waitForCaptchaSuccessFeedback,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    } = options || {};

    let currentTurnstileWidgetId = null;
    let currentTurnstileSiteKey = "";
    let turnstileScriptPromise = null;

    function showTurnstileError(message) {
      const errorNode = document.getElementById("register-turnstile-error-msg");
      if (!errorNode) return;
      errorNode.textContent = message;
      errorNode.style.display = "block";
    }

    function clearTurnstileError() {
      const errorNode = document.getElementById("register-turnstile-error-msg");
      if (!errorNode) return;
      errorNode.textContent = "";
      errorNode.style.display = "none";
    }

    function openTurnstileModal() {
      const modal = document.getElementById("register-turnstile-modal");
      if (modal) modal.style.display = "flex";
    }

    function closeTurnstileModal() {
      const modal = document.getElementById("register-turnstile-modal");
      if (modal) modal.style.display = "none";
      clearTurnstileError();
      if (window.turnstile && currentTurnstileWidgetId !== null) {
        window.turnstile.remove(currentTurnstileWidgetId);
        currentTurnstileWidgetId = null;
      }
    }

    function loadTurnstileScript() {
      if (window.turnstile) {
        return Promise.resolve();
      }
      if (turnstileScriptPromise) {
        return turnstileScriptPromise;
      }
      turnstileScriptPromise = new Promise((resolve, reject) => {
        const script = document.createElement("script");
        script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
        script.async = true;
        script.defer = true;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error("Turnstile script failed to load"));
        document.head.appendChild(script);
      });
      return turnstileScriptPromise;
    }

    async function continueRegisterWithTurnstile(token, successFeedbackStartedAt = Date.now()) {
      if (!getPendingRegisterPayload?.()) {
        showTurnstileError("注册上下文已失效，请重新提交");
        return;
      }
      if (!token) {
        showTurnstileError("Cloudflare Turnstile token 为空");
        triggerCaptchaFailureAnimation?.();
        return;
      }

      const response = await requestRegisterEmailCodeDelivery("", token, false);
      const payload = await response.json();
      if (!response.ok || !payload.success) {
        showTurnstileError(payload.message || "Cloudflare Turnstile 校验失败，请重试");
        triggerCaptchaFailureAnimation?.();
        if (window.turnstile && currentTurnstileWidgetId !== null) {
          window.turnstile.reset(currentTurnstileWidgetId);
        }
        return;
      }

      await waitForCaptchaSuccessFeedback(successFeedbackStartedAt);
      closeTurnstileModal();
      const pendingRegisterPayload = getPendingRegisterPayload?.() || null;
      if (pendingRegisterPayload) {
        pendingRegisterPayload.riskLevel = payload.riskLevel || pendingRegisterPayload.riskLevel || "";
        pendingRegisterPayload.requirePhoneBinding = Boolean(payload.requirePhoneBinding);
      }
      openRegisterOtpAfterEmailSent(payload);
    }

    async function renderTurnstileCaptcha(siteKey) {
      currentTurnstileSiteKey = siteKey || "";
      if (!currentTurnstileSiteKey) {
        showRegisterError("Cloudflare Turnstile siteKey 未配置");
        return;
      }
      openTurnstileModal();
      clearTurnstileError();
      await loadTurnstileScript();

      const container = document.getElementById("register-turnstile-container");
      if (!container || !window.turnstile) {
        showTurnstileError("Cloudflare Turnstile 初始化失败");
        return;
      }
      if (currentTurnstileWidgetId !== null) {
        window.turnstile.remove(currentTurnstileWidgetId);
        currentTurnstileWidgetId = null;
      }
      container.innerHTML = "";
      currentTurnstileWidgetId = window.turnstile.render(container, {
        sitekey: currentTurnstileSiteKey,
        callback: (token) => {
          const successFeedbackStartedAt = Date.now();
          continueRegisterWithTurnstile(token, successFeedbackStartedAt).catch(() => {
            showTurnstileError("Cloudflare Turnstile 校验失败，请重试");
            triggerCaptchaFailureAnimation?.();
            if (window.turnstile && currentTurnstileWidgetId !== null) {
              window.turnstile.reset(currentTurnstileWidgetId);
            }
          });
        },
        "error-callback": () => {
          showTurnstileError("Cloudflare Turnstile 加载失败，请刷新重试");
        },
        "expired-callback": () => {
          showTurnstileError("Cloudflare Turnstile 已过期，请刷新重试");
        }
      });
    }

    return {
      showTurnstileError,
      clearTurnstileError,
      openTurnstileModal,
      closeTurnstileModal,
      loadTurnstileScript,
      renderTurnstileCaptcha,
      continueRegisterWithTurnstile
    };
  }

  return {
    createRegisterTurnstile
  };
});
