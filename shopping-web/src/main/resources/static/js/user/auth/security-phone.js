(function () {
  const CODE_PATH = "/shopping/user/security/phone/code";
  const BIND_PATH = "/shopping/user/security/phone/bind";
  const CONSOLE_PATH = "/shopping/user/console";
  const DEFAULT_COOLDOWN_MS = 60000;

  const form = document.getElementById("security-phone-form");
  const status = document.getElementById("phone-status");
  const countryTrigger = document.getElementById("security-phone-country-trigger");
  const phoneInput = document.getElementById("security-phone-number");
  const codeRow = document.getElementById("security-phone-code-row");
  const codeInput = document.getElementById("security-phone-code");
  const sendButton = document.getElementById("security-phone-send");
  const bindButton = document.getElementById("security-phone-bind");
  const challengeBox = document.getElementById("security-phone-challenge");
  const challengeTitle = document.getElementById("security-phone-challenge-title");
  const challengeWidget = document.getElementById("security-phone-challenge-widget");
  const authClient = window.ShoppingAuthClient;
  const countryPickerApi = window.ShoppingLoginCountryPicker;

  let cooldownTimer = null;
  let cooldownUntil = 0;
  let sending = false;
  let binding = false;

  countryPickerApi?.initSecurityPhoneCountryPicker?.();
  countryPickerApi?.autoDetectPhoneCountryCode?.();

  function setStatus(message, type = "") {
    status.textContent = message || "";
    status.classList.toggle("error", type === "error");
    status.classList.toggle("success", type === "success");
  }

  function resolvePhoneParts() {
    const resolved = countryPickerApi?.resolveSecurityPhoneForSubmit?.({ silent: true });
    if (resolved) {
      return resolved;
    }
    return {
      dialCode: (document.getElementById("security-phone-country-code")?.value || "").trim(),
      phoneNumber: (phoneInput.value || "").replace(/[^\d]/g, "")
    };
  }

  function validatePhoneFields() {
    const phoneParts = resolvePhoneParts();
    const dialCode = (phoneParts?.dialCode || "").trim();
    const phoneNumber = (phoneParts?.phoneNumber || "").replace(/[^\d]/g, "");
    if (!/^\+\d{1,5}$/.test(dialCode)) {
      setStatus("请输入正确的国家或地区区号。", "error");
      countryTrigger?.focus();
      return null;
    }
    if (!/^\d{6,15}$/.test(phoneNumber)) {
      setStatus("请输入正确的手机号。", "error");
      phoneInput.focus();
      return null;
    }
    return { dialCode, phoneNumber };
  }

  async function parseJson(response) {
    try {
      return await response.json();
    } catch (_) {
      return null;
    }
  }

  async function postJson(url, body) {
    if (!authClient?.fetchWithAuth) {
      throw new Error("认证客户端未加载。");
    }
    return authClient.fetchWithAuth(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body || {})
    });
  }

  function clearChallenge() {
    challengeBox.hidden = true;
    challengeWidget.innerHTML = "";
  }

  function startCooldown(retryAfterMs) {
    const cooldownMs = Math.max(0, Number(retryAfterMs) || DEFAULT_COOLDOWN_MS);
    if (cooldownMs <= 0) {
      return;
    }
    cooldownUntil = Date.now() + cooldownMs;
    window.clearInterval(cooldownTimer);
    cooldownTimer = window.setInterval(refreshCooldown, 250);
    refreshCooldown();
  }

  function refreshCooldown() {
    const remainingMs = Math.max(0, cooldownUntil - Date.now());
    if (remainingMs <= 0) {
      window.clearInterval(cooldownTimer);
      cooldownTimer = null;
      sendButton.disabled = sending;
      sendButton.textContent = "发送验证码";
      return;
    }
    sendButton.disabled = true;
    sendButton.textContent = `${Math.ceil(remainingMs / 1000)} 秒后重发`;
  }

  function onCodeSent(payload) {
    clearChallenge();
    codeRow.hidden = false;
    codeInput.focus();
    startCooldown(payload?.retryAfterMs || DEFAULT_COOLDOWN_MS);
    setStatus(payload?.message || "验证码已发送，请查看短信。", "success");
  }

  function loadScript(src, globalName) {
    if (globalName && window[globalName]) {
      return Promise.resolve(window[globalName]);
    }
    const existing = document.querySelector(`script[data-security-phone-src="${src}"]`);
    if (existing) {
      return new Promise((resolve, reject) => {
        existing.addEventListener("load", () => resolve(globalName ? window[globalName] : true), { once: true });
        existing.addEventListener("error", reject, { once: true });
      });
    }
    return new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = src;
      script.async = true;
      script.defer = true;
      script.dataset.securityPhoneSrc = src;
      script.onload = () => resolve(globalName ? window[globalName] : true);
      script.onerror = reject;
      document.head.appendChild(script);
    });
  }

  async function submitChallengeCode(captchaCode) {
    if (!captchaCode) {
      setStatus("安全验证失败，请重试。", "error");
      return;
    }
    await sendCode("", captchaCode);
  }

  async function renderTurnstile(siteKey) {
    const turnstile = await loadScript("https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit", "turnstile");
    turnstile.render(challengeWidget, {
      sitekey: siteKey,
      callback: submitChallengeCode
    });
  }

  async function renderHCaptcha(siteKey) {
    const hcaptcha = await loadScript("https://js.hcaptcha.com/1/api.js?render=explicit", "hcaptcha");
    hcaptcha.render(challengeWidget, {
      sitekey: siteKey,
      callback: submitChallengeCode
    });
  }

  async function renderRecaptchaV2(siteKey) {
    const grecaptcha = await loadScript("https://www.google.com/recaptcha/api.js?render=explicit", "grecaptcha");
    grecaptcha.ready(() => {
      grecaptcha.render(challengeWidget, {
        sitekey: siteKey,
        callback: submitChallengeCode
      });
    });
  }

  async function executeRecaptchaV3(siteKey) {
    const grecaptcha = await loadScript(`https://www.google.com/recaptcha/api.js?render=${encodeURIComponent(siteKey)}`, "grecaptcha");
    grecaptcha.ready(() => {
      grecaptcha.execute(siteKey, { action: "security_phone" }).then(submitChallengeCode);
    });
  }

  async function showChallenge(payload) {
    const challengeType = String(payload?.challengeType || "").toUpperCase();
    const siteKey = payload?.challengeSiteKey || "";
    challengeBox.hidden = false;
    challengeWidget.innerHTML = "";
    challengeTitle.textContent = "请先完成安全验证，完成后会自动发送短信验证码。";
    if (!siteKey) {
      setStatus("安全验证配置缺失，暂时不能发送短信。", "error");
      return;
    }
    try {
      if (challengeType === "CLOUDFLARE_TURNSTILE") {
        await renderTurnstile(siteKey);
        return;
      }
      if (challengeType === "HCAPTCHA") {
        await renderHCaptcha(siteKey);
        return;
      }
      if (challengeType === "GOOGLE_RECAPTCHA_V2") {
        await renderRecaptchaV2(siteKey);
        return;
      }
      if (challengeType === "GOOGLE_RECAPTCHA_V3") {
        await executeRecaptchaV3(siteKey);
        return;
      }
      setStatus(`暂不支持当前安全验证类型：${challengeType || "UNKNOWN"}。`, "error");
    } catch (_) {
      setStatus("安全验证组件加载失败，请刷新后重试。", "error");
    }
  }

  async function sendCode(captchaUuid = "", captchaCode = "") {
    const phone = validatePhoneFields();
    if (!phone || sending) {
      return;
    }
    sending = true;
    sendButton.disabled = true;
    bindButton.disabled = true;
    setStatus("正在发送短信验证码...");
    try {
      const response = await postJson(CODE_PATH, {
        dialCode: phone.dialCode,
        phoneNumber: phone.phoneNumber,
        captchaUuid,
        captchaCode
      });
      const payload = await parseJson(response);
      if (response.ok && payload?.success) {
        if (payload.requirePhoneBinding === false && payload.redirectPath) {
          window.location.assign(payload.redirectPath);
          return;
        }
        onCodeSent(payload);
        return;
      }
      if (payload?.challengeType) {
        setStatus(payload?.message || "需要完成安全验证。");
        await showChallenge(payload);
        return;
      }
      if (payload?.retryAfterMs) {
        startCooldown(payload.retryAfterMs);
      }
      setStatus(payload?.message || "短信验证码发送失败。", "error");
    } catch (error) {
      setStatus(error?.message || "短信验证码发送失败。", "error");
    } finally {
      sending = false;
      bindButton.disabled = false;
      refreshCooldown();
    }
  }

  async function bindPhone() {
    const phone = validatePhoneFields();
    if (!phone || binding) {
      return;
    }
    const smsCode = (codeInput.value || "").trim();
    if (!/^\d{6}$/.test(smsCode)) {
      setStatus("请输入 6 位短信验证码。", "error");
      codeInput.focus();
      return;
    }
    binding = true;
    sendButton.disabled = true;
    bindButton.disabled = true;
    setStatus("正在完成手机号验证...");
    try {
      const response = await postJson(BIND_PATH, {
        dialCode: phone.dialCode,
        phoneNumber: phone.phoneNumber,
        smsCode
      });
      const payload = await parseJson(response);
      if (!response.ok || !payload?.success) {
        setStatus(payload?.message || "手机号验证失败。", "error");
        return;
      }
      setStatus(payload?.message || "手机号验证已完成。", "success");
      window.location.assign(payload?.redirectPath || CONSOLE_PATH);
    } catch (error) {
      setStatus(error?.message || "手机号验证失败。", "error");
    } finally {
      binding = false;
      bindButton.disabled = false;
      refreshCooldown();
    }
  }

  sendButton?.addEventListener("click", () => sendCode());
  form?.addEventListener("submit", (event) => {
    event.preventDefault();
    bindPhone();
  });
})();
