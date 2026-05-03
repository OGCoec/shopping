(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterPasswordStep = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const REGISTER_PASSWORD_HIDDEN_ICON = `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M2 12s3.6-7 10-7c2.1 0 4 .55 5.62 1.47"></path>
      <path d="M22 12s-3.6 7-10 7c-2.1 0-4-.55-5.62-1.47"></path>
      <path d="M3 3l18 18"></path>
      <path d="M9.88 9.88A3 3 0 0 0 12 15a3 3 0 0 0 2.12-.88"></path>
    </svg>
  `;

  const REGISTER_PASSWORD_VISIBLE_ICON = `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"></path>
      <circle cx="12" cy="12" r="3"></circle>
    </svg>
  `;

  function createRegisterPasswordStep(options = {}) {
    const getRegisterFormApi = typeof options.getRegisterFormApi === "function"
      ? options.getRegisterFormApi
      : () => options.registerFormApi || null;
    const readRegisterDraftState = typeof options.readRegisterDraftState === "function"
      ? options.readRegisterDraftState
      : () => null;
    const updateRegisterDraftState = typeof options.updateRegisterDraftState === "function"
      ? options.updateRegisterDraftState
      : () => null;
    const fetchCurrentRegisterFlowState = typeof options.fetchCurrentRegisterFlowState === "function"
      ? options.fetchCurrentRegisterFlowState
      : async () => null;
    const navigateToRegisterStep = typeof options.navigateToRegisterStep === "function"
      ? options.navigateToRegisterStep
      : () => {};
    const buildRegisterDeviceFingerprint = typeof options.buildRegisterDeviceFingerprint === "function"
      ? options.buildRegisterDeviceFingerprint
      : () => "";
    const showRegisterError = typeof options.showRegisterError === "function"
      ? options.showRegisterError
      : () => {};
    const clearRegisterError = typeof options.clearRegisterError === "function"
      ? options.clearRegisterError
      : () => {};
    const bindRegisterCaptchaControls = typeof options.bindRegisterCaptchaControls === "function"
      ? options.bindRegisterCaptchaControls
      : () => {};
    const registerStepPaths = options.registerStepPaths || {};

    let registerPasswordStepSubmitHandler = null;

    function syncRegisterPasswordStepFromDraft() {
      const registerDraft = readRegisterDraftState();
      const registerEmailInput = document.getElementById("register-email");
      const registerEmailDisplay = document.getElementById("register-email-display");
      const registerUsernameInput = document.getElementById("register-username");
      if (!registerDraft?.email || !registerEmailInput || !registerEmailDisplay) {
        return false;
      }
      registerEmailInput.value = registerDraft.email;
      registerEmailDisplay.textContent = registerDraft.email;
      if (registerUsernameInput && !registerUsernameInput.value && registerDraft.username) {
        registerUsernameInput.value = registerDraft.username;
      }
      return true;
    }

    function syncRegisterPasswordStepFromFlowState(flowState = {}) {
      const email = typeof flowState?.email === "string" ? flowState.email.trim() : "";
      const registerEmailInput = document.getElementById("register-email");
      const registerEmailDisplay = document.getElementById("register-email-display");
      if (!email || !registerEmailInput || !registerEmailDisplay) {
        return false;
      }
      updateRegisterDraftState({ email });
      getRegisterFormApi()?.hydratePendingRegisterPayload?.({
        email,
        deviceFingerprint: buildRegisterDeviceFingerprint(),
        riskLevel: flowState?.riskLevel || "",
        requirePhoneBinding: Boolean(flowState?.requirePhoneBinding)
      });
      registerEmailInput.value = email;
      registerEmailDisplay.textContent = email;
      return true;
    }

    async function restoreRegisterPasswordStepFromSession() {
      clearRegisterError();
      if (syncRegisterPasswordStepFromDraft()) {
        return true;
      }
      const flowState = await fetchCurrentRegisterFlowState();
      if (syncRegisterPasswordStepFromFlowState(flowState)) {
        return true;
      }
      if (typeof window !== "undefined") {
        navigateToRegisterStep("register", registerStepPaths.CREATE_ACCOUNT, { replace: true });
      }
      return false;
    }

    function updateRegisterPasswordToggle(button, input, showLabel, hideLabel) {
      const visible = input?.type === "text";
      button.innerHTML = visible ? REGISTER_PASSWORD_VISIBLE_ICON : REGISTER_PASSWORD_HIDDEN_ICON;
      button.classList.toggle("is-visible", visible);
      button.setAttribute("aria-label", visible ? hideLabel : showLabel);
      button.setAttribute("title", visible ? hideLabel : showLabel);
    }

    function bindRegisterPasswordToggle(buttonId, inputId, showLabel, hideLabel) {
      const button = document.getElementById(buttonId);
      const input = document.getElementById(inputId);
      if (!button || !input) {
        return;
      }

      updateRegisterPasswordToggle(button, input, showLabel, hideLabel);

      if (button.dataset.bound === "true") {
        return;
      }

      button.dataset.bound = "true";
      button.addEventListener("click", () => {
        input.type = input.type === "password" ? "text" : "password";
        updateRegisterPasswordToggle(button, input, showLabel, hideLabel);
        input.focus({ preventScroll: true });
        try {
          const caretPosition = typeof input.value === "string" ? input.value.length : 0;
          input.setSelectionRange(caretPosition, caretPosition);
        } catch (_) {
          // Ignore caret restore failures on browsers that do not support it.
        }
        globalThis.ShoppingLoginVisuals?.applyFocusModeFromActiveElement?.();
      });
    }

    async function submitRegisterPasswordStep() {
      if (typeof registerPasswordStepSubmitHandler === "function") {
        return registerPasswordStepSubmitHandler();
      }
      return { submitCooldownMs: 0 };
    }

    function initializeRegisterPasswordFragment() {
      restoreRegisterPasswordStepFromSession();

      const registerEmailInput = document.getElementById("register-email");
      const registerUsernameInput = document.getElementById("register-username");
      const editRegisterEmailButton = document.getElementById("edit-register-email-btn");

      if (editRegisterEmailButton && editRegisterEmailButton.dataset.bound !== "true") {
        editRegisterEmailButton.dataset.bound = "true";
        editRegisterEmailButton.addEventListener("click", () => {
          updateRegisterDraftState({
            email: registerEmailInput?.value || "",
            username: registerUsernameInput?.value || ""
          });
          navigateToRegisterStep("register", registerStepPaths.CREATE_ACCOUNT);
        });
      }

      if (registerUsernameInput && registerUsernameInput.dataset.bound !== "true") {
        registerUsernameInput.dataset.bound = "true";
        registerUsernameInput.addEventListener("input", () => {
          updateRegisterDraftState({
            email: registerEmailInput?.value || "",
            username: registerUsernameInput.value.trim()
          });
          clearRegisterError();
        });
      }

      bindRegisterPasswordToggle(
        "register-password-toggle",
        "register-password",
        "Show password",
        "Hide password"
      );
      bindRegisterPasswordToggle(
        "register-confirm-toggle",
        "register-confirm",
        "Show confirm password",
        "Hide confirm password"
      );

      const registerFormApi = getRegisterFormApi();
      const registerPasswordInput = document.getElementById("register-password");
      if (registerPasswordInput && registerPasswordInput.dataset.strengthBound !== "true") {
        registerPasswordInput.dataset.strengthBound = "true";
        registerPasswordInput.addEventListener("input", (event) => {
          getRegisterFormApi()?.updateRegisterPasswordStrengthDisplay?.(event.target.value || "");
        });
      }
      if (registerPasswordInput) {
        registerFormApi?.updateRegisterPasswordStrengthDisplay?.(registerPasswordInput.value || "");
      }

      const registerSubmitButton = document.getElementById("btn-register-submit");
      if (registerSubmitButton && registerSubmitButton.dataset.submitBound !== "true") {
        registerSubmitButton.dataset.submitBound = "true";
        let submitCooldownTimer = null;
        let submitCooldownUntil = 0;
        const buttonTextNode = registerSubmitButton.querySelector(".btn-text");
        const buttonHoverTextNode = registerSubmitButton.querySelector(".btn-hover-content span");
        const buttonDisabledTextNode = registerSubmitButton.querySelector(".btn-disabled-content span");
        const defaultButtonText = (buttonTextNode?.textContent || "Create account").trim() || "Create account";

        const applyButtonText = (text) => {
          if (buttonTextNode) {
            buttonTextNode.textContent = text;
          }
          if (buttonHoverTextNode) {
            buttonHoverTextNode.textContent = text;
          }
          if (buttonDisabledTextNode) {
            buttonDisabledTextNode.textContent = text;
          }
        };

        const clearSubmitCooldownTimer = () => {
          if (!submitCooldownTimer) {
            return;
          }
          clearInterval(submitCooldownTimer);
          submitCooldownTimer = null;
        };

        const isSubmitCoolingDown = () => Date.now() < submitCooldownUntil;

        const setRegisterSubmitLocked = (locked) => {
          const finalLocked = locked || isSubmitCoolingDown();
          registerSubmitButton.disabled = finalLocked;
          registerSubmitButton.classList.toggle("is-submitting", finalLocked);
          registerSubmitButton.setAttribute("aria-disabled", finalLocked ? "true" : "false");
        };

        const startSubmitCooldown = (cooldownMs) => {
          const safeCooldownMs = Math.max(0, Math.round(Number(cooldownMs) || 0));
          if (safeCooldownMs <= 0) {
            return;
          }

          submitCooldownUntil = Date.now() + safeCooldownMs;
          clearSubmitCooldownTimer();

          const updateCooldownView = () => {
            const remainingMs = Math.max(0, submitCooldownUntil - Date.now());
            if (remainingMs <= 0) {
              clearSubmitCooldownTimer();
              submitCooldownUntil = 0;
              applyButtonText(defaultButtonText);
              if (registerSubmitButton.dataset.submitting !== "true") {
                setRegisterSubmitLocked(false);
              }
              return;
            }
            const remainingSeconds = Math.max(1, Math.ceil(remainingMs / 1000));
            applyButtonText(`\u8bf7\u7a0d\u540e\u91cd\u8bd5 ${remainingSeconds}s`);
            setRegisterSubmitLocked(true);
          };

          updateCooldownView();
          submitCooldownTimer = setInterval(updateCooldownView, 200);
        };

        registerPasswordStepSubmitHandler = async () => {
          if (registerSubmitButton.dataset.submitting === "true") {
            return;
          }
          if (isSubmitCoolingDown()) {
            setRegisterSubmitLocked(true);
            return;
          }
          registerSubmitButton.dataset.submitting = "true";
          setRegisterSubmitLocked(true);
          try {
            updateRegisterDraftState({
              email: registerEmailInput?.value || "",
              username: registerUsernameInput?.value || ""
            });
            const submitResult = await getRegisterFormApi()?.submitRegisterEmailCode?.();
            const submitCooldownMs = Math.max(0, Math.round(Number(submitResult?.submitCooldownMs) || 0));
            if (submitCooldownMs > 0) {
              startSubmitCooldown(submitCooldownMs);
            } else if (!isSubmitCoolingDown()) {
              applyButtonText(defaultButtonText);
            }
          } catch (_) {
            showRegisterError("\u6ce8\u518c\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
          } finally {
            registerSubmitButton.dataset.submitting = "false";
            if (!isSubmitCoolingDown()) {
              applyButtonText(defaultButtonText);
              setRegisterSubmitLocked(false);
            } else {
              setRegisterSubmitLocked(true);
            }
          }
        };

        registerSubmitButton.addEventListener("click", () => {
          submitRegisterPasswordStep();
        });
      }

      bindRegisterCaptchaControls();
    }

    return {
      syncRegisterPasswordStepFromDraft,
      syncRegisterPasswordStepFromFlowState,
      restoreRegisterPasswordStepFromSession,
      updateRegisterPasswordToggle,
      bindRegisterPasswordToggle,
      submitRegisterPasswordStep,
      initializeRegisterPasswordFragment
    };
  }

  return {
    REGISTER_PASSWORD_HIDDEN_ICON,
    REGISTER_PASSWORD_VISIBLE_ICON,
    createRegisterPasswordStep
  };
});
