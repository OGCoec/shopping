(function (root) {
  const CONTROLLER_MODULE_PATH = "/js/admin/particle-transition/transition-controller.js";
  const PENDING_KEY = "shopping:admin:parti-morph:pending:v1";

  let controllerPromise = null;

  function loadController() {
    if (!controllerPromise) {
      controllerPromise = import(CONTROLLER_MODULE_PATH)
        .then((module) => module.createAdminParticleTransition(root))
        .catch(() => null);
    }
    return controllerPromise;
  }

  function callController(methodName, args) {
    return loadController().then((controller) => {
      if (!controller || typeof controller[methodName] !== "function") {
        return undefined;
      }
      return controller[methodName](...args);
    });
  }

  function markPendingEnterFallback() {
    try {
      sessionStorage.setItem(PENDING_KEY, "1");
    } catch (_) {
    }
    return callController("markPendingEnter", []);
  }

  function beginExit(options) {
    const settings = typeof options === "string" ? { to: options } : (options || {});
    return loadController().then((controller) => {
      if (controller && typeof controller.beginExit === "function") {
        return controller.beginExit(options);
      }
      markPendingEnterFallback();
      if (settings.to) {
        window.location.assign(settings.to);
      }
      return undefined;
    });
  }

  root.AdminParticleTransition = {
    beginExit,
    markPendingEnter: markPendingEnterFallback,
    playEnter: (targets) => callController("playEnter", [targets]),
    playExit: (source) => callController("playExit", [source]),
    prewarm: (source, options) => callController("prewarm", [source, options])
  };

  loadController();
})(window);
