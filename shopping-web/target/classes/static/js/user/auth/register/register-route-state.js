(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterRouteState = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const REGISTER_DRAFT_CONTEXT_KEY = "shopping:register:draft-account:v1";

  function canUseSessionStorage() {
    return typeof window !== "undefined" && typeof sessionStorage !== "undefined";
  }

  function normalizeRegisterDraftState(draft) {
    if (!draft || typeof draft !== "object") {
      return null;
    }
    const email = typeof draft.email === "string" ? draft.email.trim() : "";
    const username = typeof draft.username === "string" ? draft.username.trim() : "";
    if (!email && !username) {
      return null;
    }
    return { email, username };
  }

  function readRegisterDraftState() {
    if (!canUseSessionStorage()) {
      return null;
    }
    try {
      const raw = sessionStorage.getItem(REGISTER_DRAFT_CONTEXT_KEY);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw);
      return normalizeRegisterDraftState(parsed?.draft);
    } catch (_) {
      return null;
    }
  }

  function writeRegisterDraftState(draft) {
    const normalizedDraft = normalizeRegisterDraftState(draft);
    if (!canUseSessionStorage()) {
      return normalizedDraft;
    }
    try {
      if (!normalizedDraft) {
        sessionStorage.removeItem(REGISTER_DRAFT_CONTEXT_KEY);
        return null;
      }
      sessionStorage.setItem(REGISTER_DRAFT_CONTEXT_KEY, JSON.stringify({
        draft: normalizedDraft,
        savedAt: Date.now()
      }));
    } catch (_) {
    }
    return normalizedDraft;
  }

  function updateRegisterDraftState(patch = {}) {
    const currentDraft = readRegisterDraftState() || {};
    return writeRegisterDraftState({
      ...currentDraft,
      ...patch
    });
  }

  function clearRegisterDraftState() {
    if (!canUseSessionStorage()) {
      return;
    }
    try {
      sessionStorage.removeItem(REGISTER_DRAFT_CONTEXT_KEY);
    } catch (_) {
    }
  }

  return {
    REGISTER_DRAFT_CONTEXT_KEY,
    canUseSessionStorage,
    normalizeRegisterDraftState,
    readRegisterDraftState,
    writeRegisterDraftState,
    updateRegisterDraftState,
    clearRegisterDraftState
  };
});
