import {
  CAMERA_Z,
  PARTICLE_COUNT,
  THREE_READY_TIMEOUT_MS
} from "./constants.js";
import { createOverlay } from "./overlay.js";
import { blendLayouts, clamp, makeLayout, smootherStep } from "./particle-layout.js";
import { createParticleMaterial } from "./particle-shader.js";
import { takeRenderer } from "./renderer-pool.js";
import { getThree } from "./three-loader.js";
import { withTimeout } from "./timing.js";

export async function renderEnterMorph(sourceCapture, targetCaptures, existingOverlay) {
  if (!sourceCapture || !targetCaptures.length) {
    return;
  }
  const overlay = existingOverlay || createOverlay();
  overlay.classList.add("is-entering");
  const THREE = await withTimeout(getThree(), THREE_READY_TIMEOUT_MS, null);
  if (!THREE) {
    overlay.remove();
    return;
  }
  const width = window.innerWidth;
  const height = window.innerHeight;
  const scene = new THREE.Scene();
  const camera = new THREE.OrthographicCamera(
    -width / 2,
    width / 2,
    height / 2,
    -height / 2,
    1,
    2600
  );
  camera.position.z = CAMERA_Z;
  const renderer = takeRenderer(THREE, overlay, width, height);

  const sourceLayout = makeLayout(width, height, "source", sourceCapture, targetCaptures);
  const scatterLayout = makeLayout(width, height, "scatter", sourceCapture, targetCaptures);
  const targetLayout = makeLayout(width, height, "target", sourceCapture, targetCaptures);
  const positions = new Float32Array(PARTICLE_COUNT * 3);
  const colors = new Float32Array(PARTICLE_COUNT * 3);
  const alphas = new Float32Array(PARTICLE_COUNT);
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute("particleColor", new THREE.BufferAttribute(colors, 3));
  geometry.setAttribute("alpha", new THREE.BufferAttribute(alphas, 1));
  const basePointSize = Math.max(2.2, Math.min(width, height) / 260) * renderer.getPixelRatio();
  const material = createParticleMaterial(THREE, basePointSize);
  const points = new THREE.Points(geometry, material);
  scene.add(points);

  await new Promise((resolve) => {
    const morphDuration = 1800;
    const startedAt = performance.now();
    let frame = 0;

    function cleanup() {
      window.cancelAnimationFrame(frame);
      scene.remove(points);
      geometry.dispose();
      material.dispose();
      renderer.dispose();
      renderer.domElement.remove();
      overlay.remove();
      resolve();
    }

    function draw(now) {
      const progress = clamp((now - startedAt) / morphDuration, 0, 1);
      blendLayouts(positions, colors, alphas, sourceLayout, scatterLayout, targetLayout, progress, 1);
      material.uniforms.pointSize.value = basePointSize * (1.95 - smootherStep(progress) * 0.9);
      overlay.style.opacity = String(1 - smootherStep(clamp((progress - 0.92) / 0.08, 0, 1)));
      points.rotation.z = progress * 0.018;
      geometry.attributes.position.needsUpdate = true;
      geometry.attributes.particleColor.needsUpdate = true;
      geometry.attributes.alpha.needsUpdate = true;
      renderer.render(scene, camera);
      if (progress < 1) {
        frame = window.requestAnimationFrame(draw);
        return;
      }
      cleanup();
    }

    frame = window.requestAnimationFrame(draw);
  });
}
