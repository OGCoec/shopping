export function cssColorToRgb(color) {
  if (!color) {
    return null;
  }
  const normalized = color.trim();
  const hex = normalized.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
  if (hex) {
    const raw = hex[1].length === 3
      ? hex[1].split("").map((item) => item + item).join("")
      : hex[1];
    return [
      parseInt(raw.slice(0, 2), 16),
      parseInt(raw.slice(2, 4), 16),
      parseInt(raw.slice(4, 6), 16)
    ];
  }
  const rgb = normalized.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (!rgb) {
    return null;
  }
  return [Number(rgb[1]), Number(rgb[2]), Number(rgb[3])];
}

export function isVisibleColor(color) {
  return color && color !== "transparent" && color !== "rgba(0, 0, 0, 0)";
}

export function readPageBackground() {
  const rootStyles = window.getComputedStyle(document.documentElement);
  const bodyStyles = window.getComputedStyle(document.body);
  const candidates = [
    rootStyles.getPropertyValue("--admin-bg").trim(),
    bodyStyles.backgroundColor,
    rootStyles.backgroundColor
  ];
  return candidates.find(isVisibleColor) || "#ffffff";
}

export function readCaptureBackground(element) {
  const styles = window.getComputedStyle(element);
  return [styles.backgroundColor, readPageBackground()].find(isVisibleColor) || "#ffffff";
}
