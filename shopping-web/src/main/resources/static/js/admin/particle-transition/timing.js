import { FONT_READY_TIMEOUT_MS } from "./constants.js";

export function prefersReducedMotion() {
  return window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function withTimeout(promise, timeoutMs, fallbackValue) {
  return new Promise((resolve) => {
    let settled = false;
    const timer = window.setTimeout(() => {
      if (!settled) {
        settled = true;
        resolve(fallbackValue);
      }
    }, timeoutMs);
    Promise.resolve(promise)
      .then((value) => {
        if (!settled) {
          settled = true;
          window.clearTimeout(timer);
          resolve(value);
        }
      })
      .catch(() => {
        if (!settled) {
          settled = true;
          window.clearTimeout(timer);
          resolve(fallbackValue);
        }
      });
  });
}

export function nextFrame() {
  return new Promise((resolve) => {
    let resolved = false;
    const timer = window.setTimeout(() => {
      if (!resolved) {
        resolved = true;
        resolve();
      }
    }, 120);
    requestAnimationFrame(() => {
      if (!resolved) {
        resolved = true;
        window.clearTimeout(timer);
        resolve();
      }
    });
  });
}

export function waitForFonts() {
  if (!document.fonts || !document.fonts.ready) {
    return Promise.resolve();
  }
  return withTimeout(document.fonts.ready, FONT_READY_TIMEOUT_MS, undefined).catch(() => undefined);
}

export function requestIdleTask(root, callback, timeout) {
  if (root.requestIdleCallback) {
    return root.requestIdleCallback(callback, { timeout: timeout || 1200 });
  }
  return window.setTimeout(callback, 280);
}
