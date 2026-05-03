(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterTurnstile = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function createRegisterTurnstile(options) {
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

    let currentTurnstileWidgetId = null;
    let currentTurnstileSiteKey = "";
    let turnstileScriptPromise = null;
    let turnstileSubmissionInFlight = false;

    function getDomId(suffix) {
      return `${domIdPrefix}-${suffix}`;
    }

    function showTurnstileError(message) {
      const errorNode = document.getElementById(getDomId("turnstile-error-msg"));
      if (!errorNode) return;
      errorNode.textContent = message;
      errorNode.style.display = "block";
    }

    function clearTurnstileError() {
      const errorNode = document.getElementById(getDomId("turnstile-error-msg"));
      if (!errorNode) return;
      errorNode.textContent = "";
      errorNode.style.display = "none";
    }

    function openTurnstileModal() {
      const modal = document.getElementById(getDomId("turnstile-modal"));
      if (modal) modal.style.display = "flex";
    }

    function closeTurnstileModal() {
      const modal = document.getElementById(getDomId("turnstile-modal"));
      if (modal) modal.style.display = "none";
      turnstileSubmissionInFlight = false;
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
      if (turnstileSubmissionInFlight) {
        return;
      }

      turnstileSubmissionInFlight = true;
      try {
        const deliveryResult = await requestRegisterEmailCodeDelivery("", token, false);
        const { ok, payload } = await resolveDeliveryResult(deliveryResult);
        if (!ok || !payload.success) {
          if (typeof handleCaptchaDeliveryFailure === "function") {
            const handled = await handleCaptchaDeliveryFailure(payload, {
              defaultMessage: "Captcha verification failed. Please retry.",
              closeModal: closeTurnstileModal,
              showCaptchaError: showTurnstileError
            });
            if (handled) {
              return;
            }
          }
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
      } finally {
        turnstileSubmissionInFlight = false;
      }
    }

    async function renderTurnstileCaptcha(siteKey) {
      currentTurnstileSiteKey = siteKey || "";
      openTurnstileModal();
      clearTurnstileError();
      if (!currentTurnstileSiteKey) {
        showTurnstileError("Cloudflare Turnstile site key is not configured.");
        showRegisterError("Cloudflare Turnstile siteKey 未配置");
        return;
      }
      openTurnstileModal();
      clearTurnstileError();
      try {
        await loadTurnstileScript();
      } catch (_) {
        showTurnstileError("Cloudflare Turnstile script failed to load. Please retry.");
        return;
      }

      const container = document.getElementById(getDomId("turnstile-container"));
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
