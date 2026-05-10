import { SOURCE_CAPTURE_TIMEOUT_MS } from "./constants.js";
import { captureElement } from "./capture-html.js";
import {
  createSyntheticSourceCapture,
  shouldUseSyntheticSourceCapture
} from "./capture-synthetic.js";
import { hasPendingEnter } from "./state-store.js";
import { requestIdleTask, withTimeout } from "./timing.js";

let prewarmCapturePromise = null;
let prewarmCaptureElement = null;
let prewarmCaptureVersion = 0;
let cachedSourceCapture = null;

export function scheduleSourceCapturePrewarm(root, element, options) {
  if (!element || hasPendingEnter()) {
    return Promise.resolve(null);
  }
  if (shouldUseSyntheticSourceCapture(element) || !root.html2canvas) {
    cachedSourceCapture = createSyntheticSourceCapture(element);
    return Promise.resolve(cachedSourceCapture);
  }
  const settings = options || {};
  if (settings.forceCapture) {
    cachedSourceCapture = null;
    prewarmCapturePromise = null;
    prewarmCaptureElement = null;
  }
  if (prewarmCapturePromise && prewarmCaptureElement === element) {
    return prewarmCapturePromise;
  }
  prewarmCaptureElement = element;
  prewarmCaptureVersion += 1;
  const captureVersion = prewarmCaptureVersion;
  prewarmCapturePromise = new Promise((resolve) => {
    const capture = () => {
      captureElement(root, element, "solid")
        .then((capture) => {
          if (capture && captureVersion === prewarmCaptureVersion) {
            cachedSourceCapture = capture;
          }
          resolve(capture);
        })
        .catch(() => resolve(null));
    };
    if (settings.immediate) {
      capture();
      return;
    }
    requestIdleTask(root, capture, 650);
  });
  return prewarmCapturePromise;
}

export async function resolveSourceCapture(root, sourceElement, timeoutMs = SOURCE_CAPTURE_TIMEOUT_MS) {
  if (cachedSourceCapture) {
    return cachedSourceCapture;
  }
  if (shouldUseSyntheticSourceCapture(sourceElement)) {
    return createSyntheticSourceCapture(sourceElement);
  }
  if (prewarmCapturePromise && prewarmCaptureElement === sourceElement) {
    const warmedCapture = timeoutMs
      ? await withTimeout(prewarmCapturePromise, timeoutMs, null)
      : await prewarmCapturePromise;
    if (warmedCapture) {
      return warmedCapture;
    }
    if (timeoutMs) {
      return createSyntheticSourceCapture(sourceElement);
    }
  }
  if (timeoutMs) {
    return createSyntheticSourceCapture(sourceElement);
  }
  return captureElement(root, sourceElement, "solid");
}
