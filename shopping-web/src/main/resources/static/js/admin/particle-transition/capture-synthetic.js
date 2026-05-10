import { SYNTHETIC_SOURCE_AREA_THRESHOLD } from "./constants.js";
import { cssColorToRgb, readCaptureBackground } from "./color-utils.js";
import { captureCanvasToDataUrl } from "./capture-store.js";
import { elementRectToSceneRect, getElementRadius } from "./rect-utils.js";

function expandTargetElements(targets) {
  const result = [];
  Array.from(targets || []).filter(Boolean).forEach((element) => {
    if (element.classList && element.classList.contains("admin-console-grid")) {
      const cards = element.querySelectorAll(".admin-metric-card, .admin-activity-card");
      if (cards.length) {
        result.push(...Array.from(cards));
        return;
      }
    }
    result.push(element);
  });
  return result;
}

function readElementInkRgb(element) {
  const color = cssColorToRgb(window.getComputedStyle(element).color);
  return color || [21, 32, 24];
}

export function createSyntheticTargetCapture(element, index) {
  if (!element) {
    return null;
  }
  const rect = element.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) {
    return null;
  }
  const canvas = document.createElement("canvas");
  canvas.width = 96;
  canvas.height = 72;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) {
    return null;
  }
  const backgroundRgb = cssColorToRgb(readCaptureBackground(element)) || [255, 255, 255];
  const inkRgb = readElementInkRgb(element);
  const accentRgb = index % 3 === 0 ? [15, 139, 110] : index % 3 === 1 ? [213, 111, 69] : [216, 162, 54];
  const background = `rgb(${backgroundRgb[0]}, ${backgroundRgb[1]}, ${backgroundRgb[2]})`;
  const ink = `rgb(${inkRgb[0]}, ${inkRgb[1]}, ${inkRgb[2]})`;
  const accent = `rgb(${accentRgb[0]}, ${accentRgb[1]}, ${accentRgb[2]})`;
  context.fillStyle = background;
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = ink;
  context.globalAlpha = 0.86;
  context.fillRect(8, 10, 28 + (index % 4) * 6, 6);
  context.fillRect(8, 25, 44 + (index % 3) * 10, 11);
  context.globalAlpha = 0.72;
  context.fillStyle = accent;
  context.fillRect(8, 49, 62 + (index % 2) * 16, 5);
  if (element.classList && element.classList.contains("admin-activity-card")) {
    context.fillRect(8, 58, 48, 5);
    context.fillRect(8, 64, 70, 5);
  }
  context.globalAlpha = 1;
  return {
    backgroundRgb,
    imageData: context.getImageData(0, 0, canvas.width, canvas.height),
    mode: "solid",
    rect: elementRectToSceneRect(rect, getElementRadius(element))
  };
}

export function shouldUseSyntheticSourceCapture(element) {
  if (!element) {
    return true;
  }
  if (element.matches?.(".admin-split-console, .admin-console-body, .admin-main")) {
    return true;
  }
  const rect = element.getBoundingClientRect();
  return rect.width * rect.height > SYNTHETIC_SOURCE_AREA_THRESHOLD;
}

export function createSyntheticSourceCapture(element) {
  const rect = element?.getBoundingClientRect?.() || {
    height: Math.min(window.innerHeight * 0.42, 520),
    left: window.innerWidth * 0.28,
    top: window.innerHeight * 0.24,
    width: Math.min(window.innerWidth * 0.42, 620)
  };
  if (rect.width <= 0 || rect.height <= 0) {
    return null;
  }
  const canvas = document.createElement("canvas");
  canvas.width = 128;
  canvas.height = 96;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) {
    return null;
  }
  const backgroundRgb = cssColorToRgb(readCaptureBackground(element || document.body)) || [255, 255, 255];
  const inkRgb = element ? readElementInkRgb(element) : [21, 32, 24];
  const background = `rgb(${backgroundRgb[0]}, ${backgroundRgb[1]}, ${backgroundRgb[2]})`;
  const ink = `rgb(${inkRgb[0]}, ${inkRgb[1]}, ${inkRgb[2]})`;
  context.fillStyle = background;
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = ink;
  context.globalAlpha = 0.82;

  if (element?.matches?.(".admin-split-console, .admin-console-body, .admin-main")) {
    context.fillRect(10, 11, 26, 7);
    context.fillRect(10, 26, 32, 8);
    context.fillRect(10, 40, 36, 34);
    context.globalAlpha = 0.6;
    context.fillRect(54, 14, 58, 8);
    context.fillRect(54, 31, 48, 9);
    context.fillRect(54, 50, 22, 25);
    context.fillRect(82, 50, 22, 25);
  } else {
    context.fillRect(21, 14, 13, 13);
    context.fillRect(37, 14, 13, 13);
    context.fillRect(21, 30, 13, 13);
    context.fillRect(37, 30, 13, 13);
    context.fillRect(21, 52, 65, 7);
    context.fillRect(21, 66, 78, 7);
    context.globalAlpha = 0.68;
    context.fillRect(21, 80, 86, 8);
  }

  context.globalAlpha = 1;
  const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
  return {
    backgroundRgb,
    imageData,
    imageDataUrl: captureCanvasToDataUrl(canvas),
    mode: "solid",
    rect: elementRectToSceneRect(rect, getElementRadius(element || document.body))
  };
}

export async function captureTargets(targets) {
  const elements = expandTargetElements(targets);
  return elements
    .map((element, index) => createSyntheticTargetCapture(element, index))
    .filter(Boolean);
}
