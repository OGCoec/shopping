import {
  BACKGROUND_DIFF_THRESHOLD,
  PARTICLE_COUNT,
  PARTICLE_SIDE,
  PLANE_Z
} from "./constants.js";
import { inRoundedRect, pointInRect } from "./rect-utils.js";

export function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

export function smootherStep(value) {
  const x = clamp(value, 0, 1);
  return x * x * x * (x * (x * 6 - 15) + 10);
}

function hash01(index, salt) {
  const x = Math.sin(index * 12.9898 + salt * 78.233) * 43758.5453;
  return x - Math.floor(x);
}

function pixelAt(capture, u, v) {
  const x = clamp(Math.floor(u * capture.imageData.width), 0, capture.imageData.width - 1);
  const y = clamp(Math.floor(v * capture.imageData.height), 0, capture.imageData.height - 1);
  const offset = (y * capture.imageData.width + x) * 4;
  return {
    a: capture.imageData.data[offset + 3] / 255,
    b: capture.imageData.data[offset + 2] / 255,
    g: capture.imageData.data[offset + 1] / 255,
    r: capture.imageData.data[offset] / 255,
    rawB: capture.imageData.data[offset + 2],
    rawG: capture.imageData.data[offset + 1],
    rawR: capture.imageData.data[offset]
  };
}

function pointWithColor(point, pixel, active) {
  return {
    active,
    b: pixel.b,
    g: pixel.g,
    r: pixel.r,
    x: point.x,
    y: point.y,
    z: PLANE_Z
  };
}

function sourcePoint(capture, u, v, index, viewportScale) {
  const point = pointInRect(u, v, capture.rect);
  const pixel = pixelAt(capture, u, v);
  const active = pixel.a > 0.08 && inRoundedRect(point.x, point.y, capture.rect);
  point.x += (hash01(index, 1) - 0.5) * 3 * viewportScale;
  point.y += (hash01(index, 2) - 0.5) * 3 * viewportScale;
  return pointWithColor(point, pixel, active);
}

function capturePoint(capture, u, v, index, salt, viewportScale) {
  const point = pointInRect(u, v, capture.rect);
  const pixel = pixelAt(capture, u, v);
  const diff = (
    Math.abs(pixel.rawR - capture.backgroundRgb[0]) +
    Math.abs(pixel.rawG - capture.backgroundRgb[1]) +
    Math.abs(pixel.rawB - capture.backgroundRgb[2])
  ) / (255 * 3);
  const active = pixel.a > 0.08
    && inRoundedRect(point.x, point.y, capture.rect)
    && (capture.mode === "solid" || diff > BACKGROUND_DIFF_THRESHOLD);
  point.x += (hash01(index, salt) - 0.5) * 3 * viewportScale;
  point.y += (hash01(index, salt + 1) - 0.5) * 3 * viewportScale;
  return pointWithColor(point, pixel, active);
}

function makeTargetPicker(targetCaptures) {
  const weights = targetCaptures.map((capture) => Math.sqrt(Math.max(1, capture.rect.w * capture.rect.h)));
  const total = weights.reduce((sum, weight) => sum + weight, 0);
  return function pick(seed) {
    if (!targetCaptures.length || total <= 0) {
      return null;
    }
    let cursor = seed * total;
    for (let index = 0; index < targetCaptures.length; index += 1) {
      cursor -= weights[index];
      if (cursor <= 0) {
        return targetCaptures[index];
      }
    }
    return targetCaptures[targetCaptures.length - 1];
  };
}

function fallbackTargetPoint(capture, u, v, index, viewportScale) {
  const point = pointInRect(u, v, capture.rect);
  const tint = hash01(index, 71);
  const background = capture.backgroundRgb || [255, 255, 255];
  point.x += (hash01(index, 72) - 0.5) * 4 * viewportScale;
  point.y += (hash01(index, 73) - 0.5) * 4 * viewportScale;
  return {
    active: true,
    b: (background[2] * (1 - tint) + 54 * tint) / 255,
    g: (background[1] * (1 - tint) + 162 * tint) / 255,
    r: (background[0] * (1 - tint) + 15 * tint) / 255,
    x: point.x,
    y: point.y,
    z: PLANE_Z
  };
}

function targetPoint(targetCaptures, pickTarget, u, v, index, viewportScale) {
  for (let attempt = 0; attempt < 7; attempt += 1) {
    const capture = pickTarget(hash01(index, 18 + attempt));
    if (!capture) {
      break;
    }
    const point = capturePoint(
      capture,
      (u + hash01(index, 24 + attempt) * 0.73) % 1,
      (v + hash01(index, 34 + attempt) * 0.73) % 1,
      index,
      44 + attempt,
      viewportScale
    );
    if (point.active) {
      return point;
    }
  }
  const fallback = pickTarget(hash01(index, 91));
  if (fallback) {
    return fallbackTargetPoint(
      fallback,
      (u + hash01(index, 92) * 0.31) % 1,
      (v + hash01(index, 93) * 0.31) % 1,
      index,
      viewportScale
    );
  }
  return {
    active: false,
    b: 0,
    g: 0,
    r: 0,
    x: 0,
    y: 0,
    z: PLANE_Z
  };
}

export function makeLayout(width, height, kind, sourceCapture, targetCaptures) {
  const positions = new Float32Array(PARTICLE_COUNT * 3);
  const colors = new Float32Array(PARTICLE_COUNT * 3);
  const alphas = new Float32Array(PARTICLE_COUNT);
  const viewportScale = Math.min(width, height) / 860;
  const pickTarget = makeTargetPicker(targetCaptures);

  for (let row = 0; row < PARTICLE_SIDE; row += 1) {
    for (let col = 0; col < PARTICLE_SIDE; col += 1) {
      const index = row * PARTICLE_SIDE + col;
      const u = (col + 0.5) / PARTICLE_SIDE;
      const v = (row + 0.5) / PARTICLE_SIDE;
      const source = kind !== "target" ? sourcePoint(sourceCapture, u, v, index, viewportScale) : null;
      const target = kind !== "source" ? targetPoint(targetCaptures, pickTarget, u, v, index, viewportScale) : null;
      let point = source || target;

      if (kind === "target") {
        point = target;
      } else if (kind === "scatter") {
        const angle = hash01(index, 3) * Math.PI * 2;
        const radius = (0.08 + Math.sqrt(hash01(index, 4)) * 0.34) * Math.min(width, height);
        const mix = hash01(index, 6);
        point = {
          active: source.active || target.active,
          b: source.b + (target.b - source.b) * mix,
          g: source.g + (target.g - source.g) * mix,
          r: source.r + (target.r - source.r) * mix,
          x: source.x + Math.cos(angle) * radius * 0.42,
          y: source.y + Math.sin(angle) * radius * 0.3,
          z: (hash01(index, 5) - 0.5) * 240
        };
      }

      const offset = index * 3;
      positions[offset] = point.x;
      positions[offset + 1] = point.y;
      positions[offset + 2] = point.z;
      colors[offset] = point.r;
      colors[offset + 1] = point.g;
      colors[offset + 2] = point.b;
      alphas[index] = point.active ? 1 : 0;
    }
  }
  return { alphas, colors, positions };
}

export function blendLayouts(outputPositions, outputColors, outputAlphas, from, middle, to, progress, revealProgress) {
  const first = smootherStep(clamp(progress / 0.3, 0, 1));
  const second = smootherStep(clamp((progress - 0.24) / 0.76, 0, 1));
  const reveal = clamp(revealProgress, 0, 1);

  for (let particleIndex = 0; particleIndex < PARTICLE_COUNT; particleIndex += 1) {
    const revealSeed = hash01(particleIndex, 11);
    const revealAmount = smootherStep(clamp((reveal - revealSeed * 0.82) / 0.18, 0, 1));
    for (let axis = 0; axis < 3; axis += 1) {
      const index = particleIndex * 3 + axis;
      const scatterValue = from.positions[index] + (middle.positions[index] - from.positions[index]) * first;
      outputPositions[index] = scatterValue + (to.positions[index] - scatterValue) * second;
      const scatterColor = from.colors[index] + (middle.colors[index] - from.colors[index]) * first;
      outputColors[index] = scatterColor + (to.colors[index] - scatterColor) * second;
    }
    const scatterAlpha = from.alphas[particleIndex]
      + (middle.alphas[particleIndex] - from.alphas[particleIndex]) * first;
    outputAlphas[particleIndex] =
      (scatterAlpha + (to.alphas[particleIndex] - scatterAlpha) * second) * revealAmount;
  }
}
