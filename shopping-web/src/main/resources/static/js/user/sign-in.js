(function () {
  const API_BASE = "/shopping/user/api/sign-in";
  const PERIOD_LABELS = {
    DAY: "每日签到"
  };

  const statusEl = document.getElementById("sign-in-page-status");
  const periodLabel = document.getElementById("sign-in-period-label");
  const statusBadge = document.getElementById("sign-in-status-badge");
  const statusTitle = document.getElementById("sign-in-status-title");
  const statusCopy = document.getElementById("sign-in-status-copy");
  const signButton = document.getElementById("sign-in-button");
  const cycleDayEl = document.getElementById("sign-in-cycle-day");
  const cycleRing = cycleDayEl?.closest(".sign-in-cycle-ring");
  const progressFill = document.getElementById("sign-in-progress-fill");
  const nextRewardEl = document.getElementById("sign-in-next-reward");
  const availablePointsEl = document.getElementById("sign-in-available-points");
  const totalEarnedPointsEl = document.getElementById("sign-in-total-earned-points");
  const continuousCountEl = document.getElementById("sign-in-continuous-count");
  const nextPointsEl = document.getElementById("sign-in-next-points");

  let currentState = null;

  function authClient() {
    return window.ShoppingAuthClient || null;
  }

  function setStatus(message, type = "") {
    if (!statusEl) {
      return;
    }
    statusEl.textContent = message || "";
    statusEl.hidden = !message;
    statusEl.classList.toggle("is-error", type === "error");
    statusEl.classList.toggle("is-ok", type === "ok");
  }

  async function fetchJson(path, options = {}) {
    const client = authClient();
    if (!client?.fetchWithAuth) {
      throw new Error("认证客户端不可用");
    }

    const response = await client.fetchWithAuth(path, {
      method: options.method || "GET",
      credentials: "same-origin",
      headers: {
        Accept: "application/json"
      }
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      const message = payload?.message || payload?.error || `HTTP ${response.status}`;
      const error = new Error(message);
      error.status = response.status;
      error.payload = payload;
      throw error;
    }
    return payload || {};
  }

  function numberValue(value) {
    const number = Number(value);
    return Number.isFinite(number) ? Math.max(0, Math.trunc(number)) : 0;
  }

  function formatNumber(value) {
    return new Intl.NumberFormat("zh-CN").format(numberValue(value));
  }

  function periodLabelText(periodUnit) {
    const key = String(periodUnit || "").trim().toUpperCase();
    return PERIOD_LABELS[key] || "每日签到";
  }

  function normalizeState(payload) {
    const signedInCurrentPeriod = Boolean(payload.signedInCurrentPeriod || payload.signed || payload.alreadySigned);
    return {
      success: payload.success !== false,
      code: String(payload.code || ""),
      message: String(payload.message || ""),
      signedInCurrentPeriod,
      availablePoints: numberValue(payload.availablePoints),
      totalEarnedPoints: numberValue(payload.totalEarnedPoints),
      continuousCount: numberValue(payload.continuousCount),
      cycleDay: numberValue(payload.cycleDay),
      nextMilestoneCycleDay: numberValue(payload.nextMilestoneCycleDay),
      periodsToNextMilestone: numberValue(payload.periodsToNextMilestone),
      nextMilestoneRewardPoints: numberValue(payload.nextMilestoneRewardPoints),
      periodUnit: String(payload.periodUnit || currentState?.periodUnit || "")
    };
  }

  function progressPercent(state) {
    const target = state.nextMilestoneCycleDay > 0 ? state.nextMilestoneCycleDay : 3;
    if (state.cycleDay <= 0) {
      return 0;
    }
    if (state.cycleDay >= target) {
      return 100;
    }
    return Math.round((state.cycleDay / target) * 100);
  }

  function renderState(payload) {
    const state = normalizeState(payload);
    currentState = state;

    periodLabel.textContent = periodLabelText(state.periodUnit);
    statusBadge.textContent = state.signedInCurrentPeriod ? "已签到" : "待签到";
    statusBadge.classList.toggle("is-signed", state.signedInCurrentPeriod);
    statusTitle.textContent = state.signedInCurrentPeriod ? "今日已签到" : "今日待签到";
    statusCopy.textContent = state.signedInCurrentPeriod
      ? "今天的签到积分已入账，可以继续保持连续签到。"
      : "点击签到后，今日奖励会立即计入可用积分。";

    cycleDayEl.textContent = formatNumber(state.cycleDay);
    const percent = progressPercent(state);
    cycleRing?.style.setProperty("--sign-in-progress", String(percent));
    progressFill?.style.setProperty("--sign-in-progress", String(percent));
    nextRewardEl.textContent = state.periodsToNextMilestone > 0
      ? `距离第 ${state.nextMilestoneCycleDay} 天奖励还差 ${state.periodsToNextMilestone} 天`
      : "下一档奖励等待刷新";

    availablePointsEl.textContent = formatNumber(state.availablePoints);
    totalEarnedPointsEl.textContent = formatNumber(state.totalEarnedPoints);
    continuousCountEl.textContent = formatNumber(state.continuousCount);
    nextPointsEl.textContent = `${formatNumber(state.nextMilestoneRewardPoints)} 分`;

    signButton.disabled = state.signedInCurrentPeriod;
    signButton.textContent = state.signedInCurrentPeriod ? "今日已签到" : "立即签到";
  }

  async function loadStatus() {
    setStatus("正在加载签到状态");
    signButton.disabled = true;
    try {
      const payload = await fetchJson(`${API_BASE}/status`);
      renderState(payload);
      setStatus("", "ok");
    } catch (error) {
      signButton.disabled = true;
      setStatus(error.message || "签到状态加载失败", "error");
    }
  }

  async function signIn() {
    signButton.disabled = true;
    signButton.textContent = "正在签到";
    setStatus("正在签到");
    try {
      const payload = await fetchJson(API_BASE, { method: "POST" });
      renderState(payload);
      if (payload.code === "SIGN_IN_ALREADY_DONE") {
        setStatus("今日已经签到", "ok");
        return;
      }
      setStatus(payload.message || `签到成功，+${formatNumber(payload.rewardPoints)} 积分`, "ok");
    } catch (error) {
      setStatus(error.payload?.message || error.message || "签到失败", "error");
      if (currentState && !currentState.signedInCurrentPeriod) {
        signButton.disabled = false;
        signButton.textContent = "立即签到";
      }
    }
  }

  signButton?.addEventListener("click", signIn);

  async function startPage() {
    const pageGate = window.ShoppingPageAccessGate;
    if (pageGate?.ready) {
      const allowed = await pageGate.ready();
      if (allowed === false) {
        return;
      }
    }
    await loadStatus();
  }

  startPage().catch((error) => {
    setStatus(error.message || "签到页面加载失败", "error");
  });
})();
