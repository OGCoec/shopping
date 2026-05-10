(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory(root);
    return;
  }
  root.ShoppingPageAccessGate = factory(root);
})(typeof globalThis !== "undefined" ? globalThis : this, function (root) {
  const PAGE_GATE_PATH = "/shopping/user/session/page-gate";
  const LOGIN_PATH = "/shopping/user/log-in";
  const PHONE_BINDING_PATH = "/shopping/user/security/phone";
  const WAF_REQUIRED_ERROR = "PREAUTH_IP_CHANGED_WAF_REQUIRED";
  const PHONE_BINDING_REQUIRED_ERROR = "PHONE_BINDING_REQUIRED";
  const DEFAULT_MESSAGE = "\u6b63\u5728\u6821\u9a8c\u8bbe\u5907\u73af\u5883...";
  const DEVICE_CHECK_MESSAGE = "\u6b63\u5728\u6821\u9a8c\u8bbe\u5907\u73af\u5883...";
  const DEVICE_DONE_MESSAGE = "\u8bbe\u5907\u73af\u5883\u6821\u9a8c\u5b8c\u6210";
  const SESSION_CHECK_MESSAGE = "\u6b63\u5728\u786e\u8ba4\u767b\u5f55\u51c6\u5165...";
  const SESSION_DONE_MESSAGE = "\u767b\u5f55\u51c6\u5165\u6821\u9a8c\u5b8c\u6210";
  const FAILED_MESSAGE = "\u5f53\u524d\u767b\u5f55\u73af\u5883\u9a8c\u8bc1\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
  const PAGE_FAILED_MESSAGE = "\u5f53\u524d\u9875\u9762\u6682\u65f6\u65e0\u6cd5\u8bbf\u95ee\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
  const DEVICE_STEP_LABEL = "\u8bbe\u5907\u73af\u5883";
  const SESSION_STEP_LABEL = "\u767b\u5f55\u51c6\u5165";

  let gatePromise = null;

  function isBrowserRuntime() {
    return typeof document !== "undefined" && typeof window !== "undefined";
  }

  function ensurePendingClass() {
    if (!isBrowserRuntime()) {
      return;
    }
    document.documentElement.classList.add("page-gate-pending");
  }

  function ensureOverlay() {
    if (!isBrowserRuntime()) {
      return null;
    }
    let overlay = document.querySelector(".page-access-gate-overlay");
    if (overlay) {
      return overlay;
    }
    overlay = document.createElement("div");
    overlay.className = "page-access-gate-overlay";
    overlay.setAttribute("role", "status");
    overlay.setAttribute("aria-live", "polite");

    const shell = document.createElement("div");
    shell.className = "page-access-gate-shell is-device-checking";

    const visual = document.createElement("div");
    visual.className = "page-access-gate-visual";
    visual.setAttribute("aria-hidden", "true");

    const aura = document.createElement("div");
    aura.className = "page-access-gate-aura";

    const deviceRing = document.createElement("div");
    deviceRing.className = "page-access-gate-ring page-access-gate-ring-device";

    const sessionRing = document.createElement("div");
    sessionRing.className = "page-access-gate-ring page-access-gate-ring-session";

    const core = document.createElement("div");
    core.className = "page-access-gate-core";

    const coreDot = document.createElement("span");
    coreDot.className = "page-access-gate-core-dot";

    core.appendChild(coreDot);
    visual.append(aura, deviceRing, sessionRing, core);

    const steps = document.createElement("div");
    steps.className = "page-access-gate-steps";
    steps.append(
      buildStep("device", DEVICE_STEP_LABEL, true),
      buildStep("session", SESSION_STEP_LABEL, false)
    );

    const message = document.createElement("p");
    message.className = "page-access-gate-message";
    message.textContent = DEFAULT_MESSAGE;

    shell.append(visual, steps, message);
    overlay.appendChild(shell);
    document.body.appendChild(overlay);
    return overlay;
  }

  function buildStep(name, label, active) {
    const step = document.createElement("div");
    step.className = active ? "page-access-gate-step is-active" : "page-access-gate-step";
    step.dataset.gateStep = name;

    const mark = document.createElement("span");
    mark.className = "page-access-gate-step-mark";

    const text = document.createElement("span");
    text.textContent = label;

    step.append(mark, text);
    return step;
  }

  function setGateState(state, message) {
    const overlay = ensureOverlay();
    const shell = overlay?.querySelector(".page-access-gate-shell");
    if (shell) {
      shell.className = `page-access-gate-shell ${state}`;
    }

    const deviceStep = overlay?.querySelector('[data-gate-step="device"]');
    const sessionStep = overlay?.querySelector('[data-gate-step="session"]');
    const deviceDone = state === "is-device-done"
      || state === "is-session-checking"
      || state === "is-session-done";

    deviceStep?.classList.toggle("is-active", state === "is-device-checking");
    deviceStep?.classList.toggle("is-done", deviceDone);
    deviceStep?.classList.toggle("is-failed", state === "is-failed");
    sessionStep?.classList.toggle("is-active", state === "is-session-checking");
    sessionStep?.classList.toggle("is-done", state === "is-session-done");
    sessionStep?.classList.toggle("is-failed", state === "is-failed");
    setOverlayMessage(message);
  }

  function setOverlayMessage(message) {
    const overlay = ensureOverlay();
    const messageNode = overlay?.querySelector(".page-access-gate-message");
    if (messageNode) {
      messageNode.textContent = message || DEFAULT_MESSAGE;
    }
  }

  function revealPage() {
    if (!isBrowserRuntime()) {
      return;
    }
    document.documentElement.classList.remove("page-gate-pending", "page-gate-failed");
    document.querySelector(".page-access-gate-overlay")?.remove();
  }

  function failPage(message) {
    if (!isBrowserRuntime()) {
      return;
    }
    document.documentElement.classList.remove("page-gate-pending");
    document.documentElement.classList.add("page-gate-failed");
    setGateState("is-failed", message || PAGE_FAILED_MESSAGE);
  }

  function redirectTo(path) {
    if (!isBrowserRuntime() || !path) {
      return;
    }
    if (window.location.pathname === path) {
      return;
    }
    window.location.assign(path);
  }

  async function parseJson(response) {
    try {
      return await response.json();
    } catch (_) {
      return null;
    }
  }

  function handleBlockedResponse(response, payload) {
    const error = payload?.error ? String(payload.error) : "";
    if (response.status === 409 || error === WAF_REQUIRED_ERROR) {
      return true;
    }
    if (response.status === 428 || error === PHONE_BINDING_REQUIRED_ERROR) {
      redirectTo(payload?.redirectPath || PHONE_BINDING_PATH);
      return true;
    }
    if (response.status === 401) {
      redirectTo(LOGIN_PATH);
      return true;
    }
    if (payload?.redirectPath) {
      redirectTo(payload.redirectPath);
      return true;
    }
    return false;
  }

  async function runDeviceGate() {
    const preAuthClient = root.ShoppingPreAuthClient;
    if (!preAuthClient?.bootstrapPreAuthToken) {
      return true;
    }
    const payload = await preAuthClient.bootstrapPreAuthToken(false);
    return payload?.error !== WAF_REQUIRED_ERROR;
  }

  async function runGate() {
    ensurePendingClass();
    setGateState("is-device-checking", DEVICE_CHECK_MESSAGE);

    const authClient = root.ShoppingAuthClient;
    if (!authClient?.fetchWithAuth) {
      redirectTo(LOGIN_PATH);
      return false;
    }

    try {
      const deviceAllowed = await runDeviceGate();
      if (!deviceAllowed) {
        return false;
      }
      setGateState("is-device-done", DEVICE_DONE_MESSAGE);
      await wait(180);
      setGateState("is-session-checking", SESSION_CHECK_MESSAGE);

      const response = await authClient.fetchWithAuth(PAGE_GATE_PATH, {
        method: "GET",
        headers: { "Accept": "application/json" }
      });
      if (response.ok) {
        setGateState("is-session-done", SESSION_DONE_MESSAGE);
        await wait(220);
        revealPage();
        return true;
      }
      const payload = await parseJson(response);
      if (handleBlockedResponse(response, payload)) {
        return false;
      }
      failPage(payload?.message || FAILED_MESSAGE);
      return false;
    } catch (_) {
      failPage(FAILED_MESSAGE);
      return false;
    }
  }

  function wait(durationMs) {
    return new Promise((resolve) => {
      setTimeout(resolve, durationMs);
    });
  }

  function ready() {
    if (!gatePromise) {
      gatePromise = runGate();
    }
    return gatePromise;
  }

  ready();

  return {
    ready,
    revealPage
  };
});
