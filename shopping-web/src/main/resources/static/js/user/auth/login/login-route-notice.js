(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingLoginRouteNotice = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const REGISTER_NOTICE_QUERY_PARAM = "register_notice";
  const DEFAULT_AUTH_PATHS = {
    LOGIN: "/shopping/user/log-in",
    CREATE_ACCOUNT: "/shopping/user/create-account",
    CREATE_ACCOUNT_PASSWORD: "/shopping/user/create-account/password",
    EMAIL_VERIFICATION: "/shopping/user/email-verification",
    ADD_PHONE: "/shopping/user/add-phone",
    SESSION_ENDED: "/shopping/user/session-ended"
  };
  const REGISTER_NOTICE_MESSAGES = {
    "flow-expired": "\u6ce8\u518c\u4f1a\u8bdd\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u5f00\u59cb\u3002",
    "step-restored": "\u5df2\u6062\u590d\u5230\u5f53\u524d\u6ce8\u518c\u6b65\u9aa4\u3002",
    "register-completed": "\u8be5\u8d26\u53f7\u5df2\u5b8c\u6210\u6ce8\u518c\uff0c\u8bf7\u76f4\u63a5\u767b\u5f55\u3002"
  };

  function createLoginRouteNotice(options = {}) {
    const authRoutesApi = options.authRoutesApi || null;
    const authPaths = options.authPaths || DEFAULT_AUTH_PATHS;
    const triggerLoginError = typeof options.triggerLoginError === "function"
      ? options.triggerLoginError
      : () => {};

    function consumeRegisterRouteNotice() {
      if (typeof window === "undefined") {
        return "";
      }
      try {
        const current = new URL(window.location.href);
        const notice = current.searchParams.get(REGISTER_NOTICE_QUERY_PARAM) || "";
        if (!notice) {
          return "";
        }
        current.searchParams.delete(REGISTER_NOTICE_QUERY_PARAM);
        window.history.replaceState({}, "", `${current.pathname}${current.search}${current.hash}`);
        return notice;
      } catch (_) {
        return "";
      }
    }

    function getRegisterNoticeMessage(notice) {
      return REGISTER_NOTICE_MESSAGES[notice] || "";
    }

    function showInlineRouteNotice(pathname, message) {
      if (!message) {
        return;
      }
      const normalizedPath = typeof pathname === "string" ? pathname : "";
      const path = authRoutesApi?.canonicalPathForPathname?.(normalizedPath) || normalizedPath;
      if (path === authPaths.SESSION_ENDED) {
        return;
      }
      const targetId = path === authPaths.CREATE_ACCOUNT
        ? "register-entry-error-msg"
        : path === authPaths.CREATE_ACCOUNT_PASSWORD
          ? "register-error-msg"
          : path === authPaths.EMAIL_VERIFICATION
            ? "otp-error-msg"
            : path === authPaths.ADD_PHONE
              ? "register-phone-required-error-msg"
              : "error-msg";
      const target = document.getElementById(targetId);
      if (!target) {
        return;
      }
      target.textContent = message;
      target.style.display = "block";
      triggerLoginError();
    }

    return {
      REGISTER_NOTICE_QUERY_PARAM,
      REGISTER_NOTICE_MESSAGES,
      consumeRegisterRouteNotice,
      getRegisterNoticeMessage,
      showInlineRouteNotice
    };
  }

  return {
    REGISTER_NOTICE_QUERY_PARAM,
    REGISTER_NOTICE_MESSAGES,
    createLoginRouteNotice
  };
});
