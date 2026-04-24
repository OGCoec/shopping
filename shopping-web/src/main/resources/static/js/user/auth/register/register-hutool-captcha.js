(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory(require("../shared/preauth-client.js"));
    return;
  }
  root.ShoppingRegisterHutoolCaptcha = factory(root.ShoppingPreAuthClient);
})(typeof globalThis !== "undefined" ? globalThis : this, function (preAuthClientApi) {
  const preAuthFetch = preAuthClientApi && typeof preAuthClientApi.fetchWithPreAuth === "function"
    ? preAuthClientApi.fetchWithPreAuth
    : fetch;

  function createRegisterHutoolCaptcha(options) {
    const {
      showRegisterError,
      triggerCaptchaFailureAnimation,
      requestRegisterEmailCodeDelivery,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    } = options || {};

    let currentRegisterCaptchaUuid = "";

    function showRegisterCaptchaError(message) {
      const captchaErrorMessage = document.getElementById("register-captcha-error-msg");
      if (!captchaErrorMessage) return;
      captchaErrorMessage.textContent = message;
      captchaErrorMessage.style.display = "block";
    }

    function clearRegisterCaptchaError() {
      const captchaErrorMessage = document.getElementById("register-captcha-error-msg");
      if (!captchaErrorMessage) return;
      captchaErrorMessage.textContent = "";
      captchaErrorMessage.style.display = "none";
    }

    function openRegisterCaptchaModal() {
      const captchaModal = document.getElementById("register-captcha-modal");
      if (!captchaModal) return;
      captchaModal.style.display = "flex";
    }

    function closeRegisterCaptchaModal() {
      const captchaModal = document.getElementById("register-captcha-modal");
      const captchaCodeInput = document.getElementById("register-captcha-code");
      if (captchaModal) {
        captchaModal.style.display = "none";
      }
      if (captchaCodeInput) {
        captchaCodeInput.value = "";
      }
      clearRegisterCaptchaError();
    }

    async function loadRegisterCaptcha(existingUuid = "") {
      const imageNode = document.getElementById("register-captcha-image");
      if (!imageNode) return;

      clearRegisterCaptchaError();
      const pendingRegisterPayload = getPendingRegisterPayload?.() || null;
      const params = new URLSearchParams();
      if (existingUuid) {
        params.set("uuid", existingUuid);
      }
      if (pendingRegisterPayload?.email) {
        params.set("email", pendingRegisterPayload.email);
      }
      if (pendingRegisterPayload?.deviceFingerprint) {
        params.set("deviceFingerprint", pendingRegisterPayload.deviceFingerprint);
      }

      const query = params.toString() ? `?${params.toString()}` : "";
      const response = await preAuthFetch(`/shopping/user/register/hutoolcaptcha${query}`);
      if (!response.ok) {
        showRegisterCaptchaError("验证码加载失败，请稍后重试");
        return;
      }

      const payload = await response.json();
      currentRegisterCaptchaUuid = payload.uuid || "";
      imageNode.src = payload.image || "";
    }

    async function continueRegisterWithCaptcha() {
      const captchaCodeInput = document.getElementById("register-captcha-code");
      if (!captchaCodeInput) return;

      const captchaCode = captchaCodeInput.value.trim();
      if (!captchaCode) {
        showRegisterCaptchaError("请输入图形验证码");
        return;
      }

      const pendingRegisterPayload = getPendingRegisterPayload?.();
      if (!pendingRegisterPayload) {
        showRegisterCaptchaError("注册上下文已失效，请重新提交");
        return;
      }

      const response = await requestRegisterEmailCodeDelivery(
        currentRegisterCaptchaUuid,
        captchaCode,
        false
      );

      const payload = await response.json();
      if (!response.ok || !payload.success) {
        pendingRegisterPayload.challengeType = payload.challengeType || pendingRegisterPayload.challengeType || "";
        pendingRegisterPayload.challengeSubType = payload.challengeSubType || pendingRegisterPayload.challengeSubType || "";
        const canRetryCurrentHutool = !payload.challengeType || payload.challengeType === "HUTOOL_SHEAR_CAPTCHA";
        if (canRetryCurrentHutool) {
          showRegisterCaptchaError(payload.message || "验证码校验失败，请重试");
          triggerCaptchaFailureAnimation?.();
          await loadRegisterCaptcha(currentRegisterCaptchaUuid);
          return;
        }

        closeRegisterCaptchaModal();
        showRegisterError(payload.message || "注册请求失败");
        return;
      }

      closeRegisterCaptchaModal();
      pendingRegisterPayload.riskLevel = payload.riskLevel || pendingRegisterPayload.riskLevel || "";
      pendingRegisterPayload.requirePhoneBinding = Boolean(payload.requirePhoneBinding);
      openRegisterOtpAfterEmailSent(payload);
    }

    return {
      showRegisterCaptchaError,
      clearRegisterCaptchaError,
      openRegisterCaptchaModal,
      closeRegisterCaptchaModal,
      loadRegisterCaptcha,
      continueRegisterWithCaptcha,
      getCurrentCaptchaUuid() {
        return currentRegisterCaptchaUuid;
      }
    };
  }

  return {
    createRegisterHutoolCaptcha
  };
});
