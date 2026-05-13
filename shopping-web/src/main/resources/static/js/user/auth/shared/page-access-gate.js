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

  const DEVICE_STEP_LABEL = "\u8bbe\u5907\u73af\u5883";
  const SESSION_STEP_LABEL = "\u767b\u5f55\u51c6\u5165";
  const DEFAULT_TITLE = "\u6b63\u5728\u6821\u9a8c\u8bbe\u5907\u73af\u5883...";
  const DEFAULT_SUBTITLE = "\u6b63\u5728\u786e\u8ba4\u5f53\u524d\u8bbe\u5907\u4e0e\u7f51\u7edc\u73af\u5883\u3002";
  const DEVICE_DONE_TITLE = "\u8bbe\u5907\u73af\u5883\u5df2\u786e\u8ba4";
  const DEVICE_DONE_SUBTITLE = "\u5f53\u524d\u8bbe\u5907\u73af\u5883\u6821\u9a8c\u5b8c\u6210\u3002";
  const SESSION_CHECK_TITLE = "\u6b63\u5728\u786e\u8ba4\u767b\u5f55\u51c6\u5165...";
  const SESSION_CHECK_SUBTITLE = "\u6b63\u5728\u68c0\u67e5\u8d26\u53f7\u72b6\u6001\u4e0e\u8bbf\u95ee\u51c6\u5165\u3002";
  const SESSION_DONE_TITLE = "\u51c6\u5165\u6821\u9a8c\u5b8c\u6210";
  const SESSION_DONE_SUBTITLE = "\u6b63\u5728\u4e3a\u4f60\u6253\u5f00\u9875\u9762\u3002";
  const FAILED_TITLE = "\u8bbf\u95ee\u88ab\u6682\u65f6\u963b\u6b62";
  const FAILED_MESSAGE = "\u5f53\u524d\u767b\u5f55\u73af\u5883\u9a8c\u8bc1\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
  const PAGE_FAILED_MESSAGE = "\u5f53\u524d\u64cd\u4f5c\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf7\u5173\u95ed\u6b64\u9875\u9762\u5e76\u8c03\u6574\u8bbe\u5907\u6216\u7f51\u7edc\u73af\u5883\u540e\u91cd\u65b0\u6253\u5f00\u3002";
  const MIN_DEVICE_VISIBLE_MS = 920;
  const MIN_SESSION_VISIBLE_MS = 980;
  const PARTICLE_COUNT = 0;

  const STATE_CONFIG = {
    "is-device-checking": {
      className: "checking-device",
      active: "device",
      done: [],
      failed: [],
      title: DEFAULT_TITLE,
      subtitle: DEFAULT_SUBTITLE
    },
    "is-device-done": {
      className: "device-passed",
      active: "",
      done: ["device"],
      failed: [],
      title: DEVICE_DONE_TITLE,
      subtitle: DEVICE_DONE_SUBTITLE
    },
    "is-session-checking": {
      className: "checking-session",
      active: "session",
      done: ["device"],
      failed: [],
      title: SESSION_CHECK_TITLE,
      subtitle: SESSION_CHECK_SUBTITLE
    },
    "is-session-done": {
      className: "passed",
      active: "",
      done: ["device", "session"],
      failed: [],
      title: SESSION_DONE_TITLE,
      subtitle: SESSION_DONE_SUBTITLE
    },
    "is-failed": {
      className: "blocked",
      active: "",
      done: [],
      failed: ["device", "session"],
      title: FAILED_TITLE,
      subtitle: PAGE_FAILED_MESSAGE
    }
  };

  let gatePromise = null;
  let copyTransitionTimer = null;

  function isBrowserRuntime() {
    return typeof document !== "undefined" && typeof window !== "undefined";
  }

  function ensurePendingClass() {
    if (!isBrowserRuntime()) {
      return;
    }
    document.documentElement.classList.add("page-gate-pending");
    document.documentElement.classList.remove("page-gate-passed", "page-gate-failed");
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

    const shell = document.createElement("section");
    shell.className = "page-access-gate-shell checking-device";
    shell.setAttribute("aria-labelledby", "page-access-gate-title");

    const chrome = document.createElement("div");
    chrome.className = "page-access-gate-chrome";

    const back = document.createElement("span");
    back.className = "page-access-gate-chrome-icon";
    back.setAttribute("aria-hidden", "true");
    back.textContent = "\u2039";

    const label = document.createElement("span");
    label.className = "page-access-gate-chrome-title";
    label.textContent = "\u5b89\u5168\u51c6\u5165";

    const close = document.createElement("span");
    close.className = "page-access-gate-chrome-icon";
    close.setAttribute("aria-hidden", "true");
    close.textContent = "\u00d7";

    chrome.append(back, label, close);

    const stepper = document.createElement("div");
    stepper.className = "page-access-gate-stepper";
    stepper.append(
      buildProgressStep("device", "1", DEVICE_STEP_LABEL),
      buildConnector(),
      buildProgressStep("session", "2", SESSION_STEP_LABEL)
    );

    const copy = document.createElement("div");
    copy.className = "page-access-gate-copy";

    const title = document.createElement("h1");
    title.id = "page-access-gate-title";
    title.className = "page-access-gate-title";
    title.textContent = DEFAULT_TITLE;

    const subtitle = document.createElement("p");
    subtitle.className = "page-access-gate-subtitle";
    subtitle.textContent = DEFAULT_SUBTITLE;

    const instruction = document.createElement("p");
    instruction.className = "page-access-gate-instruction";
    instruction.textContent = "\u5982\u679c\u9875\u9762\u505c\u7559\u5728\u963b\u6b62\u72b6\u6001\uff0c\u8bf7\u5173\u95ed\u9875\u9762\u5e76\u8c03\u6574\u5f53\u524d\u8bbf\u95ee\u73af\u5883\u3002";

    const bloom = document.createElement("div");
    bloom.className = "page-access-gate-result-bloom";
    bloom.setAttribute("aria-hidden", "true");

    const particles = buildParticles("page-access-gate-particles", PARTICLE_COUNT);

    copy.append(title, subtitle, instruction);
    shell.append(chrome, stepper, copy, particles, bloom);
    overlay.appendChild(shell);
    document.body.appendChild(overlay);
    return overlay;
  }

  function buildParticles(className, count) {
    const particles = document.createElement("div");
    particles.className = className;
    particles.setAttribute("aria-hidden", "true");
    for (let index = 0; index < count; index += 1) {
      const distance = 34 + (index % 5) * 7;
      const particle = document.createElement("span");
      particle.style.setProperty("--particle-index", String(index));
      particle.style.setProperty("--particle-angle", `${Math.round((360 / count) * index)}deg`);
      particle.style.setProperty("--particle-distance", `${distance}px`);
      particle.style.setProperty("--particle-distance-near", `${Math.round(distance * 0.6)}px`);
      particle.style.setProperty("--particle-distance-far", `${Math.round(distance * 1.55)}px`);
      particle.style.setProperty("--particle-distance-wide", `${Math.round(distance * 1.18)}px`);
      particle.style.setProperty("--particle-distance-tight", `${Math.round(distance * 0.48)}px`);
      particle.style.setProperty("--particle-distance-scatter", `${Math.round(distance * 1.42)}px`);
      const delay = index * 38;
      particle.style.setProperty("--particle-delay", `${delay}ms`);
      particle.style.setProperty("--particle-success-delay", `${Math.round(delay * 0.25)}ms`);
      particle.style.setProperty("--particle-fail-delay", `${Math.round(delay * 0.18)}ms`);
      particles.appendChild(particle);
    }
    return particles;
  }

  function buildProgressStep(name, number, label) {
    const step = document.createElement("div");
    step.className = "page-access-gate-progress-step";
    step.dataset.gateStep = name;

    const circle = document.createElement("span");
    circle.className = "page-access-gate-progress-circle";

    const spinner = document.createElement("span");
    spinner.className = "page-access-gate-progress-spinner";
    spinner.setAttribute("aria-hidden", "true");

    const spinnerDot = document.createElement("span");
    spinnerDot.className = "page-access-gate-progress-spinner-dot";
    spinnerDot.setAttribute("aria-hidden", "true");

    const numberNode = document.createElement("span");
    numberNode.className = "page-access-gate-progress-number";
    numberNode.textContent = number;

    const checkNode = document.createElement("span");
    checkNode.className = "page-access-gate-progress-check";
    checkNode.setAttribute("aria-hidden", "true");
    checkNode.textContent = "\u2713";

    const labelNode = document.createElement("span");
    labelNode.className = "page-access-gate-progress-label";
    labelNode.textContent = label;

    circle.append(spinner, spinnerDot, numberNode, checkNode);
    step.append(circle, labelNode);
    return step;
  }

  function buildConnector() {
    const connector = document.createElement("span");
    connector.className = "page-access-gate-connector";
    connector.setAttribute("aria-hidden", "true");
    return connector;
  }

  function setGateState(state, message, options = {}) {
    const overlay = ensureOverlay();
    const shell = overlay?.querySelector(".page-access-gate-shell");
    const config = STATE_CONFIG[state] || STATE_CONFIG["is-device-checking"];
    const failedStep = options.failedStep || "";
    const failedSteps = state === "is-failed" && failedStep
      ? [failedStep]
      : config.failed;

    if (shell) {
      shell.className = `page-access-gate-shell ${config.className}`;
    }

    updateStep(overlay, "device", config.active, config.done, failedSteps);
    updateStep(overlay, "session", config.active, config.done, failedSteps);
    updateConnector(overlay, config, failedSteps);
    updateCopy(config.title, message || config.subtitle);

    if (state === "is-session-done") {
      document.documentElement.classList.add("page-gate-passed");
    }
    if (state === "is-failed") {
      document.documentElement.classList.add("page-gate-failed");
      document.documentElement.classList.remove("page-gate-pending", "page-gate-passed");
    }
  }

  function updateStep(overlay, name, active, done, failed) {
    const step = overlay?.querySelector(`[data-gate-step="${name}"]`);
    if (!step) {
      return;
    }
    const isActive = active === name;
    const isDone = Array.isArray(done) && done.includes(name);
    const isFailed = Array.isArray(failed) && failed.includes(name);
    step.classList.toggle("is-active", isActive);
    step.classList.toggle("is-done", isDone);
    step.classList.toggle("is-failed", isFailed);
    step.classList.toggle("is-muted", !isActive && !isDone && !isFailed);
  }

  function updateConnector(overlay, config, failedSteps) {
    const connector = overlay?.querySelector(".page-access-gate-connector");
    if (!connector) {
      return;
    }
    const deviceDone = Array.isArray(config.done) && config.done.includes("device");
    connector.classList.toggle("is-active", config.active === "session");
    connector.classList.toggle("is-done", deviceDone);
    connector.classList.toggle("is-failed", Array.isArray(failedSteps) && failedSteps.length > 0);
  }

  function updateCopy(title, subtitle) {
    const overlay = ensureOverlay();
    const copy = overlay?.querySelector(".page-access-gate-copy");
    const titleNode = overlay?.querySelector(".page-access-gate-title");
    const subtitleNode = overlay?.querySelector(".page-access-gate-subtitle");
    if (!copy || !titleNode || !subtitleNode) {
      return;
    }
    if (titleNode.textContent === title && subtitleNode.textContent === subtitle) {
      return;
    }
    if (copyTransitionTimer) {
      window.clearTimeout(copyTransitionTimer);
    }
    copy.classList.remove("is-entering");
    copy.classList.add("is-leaving");
    copyTransitionTimer = window.setTimeout(() => {
      titleNode.textContent = title || DEFAULT_TITLE;
      subtitleNode.textContent = subtitle || DEFAULT_SUBTITLE;
      copy.classList.remove("is-leaving");
      copy.classList.add("is-entering");
      copyTransitionTimer = window.setTimeout(() => {
        copy.classList.remove("is-entering");
        copyTransitionTimer = null;
      }, 260);
    }, 130);
  }

  function revealPage() {
    if (!isBrowserRuntime()) {
      return;
    }
    document.documentElement.classList.remove("page-gate-pending", "page-gate-failed", "page-gate-passed");
    document.querySelector(".page-access-gate-overlay")?.remove();
  }

  function failPage(message, failedStep = "session") {
    if (!isBrowserRuntime()) {
      return;
    }
    setGateState("is-failed", message || PAGE_FAILED_MESSAGE, { failedStep });
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

  async function runDeviceGate(startedAt) {
    const preAuthClient = root.ShoppingPreAuthClient;
    if (!preAuthClient?.bootstrapPreAuthToken) {
      await waitRemaining(startedAt, MIN_DEVICE_VISIBLE_MS);
      return true;
    }
    const payload = await preAuthClient.bootstrapPreAuthToken(false);
    if (payload?.error === WAF_REQUIRED_ERROR) {
      await waitRemaining(startedAt, MIN_DEVICE_VISIBLE_MS);
      return false;
    }
    if (payload?.blocked || String(payload?.riskLevel || "").toUpperCase() === "L6") {
      await waitRemaining(startedAt, MIN_DEVICE_VISIBLE_MS);
      failPage(PAGE_FAILED_MESSAGE, "device");
      return false;
    }
    await waitRemaining(startedAt, MIN_DEVICE_VISIBLE_MS);
    return true;
  }

  async function runGate() {
    ensurePendingClass();
    setGateState("is-device-checking", DEFAULT_SUBTITLE);

    const authClient = root.ShoppingAuthClient;
    if (!authClient?.fetchWithAuth) {
      redirectTo(LOGIN_PATH);
      return false;
    }

    try {
      const deviceStartedAt = Date.now();
      const deviceAllowed = await runDeviceGate(deviceStartedAt);
      if (!deviceAllowed) {
        return false;
      }
      setGateState("is-device-done", DEVICE_DONE_SUBTITLE);
      await wait(260);
      setGateState("is-session-checking", SESSION_CHECK_SUBTITLE);

      const sessionStartedAt = Date.now();
      const response = await authClient.fetchWithAuth(PAGE_GATE_PATH, {
        method: "GET",
        headers: { "Accept": "application/json" }
      });
      if (response.ok) {
        await waitRemaining(sessionStartedAt, MIN_SESSION_VISIBLE_MS);
        setGateState("is-session-done", SESSION_DONE_SUBTITLE);
        await wait(620);
        revealPage();
        return true;
      }
      const payload = await parseJson(response);
      await waitRemaining(sessionStartedAt, MIN_SESSION_VISIBLE_MS);
      if (handleBlockedResponse(response, payload)) {
        return false;
      }
      failPage(payload?.message || FAILED_MESSAGE, "session");
      return false;
    } catch (_) {
      await wait(MIN_SESSION_VISIBLE_MS);
      failPage(FAILED_MESSAGE, "session");
      return false;
    }
  }

  function waitRemaining(startedAt, minimumMs) {
    const elapsed = Date.now() - startedAt;
    return wait(Math.max(0, minimumMs - elapsed));
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
