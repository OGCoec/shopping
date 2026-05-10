import {
  CAPTURE_SCALE,
  HTML2CANVAS_TIMEOUT_MS,
  MAX_CAPTURE_SIDE,
  OVERLAY_CLASS
} from "./constants.js";
import { cssColorToRgb, readCaptureBackground } from "./color-utils.js";
import { elementRectToSceneRect, getElementRadius } from "./rect-utils.js";
import { nextFrame, waitForFonts, withTimeout } from "./timing.js";
import { captureCanvasToDataUrl } from "./capture-store.js";

function normalizeCaptureCanvas(canvas) {
  const longestSide = Math.max(canvas.width, canvas.height);
  if (longestSide <= MAX_CAPTURE_SIDE) {
    return canvas;
  }
  const ratio = MAX_CAPTURE_SIDE / longestSide;
  const normalized = document.createElement("canvas");
  normalized.width = Math.max(1, Math.round(canvas.width * ratio));
  normalized.height = Math.max(1, Math.round(canvas.height * ratio));
  const context = normalized.getContext("2d", { willReadFrequently: true });
  if (!context) {
    return canvas;
  }
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = "high";
  context.drawImage(canvas, 0, 0, normalized.width, normalized.height);
  return normalized;
}

export async function captureElement(root, element, mode) {
  if (!element || !root.html2canvas) {
    return null;
  }
  await waitForFonts();
  await nextFrame();
  const rect = element.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) {
    return null;
  }
  const backgroundRgb = cssColorToRgb(readCaptureBackground(element)) || [255, 255, 255];
  const canvas = await withTimeout(root.html2canvas(element, {
    backgroundColor: null,
    height: rect.height,
    ignoreElements: (target) => target.classList && target.classList.contains(OVERLAY_CLASS),
    logging: false,
    scale: CAPTURE_SCALE,
    useCORS: true,
    width: rect.width,
    windowHeight: window.innerHeight,
    windowWidth: window.innerWidth
  }), HTML2CANVAS_TIMEOUT_MS, null);
  if (!canvas) {
    return null;
  }
  const normalizedCanvas = normalizeCaptureCanvas(canvas);
  const context = normalizedCanvas.getContext("2d", { willReadFrequently: true });
  if (!context) {
    return null;
  }
  return {
    backgroundRgb,
    imageData: context.getImageData(0, 0, normalizedCanvas.width, normalizedCanvas.height),
    imageDataUrl: captureCanvasToDataUrl(normalizedCanvas),
    mode: mode || "solid",
    rect: elementRectToSceneRect(rect, getElementRadius(element))
  };
}
