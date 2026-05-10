export function rectSignedDistance(x, y, rect) {
  const qx = Math.abs(x - rect.cx) - rect.w / 2 + rect.radius;
  const qy = Math.abs(y - rect.cy) - rect.h / 2 + rect.radius;
  const ox = Math.max(qx, 0);
  const oy = Math.max(qy, 0);
  return Math.hypot(ox, oy) + Math.min(Math.max(qx, qy), 0) - rect.radius;
}

export function inRoundedRect(x, y, rect) {
  return rectSignedDistance(x, y, rect) <= 0;
}

export function pointInRect(u, v, rect) {
  return {
    x: rect.cx + (u - 0.5) * rect.w,
    y: rect.cy + (0.5 - v) * rect.h
  };
}

export function elementRectToSceneRect(rect, radius) {
  return {
    cx: rect.left + rect.width / 2 - window.innerWidth / 2,
    cy: window.innerHeight / 2 - (rect.top + rect.height / 2),
    h: rect.height,
    radius,
    w: rect.width
  };
}

export function getElementRadius(element) {
  const styles = window.getComputedStyle(element);
  return Number.parseFloat(styles.borderTopLeftRadius) || 0;
}
