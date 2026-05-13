(function () {
  const DEFAULT_MESSAGE = "请求被网络环境校验拦截。请关闭 VPN、代理、加速器，或调整分流规则后关闭此页面并重新打开站点。";
  const ADMIN_MESSAGE = "管理员访问被网络环境校验拦截。请调整 VPN、代理或分流规则后关闭此页面并重新打开后台。";
  const DEVICE_REPLAY_MS = 560;
  const NETWORK_REPLAY_MS = 980;
  const FINAL_SETTLE_MS = 140;
  const PARTICLE_COUNT = 0;
  const DEVICE_REPLAY_TITLE = "正在校验设备环境...";
  const DEVICE_REPLAY_COPY = "正在确认当前设备与访问环境。";
  const NETWORK_REPLAY_TITLE = "正在确认网络一致性...";
  const NETWORK_REPLAY_COPY = "正在比对浏览器网络信号与站点访问链路。";
  const ERROR_MESSAGES = {
    WEBRTC_IP_MISMATCH: "网络环境异常，请关闭 VPN/代理后重试",
    WEBRTC_SIGNAL_REQUIRED: "网络环境校验失败，未能获取浏览器 WebRTC 公网 IP。请关闭 VPN、代理或加速器后重试；如果仍失败，请检查浏览器是否禁用了 WebRTC，并开启 WebRTC 后重新打开页面。",
    NETWORK_CHECK_BLOCKED: DEFAULT_MESSAGE,
    PREAUTH_IP_CHANGED_WAF_REQUIRED: "检测到访问 IP 变化，请完成安全验证后重试",
    DEVICE_RISK_BLOCKED: "当前设备环境异常，请调整设备或网络环境后重试",
    ACCOUNT_RISK_BLOCKED: "当前账号状态异常，请调整访问环境后重试"
  };
  const WEBRTC_SIGNAL_REQUIRED_MESSAGES = {
    timeout: "网络环境校验超时，未能及时获取浏览器 WebRTC 公网 IP。请关闭 VPN、代理或加速器，或确认当前代理节点支持 UDP/WebRTC 后重试。",
    unsupported: "当前浏览器不支持或禁用了 WebRTC。请更换浏览器，或在浏览器/插件设置中开启 WebRTC 后重新打开页面。",
    private_only: "浏览器只返回了本地 WebRTC 地址，无法完成公网一致性校验。请检查 WebRTC 隐私设置或禁用相关拦截插件后重试。"
  };

  function readParams() {
    try {
      return new URLSearchParams(window.location.search || "");
    } catch (_) {
      return new URLSearchParams();
    }
  }

  function clean(value) {
    return String(value || "").trim();
  }

  function display(value) {
    return clean(value) || "-";
  }

  function firstParam(params, names) {
    for (const name of names) {
      const value = clean(params.get(name));
      if (value) {
        return value;
      }
    }
    return "";
  }

  function looksGarbled(value) {
    const text = clean(value);
    return /�/.test(text) || /锟斤拷/.test(text) || /璇锋眰|缃戠粶|鐜|鎷︽埅/.test(text);
  }

  function resolveWebRtcSignalRequiredMessage(webRtcStatus) {
    const normalizedStatus = clean(webRtcStatus).toLowerCase();
    return WEBRTC_SIGNAL_REQUIRED_MESSAGES[normalizedStatus] || ERROR_MESSAGES.WEBRTC_SIGNAL_REQUIRED;
  }

  function resolveMessage(scope, error, rawMessage, webRtcStatus) {
    const normalizedError = clean(error).toUpperCase();
    if (normalizedError === "WEBRTC_SIGNAL_REQUIRED") {
      return resolveWebRtcSignalRequiredMessage(webRtcStatus);
    }
    if (ERROR_MESSAGES[normalizedError]) {
      return ERROR_MESSAGES[normalizedError];
    }
    const message = clean(rawMessage);
    if (message && !looksGarbled(message)) {
      return message;
    }
    return scope === "admin" ? ADMIN_MESSAGE : DEFAULT_MESSAGE;
  }

  function appendDetail(list, label, value) {
    if (!list) {
      return;
    }
    const term = document.createElement("dt");
    term.textContent = label;
    const description = document.createElement("dd");
    description.textContent = display(value);
    list.append(term, description);
  }

  function setText(id, value) {
    const node = document.getElementById(id);
    if (node) {
      node.textContent = value;
    }
  }

  function setCopyText(title, message) {
    setText("network-check-title", title);
    setText("network-check-copy", message);
  }

  function resetStep(step) {
    step?.classList.remove("is-active", "is-done", "is-failed", "is-muted");
  }

  function applyStepState(step, state) {
    resetStep(step);
    step?.classList.add(`is-${state}`);
  }

  function applyConnectorState(connector, state) {
    connector?.classList.remove("is-active", "is-done", "is-failed", "is-muted");
    connector?.classList.add(`is-${state}`);
  }

  function createParticles() {
    const particles = document.querySelector(".network-check-particles");
    if (!particles || particles.childElementCount > 0) {
      return;
    }
    for (let index = 0; index < PARTICLE_COUNT; index += 1) {
      const distance = 34 + (index % 5) * 7;
      const particle = document.createElement("span");
      particle.style.setProperty("--particle-index", String(index));
      particle.style.setProperty("--particle-angle", `${Math.round((360 / PARTICLE_COUNT) * index)}deg`);
      particle.style.setProperty("--particle-distance", `${distance}px`);
      particle.style.setProperty("--particle-distance-wide", `${Math.round(distance * 1.18)}px`);
      particle.style.setProperty("--particle-distance-tight", `${Math.round(distance * 0.48)}px`);
      particle.style.setProperty("--particle-distance-scatter", `${Math.round(distance * 1.42)}px`);
      const delay = index * 38;
      particle.style.setProperty("--particle-delay", `${delay}ms`);
      particle.style.setProperty("--particle-fail-delay", `${Math.round(delay * 0.18)}ms`);
      particles.appendChild(particle);
    }
  }

  function isNetworkFailure(error) {
    const normalizedError = clean(error).toUpperCase();
    return normalizedError.includes("WEBRTC")
      || normalizedError.includes("IP_MISMATCH")
      || normalizedError.includes("SIGNAL")
      || normalizedError.includes("NETWORK");
  }

  function setStepState(scope, error, replayState) {
    const deviceStep = document.querySelector('[data-network-step="device"]');
    const networkStep = document.querySelector('[data-network-step="network"]');
    const connector = document.querySelector(".network-check-connector");
    const networkFailed = isNetworkFailure(error);

    if (replayState === "checking-device") {
      applyStepState(deviceStep, "active");
      applyStepState(networkStep, "muted");
      applyConnectorState(connector, "muted");
    } else if (replayState === "checking-network") {
      applyStepState(deviceStep, "done");
      applyStepState(networkStep, "active");
      applyConnectorState(connector, "active");
    } else {
      applyStepState(deviceStep, networkFailed ? "done" : "failed");
      applyStepState(networkStep, networkFailed ? "failed" : "muted");
      applyConnectorState(connector, "failed");
    }

    setText("network-check-device-label", scope === "admin" ? "后台环境" : "设备环境");
    setText("network-check-network-label", networkFailed ? "网络一致性" : "登录准入");
  }

  function wait(durationMs) {
    return new Promise((resolve) => {
      setTimeout(resolve, durationMs);
    });
  }

  async function replayFailure(scope, error) {
    const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches;
    const finalTitle = scope === "admin" ? "后台访问被暂时阻止" : "访问被暂时阻止";
    const finalParams = readParams();
    const finalMessage = resolveMessage(scope, error, finalParams.get("message"), finalParams.get("webRtcStatus"));

    document.body.dataset.networkReplay = "checking-device";
    setCopyText(DEVICE_REPLAY_TITLE, scope === "admin" ? "正在确认后台访问环境。" : DEVICE_REPLAY_COPY);
    setStepState(scope, error, "checking-device");
    await wait(reduceMotion ? 1 : DEVICE_REPLAY_MS);

    document.body.dataset.networkReplay = "checking-network";
    setCopyText(NETWORK_REPLAY_TITLE, NETWORK_REPLAY_COPY);
    setStepState(scope, error, "checking-network");
    await wait(reduceMotion ? 1 : NETWORK_REPLAY_MS);

    document.body.dataset.networkReplay = "final";
    setCopyText(finalTitle, finalMessage);
    setStepState(scope, error, "final");
    await wait(reduceMotion ? 1 : FINAL_SETTLE_MS);
    document.body.classList.add("network-check-details-visible");
  }

  function main() {
    const params = readParams();
    const scope = params.get("scope") === "admin" ? "admin" : "user";
    const error = clean(params.get("error")) || "NETWORK_CHECK_BLOCKED";
    const message = resolveMessage(scope, error, params.get("message"), params.get("webRtcStatus"));
    const details = document.getElementById("network-check-details");
    const copy = document.getElementById("network-check-copy");

    document.body.dataset.scope = scope;
    createParticles();
    setText("network-check-eyebrow", scope === "admin" ? "后台安全准入" : "安全准入");
    setText("network-check-title", scope === "admin" ? "后台访问被暂时阻止" : "访问被暂时阻止");
    if (copy) {
      copy.textContent = message;
    }

    appendDetail(details, "范围", scope === "admin" ? "管理员" : "用户");
    appendDetail(details, "原因", error);
    appendDetail(details, "路径", params.get("path"));
    appendDetail(details, "HTTP IP", params.get("httpIp"));
    appendDetail(details, "WebRTC IP", params.get("webRtcIp"));
    appendDetail(details, "WebRTC", params.get("webRtcStatus"));
    appendDetail(details, "Ray ID", firstParam(params, ["cfRay", "rayId", "rayID", "cf-ray", "CF-Ray", "cf_ray"]));
    void replayFailure(scope, error);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", main, { once: true });
  } else {
    main();
  }
})();
