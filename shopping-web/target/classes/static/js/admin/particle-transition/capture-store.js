import { SNAPSHOT_KEY, STORED_SNAPSHOT_DECODE_TIMEOUT_MS } from "./constants.js";
import { withTimeout } from "./timing.js";

export function captureCanvasToDataUrl(canvas) {
  try {
    const webp = canvas.toDataURL("image/webp", 0.86);
    if (webp && webp.startsWith("data:image/webp")) {
      return webp;
    }
  } catch (_) {
  }
  return canvas.toDataURL("image/png");
}

export function storeCapture(capture) {
  if (!capture || !capture.imageDataUrl) {
    return;
  }
  try {
    sessionStorage.setItem(SNAPSHOT_KEY, JSON.stringify({
      backgroundRgb: capture.backgroundRgb,
      imageDataUrl: capture.imageDataUrl,
      mode: capture.mode,
      rect: capture.rect,
      storedAt: Date.now(),
      version: 2
    }));
  } catch (_) {
    sessionStorage.removeItem(SNAPSHOT_KEY);
  }
}

export function readStoredPayload() {
  let raw = null;
  try {
    raw = sessionStorage.getItem(SNAPSHOT_KEY);
    sessionStorage.removeItem(SNAPSHOT_KEY);
  } catch (_) {
    return null;
  }
  if (!raw) {
    return null;
  }
  try {
    const payload = JSON.parse(raw);
    if (!payload || payload.version !== 2 || !payload.imageDataUrl || !payload.rect) {
      return null;
    }
    return payload;
  } catch (_) {
    return null;
  }
}

function imageDataFromDataUrl(dataUrl) {
  return new Promise((resolve) => {
    const image = new Image();
    image.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = image.naturalWidth || image.width;
      canvas.height = image.naturalHeight || image.height;
      const context = canvas.getContext("2d", { willReadFrequently: true });
      if (!context) {
        resolve(null);
        return;
      }
      context.drawImage(image, 0, 0);
      resolve(context.getImageData(0, 0, canvas.width, canvas.height));
    };
    image.onerror = () => resolve(null);
    image.src = dataUrl;
  });
}

export async function loadStoredSnapshot() {
  const payload = readStoredPayload();
  if (!payload) {
    return null;
  }
  const imageData = await withTimeout(
    imageDataFromDataUrl(payload.imageDataUrl),
    STORED_SNAPSHOT_DECODE_TIMEOUT_MS,
    null
  );
  if (!imageData) {
    return null;
  }
  return {
    backgroundRgb: payload.backgroundRgb || [255, 255, 255],
    imageData,
    mode: payload.mode || "solid",
    rect: payload.rect
  };
}
