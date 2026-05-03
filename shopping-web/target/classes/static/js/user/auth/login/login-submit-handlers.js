(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingLoginSubmitHandlers = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const DEFAULT_PHONE_VALIDATE_PATH = "/shopping/auth/preauth/phone-validate";
  const LOGIN_FLOW_START_PATH = "/shopping/user/login/flow/start";
  const LOGIN_FLOW_CURRENT_PATH = "/shopping/user/login/flow/current";
  const LOGIN_PASSWORD_PATH = "/shopping/user/login/password";
  const LOGIN_EMAIL_CODE_PATH = "/shopping/user/login/email-code";
  const LOGIN_EMAIL_CODE_VERIFY_PATH = "/shopping/user/login/email-code/verify";
  const LOGIN_TOTP_VERIFY_PATH = "/shopping/user/login/totp/verify";
  const LOGIN_PHONE_CHECK_PATH = "/shopping/user/login/phone/check";
  const LOGIN_PHONE_CODE_PATH = "/shopping/user/login/phone/code";
  const LOGIN_PHONE_LOGIN_CODE_PATH = "/shopping/user/login/phone-login/code";
  const LOGIN_PHONE_BIND_PATH = "/shopping/user/login/phone/bind";
  const REGISTER_PHONE_CODE_PATH = "/shopping/user/register/phone/code";
  const REGISTER_PHONE_BIND_PATH = "/shopping/user/register/phone/bind";
  const LOGIN_WAF_PENDING_START_KEY = "shopping.login.waf.pending-start";
  const ERROR_INVALID_STATE = "INVALID_STATE";
  const INVALID_STATE_MESSAGE = "验证过程中出错 (invalid_state)。请重试。";

  function createLoginSubmitHandlers(options = {}) {
    const shellApi = options.shellApi || null;
    const otpApi = options.otpApi || null;
    const preAuthClientApi = options.preAuthClientApi || null;
    const phoneValidatePath = options.phoneValidatePath || DEFAULT_PHONE_VALIDATE_PATH;
    const loginForm = options.loginForm || null;
    const emailInput = options.emailInput || null;
    const phoneNumberInput = options.phoneNumberInput || null;
    const passwordInput = options.passwordInput || null;
    const otpCodeInput = options.otpCodeInput || null;
    const phoneErrorMessage = options.phoneErrorMessage || null;
    const phoneNumberLabel = options.phoneNumberLabel || null;
    const identifierLabel = options.identifierLabel || null;
    const identifierValue = options.identifierValue || null;
    const authPaths = options.authPaths || {};
    const setLastIdentifierView = typeof options.setLastIdentifierView === "function"
      ? options.setLastIdentifierView
      : () => {};
    const triggerLoginError = typeof options.triggerLoginError === "function"
      ? options.triggerLoginError
      : () => {};

    let handleRegisterSubmit = typeof options.handleRegisterSubmit === "function"
      ? options.handleRegisterSubmit
      : async () => false;
    let handleLoginChallenge = typeof options.handleLoginChallenge === "function"
      ? options.handleLoginChallenge
      : async () => false;
    let currentLoginFlow = null;
    let pendingLoginStartPayload = null;
    let loginEmailCodeRequestInFlight = false;

    function normalizeRoutePath(routeTarget) {
      const rawRouteTarget = typeof routeTarget === "string" ? routeTarget.trim() : "";
      if (!rawRouteTarget) {
        return "";
      }
      try {
        return new URL(rawRouteTarget, window.location.origin).pathname.replace(/\/+$/g, "");
      } catch (_) {
        return rawRouteTarget.split(/[?#]/)[0].replace(/\/+$/g, "");
      }
    }

    function loginModePath(basePathKey, modePathKey) {
      const modePath = authPaths[modePathKey];
      if (typeof modePath === "string" && modePath) {
        return modePath;
      }
      const basePath = authPaths[basePathKey] || "";
      return basePath ? `${basePath}?mode=login` : "";
    }

    function setHandleRegisterSubmit(nextHandleRegisterSubmit) {
      handleRegisterSubmit = typeof nextHandleRegisterSubmit === "function"
        ? nextHandleRegisterSubmit
        : async () => false;
    }

    function setHandleLoginChallenge(nextHandleLoginChallenge) {
      handleLoginChallenge = typeof nextHandleLoginChallenge === "function"
        ? nextHandleLoginChallenge
        : async () => false;
    }

    function getFetchWithPreAuth() {
      return preAuthClientApi?.fetchWithPreAuth
        ? preAuthClientApi.fetchWithPreAuth
        : (url, requestOptions) => fetch(url, { ...requestOptions, credentials: "same-origin" });
    }

    function buildDeviceFingerprint() {
      return preAuthClientApi?.buildDeviceFingerprint?.() || "";
    }

    async function parseJsonSafely(response) {
      try {
        return await response.json();
      } catch (_) {
        return {};
      }
    }

    function setPendingLoginStartPayload(payload) {
      pendingLoginStartPayload = payload && typeof payload === "object" ? payload : null;
    }

    function getPendingLoginStartPayload() {
      return pendingLoginStartPayload;
    }

    function persistPendingLoginStartPayloadForWaf() {
      if (typeof window === "undefined" || !window.sessionStorage || !pendingLoginStartPayload?.email) {
        return;
      }
      try {
        window.sessionStorage.setItem(LOGIN_WAF_PENDING_START_KEY, JSON.stringify({
          email: pendingLoginStartPayload.email,
          deviceFingerprint: pendingLoginStartPayload.deviceFingerprint || ""
        }));
      } catch (_) {
      }
    }

    function consumePendingLoginStartPayloadForWaf() {
      if (typeof window === "undefined" || !window.sessionStorage) {
        return null;
      }
      try {
        const raw = window.sessionStorage.getItem(LOGIN_WAF_PENDING_START_KEY);
        window.sessionStorage.removeItem(LOGIN_WAF_PENDING_START_KEY);
        if (!raw) {
          return null;
        }
        const payload = JSON.parse(raw);
        if (!payload || typeof payload !== "object") {
          return null;
        }
        return {
          email: typeof payload.email === "string" ? payload.email : "",
          deviceFingerprint: typeof payload.deviceFingerprint === "string" ? payload.deviceFingerprint : ""
        };
      } catch (_) {
        return null;
      }
    }

    function shouldResumeAfterWafVerification() {
      if (typeof window === "undefined" || !window.location) {
        return false;
      }
      try {
        return new URL(window.location.href).searchParams.get("waf_verified") === "1";
      } catch (_) {
        return false;
      }
    }

    function stripWafVerifiedQueryFlag() {
      if (typeof window === "undefined" || !window.location || !window.history
          || typeof window.history.replaceState !== "function") {
        return;
      }
      try {
        const currentUrl = new URL(window.location.href);
        if (!currentUrl.searchParams.has("waf_verified")) {
          return;
        }
        currentUrl.searchParams.delete("waf_verified");
        const nextUrl = `${currentUrl.pathname}${currentUrl.search}${currentUrl.hash}`;
        window.history.replaceState({}, "", nextUrl);
      } catch (_) {
      }
    }

    function setCurrentLoginFlow(flow) {
      currentLoginFlow = flow && typeof flow === "object" ? flow : null;
    }

    function getCurrentLoginFlow() {
      return currentLoginFlow;
    }

    function resetPhoneValidationState() {
      if (phoneErrorMessage) phoneErrorMessage.style.display = "none";
      if (phoneNumberInput) phoneNumberInput.classList.remove("error");
      if (phoneNumberLabel) phoneNumberLabel.classList.remove("error-label");
    }

    function showPhoneValidationError(message, input, label) {
      if (input) input.classList.add("error");
      if (label) label.classList.add("error-label");
      if (phoneErrorMessage) {
        phoneErrorMessage.textContent = message;
        phoneErrorMessage.style.display = "block";
      }
      triggerLoginError();
    }

    function showEmailError(message) {
      const errEl = document.getElementById("error-msg");
      const emailLabel = document.getElementById("email-label");
      if (emailInput) emailInput.classList.add("error");
      if (emailLabel) emailLabel.classList.add("error-label");
      if (errEl) {
        errEl.textContent = message;
        errEl.style.display = "block";
      }
      triggerLoginError();
    }

    function clearEmailError() {
      const errEl = document.getElementById("error-msg");
      const emailLabel = document.getElementById("email-label");
      if (errEl) {
        errEl.textContent = "";
        errEl.style.display = "none";
      }
      if (emailInput) emailInput.classList.remove("error");
      if (emailLabel) emailLabel.classList.remove("error-label");
    }

    function showPasswordError(message) {
      const passwordErrorMessage = document.getElementById("password-error-msg");
      if (passwordErrorMessage) {
        passwordErrorMessage.textContent = message;
        passwordErrorMessage.style.display = "block";
      }
      triggerLoginError();
    }

    function clearPasswordError() {
      const passwordErrorMessage = document.getElementById("password-error-msg");
      if (passwordErrorMessage) {
        passwordErrorMessage.textContent = "";
        passwordErrorMessage.style.display = "none";
      }
    }

    async function showInvalidStateTerminal(payload = {}) {
      currentLoginFlow = null;
      pendingLoginStartPayload = null;
      clearEmailError();
      clearPasswordError();
      clearOtpError();

      const titleNode = document.getElementById("session-ended-title");
      const messageNode = document.getElementById("session-ended-message");
      const primaryAction = document.getElementById("session-ended-primary-action");
      const secondaryAction = document.getElementById("session-ended-secondary-action");
      if (titleNode) {
        titleNode.textContent = "糟糕，出错了！";
      }
      if (messageNode) {
        messageNode.textContent = payload?.message || INVALID_STATE_MESSAGE;
      }
      if (primaryAction) {
        primaryAction.setAttribute("href", authPaths.LOGIN || "/shopping/user/log-in");
        primaryAction.textContent = "重试";
      }
      if (secondaryAction) {
        secondaryAction.style.display = "none";
      }

      const terminalPath = payload?.redirectPath || authPaths.SESSION_ENDED || "/shopping/user/session-ended";
      if (typeof shellApi?.navigateTo === "function") {
        await shellApi.navigateTo(terminalPath, { replace: true });
        return;
      }
      shellApi?.setAuthView?.("session-ended");
    }

    function isInvalidStatePayload(payload) {
      return payload?.error === ERROR_INVALID_STATE;
    }

    function showOtpError(message) {
      const otpErrorMessage = document.getElementById("otp-error-msg");
      if (otpErrorMessage) {
        otpErrorMessage.textContent = message;
        otpErrorMessage.style.display = "block";
      }
      triggerLoginError();
    }

    function clearOtpError() {
      const otpErrorMessage = document.getElementById("otp-error-msg");
      if (otpErrorMessage) {
        otpErrorMessage.textContent = "";
        otpErrorMessage.style.display = "none";
      }
    }

    function showOtpStatus(message) {
      const otpErrorMessage = document.getElementById("otp-error-msg");
      if (otpErrorMessage) {
        otpErrorMessage.textContent = message;
        otpErrorMessage.style.display = "block";
      }
    }

    function resolveDialCodeValue(inputId) {
      return (document.getElementById(inputId)?.value || "").trim();
    }

    function resolvePhoneValidationMessage(payload) {
      const reasonCode = payload?.reasonCode || "";
      switch (reasonCode) {
        case "PHONE_VOIP_NOT_ALLOWED":
          return "Virtual or VoIP phone numbers are not allowed";
        case "PHONE_FIXED_LINE_NOT_ALLOWED":
          return "Landline phone numbers are not allowed";
        case "PHONE_TYPE_NOT_ALLOWED":
          return "Only mobile phone numbers are allowed";
        case "PHONE_INVALID_DIAL_CODE":
          return "Please choose a valid country or region";
        case "PHONE_INVALID":
          return "Please enter a valid mobile phone number";
        case "PHONE_ALREADY_BOUND":
          return "This phone number is already in use";
        case "PHONE_BOUND_BLOOM_UNAVAILABLE":
          return "Phone validation is temporarily unavailable. Please try again later";
        default:
          return payload?.message || "Phone number validation failed";
      }
    }

    async function validatePhoneNumberPolicy(dialCode, rawPhone, purpose = "") {
      const requestOptions = {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          dialCode,
          phoneNumber: rawPhone,
          purpose
        })
      };

      const response = await getFetchWithPreAuth()(phoneValidatePath, requestOptions);
      const payload = await parseJsonSafely(response);

      if (!response.ok || !payload?.success) {
        return {
          success: false,
          message: resolvePhoneValidationMessage(payload),
          normalizedE164: payload?.normalizedE164 || ""
        };
      }

      return {
        success: true,
        message: payload.message || "ok",
        normalizedE164: payload.normalizedE164 || ""
      };
    }

    function updateIdentifier(identifierType, identifierText) {
      if (!identifierLabel || !identifierValue) {
        return;
      }
      identifierLabel.textContent = identifierType === "phone" ? "Phone number" : "Email address";
      identifierValue.textContent = identifierText;
      otpApi?.setIdentifierContext?.(identifierType, identifierText);
    }

    async function navigateToLoginStep(payload, options = {}) {
      if (!payload) {
        return;
      }
      setCurrentLoginFlow(payload);
      const identifierText = options.identifierText || payload.email || identifierValue?.textContent || "";
      const identifierType = options.identifierType || "email";
      updateIdentifier(identifierType, identifierText);

      const step = payload.step || "";
      const redirectPath = payload.redirectPath || "";
      const redirectPathname = normalizeRoutePath(redirectPath);
      if (step === "TOTP_VERIFICATION" || redirectPathname === authPaths.TOTP_VERIFICATION) {
        otpApi?.setLoginFactorContext?.("TOTP", identifierType, identifierText);
        otpApi?.openLoginOtpStep?.("TOTP");
        await shellApi?.navigateTo?.(loginModePath("TOTP_VERIFICATION", "LOGIN_TOTP_VERIFICATION"));
        return;
      }
      if (step === "EMAIL_VERIFICATION" || redirectPathname === authPaths.EMAIL_VERIFICATION) {
        otpApi?.setLoginFactorContext?.("EMAIL_OTP", identifierType, identifierText);
        otpApi?.openLoginOtpStep?.("EMAIL_OTP");
        await shellApi?.navigateTo?.(loginModePath("EMAIL_VERIFICATION", "LOGIN_EMAIL_VERIFICATION"));
        if (!options.skipAutoEmailCode && !loginEmailCodeRequestInFlight) {
          loginEmailCodeRequestInFlight = true;
          try {
            await requestLoginEmailCode({
              skipNavigation: true,
              showStatus: true
            });
          } finally {
            loginEmailCodeRequestInFlight = false;
          }
        }
        return;
      }
      if (step === "ADD_PHONE" || redirectPathname === authPaths.ADD_PHONE) {
        await shellApi?.navigateTo?.(loginModePath("ADD_PHONE", "LOGIN_ADD_PHONE"));
        return;
      }
      if (redirectPath) {
        await shellApi?.navigateTo?.(redirectPath);
        return;
      }
      shellApi?.setAuthView?.("password");
    }

    function buildLoginFlowStartRequest(email, deviceFingerprint, captchaUuid = "", captchaCode = "") {
      return {
        email: email || "",
        deviceFingerprint: deviceFingerprint || "",
        captchaUuid: captchaUuid || "",
        captchaCode: captchaCode || ""
      };
    }

    async function postLoginFlowStart(requestBody) {
      return getFetchWithPreAuth()(LOGIN_FLOW_START_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(requestBody)
      });
    }

    async function resolveSuccessfulLoginStart(payload, options = {}) {
      if (!payload?.success) {
        return false;
      }

      clearEmailError();
      await navigateToLoginStep(payload, {
        identifierType: "email",
        identifierText: options.identifierText
          || payload.email
          || pendingLoginStartPayload?.email
          || identifierValue?.textContent
          || ""
      });

      const step = payload.step || "";
      const redirectPath = payload.redirectPath || "";
      if ((step === "PASSWORD" || redirectPath === authPaths.LOGIN_PASSWORD || (!step && !redirectPath))
          && passwordInput) {
        passwordInput.value = "";
        passwordInput.focus();
      }
      return true;
    }

    async function handleLoginFlowFailure(payload, fallbackMessage, errorTarget) {
      const challengeType = payload?.challengeType || "";
      const message = payload?.message || fallbackMessage;
      if (challengeType === "WAF_REQUIRED" && payload?.verifyUrl) {
        persistPendingLoginStartPayloadForWaf();
        window.location.assign(payload.verifyUrl);
        return true;
      }
      if (challengeType === "OPERATION_TIMEOUT") {
        errorTarget(message);
        return true;
      }
      if (challengeType) {
        try {
          const handled = await handleLoginChallenge(payload, {
            errorTarget,
            fallbackMessage: message
          });
          if (handled) {
            return true;
          }
        } catch (error) {
          if (typeof console !== "undefined" && typeof console.warn === "function") {
            console.warn("Login challenge handling failed", error);
          }
        }
        errorTarget(message || `Security challenge required: ${challengeType}`);
        return true;
      }
      errorTarget(message);
      return false;
    }

    async function fetchCurrentLoginFlowState() {
      const response = await getFetchWithPreAuth()(LOGIN_FLOW_CURRENT_PATH, {
        method: "GET",
        headers: {
          Accept: "application/json"
        }
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        setCurrentLoginFlow(null);
        return null;
      }
      setCurrentLoginFlow(payload);
      return payload;
    }

    async function ensureCurrentLoginFlow() {
      if (currentLoginFlow?.success) {
        return currentLoginFlow;
      }
      return fetchCurrentLoginFlowState();
    }

    async function startLoginFlowFromEmail(email) {
      const requestPayload = {
        email,
        deviceFingerprint: buildDeviceFingerprint(),
        challengeType: "",
        challengeSubType: "",
        riskLevel: ""
      };
      setPendingLoginStartPayload(requestPayload);
      const response = await postLoginFlowStart(
        buildLoginFlowStartRequest(email, requestPayload.deviceFingerprint)
      );
      const payload = await parseJsonSafely(response);
      return { response, payload };
    }

    async function submitPendingLoginChallenge(captchaUuid = "", captchaCode = "") {
      if (!pendingLoginStartPayload?.email) {
        throw new Error("login challenge context is unavailable");
      }
      return postLoginFlowStart(
        buildLoginFlowStartRequest(
          pendingLoginStartPayload.email,
          pendingLoginStartPayload.deviceFingerprint || buildDeviceFingerprint(),
          captchaUuid,
          captchaCode
        )
      );
    }

    async function resumeLoginFlowAfterWaf() {
      if (!shouldResumeAfterWafVerification()) {
        return false;
      }

      const pendingPayload = consumePendingLoginStartPayloadForWaf();
      stripWafVerifiedQueryFlag();
      if (!pendingPayload?.email) {
        return false;
      }

      setPendingLoginStartPayload({
        email: pendingPayload.email,
        deviceFingerprint: pendingPayload.deviceFingerprint || buildDeviceFingerprint(),
        challengeType: "",
        challengeSubType: "",
        riskLevel: ""
      });

      const response = await postLoginFlowStart(
        buildLoginFlowStartRequest(
          pendingLoginStartPayload.email,
          pendingLoginStartPayload.deviceFingerprint
        )
      );
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        await handleLoginFlowFailure(payload, "Login request failed.", showEmailError);
        return true;
      }

      await resolveSuccessfulLoginStart(payload, {
        identifierText: pendingLoginStartPayload.email
      });
      return true;
    }

    async function requestLoginEmailCode(requestOptions = {}) {
      clearPasswordError();
      const flow = await ensureCurrentLoginFlow();
      if (!flow?.success || !flow?.flowId) {
        showPasswordError("Login session expired. Restart from the email step.");
        return { success: false };
      }

      const response = await getFetchWithPreAuth()(LOGIN_EMAIL_CODE_PATH, {
        method: "POST",
        headers: {
          Accept: "application/json"
        }
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        if (isInvalidStatePayload(payload)) {
          await showInvalidStateTerminal(payload);
          return payload;
        }
        showPasswordError(payload?.message || "Failed to send the email code.");
        return payload;
      }

      setCurrentLoginFlow(payload);
      if (!requestOptions.skipNavigation) {
        await navigateToLoginStep(payload, {
          identifierType: flow?.email ? "email" : otpApi?.getCurrentIdentifierType?.() || "email",
          identifierText: flow?.email || otpApi?.getCurrentIdentifierValue?.() || identifierValue?.textContent || "",
          skipAutoEmailCode: true
        });
      }
      if (requestOptions.showStatus !== false) {
        showOtpStatus(payload?.message || "Email code sent.");
      } else {
        clearOtpError();
      }
      return payload;
    }

    async function handlePhoneSubmit() {
      const rawPhone = phoneNumberInput ? phoneNumberInput.value.trim() : "";

      resetPhoneValidationState();

      const dialCode = resolveDialCodeValue("phone-country-code");
      if (!dialCode) {
        showPhoneValidationError("Please choose a country or region.", phoneNumberInput, phoneNumberLabel);
        return;
      }

      if (!/^\d{6,15}$/.test(rawPhone)) {
        showPhoneValidationError("Please enter a valid mobile phone number.", phoneNumberInput, phoneNumberLabel);
        return;
      }

      const phoneValidationResult = await validatePhoneNumberPolicy(dialCode, rawPhone);
      if (!phoneValidationResult.success) {
        showPhoneValidationError(phoneValidationResult.message, phoneNumberInput, phoneNumberLabel);
        return;
      }

      const response = await getFetchWithPreAuth()(LOGIN_PHONE_LOGIN_CODE_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          dialCode,
          phoneNumber: rawPhone
        })
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        if (payload?.challengeType) {
          const challengePayload = {
            ...payload,
            deviceFingerprint: buildDeviceFingerprint()
          };
          const handled = await handleLoginChallenge(payload, {
            errorTarget(message) {
              showPhoneValidationError(message, phoneNumberInput, phoneNumberLabel);
            },
            submitChallenge(captchaUuid = "", captchaCode = "") {
              return submitPhoneLoginSmsCode(dialCode, rawPhone, captchaUuid, captchaCode);
            },
            getPendingChallengePayload() {
              return challengePayload;
            },
            resolveChallengeSuccess(successPayload) {
              if (successPayload?.success) {
                showPhoneValidationError(successPayload.message || "SMS code sent.", phoneNumberInput, phoneNumberLabel);
                return true;
              }
              return false;
            }
          });
          if (handled) {
            return;
          }
        }
        showPhoneValidationError(payload?.message || "Failed to send SMS code.", phoneNumberInput, phoneNumberLabel);
        return;
      }
      showPhoneValidationError(payload?.message || "SMS code sent.", phoneNumberInput, phoneNumberLabel);
    }

    async function handlePasswordSubmit() {
      clearPasswordError();
      const rawPassword = passwordInput ? passwordInput.value : "";
      if (!rawPassword || rawPassword.length < 6) {
        showPasswordError("Please enter at least 6 characters.");
        return;
      }

      const flow = await ensureCurrentLoginFlow();
      if (!flow?.success || !flow?.flowId) {
        showPasswordError("Login session expired. Restart from the email step.");
        return;
      }

      const response = await getFetchWithPreAuth()(LOGIN_PASSWORD_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          password: rawPassword
        })
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        showPasswordError(payload?.message || "Password verification failed.");
        return;
      }

      if (payload?.authenticated && payload?.redirectPath) {
        window.location.assign(payload.redirectPath);
        return;
      }

      await navigateToLoginStep(payload, {
        identifierType: "email",
        identifierText: flow?.email || identifierValue?.textContent || ""
      });
    }

    async function handleOtpSubmit() {
      clearOtpError();
      const rawOtp = otpCodeInput ? otpCodeInput.value.trim() : "";
      if (!/^\d{4,8}$/.test(rawOtp)) {
        showOtpError("Please enter a valid code.");
        return;
      }

      const flow = await ensureCurrentLoginFlow();
      if (!flow?.success || !flow?.flowId) {
        showOtpError("Login session expired. Restart from the email step.");
        return;
      }

      const isTotp = otpApi?.getCurrentLoginFactor?.() === "TOTP"
        || flow?.step === "TOTP_VERIFICATION"
        || window.location.pathname === authPaths.TOTP_VERIFICATION;
      const requestPath = isTotp ? LOGIN_TOTP_VERIFY_PATH : LOGIN_EMAIL_CODE_VERIFY_PATH;
      const requestBody = isTotp ? { code: rawOtp } : { emailCode: rawOtp };
      const response = await getFetchWithPreAuth()(requestPath, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(requestBody)
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        showOtpError(payload?.message || "Verification failed.");
        return;
      }

      if (payload?.authenticated && payload?.redirectPath) {
        window.location.assign(payload.redirectPath);
        return;
      }

      await navigateToLoginStep(payload, {
        identifierType: otpApi?.getCurrentIdentifierType?.() || "email",
        identifierText: flow?.email || otpApi?.getCurrentIdentifierValue?.() || identifierValue?.textContent || ""
      });
    }

    async function handleEmailSubmit() {
      const email = emailInput ? emailInput.value.trim() : "";
      clearEmailError();

      if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showEmailError("Please enter a valid email address.");
        return;
      }

      setLastIdentifierView("email");
      const { response, payload } = await startLoginFlowFromEmail(email);
      if (!response.ok || !payload?.success) {
        await handleLoginFlowFailure(payload, "Login request failed.", showEmailError);
        return;
      }

      await resolveSuccessfulLoginStart(payload, { identifierText: email });
    }

    async function submitPhoneLoginSmsCode(dialCode, rawPhone, captchaUuid = "", captchaCode = "") {
      return getFetchWithPreAuth()(LOGIN_PHONE_LOGIN_CODE_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          dialCode,
          phoneNumber: rawPhone,
          captchaUuid,
          captchaCode
        })
      });
    }

    async function submitLoginPhoneCode(dialCode, rawPhone, captchaUuid = "", captchaCode = "") {
      const response = await getFetchWithPreAuth()(LOGIN_PHONE_CODE_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          dialCode,
          phoneNumber: rawPhone,
          captchaUuid,
          captchaCode
        })
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        return {
          success: false,
          message: payload?.message || "Failed to send SMS code.",
          error: payload?.error || "",
          challengeType: payload?.challengeType || "",
          challengeSubType: payload?.challengeSubType || "",
          challengeSiteKey: payload?.challengeSiteKey || "",
          email: payload?.email || "",
          riskLevel: payload?.riskLevel || "",
          deviceFingerprint: buildDeviceFingerprint()
        };
      }
      setCurrentLoginFlow(payload);
      return payload;
    }

    async function submitLoginPhoneBinding(dialCode, rawPhone, smsCode) {
      const response = await getFetchWithPreAuth()(LOGIN_PHONE_BIND_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          dialCode,
          phoneNumber: rawPhone,
          smsCode
        })
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        return {
          success: false,
          message: payload?.message || "Failed to bind phone number."
        };
      }
      if (payload?.authenticated && payload?.redirectPath) {
        window.location.assign(payload.redirectPath);
      }
      return payload;
    }

    async function submitRegisterPhoneCode(dialCode, rawPhone, captchaUuid = "", captchaCode = "") {
      const response = await getFetchWithPreAuth()(REGISTER_PHONE_CODE_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          dialCode,
          phoneNumber: rawPhone,
          captchaUuid,
          captchaCode
        })
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        return {
          success: false,
          message: payload?.message || "Failed to send SMS code.",
          error: payload?.error || "",
          challengeType: payload?.challengeType || "",
          challengeSubType: payload?.challengeSubType || "",
          challengeSiteKey: payload?.challengeSiteKey || "",
          email: payload?.email || "",
          riskLevel: payload?.riskLevel || "",
          deviceFingerprint: buildDeviceFingerprint()
        };
      }
      return payload;
    }

    async function submitRegisterPhoneBinding(dialCode, rawPhone, smsCode) {
      const response = await getFetchWithPreAuth()(REGISTER_PHONE_BIND_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          dialCode,
          phoneNumber: rawPhone,
          smsCode
        })
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || !payload?.success) {
        return {
          success: false,
          message: payload?.message || "Failed to bind phone number.",
          error: payload?.error || ""
        };
      }
      return payload;
    }

    async function restoreLoginFlowState(currentPath) {
      const currentMode = (() => {
        try {
          return new URL(currentPath || window.location.href, window.location.origin).searchParams.get("mode") || "";
        } catch (_) {
          return "";
        }
      })();
      if (currentMode && currentMode !== "login") {
        return false;
      }
      const normalizedPath = normalizeRoutePath(currentPath || window.location.pathname || "");
      const loginRelatedPaths = new Set([
        authPaths.LOGIN,
        authPaths.LOGIN_PASSWORD,
        authPaths.EMAIL_VERIFICATION,
        authPaths.TOTP_VERIFICATION,
        authPaths.ADD_PHONE,
        "/shopping/user/login"
      ]);
      if (!loginRelatedPaths.has(normalizedPath)) {
        return false;
      }

      if (await resumeLoginFlowAfterWaf()) {
        return true;
      }

      const payload = await fetchCurrentLoginFlowState();
      if (!payload?.success) {
        return false;
      }

      await navigateToLoginStep(payload, {
        identifierType: "email",
        identifierText: payload.email || identifierValue?.textContent || "",
        skipAutoEmailCode: true
      });
      return true;
    }

    async function handleLoginFormSubmit(event) {
      event.preventDefault();

      const authView = shellApi?.getAuthView?.() || "";

      if (await handleRegisterSubmit(authView)) {
        return;
      }

      if (authView === "session-ended") {
        return;
      }

      if (authView === "phone") {
        await handlePhoneSubmit();
        return;
      }

      if (authView === "password") {
        await handlePasswordSubmit();
        return;
      }

      if (authView === "otp") {
        await handleOtpSubmit();
        return;
      }

      await handleEmailSubmit();
    }

    function bindLoginFormSubmit() {
      if (!loginForm) {
        return;
      }
      loginForm.addEventListener("submit", handleLoginFormSubmit);
    }

    return {
      bindLoginFormSubmit,
      handleLoginFormSubmit,
      resetPhoneValidationState,
      showPhoneValidationError,
      resolveDialCodeValue,
      resolvePhoneValidationMessage,
      validatePhoneNumberPolicy,
      setHandleRegisterSubmit,
      setHandleLoginChallenge,
      showEmailError,
      clearEmailError,
      requestLoginEmailCode,
      submitPendingLoginChallenge,
      resolveSuccessfulLoginStart,
      getPendingLoginStartPayload,
      restoreLoginFlowState,
      submitLoginPhoneCode,
      submitLoginPhoneBinding,
      submitRegisterPhoneCode,
      submitRegisterPhoneBinding,
      getCurrentLoginFlow,
      setCurrentLoginFlow
    };
  }

  return {
    DEFAULT_PHONE_VALIDATE_PATH,
    createLoginSubmitHandlers
  };
});
