(function () {
  const pathValue = document.getElementById("pathValue");
  if (pathValue) {
    pathValue.textContent = window.location.pathname || "/";
  }

  const buttons = Array.from(document.querySelectorAll(".spring-button"));

  function navigate(action) {
    if (action === "back") {
      if (window.history.length > 1) {
        window.history.back();
        return;
      }
      window.location.href = "/shopping/user/log-in";
      return;
    }

    if (action === "login") {
      window.location.href = "/shopping/user/log-in";
    }
  }

  buttons.forEach((button) => {
    const state = {
      x: 0,
      y: 0,
      scale: 1,
      tx: 0,
      ty: 0,
      ts: 1,
      vx: 0,
      vy: 0,
      vs: 0,
      raf: 0,
    };

    const stiffness = 0.16;
    const damping = 0.72;
    const scaleStiffness = 0.2;
    const scaleDamping = 0.68;

    const update = () => {
      state.vx = (state.vx + (state.tx - state.x) * stiffness) * damping;
      state.vy = (state.vy + (state.ty - state.y) * stiffness) * damping;
      state.vs = (state.vs + (state.ts - state.scale) * scaleStiffness) * scaleDamping;

      state.x += state.vx;
      state.y += state.vy;
      state.scale += state.vs;

      button.style.setProperty("--button-x", `${state.x.toFixed(3)}px`);
      button.style.setProperty("--button-y", `${state.y.toFixed(3)}px`);
      button.style.setProperty("--button-scale", state.scale.toFixed(4));

      const settled =
        Math.abs(state.tx - state.x) < 0.01 &&
        Math.abs(state.ty - state.y) < 0.01 &&
        Math.abs(state.ts - state.scale) < 0.001 &&
        Math.abs(state.vx) < 0.01 &&
        Math.abs(state.vy) < 0.01 &&
        Math.abs(state.vs) < 0.001;

      if (!settled) {
        state.raf = window.requestAnimationFrame(update);
      } else {
        state.raf = 0;
      }
    };

    const start = () => {
      if (!state.raf) {
        state.raf = window.requestAnimationFrame(update);
      }
    };

    button.addEventListener("pointermove", (event) => {
      const rect = button.getBoundingClientRect();
      const dx = (event.clientX - rect.left) / rect.width - 0.5;
      const dy = (event.clientY - rect.top) / rect.height - 0.5;
      state.tx = dx * 10;
      state.ty = dy * 6;
      state.ts = 1.035;
      start();
    });

    button.addEventListener("pointerdown", () => {
      state.ts = 0.94;
      start();
    });

    button.addEventListener("pointerup", () => {
      state.ts = 1.06;
      start();
    });

    button.addEventListener("pointerleave", () => {
      state.tx = 0;
      state.ty = 0;
      state.ts = 1;
      start();
    });

    button.addEventListener("click", () => {
      navigate(button.dataset.action);
    });
  });
})();
