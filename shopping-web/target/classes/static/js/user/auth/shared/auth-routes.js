(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingAuthRoutes = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const PATHS = {
    LOGIN: "/shopping/user/log-in",
    LOGIN_PASSWORD: "/shopping/user/log-in/password",
    LEGACY_LOGIN: "/shopping/user/login",
    CREATE_ACCOUNT: "/shopping/user/create-account",
    CREATE_ACCOUNT_PASSWORD: "/shopping/user/create-account/password",
    LEGACY_REGISTER: "/shopping/user/register",
    EMAIL_VERIFICATION: "/shopping/user/email-verification",
    TOTP_VERIFICATION: "/shopping/user/totp-verification",
    ADD_PHONE: "/shopping/user/add-phone",
    REGISTER_EMAIL_VERIFICATION: "/shopping/user/email-verification?mode=register",
    LOGIN_EMAIL_VERIFICATION: "/shopping/user/email-verification?mode=login",
    LOGIN_TOTP_VERIFICATION: "/shopping/user/totp-verification?mode=login",
    REGISTER_ADD_PHONE: "/shopping/user/add-phone?mode=register",
    LOGIN_ADD_PHONE: "/shopping/user/add-phone?mode=login",
    SESSION_ENDED: "/shopping/user/session-ended",
    FORGOT_PASSWORD: "/shopping/user/forgot-password",
    RESET_PASSWORD_URL: "/shopping/user/reset-password-url",
    RESET_PASSWORD_CODE: "/shopping/user/reset-password-code"
  };

  const VIEW_BY_PATH = {
    [PATHS.LOGIN]: "email",
    [PATHS.LOGIN_PASSWORD]: "password",
    [PATHS.LEGACY_LOGIN]: "email",
    [PATHS.CREATE_ACCOUNT]: "register",
    [PATHS.CREATE_ACCOUNT_PASSWORD]: "register-password",
    [PATHS.LEGACY_REGISTER]: "register",
    [PATHS.EMAIL_VERIFICATION]: "otp",
    [PATHS.TOTP_VERIFICATION]: "otp",
    [PATHS.ADD_PHONE]: "register-phone-required",
    [PATHS.SESSION_ENDED]: "session-ended",
    [PATHS.FORGOT_PASSWORD]: "forgot-password",
    [PATHS.RESET_PASSWORD_URL]: "forgot-password",
    [PATHS.RESET_PASSWORD_CODE]: "forgot-password"
  };

  const PATH_BY_VIEW = {
    email: PATHS.LOGIN,
    password: PATHS.LOGIN_PASSWORD,
    register: PATHS.CREATE_ACCOUNT,
    "register-password": PATHS.CREATE_ACCOUNT_PASSWORD,
    otp: PATHS.EMAIL_VERIFICATION,
    "register-phone-required": PATHS.ADD_PHONE,
    "session-ended": PATHS.SESSION_ENDED,
    "forgot-password": PATHS.FORGOT_PASSWORD
  };

  const LEGACY_CANONICAL_PATHS = {
    [PATHS.LEGACY_LOGIN]: PATHS.LOGIN,
    [PATHS.LEGACY_REGISTER]: PATHS.CREATE_ACCOUNT
  };

  function extractPathname(routeTarget) {
    const rawRouteTarget = typeof routeTarget === "string" ? routeTarget.trim() : "";
    if (!rawRouteTarget) {
      return "";
    }
    try {
      const url = new URL(rawRouteTarget, "https://shopping.local");
      return url.pathname;
    } catch (_) {
      return rawRouteTarget.split(/[?#]/)[0] || "";
    }
  }

  function normalizePathname(pathname) {
    const rawPathname = extractPathname(pathname);
    if (!rawPathname || rawPathname === "/") {
      return PATHS.LOGIN;
    }
    return rawPathname.length > 1 ? rawPathname.replace(/\/+$/g, "") : rawPathname;
  }

  function mapPathToView(pathname) {
    return VIEW_BY_PATH[normalizePathname(pathname)] || "email";
  }

  function mapViewToPath(view) {
    return PATH_BY_VIEW[view] || PATHS.LOGIN;
  }

  function canonicalPathForPathname(pathname) {
    const normalizedPathname = normalizePathname(pathname);
    return LEGACY_CANONICAL_PATHS[normalizedPathname] || normalizedPathname;
  }

  function shouldReplaceLegacyPath(pathname) {
    return Boolean(LEGACY_CANONICAL_PATHS[normalizePathname(pathname)]);
  }

  function isPathForView(pathname, view) {
    return mapPathToView(pathname) === view;
  }

  function withMode(pathname, mode) {
    const normalizedMode = typeof mode === "string" ? mode.trim() : "";
    const normalizedPathname = normalizePathname(pathname);
    if (!normalizedMode) {
      return normalizedPathname;
    }
    return `${normalizedPathname}?mode=${encodeURIComponent(normalizedMode)}`;
  }

  function modeForRoute(routeTarget) {
    const rawRouteTarget = typeof routeTarget === "string" ? routeTarget.trim() : "";
    if (!rawRouteTarget) {
      return "";
    }
    try {
      const url = new URL(rawRouteTarget, "https://shopping.local");
      return url.searchParams.get("mode") || "";
    } catch (_) {
      const query = rawRouteTarget.split("?")[1] || "";
      return new URLSearchParams(query.split("#")[0] || "").get("mode") || "";
    }
  }

  return {
    PATHS,
    extractPathname,
    normalizePathname,
    mapPathToView,
    mapViewToPath,
    canonicalPathForPathname,
    shouldReplaceLegacyPath,
    isPathForView,
    withMode,
    modeForRoute
  };
});
