(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingPasswordReset = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const EMAIL_CODE_PATH = "/shopping/user/forgot-password/email-code";
  const VERIFY_CODE_PATH = "/shopping/user/forgot-password/verify-code";
  const CRYPTO_KEY_PATH = "/shopping/user/forgot-password/crypto-key";
  const RESET_BY_LINK_PATH = "/shopping/user/forgot-password/reset-by-link";
  const RESET_BY_CODE_PATH = "/shopping/user/forgot-password/reset-by-code";
  const PASSWORD_RESET_HUTOOL_PATH = "/shopping/user/forgot-password/hutoolcaptcha";
  const PASSWORD_RESET_TIANAI_PATH_MAP = {
    SLIDER: "/shopping/user/forgot-password/tianai/slider",
    ROTATE: "/shopping/user/forgot-password/tianai/rotate",
    CONCAT: "/shopping/user/forgot-password/tianai/concat",
    WORD_IMAGE_CLICK: "/shopping/user/forgot-password/tianai/word-click"
  };
  const WAF_PENDING_KEY = "shopping.password-reset.waf.pending";
  const WAF_RESUME_COOKIE = "PASSWORD_RESET_WAF_RESUME";
  const WAF_RESUME_HEADER = "X-Password-Reset-Waf-Resume";
  const CAPTCHA_SUCCESS_FEEDBACK_MIN_MS = 1200;
  const HCAPTCHA_AUTO_RETRY_DELAY_MS = 180;
  const HCAPTCHA_AUTO_RETRY_LIMIT = 1;
  const PASSWORD_STRENGTH_COLORS = ["#ccc", "#ef4444", "#f97316", "#84cc16", "#16a34a"];
  const PASSWORD_STRENGTH_LABELS = [
    "\u592a\u77ed",
    "\u5f31",
    "\u4e2d\u7b49",
    "\u5f3a",
    "\u5f88\u5f3a"
  ];
  const PASSWORD_HIDDEN_ICON = `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M2 12s3.6-7 10-7c2.1 0 4 .55 5.62 1.47"></path>
      <path d="M22 12s-3.6 7-10 7c-2.1 0-4-.55-5.62-1.47"></path>
      <path d="M3 3l18 18"></path>
      <path d="M9.88 9.88A3 3 0 0 0 12 15a3 3 0 0 0 2.12-.88"></path>
    </svg>
  `;
  const PASSWORD_VISIBLE_ICON = `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"></path>
      <circle cx="12" cy="12" r="3"></circle>
    </svg>
  `;

  let initialized = false;
  let cooldownTimer = null;
  let passwordResetCaptchaApi = null;
  let pendingPasswordResetChallenge = null;

  function safeSameOriginPath(value, fallback, allowedPrefixes = ["/shopping/"]) {
    const securityUrls = globalThis.ShoppingSecurityUrls;
    if (securityUrls && typeof securityUrls.safeSameOriginPath === "function") {
      return securityUrls.safeSameOriginPath(value, fallback, allowedPrefixes);
    }
    return fallback || "/shopping/user/log-in";
  }

  function initializePasswordResetFragment(options = {}) {
    if (initialized) {
      syncMode();
      return;
    }
    initialized = true;

    bindSendButton("btn-reset-code", options);
    bindVerifyCode(options);
    bindResetByLink(options);
    bindResetPasswordStrength();
    bindPasswordVisibilityToggle("reset-link-password-toggle", "reset-link-password", "Show password", "Hide password");
    bindPasswordVisibilityToggle("reset-link-confirm-toggle", "reset-link-confirm", "Show confirm password", "Hide confirm password");
    initializePasswordResetCaptcha(options);
    syncMode();
    resumeAfterWaf(options);
  }

  function syncMode() {
    const token = currentResetToken();
    const resetPage = isResetPasswordPage();
    const requestPanel = document.getElementById("password-reset-request-panel");
    const linkPanel = document.getElementById("password-reset-link-panel");
    if (requestPanel) requestPanel.style.display = resetPage ? "none" : "";
    if (linkPanel) linkPanel.style.display = resetPage ? "" : "none";
    if (resetPage) {
      document.getElementById("reset-link-password")?.focus();
      updateResetPasswordStrengthDisplay(document.getElementById("reset-link-password")?.value || "");
    }
  }

  function bindSendButton(id, options) {
    const button = document.getElementById(id);
    if (!button || button.dataset.passwordResetBound === "true") return;
    button.dataset.passwordResetBound = "true";
    button.addEventListener("click", async () => {
      const email = readEmail();
      if (!email) {
        showRequestMessage("\u8bf7\u8f93\u5165\u90ae\u7bb1\u3002", true);
        return;
      }
      await sendResetEmail(email, options, false);
    });
  }

  function bindVerifyCode(options) {
    const button = document.getElementById("btn-verify-reset-code");
    if (!button || button.dataset.passwordResetBound === "true") return;
    button.dataset.passwordResetBound = "true";
    button.addEventListener("click", async () => {
      const email = readEmail();
      const code = document.getElementById("reset-code")?.value?.trim() || "";
      if (!email || !code) {
        showRequestMessage("\u8bf7\u8f93\u5165\u90ae\u7bb1\u548c 6 \u4f4d\u9a8c\u8bc1\u7801\u3002", true);
        return;
      }
      try {
        const response = await fetchWithPreAuth(options)(VERIFY_CODE_PATH, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, code })
        });
        const payload = await parseJsonSafely(response);
        if (!response.ok || !payload?.success || !payload?.redirectPath) {
          showRequestMessage(payload?.message || "\u9a8c\u8bc1\u7801\u9519\u8bef\u6216\u5df2\u8fc7\u671f\u3002", true);
          return;
        }
        await options.shellApi?.navigateTo?.(safeSameOriginPath(payload.redirectPath, "/shopping/user/reset-password-code"));
      } catch (_) {
        showRequestMessage("\u9a8c\u8bc1\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
      }
    });
  }

  function bindResetByLink(options) {
    const button = document.getElementById("btn-reset-by-link");
    if (!button || button.dataset.passwordResetBound === "true") return;
    button.dataset.passwordResetBound = "true";
    button.addEventListener("click", async () => {
      const token = currentResetToken();
      const password = document.getElementById("reset-link-password")?.value || "";
      const confirmPassword = document.getElementById("reset-link-confirm")?.value || "";
      const resetPath = currentResetMode() === "code" ? RESET_BY_CODE_PATH : RESET_BY_LINK_PATH;
      const identity = currentResetMode() === "code" ? {} : { token };
      await submitReset(resetPath, identity, password, confirmPassword, showLinkMessage, options);
    });
  }

  function bindResetPasswordStrength() {
    const passwordInput = document.getElementById("reset-link-password");
    if (!passwordInput || passwordInput.dataset.passwordStrengthBound === "true") return;
    passwordInput.dataset.passwordStrengthBound = "true";
    passwordInput.addEventListener("input", (event) => {
      updateResetPasswordStrengthDisplay(event.target.value || "");
    });
    updateResetPasswordStrengthDisplay(passwordInput.value || "");
  }

  function updatePasswordVisibilityToggle(button, input, showLabel, hideLabel) {
    const visible = input?.type === "text";
    button.innerHTML = visible ? PASSWORD_VISIBLE_ICON : PASSWORD_HIDDEN_ICON;
    button.classList.toggle("is-visible", visible);
    button.setAttribute("aria-label", visible ? hideLabel : showLabel);
    button.setAttribute("title", visible ? hideLabel : showLabel);
  }

  function bindPasswordVisibilityToggle(buttonId, inputId, showLabel, hideLabel) {
    const button = document.getElementById(buttonId);
    const input = document.getElementById(inputId);
    if (!button || !input) return;

    updatePasswordVisibilityToggle(button, input, showLabel, hideLabel);

    if (button.dataset.passwordVisibilityBound === "true") return;
    button.dataset.passwordVisibilityBound = "true";
    button.addEventListener("click", () => {
      input.type = input.type === "password" ? "text" : "password";
      updatePasswordVisibilityToggle(button, input, showLabel, hideLabel);
      input.focus({ preventScroll: true });
      try {
        const caretPosition = typeof input.value === "string" ? input.value.length : 0;
        input.setSelectionRange(caretPosition, caretPosition);
      } catch (_) {
      }
      globalThis.ShoppingLoginVisuals?.applyFocusModeFromActiveElement?.();
    });
  }

  function updateResetPasswordStrengthDisplay(password) {
    const passwordStrengthBar = document.getElementById("resetPasswordStrengthBar");
    const passwordStrengthText = document.getElementById("resetPasswordStrengthText");
    if (!passwordStrengthBar || !passwordStrengthText) return;

    if (!password) {
      passwordStrengthBar.style.width = "0";
      passwordStrengthBar.style.background = "transparent";
      passwordStrengthText.textContent = "";
      passwordStrengthText.style.color = "";
      return;
    }

    const level = checkPasswordStrength(password);
    const color = PASSWORD_STRENGTH_COLORS[level] || PASSWORD_STRENGTH_COLORS[0];
    passwordStrengthBar.style.width = `${level === 0 ? 20 : 20 + level * 20}%`;
    passwordStrengthBar.style.background = color;
    passwordStrengthText.textContent = PASSWORD_STRENGTH_LABELS[level] || "";
    passwordStrengthText.style.color = color;
  }

  function checkPasswordStrength(password) {
    if (!password || password.length <= 6) return 0;

    const isSingleCharacterTypePassword =
      /^[0-9]{7,}$/.test(password) || /^[a-z]{7,}$/.test(password) || /^[A-Z]{7,}$/.test(password);

    if (isSingleCharacterTypePassword) {
      return 1;
    }

    let score = 0;
    if (/[a-z]/.test(password)) score += 1;
    if (/[A-Z]/.test(password)) score += 1;
    if (/[0-9]/.test(password)) score += 1;
    if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) score += 1;

    if (password.length >= 9 && score === 4) return 4;
    if (password.length >= 9 && score === 3) return 3;
    return 2;
  }

  async function sendResetEmail(email, options, wafResume, captchaPayload = {}) {
    try {
      const { response, payload } = await requestResetEmailCode(email, options, wafResume, captchaPayload);
      if (payload?.challengeType === "WAF_REQUIRED" && payload?.verifyUrl) {
        persistWafPending({ email });
        window.location.assign(safeSameOriginPath(payload.verifyUrl, "/shopping/auth/waf/verify", ["/shopping/auth/waf/verify"]));
        return payload;
      }
      if (payload?.challengeType) {
        const handled = await handlePasswordResetChallenge(payload, email, options);
        if (handled) {
          return payload;
        }
      }
      if (!response.ok || !payload?.success) {
        showRequestMessage(payload?.message || "\u53d1\u9001\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
        startCooldown(Number(payload?.retryAfterMs || 0));
        return payload;
      }
      handleEmailCodeSent(payload);
      return payload;
    } catch (_) {
      showRequestMessage("\u53d1\u9001\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
      return { success: false };
    }
  }

  async function requestResetEmailCode(email, options, wafResume, captchaPayload = {}) {
    const response = await fetchWithPreAuth(options)(EMAIL_CODE_PATH, {
      method: "POST",
      headers: buildJsonHeaders(wafResume),
      body: JSON.stringify({
        email,
        captchaUuid: captchaPayload.captchaUuid || "",
        captchaCode: captchaPayload.captchaCode || ""
      })
    });
    return {
      response,
      payload: await parseJsonSafely(response)
    };
  }

  function handleEmailCodeSent(payload) {
    pendingPasswordResetChallenge = null;
    showRequestMessage(payload?.message || "\u5df2\u53d1\u9001\uff0c\u8bf7\u67e5\u770b\u90ae\u7bb1\u3002", false);
    startCooldown(Number(payload?.retryAfterMs || 60000));
    const codePanel = document.getElementById("password-reset-code-panel");
    if (codePanel) codePanel.style.display = "";
    document.getElementById("reset-code")?.focus();
    return true;
  }

  async function handlePasswordResetChallenge(payload, email, options) {
    const challengeType = String(payload?.challengeType || "").trim().toUpperCase();
    if (!challengeType || challengeType === "WAF_REQUIRED") {
      return false;
    }
    const captchaApi = initializePasswordResetCaptcha(options);
    if (!captchaApi) {
      return false;
    }
    pendingPasswordResetChallenge = {
      email,
      deviceFingerprint: resolveDeviceFingerprint(options),
      challengeType,
      challengeSubType: payload?.challengeSubType || "",
      riskLevel: payload?.riskLevel || "",
      options
    };

    if (challengeType === "HUTOOL_SHEAR_CAPTCHA") {
      captchaApi.openRegisterCaptchaModal();
      try {
        await captchaApi.loadRegisterCaptcha();
      } catch (_) {
        captchaApi.showRegisterCaptchaError("Captcha image failed to load. Please refresh and try again.");
      }
      return true;
    }

    if (challengeType === "TIANAI_CAPTCHA") {
      captchaApi.openTianaiModal();
      try {
        await captchaApi.loadTianaiCaptcha(payload?.challengeSubType || "");
      } catch (_) {
        captchaApi.showTianaiError("Security challenge failed to load. Please refresh and try again.");
      }
      return true;
    }

    if (challengeType === "CLOUDFLARE_TURNSTILE") {
      try {
        await captchaApi.renderTurnstileCaptcha(payload?.challengeSiteKey || "");
      } catch (_) {
        captchaApi.openTurnstileModal();
        captchaApi.showTurnstileError("Cloudflare Turnstile failed to load. Check the site key or network.");
      }
      return true;
    }

    if (challengeType === "HCAPTCHA") {
      try {
        await captchaApi.renderHCaptcha(payload?.challengeSiteKey || "");
      } catch (_) {
        captchaApi.openHCaptchaModal();
        captchaApi.showHCaptchaError("hCaptcha failed to load. Check the site key or network.");
      }
      return true;
    }

    if (challengeType === "GOOGLE_RECAPTCHA_V2" || challengeType === "GOOGLE_RECAPTCHA_V3") {
      try {
        await captchaApi.executeRecaptcha(payload?.challengeSiteKey || "");
      } catch (_) {
        showRequestMessage("Google reCAPTCHA failed to load. Please retry.", true);
      }
      return true;
    }

    return false;
  }

  function initializePasswordResetCaptcha(options) {
    if (passwordResetCaptchaApi) {
      return passwordResetCaptchaApi;
    }
    const dependencies = resolveCaptchaDependencies();
    if (!dependencies) {
      return null;
    }
    passwordResetCaptchaApi = dependencies.createRegisterCaptchaCoordinator({
      idPrefix: "password-reset",
      createRegisterTianai: dependencies.createRegisterTianai,
      createRegisterHutoolCaptcha: dependencies.createRegisterHutoolCaptcha,
      createRegisterTurnstile: dependencies.createRegisterTurnstile,
      createRegisterHCaptcha: dependencies.createRegisterHCaptcha,
      createRegisterRecaptcha: dependencies.createRegisterRecaptcha,
      getRegisterFormApi() {
        return {
          requestRegisterEmailCodeDelivery: requestPasswordResetEmailCodeDelivery,
          getPendingRegisterPayload() {
            return pendingPasswordResetChallenge;
          }
        };
      },
      showRegisterError(message) {
        showRequestMessage(message || "\u9a8c\u8bc1\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
      },
      triggerCaptchaFailureAnimation,
      openRegisterOtpAfterEmailSent: handleEmailCodeSent,
      handleCaptchaDeliveryFailure(payload, controls = {}) {
        if (payload?.challengeType) {
          return false;
        }
        controls.closeModal?.();
        showRequestMessage(payload?.message || controls.defaultMessage || "\u9a8c\u8bc1\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
        return true;
      },
      waitForCaptchaSuccessFeedback,
      waitForNextPaint,
      getElementDisplaySize,
      captchaSuccessFeedbackMinMs: CAPTCHA_SUCCESS_FEEDBACK_MIN_MS,
      hcaptchaAutoRetryDelayMs: HCAPTCHA_AUTO_RETRY_DELAY_MS,
      hcaptchaAutoRetryLimit: HCAPTCHA_AUTO_RETRY_LIMIT,
      hutoolCaptchaPath: PASSWORD_RESET_HUTOOL_PATH,
      tianaiCaptchaPathMap: PASSWORD_RESET_TIANAI_PATH_MAP,
      hcaptchaScriptOnloadCallbackName: "onloadPasswordResetHCaptcha"
    });
    passwordResetCaptchaApi.bindRegisterCaptchaControls();
    return passwordResetCaptchaApi;
  }

  function resolveCaptchaDependencies() {
    const registerCaptchaCoordinatorModule = globalThis.ShoppingRegisterCaptchaCoordinator;
    const registerTianaiModule = globalThis.ShoppingRegisterTianai;
    const registerHutoolCaptchaModule = globalThis.ShoppingRegisterHutoolCaptcha;
    const registerTurnstileModule = globalThis.ShoppingRegisterTurnstile;
    const registerHCaptchaModule = globalThis.ShoppingRegisterHCaptcha;
    const registerRecaptchaModule = globalThis.ShoppingRegisterRecaptcha;
    if (!registerCaptchaCoordinatorModule
        || !registerTianaiModule
        || !registerHutoolCaptchaModule
        || !registerTurnstileModule
        || !registerHCaptchaModule
        || !registerRecaptchaModule) {
      return null;
    }
    return {
      createRegisterCaptchaCoordinator: registerCaptchaCoordinatorModule.createRegisterCaptchaCoordinator,
      createRegisterTianai: registerTianaiModule.createRegisterTianai,
      createRegisterHutoolCaptcha: registerHutoolCaptchaModule.createRegisterHutoolCaptcha,
      createRegisterTurnstile: registerTurnstileModule.createRegisterTurnstile,
      createRegisterHCaptcha: registerHCaptchaModule.createRegisterHCaptcha,
      createRegisterRecaptcha: registerRecaptchaModule.createRegisterRecaptcha
    };
  }

  async function requestPasswordResetEmailCodeDelivery(captchaUuid, captchaCode) {
    if (!pendingPasswordResetChallenge?.email) {
      return {
        success: false,
        message: "Password reset challenge context expired. Please submit again."
      };
    }
    try {
      const { response, payload } = await requestResetEmailCode(
        pendingPasswordResetChallenge.email,
        pendingPasswordResetChallenge.options || {},
        false,
        { captchaUuid, captchaCode }
      );
      const normalizedPayload = payload || {
        success: false,
        message: "Verification failed. Please retry."
      };
      if (!response.ok && !normalizedPayload.message) {
        normalizedPayload.message = "Verification failed. Please retry.";
      }
      if (normalizedPayload.challengeType) {
        pendingPasswordResetChallenge.challengeType = normalizedPayload.challengeType || "";
        pendingPasswordResetChallenge.challengeSubType = normalizedPayload.challengeSubType || "";
        pendingPasswordResetChallenge.riskLevel = normalizedPayload.riskLevel || pendingPasswordResetChallenge.riskLevel || "";
      }
      return normalizedPayload;
    } catch (_) {
      return {
        success: false,
        message: "Verification failed. Please retry."
      };
    }
  }

  async function resumeAfterWaf(options) {
    if (readCookie(WAF_RESUME_COOKIE) !== "1") return;
    clearCookie(WAF_RESUME_COOKIE);
    const pending = consumeWafPending();
    if (!pending?.email) return;
    const emailInput = document.getElementById("reset-email");
    if (emailInput) emailInput.value = pending.email;
    await sendResetEmail(pending.email, options, true);
  }

  async function submitReset(path, identity, password, confirmPassword, showMessage, options) {
    if (!password || password.length < 8) {
      showMessage("\u5bc6\u7801\u81f3\u5c11\u9700\u8981 8 \u4e2a\u5b57\u7b26\u3002", true);
      return;
    }
    if (password !== confirmPassword) {
      showMessage("\u4e24\u6b21\u8f93\u5165\u7684\u5bc6\u7801\u4e0d\u4e00\u81f4\u3002", true);
      return;
    }
    try {
      const encrypted = await encryptPasswordPayload({ password, confirmPassword }, options);
      const response = await fetchWithPreAuth(options)(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...identity, ...encrypted })
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        showMessage(payload?.message || "\u91cd\u7f6e\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
        return;
      }
      showMessage(payload.message || "\u5bc6\u7801\u5df2\u91cd\u7f6e\u3002", false);
      setTimeout(() => {
        options.shellApi?.navigateTo?.("/shopping/user/log-in", { replace: true });
      }, 800);
    } catch (_) {
      showMessage("\u91cd\u7f6e\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002", true);
    }
  }

  async function encryptPasswordPayload(payload, options) {
    const keyResponse = await fetchWithPreAuth(options)(CRYPTO_KEY_PATH, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}"
    });
    const keyPayload = await parseJsonSafely(keyResponse);
    const cryptoPayload = keyPayload?.passwordCrypto;
    if (!keyResponse.ok || !cryptoPayload?.kid || !cryptoPayload?.publicKeyJwk) {
      throw new Error("password reset crypto key unavailable");
    }
    const cryptoKey = await globalThis.crypto.subtle.importKey(
      "jwk",
      cryptoPayload.publicKeyJwk,
      { name: "RSA-OAEP", hash: "SHA-256" },
      false,
      ["encrypt"]
    );
    const rawBytes = new TextEncoder().encode(JSON.stringify(payload));
    const encryptedBuffer = await globalThis.crypto.subtle.encrypt({ name: "RSA-OAEP" }, cryptoKey, rawBytes);
    return {
      kid: cryptoPayload.kid,
      payloadCipher: encodeBase64Url(new Uint8Array(encryptedBuffer)),
      nonce: randomToken(24),
      timestamp: Date.now()
    };
  }

  function startCooldown(durationMs) {
    const effectiveMs = Math.max(0, Number(durationMs || 0));
    if (cooldownTimer) {
      clearInterval(cooldownTimer);
      cooldownTimer = null;
    }
    if (effectiveMs <= 0) {
      setSendButtonsDisabled(false);
      return;
    }
    const endsAt = Date.now() + effectiveMs;
    setSendButtonsDisabled(true, Math.ceil(effectiveMs / 1000));
    cooldownTimer = setInterval(() => {
      const remainingSeconds = Math.ceil(Math.max(0, endsAt - Date.now()) / 1000);
      if (remainingSeconds <= 0) {
        clearInterval(cooldownTimer);
        cooldownTimer = null;
        setSendButtonsDisabled(false);
        return;
      }
      setSendButtonsDisabled(true, remainingSeconds);
    }, 1000);
  }

  function setSendButtonsDisabled(disabled, remainingSeconds = 0) {
    setButtonState("btn-reset-code", disabled, disabled ? `${remainingSeconds}s` : "\u53d1\u9001\u9a8c\u8bc1\u7801");
  }

  function setButtonState(id, disabled, text) {
    const button = document.getElementById(id);
    if (!button) return;
    button.disabled = Boolean(disabled);
    button.querySelectorAll(".btn-text, .btn-hover-content span").forEach((node) => {
      node.textContent = text;
    });
  }

  function buildJsonHeaders(wafResume) {
    const headers = { "Content-Type": "application/json" };
    if (wafResume) {
      headers[WAF_RESUME_HEADER] = "1";
    }
    return headers;
  }

  function fetchWithPreAuth(options = {}) {
    const client = options.preAuthClientApi || window.ShoppingPreAuthClient;
    if (client?.fetchWithPreAuth) {
      return client.fetchWithPreAuth.bind(client);
    }
    return () => Promise.reject(new Error("Pre-authentication client is unavailable."));
  }

  function resolveDeviceFingerprint(options) {
    return options.preAuthClientApi?.buildDeviceFingerprint?.()
      || window.ShoppingPreAuthClient?.buildDeviceFingerprint?.()
      || "";
  }

  function triggerCaptchaFailureAnimation() {
    globalThis.ShoppingLoginVisuals?.triggerLoginError?.();
  }

  async function waitForCaptchaSuccessFeedback(startedAt, minMs = CAPTCHA_SUCCESS_FEEDBACK_MIN_MS) {
    const elapsed = Date.now() - Number(startedAt || Date.now());
    const remaining = Math.max(0, Number(minMs || 0) - elapsed);
    if (remaining <= 0) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, remaining));
  }

  async function waitForNextPaint() {
    await new Promise((resolve) => requestAnimationFrame(() => resolve()));
  }

  function getElementDisplaySize(element, fallbackWidth = 0, fallbackHeight = 0) {
    if (!element) {
      return { width: fallbackWidth, height: fallbackHeight };
    }
    const rect = element.getBoundingClientRect();
    return {
      width: Math.max(1, Math.round(rect.width || element.width || fallbackWidth || 1)),
      height: Math.max(1, Math.round(rect.height || element.height || fallbackHeight || 1))
    };
  }

  function readEmail() {
    return document.getElementById("reset-email")?.value?.trim() || "";
  }

  function currentResetToken() {
    try {
      return new URL(window.location.href).searchParams.get("token") || "";
    } catch (_) {
      return "";
    }
  }

  function currentResetMode() {
    try {
      const pathname = new URL(window.location.href).pathname;
      return pathname === "/shopping/user/reset-password-code" ? "code" : "url";
    } catch (_) {
      return "url";
    }
  }

  function isResetPasswordPage() {
    try {
      const pathname = new URL(window.location.href).pathname;
      return pathname === "/shopping/user/reset-password-code"
          || pathname === "/shopping/user/reset-password-url";
    } catch (_) {
      return Boolean(currentResetToken());
    }
  }

  function showRequestMessage(message, isError) {
    showMessage("reset-request-msg", message, isError);
  }

  function showLinkMessage(message, isError) {
    showMessage("reset-link-msg", message, isError);
  }

  function showMessage(id, message, isError) {
    const node = document.getElementById(id);
    if (!node) return;
    node.textContent = message || "";
    node.style.display = message ? "block" : "none";
    node.style.color = isError ? "" : "#166534";
  }

  async function parseJsonSafely(response) {
    try {
      return await response.json();
    } catch (_) {
      return null;
    }
  }

  function persistWafPending(payload) {
    try {
      sessionStorage.setItem(WAF_PENDING_KEY, JSON.stringify(payload));
    } catch (_) {
    }
  }

  function consumeWafPending() {
    try {
      const raw = sessionStorage.getItem(WAF_PENDING_KEY);
      sessionStorage.removeItem(WAF_PENDING_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_) {
      return null;
    }
  }

  function readCookie(name) {
    const target = `${name}=`;
    return (document.cookie || "").split(";").map((item) => item.trim())
      .find((item) => item.startsWith(target))?.substring(target.length) || "";
  }

  function clearCookie(name) {
    document.cookie = `${name}=; Max-Age=0; Path=/; SameSite=Lax`;
  }

  function randomToken(length) {
    const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    const bytes = new Uint8Array(length);
    globalThis.crypto.getRandomValues(bytes);
    return Array.from(bytes, (value) => alphabet[value % alphabet.length]).join("");
  }

  function encodeBase64Url(bytes) {
    let binary = "";
    bytes.forEach((byte) => {
      binary += String.fromCharCode(byte);
    });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  }

  return {
    initializePasswordResetFragment
  };
});
