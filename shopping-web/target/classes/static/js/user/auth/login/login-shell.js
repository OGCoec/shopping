(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory(require("../shared/auth-routes.js"));
    return;
  }
  root.ShoppingLoginShell = factory(root.ShoppingAuthRoutes);
})(typeof globalThis !== "undefined" ? globalThis : this, function (authRoutes) {
  const fallbackAuthRoutes = {
    mapPathToView(pathname) {
      if (pathname === "/shopping/user/register" || pathname === "/shopping/user/create-account") return "register";
      if (pathname === "/shopping/user/create-account/password") return "register-password";
      if (pathname === "/shopping/user/log-in/password") return "password";
      if (pathname === "/shopping/user/email-verification") return "otp";
      if (pathname === "/shopping/user/totp-verification") return "otp";
      if (pathname === "/shopping/user/add-phone") return "register-phone-required";
      if (pathname === "/shopping/user/session-ended") return "session-ended";
      if (pathname === "/shopping/user/forgot-password") return "forgot-password";
      if (pathname === "/shopping/user/reset-password-url") return "forgot-password";
      if (pathname === "/shopping/user/reset-password-code") return "forgot-password";
      return "email";
    },
    mapViewToPath(view) {
      if (view === "password") return "/shopping/user/log-in/password";
      if (view === "register") return "/shopping/user/create-account";
      if (view === "register-password") return "/shopping/user/create-account/password";
      if (view === "otp") return "/shopping/user/email-verification";
      if (view === "register-phone-required") return "/shopping/user/add-phone";
      if (view === "session-ended") return "/shopping/user/session-ended";
      if (view === "forgot-password") return "/shopping/user/forgot-password";
      return "/shopping/user/log-in";
    },
    canonicalPathForPathname(pathname) {
      return pathname || "/shopping/user/log-in";
    },
    shouldReplaceLegacyPath() {
      return false;
    }
  };

  const routes = authRoutes || fallbackAuthRoutes;

  function resolveRouteTarget(routeTarget) {
    const rawRouteTarget = typeof routeTarget === "string" ? routeTarget.trim() : "";
    const fallbackPathname = "/shopping/user/log-in";
    if (!rawRouteTarget) {
      return {
        pathname: fallbackPathname,
        href: fallbackPathname
      };
    }
    try {
      const url = new URL(rawRouteTarget, window.location.origin);
      const canonicalPathname = routes.canonicalPathForPathname(url.pathname);
      return {
        pathname: canonicalPathname,
        href: `${canonicalPathname}${url.search}${url.hash}`
      };
    } catch (_) {
      const pathname = rawRouteTarget.split(/[?#]/)[0] || fallbackPathname;
      const suffix = rawRouteTarget.slice(pathname.length);
      const canonicalPathname = routes.canonicalPathForPathname(pathname);
      return {
        pathname: canonicalPathname,
        href: `${canonicalPathname}${suffix}`
      };
    }
  }

  function appendFragmentNodes(container, fragmentHtml) {
    if (!container) return;

    const parser = new DOMParser();
    const doc = parser.parseFromString(fragmentHtml, "text/html");
    container.replaceChildren(...doc.body.childNodes);
  }

  function createLoginShell(options) {
    const {
      emailLoginView,
      phoneLoginView,
      passwordLoginView,
      otpLoginView,
      registerView,
      registerPasswordView,
      registerPhoneRequiredView,
      sessionEndedView,
      forgotPasswordView,
      signupLinkWrap,
      formContainer,
      routeFragments,
      initializeRouteFragment
    } = options || {};

    let authView = "email";

    async function ensureRouteFragmentLoaded(view) {
      const fragment = routeFragments?.[view];
      if (!fragment?.container) return;
      if (fragment.loaded) return;

      if (!fragment.loadingTask) {
        fragment.loadingTask = fetch(fragment.path, { method: "GET", cache: "no-store" })
          .then((response) => {
            if (!response.ok) {
              throw new Error(`片段加载失败: ${fragment.path}`);
            }
            return response.text();
          })
          .then((html) => {
            appendFragmentNodes(fragment.container, html);
            bindSpaRouteLinks();
            if (typeof initializeRouteFragment === "function") {
              initializeRouteFragment(view);
            }
            fragment.loaded = true;
          })
          .catch((error) => {
            fragment.loadingTask = null;
            throw error;
          });
      }

      await fragment.loadingTask;
    }

    async function setAuthView(view) {
      authView = view;

      if (!emailLoginView || !phoneLoginView || !passwordLoginView || !otpLoginView || !registerView || !registerPasswordView || !registerPhoneRequiredView || !sessionEndedView || !forgotPasswordView) {
        return;
      }

      const isEmail = view === "email";
      const isPhone = view === "phone";
      const isPassword = view === "password";
      const isOtp = view === "otp";
      const isRegister = view === "register";
      const isRegisterPassword = view === "register-password";
      const isRegisterPhoneRequired = view === "register-phone-required";
      const isSessionEnded = view === "session-ended";
      const isForgotPassword = view === "forgot-password";

      if (isRegister || isRegisterPassword || isForgotPassword) {
        await ensureRouteFragmentLoaded(view);
      }

      emailLoginView.classList.toggle("is-hidden", !isEmail);
      emailLoginView.classList.toggle("is-active", isEmail);

      phoneLoginView.classList.toggle("is-hidden", !isPhone);
      phoneLoginView.classList.toggle("is-active", isPhone);

      passwordLoginView.classList.toggle("is-hidden", !isPassword);
      passwordLoginView.classList.toggle("is-active", isPassword);

      otpLoginView.classList.toggle("is-hidden", !isOtp);
      otpLoginView.classList.toggle("is-active", isOtp);

      registerView.classList.toggle("is-hidden", !isRegister);
      registerView.classList.toggle("is-active", isRegister);

      registerPasswordView.classList.toggle("is-hidden", !isRegisterPassword);
      registerPasswordView.classList.toggle("is-active", isRegisterPassword);

      registerPhoneRequiredView.classList.toggle("is-hidden", !isRegisterPhoneRequired);
      registerPhoneRequiredView.classList.toggle("is-active", isRegisterPhoneRequired);

      sessionEndedView.classList.toggle("is-hidden", !isSessionEnded);
      sessionEndedView.classList.toggle("is-active", isSessionEnded);

      forgotPasswordView.classList.toggle("is-hidden", !isForgotPassword);
      forgotPasswordView.classList.toggle("is-active", isForgotPassword);

      if (signupLinkWrap) {
        signupLinkWrap.style.display = (isEmail || isPhone || isPassword || isOtp) ? "block" : "none";
      }

      if (formContainer) {
        formContainer.classList.toggle("password-step-active", isPassword || isRegister || isRegisterPassword || isRegisterPhoneRequired || isForgotPassword);
        formContainer.classList.toggle("otp-step-active", isOtp);
        formContainer.classList.toggle("terminal-step-active", isSessionEnded);
      }
    }

    function mapPathToView(pathname) {
      return routes.mapPathToView(pathname);
    }

    function applyRoute(pathname, options = {}) {
      const target = resolveRouteTarget(pathname);
      const canonicalPathname = target.pathname;
      if (options.replaceLegacy !== false
          && routes.shouldReplaceLegacyPath(pathname)
          && window.location.pathname !== canonicalPathname) {
        window.history.replaceState({}, "", target.href);
      }
      const view = mapPathToView(canonicalPathname);
      return setAuthView(view);
    }

    function navigateTo(pathname, options = {}) {
      const target = resolveRouteTarget(pathname);
      const shouldReplace = Boolean(options.replace);
      const currentHref = `${window.location.pathname}${window.location.search}${window.location.hash}`;
      if (currentHref !== target.href) {
        if (shouldReplace) {
          window.history.replaceState({}, "", target.href);
        } else {
          window.history.pushState({}, "", target.href);
        }
      }
      return applyRoute(target.href, { replaceLegacy: false });
    }

    function navigateToView(view, options = {}) {
      return navigateTo(routes.mapViewToPath(view), options);
    }

    function bindSpaRouteLinks() {
      document.querySelectorAll("a[data-spa-route]").forEach((anchor) => {
        anchor.addEventListener("click", (event) => {
          event.preventDefault();
          const href = anchor.getAttribute("href");
          if (!href) return;
          navigateTo(href);
        });
      });
    }

    return {
      appendFragmentNodes,
      ensureRouteFragmentLoaded,
      setAuthView,
      mapPathToView,
      applyRoute,
      navigateTo,
      navigateToView,
      bindSpaRouteLinks,
      getAuthView() {
        return authView;
      }
    };
  }

  return {
    appendFragmentNodes,
    createLoginShell
  };
});
