(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingRegisterFlow = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const REGISTER_FLOW_START_PATH = "/shopping/user/register/flow/start";
  const REGISTER_FLOW_CURRENT_PATH = "/shopping/user/register/flow/current";
  const DEFAULT_REGISTER_STEP_PATHS = {
    CREATE_ACCOUNT: "/shopping/user/create-account",
    CREATE_ACCOUNT_PASSWORD: "/shopping/user/create-account/password",
    EMAIL_VERIFICATION: "/shopping/user/email-verification",
    REGISTER_EMAIL_VERIFICATION: "/shopping/user/email-verification?mode=register",
    ADD_PHONE: "/shopping/user/add-phone",
    REGISTER_ADD_PHONE: "/shopping/user/add-phone?mode=register"
  };

  async function parseJsonSafely(response) {
    try {
      return await response.json();
    } catch (_) {
      return {};
    }
  }

  function resolveRegisterStepPaths(authRoutesApi) {
    const paths = authRoutesApi?.PATHS || DEFAULT_REGISTER_STEP_PATHS;
    return {
      ...paths,
      EMAIL_VERIFICATION: paths.REGISTER_EMAIL_VERIFICATION || paths.EMAIL_VERIFICATION,
      ADD_PHONE: paths.REGISTER_ADD_PHONE || paths.ADD_PHONE
    };
  }

  function resolveSameOriginRouteTarget(targetPath) {
    if (typeof window === "undefined" || !targetPath) {
      return null;
    }
    try {
      const targetUrl = new URL(targetPath, window.location.origin);
      if (targetUrl.origin !== window.location.origin) {
        return null;
      }
      return {
        href: `${targetUrl.pathname}${targetUrl.search}${targetUrl.hash}`,
        pathname: targetUrl.pathname
      };
    } catch (_) {
      return null;
    }
  }

  function showRouteNoticeAfterNavigation(routeApplyResult, pathname) {
    if (typeof window === "undefined") {
      return;
    }
    const routeNoticeApi = window.ShoppingLoginRouteNoticeApi;
    if (!routeNoticeApi) {
      return;
    }
    Promise.resolve(routeApplyResult).then(() => {
      const notice = routeNoticeApi.consumeRegisterRouteNotice?.() || "";
      if (!notice) {
        return;
      }
      const message = routeNoticeApi.getRegisterNoticeMessage?.(notice) || "";
      routeNoticeApi.showInlineRouteNotice?.(pathname, message);
    }).catch(() => {
    });
  }

  function navigateWithinAuthShell(targetPath, navigationOptions = {}) {
    if (typeof window === "undefined") {
      return false;
    }
    const shellApi = window.ShoppingAuthShellApi;
    if (!shellApi || typeof shellApi.applyRoute !== "function") {
      return false;
    }
    const target = resolveSameOriginRouteTarget(targetPath);
    if (!target) {
      return false;
    }
    const currentHref = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    if (currentHref !== target.href) {
      const shouldReplace = Boolean(navigationOptions.replace);
      if (shouldReplace) {
        window.history.replaceState({}, "", target.href);
      } else {
        window.history.pushState({}, "", target.href);
      }
    }
    const routeApplyResult = shellApi.applyRoute(target.href, { replaceLegacy: false });
    showRouteNoticeAfterNavigation(routeApplyResult, target.pathname);
    return true;
  }

  function createRegisterFlow(options = {}) {
    const preAuthClientApi = options.preAuthClientApi || null;
    const registerStepPaths = options.registerStepPaths || resolveRegisterStepPaths(options.authRoutesApi);
    const buildRegisterDeviceFingerprint = typeof options.buildRegisterDeviceFingerprint === "function"
      ? options.buildRegisterDeviceFingerprint
      : () => "";

    function getFetchWithPreAuth() {
      return preAuthClientApi && typeof preAuthClientApi.fetchWithPreAuth === "function"
        ? preAuthClientApi.fetchWithPreAuth
        : fetch;
    }

    async function fetchCurrentRegisterFlowState() {
      const response = await getFetchWithPreAuth()(REGISTER_FLOW_CURRENT_PATH, {
        method: "GET",
        headers: {
          "Accept": "application/json"
        }
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok) {
        if (typeof window !== "undefined" && payload?.redirectPath) {
          if (!navigateWithinAuthShell(payload.redirectPath, { replace: true })) {
            window.location.replace(payload.redirectPath);
          }
        }
        return null;
      }
      return payload;
    }

    async function startRegisterFlow(email) {
      const response = await getFetchWithPreAuth()(REGISTER_FLOW_START_PATH, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          email,
          deviceFingerprint: buildRegisterDeviceFingerprint()
        })
      });
      const payload = await parseJsonSafely(response);
      if (!response.ok || payload?.success === false) {
        return {
          success: false,
          message: payload?.message || "Register flow could not start."
        };
      }
      return {
        success: true,
        nextPath: payload?.nextPath || registerStepPaths.CREATE_ACCOUNT_PASSWORD
      };
    }

    function navigateToRegisterStep(view, fallbackPath, navigationOptions = {}) {
      if (typeof window === "undefined") {
        return;
      }
      const target = resolveSameOriginRouteTarget(fallbackPath);
      const currentHref = `${window.location.pathname}${window.location.search}${window.location.hash}`;
      if (!target || currentHref === target.href) {
        return;
      }
      if (navigateWithinAuthShell(fallbackPath, navigationOptions)) {
        return;
      }
      if (navigationOptions.replace) {
        window.location.replace(fallbackPath);
        return;
      }
      window.location.assign(fallbackPath);
    }

    return {
      REGISTER_FLOW_START_PATH,
      REGISTER_FLOW_CURRENT_PATH,
      REGISTER_STEP_PATHS: registerStepPaths,
      fetchCurrentRegisterFlowState,
      startRegisterFlow,
      navigateToRegisterStep
    };
  }

  return {
    REGISTER_FLOW_START_PATH,
    REGISTER_FLOW_CURRENT_PATH,
    DEFAULT_REGISTER_STEP_PATHS,
    parseJsonSafely,
    resolveRegisterStepPaths,
    navigateWithinAuthShell,
    createRegisterFlow
  };
});
