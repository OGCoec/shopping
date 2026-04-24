(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingAuthUtils = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function sleep(ms) {
    return new Promise((resolve) => {
      setTimeout(resolve, ms);
    });
  }

  function getCaptchaSuccessFeedbackDelay(startedAt, now = Date.now(), minimumDuration = 1200) {
    const safeStartedAt = Number.isFinite(Number(startedAt)) ? Number(startedAt) : Number(now);
    const safeNow = Number.isFinite(Number(now)) ? Number(now) : Date.now();
    return Math.max(0, Math.round(minimumDuration - Math.max(0, safeNow - safeStartedAt)));
  }

  async function waitForCaptchaSuccessFeedback(startedAt, minimumDuration = 1200) {
    const delay = getCaptchaSuccessFeedbackDelay(startedAt, Date.now(), minimumDuration);
    if (delay > 0) {
      await sleep(delay);
    }
  }

  function waitForAnimationFrame() {
    return new Promise((resolve) => {
      if (typeof requestAnimationFrame === "function") {
        requestAnimationFrame(() => resolve());
        return;
      }
      setTimeout(resolve, 0);
    });
  }

  async function waitForNextPaint(frameCount = 1) {
    const frames = Math.max(1, Math.round(Number(frameCount) || 1));
    for (let index = 0; index < frames; index += 1) {
      await waitForAnimationFrame();
    }
  }

  function getElementDisplaySize(node, fallbackWidth = 0, fallbackHeight = 0) {
    if (!node) {
      return {
        width: Math.round(fallbackWidth || 0),
        height: Math.round(fallbackHeight || 0)
      };
    }
    const rect = typeof node.getBoundingClientRect === "function" ? node.getBoundingClientRect() : null;
    return {
      width: Math.round(rect?.width || node.clientWidth || node.naturalWidth || fallbackWidth || 0),
      height: Math.round(rect?.height || node.clientHeight || node.naturalHeight || fallbackHeight || 0)
    };
  }

  return {
    sleep,
    getCaptchaSuccessFeedbackDelay,
    waitForCaptchaSuccessFeedback,
    waitForAnimationFrame,
    waitForNextPaint,
    getElementDisplaySize
  };
});
