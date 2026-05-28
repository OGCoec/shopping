(function () {
  const root = document.body;
  const reducedMotionQuery = window.matchMedia?.("(prefers-reduced-motion: reduce)");

  if (!root || reducedMotionQuery?.matches) {
    root?.style.setProperty("--pointer-x", "0");
    root?.style.setProperty("--pointer-y", "0");
    return;
  }

  if (!window.gsap?.quickTo) {
    root.style.setProperty("--pointer-x", "0");
    root.style.setProperty("--pointer-y", "0");
    return;
  }

  const pointerToX = window.gsap.quickTo(root, "--pointer-x", {
    duration: 0.4,
    ease: "power1.out"
  });
  const pointerToY = window.gsap.quickTo(root, "--pointer-y", {
    duration: 0.4,
    ease: "power1.out"
  });

  function updatePointerFromClientPosition(clientX, clientY) {
    const width = Math.max(window.innerWidth || 1, 1);
    const height = Math.max(window.innerHeight || 1, 1);
    const normalizedX = (clientX / width) * 2 - 1;
    const normalizedY = (clientY / height) * 2 - 1;
    pointerToX(normalizedX / 2);
    pointerToY(normalizedY / 2);
  }

  function handleMouseMove(event) {
    updatePointerFromClientPosition(event.clientX, event.clientY);
  }

  function handleTouch(event) {
    const touch = event.touches?.[0];
    if (touch) {
      updatePointerFromClientPosition(touch.clientX, touch.clientY);
    }
  }

  root.style.setProperty("--pointer-x", "0");
  root.style.setProperty("--pointer-y", "0");

  window.addEventListener("mousemove", handleMouseMove, false);
  root.addEventListener("touchstart", handleTouch, { passive: true });
  root.addEventListener("touchmove", handleTouch, { passive: true });

  window.addEventListener("pagehide", () => {
    pointerToX.tween?.kill();
    pointerToY.tween?.kill();
  }, { once: true });
})();
