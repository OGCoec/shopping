(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterEntry = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function createRegisterEntry(options = {}) {
    const readRegisterDraftState = options.readRegisterDraftState || (() => null);
    const updateRegisterDraftState = options.updateRegisterDraftState || (() => null);
    const startRegisterFlow = options.startRegisterFlow || (async () => ({ success: false }));
    const navigateToRegisterStep = options.navigateToRegisterStep || (() => {});
    const showRegisterEntryError = options.showRegisterEntryError || (() => {});
    const clearRegisterEntryError = options.clearRegisterEntryError || (() => {});
    const registerStepPaths = options.registerStepPaths || {};
    const invalidEmailMessage = options.invalidEmailMessage || "Please enter a valid email address.";

    function syncRegisterEntryStepFromDraft() {
      const registerEntryEmailInput = document.getElementById("register-entry-email");
      const registerDraft = readRegisterDraftState();
      if (!registerEntryEmailInput || !registerDraft?.email) {
        return false;
      }
      registerEntryEmailInput.value = registerDraft.email;
      return true;
    }

    async function continueRegisterEntryStep() {
      const registerEntryEmailInput = document.getElementById("register-entry-email");
      if (!registerEntryEmailInput) {
        return false;
      }

      clearRegisterEntryError();
      const email = registerEntryEmailInput.value.trim();
      if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showRegisterEntryError(invalidEmailMessage);
        return false;
      }

      updateRegisterDraftState({ email });
      const flowStartResult = await startRegisterFlow(email);
      if (!flowStartResult?.success) {
        showRegisterEntryError(flowStartResult?.message || "Register flow could not start.");
        return false;
      }
      navigateToRegisterStep(
        "register-password",
        flowStartResult.nextPath || registerStepPaths.CREATE_ACCOUNT_PASSWORD
      );
      return true;
    }

    function restoreRegisterEntryStepFromSession() {
      clearRegisterEntryError();
      return syncRegisterEntryStepFromDraft();
    }

    function initializeRegisterEntryFragment() {
      syncRegisterEntryStepFromDraft();

      const registerEntryEmailInput = document.getElementById("register-entry-email");
      if (registerEntryEmailInput && registerEntryEmailInput.dataset.bound !== "true") {
        registerEntryEmailInput.dataset.bound = "true";
        registerEntryEmailInput.addEventListener("input", () => {
          updateRegisterDraftState({ email: registerEntryEmailInput.value.trim() });
          clearRegisterEntryError();
        });
      }

      const registerEntryContinueButton = document.getElementById("btn-register-email-continue");
      if (registerEntryContinueButton && registerEntryContinueButton.dataset.bound !== "true") {
        registerEntryContinueButton.dataset.bound = "true";
        registerEntryContinueButton.addEventListener("click", () => {
          continueRegisterEntryStep();
        });
      }
    }

    return {
      syncRegisterEntryStepFromDraft,
      continueRegisterEntryStep,
      restoreRegisterEntryStepFromSession,
      initializeRegisterEntryFragment
    };
  }

  return {
    createRegisterEntry
  };
});
