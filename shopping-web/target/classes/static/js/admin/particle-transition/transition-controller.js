import { SOURCE_CAPTURE_TIMEOUT_MS } from "./constants.js";
import { captureTargets } from "./capture-synthetic.js";
import { scheduleSourceCapturePrewarm, resolveSourceCapture } from "./capture-resolver.js";
import { loadStoredSnapshot, readStoredPayload, storeCapture } from "./capture-store.js";
import { createOverlay } from "./overlay.js";
import { renderEnterMorph } from "./morph-renderer.js";
import { prewarmRenderer } from "./renderer-pool.js";
import {
  clearPendingEnter,
  hasPendingEnter,
  markPendingEnter,
  startPendingFallback
} from "./state-store.js";
import { getThree } from "./three-loader.js";
import { prefersReducedMotion } from "./timing.js";

export function createAdminParticleTransition(root) {
  let prewarmPromise = null;

  function prewarm(source, options) {
    if (prefersReducedMotion()) {
      return Promise.resolve();
    }
    const sourceElement = source || document.querySelector("[data-admin-source]");
    if (!prewarmPromise) {
      prewarmPromise = getThree()
        .then((THREE) => prewarmRenderer(THREE))
        .catch(() => undefined);
    }
    scheduleSourceCapturePrewarm(root, sourceElement, options);
    return prewarmPromise;
  }

  async function playExit(source) {
    if (prefersReducedMotion()) {
      return;
    }
    const sourceElement = source || document.querySelector("[data-admin-source]");
    const capture = await resolveSourceCapture(root, sourceElement, SOURCE_CAPTURE_TIMEOUT_MS);
    if (capture) {
      storeCapture(capture);
    }
    markPendingEnter();
  }

  async function beginExit(options) {
    const settings = typeof options === "string" ? { to: options } : (options || {});
    const targetUrl = settings.to;
    if (prefersReducedMotion()) {
      markPendingEnter();
      if (targetUrl) {
        window.location.assign(targetUrl);
      }
      return;
    }
    try {
      await playExit(settings.source);
      document.documentElement.classList.add("admin-morph-pending");
      startPendingFallback();
    } finally {
      if (targetUrl) {
        window.location.assign(targetUrl);
      }
    }
  }

  async function playEnter(targets) {
    if (prefersReducedMotion()) {
      readStoredPayload();
      clearPendingEnter();
      return;
    }
    const shouldShowPending = hasPendingEnter()
      || document.documentElement.classList.contains("admin-morph-pending");
    const overlay = shouldShowPending ? createOverlay() : null;
    if (overlay) {
      overlay.classList.add("is-entering");
      startPendingFallback();
    }
    getThree().catch(() => null);
    const sourceCapture = await loadStoredSnapshot();
    if (!sourceCapture) {
      if (overlay) {
        overlay.remove();
      }
      clearPendingEnter();
      return;
    }
    try {
      const targetCaptures = await captureTargets(targets || document.querySelectorAll("[data-admin-target]"));
      if (!targetCaptures.length) {
        if (overlay) {
          overlay.remove();
        }
        return;
      }
      await renderEnterMorph(sourceCapture, targetCaptures, overlay);
    } catch (_) {
      if (overlay) {
        overlay.remove();
      }
    } finally {
      clearPendingEnter();
    }
  }

  startPendingFallback();

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      if (!prefersReducedMotion()) {
        prewarm(document.querySelector("[data-admin-source]")).catch(() => undefined);
      }
    }, { once: true });
  } else if (!prefersReducedMotion()) {
    window.setTimeout(() => prewarm(document.querySelector("[data-admin-source]")).catch(() => undefined), 0);
  }

  return {
    beginExit,
    markPendingEnter,
    playEnter,
    playExit,
    prewarm
  };
}
