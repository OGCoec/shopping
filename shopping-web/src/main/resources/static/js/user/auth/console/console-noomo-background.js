(function () {
  const root = document.body;
  const reducedMotionQuery = window.matchMedia?.("(prefers-reduced-motion: reduce)");

  function cssVariable(name, fallback) {
    if (!root) {
      return fallback;
    }
    const value = window.getComputedStyle(root).getPropertyValue(name).trim();
    return value || fallback;
  }

  function cssNumberVariable(name, fallback) {
    const value = Number.parseFloat(cssVariable(name, String(fallback)));
    return Number.isFinite(value) ? value : fallback;
  }

  function setBaseVariables() {
    root?.style.setProperty("--pointer-x", "0");
    root?.style.setProperty("--pointer-y", "0");
    root?.style.setProperty("--wash-shift-x", "0px");
    root?.style.setProperty("--wash-shift-y", "0px");
    root?.style.setProperty("--grid-shift-x", "0px");
    root?.style.setProperty("--grid-shift-y", "0px");
    root?.style.setProperty("--motion-shift-x", "0px");
    root?.style.setProperty("--motion-shift-y", "0px");
    root?.style.setProperty("--panel-shift-x", "0px");
    root?.style.setProperty("--panel-shift-y", "0px");
    root?.style.setProperty("--motion-angle", cssVariable("--motion-angle", "135deg"));
    root?.style.setProperty("--motion-hue", cssVariable("--motion-hue", "205"));
    root?.style.setProperty("--motion-alpha", cssVariable("--motion-alpha", "0.04"));
    root?.style.setProperty("--motion-panel-alpha", cssVariable("--motion-panel-alpha", "0.035"));
    root?.style.setProperty("--motion-card-alpha", cssVariable("--motion-card-alpha", "0.045"));
    root?.style.setProperty("--motion-grid-opacity", cssVariable("--motion-grid-opacity", "0.24"));
  }

  if (!root || reducedMotionQuery?.matches) {
    setBaseVariables();
    return;
  }

  setBaseVariables();

  const initialHue = cssNumberVariable("--motion-hue", 205);
  const initialAngle = cssNumberVariable("--motion-angle", 135);

  const state = {
    targetX: 0,
    targetY: 0,
    currentX: 0,
    currentY: 0,
    targetMotionX: 0,
    targetMotionY: 0,
    motionX: 0,
    motionY: 0,
    targetStrength: 0,
    strength: 0,
    targetAngle: initialAngle,
    angle: initialAngle,
    targetHue: initialHue,
    hue: initialHue,
    lastClientX: window.innerWidth / 2,
    lastClientY: window.innerHeight / 2,
    lastMoveAt: performance.now(),
    hasPointer: false
  };

  let frameId = 0;

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function formatPx(value) {
    return `${value.toFixed(2)}px`;
  }

  function shortestAngleDelta(target, current) {
    return ((((target - current) % 360) + 540) % 360) - 180;
  }

  function normalizeHue(value) {
    return ((value % 360) + 360) % 360;
  }

  function updatePointerFromClientPosition(clientX, clientY) {
    const width = Math.max(window.innerWidth || 1, 1);
    const height = Math.max(window.innerHeight || 1, 1);
    const now = performance.now();

    state.targetX = clamp((clientX / width) * 2 - 1, -1, 1);
    state.targetY = clamp((clientY / height) * 2 - 1, -1, 1);

    if (state.hasPointer) {
      const deltaX = clientX - state.lastClientX;
      const deltaY = clientY - state.lastClientY;
      const distance = Math.hypot(deltaX, deltaY);

      if (distance > 0.5) {
        const elapsed = Math.max(now - state.lastMoveAt, 16);
        const directionX = deltaX / distance;
        const directionY = deltaY / distance;
        const speed = clamp(distance / elapsed / 1.25, 0, 1);

        state.targetMotionX = clamp(directionX * speed, -1, 1);
        state.targetMotionY = clamp(directionY * speed, -1, 1);
        state.targetStrength = clamp(0.1 + speed * 0.9, 0, 1);
        state.targetAngle = Math.atan2(deltaY, deltaX) * 180 / Math.PI + 90;
        state.targetHue = normalizeHue(205 + directionX * 38 - directionY * 46);
      }
    }

    state.lastClientX = clientX;
    state.lastClientY = clientY;
    state.lastMoveAt = now;
    state.hasPointer = true;
  }

  function handleMouseMove(event) {
    updatePointerFromClientPosition(event.clientX, event.clientY);
  }

  function handlePointerMove(event) {
    updatePointerFromClientPosition(event.clientX, event.clientY);
  }

  function handleTouch(event) {
    const touch = event.touches?.[0];
    if (touch) {
      updatePointerFromClientPosition(touch.clientX, touch.clientY);
    }
  }

  function tick() {
    state.currentX += (state.targetX - state.currentX) * 0.075;
    state.currentY += (state.targetY - state.currentY) * 0.075;
    state.motionX += (state.targetMotionX - state.motionX) * 0.16;
    state.motionY += (state.targetMotionY - state.motionY) * 0.16;
    state.strength += (state.targetStrength - state.strength) * 0.16;
    state.angle += shortestAngleDelta(state.targetAngle, state.angle) * 0.14;
    state.hue += shortestAngleDelta(state.targetHue, state.hue) * 0.14;

    state.targetMotionX *= 0.91;
    state.targetMotionY *= 0.91;
    state.targetStrength *= 0.88;

    root.style.setProperty("--pointer-x", state.currentX.toFixed(4));
    root.style.setProperty("--pointer-y", state.currentY.toFixed(4));
    root.style.setProperty("--wash-shift-x", formatPx(state.currentX * -22));
    root.style.setProperty("--wash-shift-y", formatPx(state.currentY * -16));
    root.style.setProperty("--grid-shift-x", formatPx(state.currentX * 42 + state.motionX * 30));
    root.style.setProperty("--grid-shift-y", formatPx(state.currentY * 30 + state.motionY * 24));
    root.style.setProperty("--motion-shift-x", formatPx(state.motionX * 18));
    root.style.setProperty("--motion-shift-y", formatPx(state.motionY * 18));
    root.style.setProperty("--panel-shift-x", formatPx(state.currentX * -7));
    root.style.setProperty("--panel-shift-y", formatPx(state.currentY * -5));
    root.style.setProperty("--motion-angle", `${state.angle.toFixed(2)}deg`);
    root.style.setProperty("--motion-hue", String(Math.round(normalizeHue(state.hue))));
    root.style.setProperty("--motion-alpha", (0.035 + state.strength * 0.08).toFixed(3));
    root.style.setProperty("--motion-panel-alpha", (0.025 + state.strength * 0.07).toFixed(3));
    root.style.setProperty("--motion-card-alpha", (0.035 + state.strength * 0.085).toFixed(3));
    root.style.setProperty("--motion-grid-opacity", (0.22 + state.strength * 0.16).toFixed(3));

    frameId = window.requestAnimationFrame(tick);
  }

  frameId = window.requestAnimationFrame(tick);

  if ("PointerEvent" in window) {
    window.addEventListener("pointermove", handlePointerMove, { passive: true });
  } else {
    window.addEventListener("mousemove", handleMouseMove, false);
  }
  root.addEventListener("touchstart", handleTouch, { passive: true });
  root.addEventListener("touchmove", handleTouch, { passive: true });

  window.addEventListener("pagehide", () => {
    window.cancelAnimationFrame(frameId);
  }, { once: true });
})();
