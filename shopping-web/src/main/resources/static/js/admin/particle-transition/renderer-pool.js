import {
  MAX_RENDER_HEIGHT,
  MAX_RENDER_PIXEL_RATIO,
  MAX_RENDER_WIDTH
} from "./constants.js";

let prewarmedRenderer = null;

function createRenderer(THREE) {
  const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
  renderer.setClearColor(0x000000, 0);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, MAX_RENDER_PIXEL_RATIO));
  renderer.setSize(1, 1);
  return renderer;
}

function getRenderSize(width, height) {
  const scale = Math.min(1, MAX_RENDER_WIDTH / width, MAX_RENDER_HEIGHT / height);
  return {
    height: Math.max(1, Math.round(height * scale)),
    scale,
    width: Math.max(1, Math.round(width * scale))
  };
}

export function prewarmRenderer(THREE) {
  if (!prewarmedRenderer && document.body) {
    prewarmedRenderer = createRenderer(THREE);
  }
}

export function takeRenderer(THREE, overlay, width, height) {
  const renderer = prewarmedRenderer || createRenderer(THREE);
  const renderSize = getRenderSize(width, height);
  prewarmedRenderer = null;
  renderer.setClearColor(0x000000, 0);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, MAX_RENDER_PIXEL_RATIO));
  renderer.setSize(renderSize.width, renderSize.height, false);
  renderer.domElement.style.width = `${width}px`;
  renderer.domElement.style.height = `${height}px`;
  overlay.appendChild(renderer.domElement);
  return renderer;
}
