import { OVERLAY_CLASS } from "./constants.js";

export function createOverlay() {
  const existing = document.querySelector(`.${OVERLAY_CLASS}`);
  if (existing) {
    return existing;
  }
  const overlay = document.createElement("div");
  overlay.className = OVERLAY_CLASS;
  overlay.setAttribute("aria-hidden", "true");
  document.body.appendChild(overlay);
  return overlay;
}

export function removeOverlay(overlay) {
  if (overlay) {
    overlay.remove();
  }
}
