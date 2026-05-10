import { PENDING_FALLBACK_MS, PENDING_KEY } from "./constants.js";

let pendingFallbackTimer = 0;

export function hasPendingEnter() {
  try {
    return sessionStorage.getItem(PENDING_KEY) === "1";
  } catch (_) {
    return false;
  }
}

export function markPendingEnter() {
  try {
    sessionStorage.setItem(PENDING_KEY, "1");
  } catch (_) {
  }
}

export function clearPendingEnter() {
  if (pendingFallbackTimer) {
    window.clearTimeout(pendingFallbackTimer);
    pendingFallbackTimer = 0;
  }
  document.documentElement.classList.remove("admin-morph-pending");
  try {
    sessionStorage.removeItem(PENDING_KEY);
  } catch (_) {
  }
}

export function startPendingFallback() {
  if (!hasPendingEnter() && !document.documentElement.classList.contains("admin-morph-pending")) {
    return;
  }
  if (pendingFallbackTimer) {
    window.clearTimeout(pendingFallbackTimer);
  }
  pendingFallbackTimer = window.setTimeout(clearPendingEnter, PENDING_FALLBACK_MS);
}
