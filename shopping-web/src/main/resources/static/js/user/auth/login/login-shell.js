(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
    return;
  }
  root.ShoppingLoginShell = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
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
      registerPhoneRequiredView,
      forgotPasswordView,
      signupLinkWrap,
      formContainer,
      routeFragments,
      initializeRegisterFragment
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
            if (view === "register" && typeof initializeRegisterFragment === "function") {
              initializeRegisterFragment();
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

      if (!emailLoginView || !phoneLoginView || !passwordLoginView || !otpLoginView || !registerView || !registerPhoneRequiredView || !forgotPasswordView) {
        return;
      }

      const isEmail = view === "email";
      const isPhone = view === "phone";
      const isPassword = view === "password";
      const isOtp = view === "otp";
      const isRegister = view === "register";
      const isRegisterPhoneRequired = view === "register-phone-required";
      const isForgotPassword = view === "forgot-password";

      if (isRegister || isForgotPassword) {
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

      registerPhoneRequiredView.classList.toggle("is-hidden", !isRegisterPhoneRequired);
      registerPhoneRequiredView.classList.toggle("is-active", isRegisterPhoneRequired);

      forgotPasswordView.classList.toggle("is-hidden", !isForgotPassword);
      forgotPasswordView.classList.toggle("is-active", isForgotPassword);

      if (signupLinkWrap) {
        signupLinkWrap.style.display = (isEmail || isPhone || isPassword || isOtp) ? "block" : "none";
      }

      if (formContainer) {
        formContainer.classList.toggle("password-step-active", isPassword || isRegister || isRegisterPhoneRequired || isForgotPassword);
        formContainer.classList.toggle("otp-step-active", isOtp);
      }
    }

    function mapPathToView(pathname) {
      if (pathname === "/shopping/user/register") return "register";
      if (pathname === "/shopping/user/forgot-password") return "forgot-password";
      return "email";
    }

    function applyRoute(pathname) {
      const view = mapPathToView(pathname);
      return setAuthView(view);
    }

    function navigateTo(pathname) {
      if (window.location.pathname !== pathname) {
        window.history.pushState({}, "", pathname);
      }
      return applyRoute(pathname);
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
