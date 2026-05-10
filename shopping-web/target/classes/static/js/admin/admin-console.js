(function () {
  const api = window.AdminApi;
  const transition = window.AdminParticleTransition;
  const accountNode = document.getElementById("admin-console-account");
  const emailNode = document.getElementById("admin-console-email");
  const phoneNode = document.getElementById("admin-console-phone");
  const logoutButton = document.getElementById("admin-console-logout");
  const eyebrowNode = document.getElementById("admin-section-eyebrow");
  const titleNode = document.getElementById("admin-section-title");
  const copyNode = document.getElementById("admin-section-copy");
  const detailDrawer = document.querySelector(".admin-detail-drawer");
  const detailTitle = document.getElementById("admin-detail-title");
  const detailBody = document.getElementById("admin-detail-body");
  const detailClose = document.querySelector(".admin-detail-close");
  const transitionSource = document.querySelector(".admin-split-console") || document.querySelector(".admin-main");
  window.__ADMIN_CONSOLE_JS_VERSION__ = "ip2location-mail-tool-v24";

  const sectionMeta = {
    overview: {
      eyebrow: "Dashboard",
      title: "管理概览",
      copy: "查看管理员身份、登录状态和核心管理入口。"
    },
    externalInterfaces: {
      eyebrow: "External APIs",
      title: "外部接口管理",
      copy: "管理第三方登录、邮件、验证码、短信、OSS 和 Risk API。"
    },
    oauth2: {
      eyebrow: "OAuth2",
      title: "第三方登录 OAuth2",
      copy: "选择 GitHub、Google、Microsoft OAuth2 登录服务。"
    },
    oauth2Github: {
      eyebrow: "OAuth2 / GitHub",
      title: "GitHub OAuth2 service",
      copy: "管理 GitHub OAuth2 第三方登录服务。"
    },
    oauth2Google: {
      eyebrow: "OAuth2 / Google",
      title: "Google OAuth2 service",
      copy: "管理 Google OAuth2 第三方登录服务。"
    },
    oauth2Microsoft: {
      eyebrow: "OAuth2 / Microsoft",
      title: "Microsoft OAuth2 service",
      copy: "管理 Microsoft OAuth2 第三方登录服务。"
    },
    smtp: {
      eyebrow: "SMTP",
      title: "邮件服务 SMTP",
      copy: "选择当前邮件发送使用的 SMTP 服务商。"
    },
    smtpQq: {
      eyebrow: "SMTP / QQ",
      title: "QQ 邮箱 SMTP",
      copy: "查看 QQ 邮箱 SMTP 邮件发送配置。"
    },
    captcha: {
      eyebrow: "Captcha",
      title: "第三方验证码",
      copy: "查看 Cloudflare Turnstile、Google reCAPTCHA、hCaptcha 等当前验证码类型。"
    },
    captchaTurnstile: {
      eyebrow: "Captcha / Cloudflare",
      title: "Cloudflare Turnstile service",
      copy: "管理 Cloudflare Turnstile 验证码的 siteKey 和 secretKey。"
    },
    captchaRecaptcha: {
      eyebrow: "Captcha / Google",
      title: "Google reCAPTCHA service",
      copy: "管理 Google reCAPTCHA 验证码的 siteKey 和 secretKey。"
    },
    captchaHcaptcha: {
      eyebrow: "Captcha / hCaptcha",
      title: "hCaptcha service",
      copy: "管理 hCaptcha 验证码的 siteKey 和 secretKey。"
    },
    sms: {
      eyebrow: "SMS",
      title: "短信服务",
      copy: "选择当前项目调用的短信服务。"
    },
    smsAliyun: {
      eyebrow: "SMS / Aliyun",
      title: "阿里云 Dypnsapi 短信服务",
      copy: "管理阿里云 Dypnsapi 短信服务的 Windows 系统环境变量。"
    },
    oss: {
      eyebrow: "OSS",
      title: "对象存储服务",
      copy: "选择当前项目调用的对象存储服务。"
    },
    ossAliyun: {
      eyebrow: "OSS / Aliyun",
      title: "阿里云 OSS 对象存储服务",
      copy: "管理阿里云 OSS 对象存储服务的 Windows 系统环境变量。"
    },
    ipRisk: {
      eyebrow: "Risk API",
      title: "Risk API",
      copy: "选择当前项目使用的 IP2Location 和 iPing 降级 API。"
    },
    riskApiIp2Location: {
      eyebrow: "Risk API / IP2Location",
      title: "IP2Location API",
      copy: "管理 Redis 中的 IP2Location API Keys，API URL 配置按需展开。"
    },
    riskApiIping: {
      eyebrow: "Risk API / iPing",
      title: "iPing 降级 API",
      copy: "管理 iPing 降级 API 的 Windows 系统环境变量。"
    }
  };
  const defaultSection = "overview";
  const consoleBasePath = "/shopping/admin/console";
  const sectionRouteMap = {
    overview: "overview",
    externalInterfaces: "external-interfaces",
    oauth2: "oauth2",
    oauth2Github: "oauth2/github",
    oauth2Google: "oauth2/google",
    oauth2Microsoft: "oauth2/microsoft",
    smtp: "smtp",
    smtpQq: "smtp/qq",
    captcha: "captcha",
    captchaTurnstile: "captcha/turnstile",
    captchaRecaptcha: "captcha/recaptcha",
    captchaHcaptcha: "captcha/hcaptcha",
    sms: "sms",
    smsAliyun: "sms/aliyun",
    oss: "oss",
    ossAliyun: "oss/aliyun",
    ipRisk: "ip-risk",
    riskApiIp2Location: "ip-risk/ip2location",
    riskApiIping: "ip-risk/iping"
  };
  const routeSectionMap = {
    overview: "overview",
    "external-interfaces": "externalInterfaces",
    oauth2: "oauth2",
    "oauth2/github": "oauth2Github",
    "oauth2/google": "oauth2Google",
    "oauth2/microsoft": "oauth2Microsoft",
    smtp: "smtp",
    "smtp/qq": "smtpQq",
    captcha: "captcha",
    "captcha/turnstile": "captchaTurnstile",
    "captcha/recaptcha": "captchaRecaptcha",
    "captcha/hcaptcha": "captchaHcaptcha",
    sms: "sms",
    "sms/aliyun": "smsAliyun",
    oss: "oss",
    "oss/aliyun": "ossAliyun",
    "ip-risk": "ipRisk",
    "ip-risk/ip2location": "riskApiIp2Location",
    "ip-risk/iping": "riskApiIping"
  };
  const smtpSectionProviderMap = {
    smtpQq: "qq"
  };

  function setText(node, value) {
    if (node) {
      node.textContent = value;
    }
  }

  function playPress(element) {
    if (!element || !element.animate || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      return;
    }
    element.animate(
      [
        { transform: "scale(1)" },
        { transform: "scale(0.972) translateY(1px)" },
        { transform: "scale(1.018) translateY(-2px)" },
        { transform: "scale(1)" }
      ],
      {
        duration: 430,
        easing: "cubic-bezier(0.18, 1.55, 0.34, 1)"
      }
    );
  }

  function getSectionFromLocation() {
    const normalizedPath = window.location.pathname.replace(/\/+$/, "");
    if (normalizedPath === consoleBasePath) {
      return defaultSection;
    }
    if (normalizedPath.startsWith(consoleBasePath + "/")) {
      const routeValue = decodeURIComponent(normalizedPath.slice(consoleBasePath.length + 1));
      return routeSectionMap[routeValue] || "";
    }
    const params = new URLSearchParams(window.location.search);
    const routeValue = params.get("section");
    return routeSectionMap[routeValue] || defaultSection;
  }

  function updateSectionUrl(sectionName, replace) {
    if (!window.history?.pushState) {
      return;
    }
    const routeValue = sectionRouteMap[sectionName];
    if (!routeValue) {
      return;
    }
    const url = new URL(window.location.href);
    url.pathname = `${consoleBasePath}/${routeValue}`;
    url.searchParams.delete("section");

    const nextUrl = url.pathname + url.search + url.hash;
    const currentUrl = window.location.pathname + window.location.search + window.location.hash;
    if (nextUrl === currentUrl) {
      return;
    }

    const method = replace ? "replaceState" : "pushState";
    window.history[method]({ adminSection: sectionName }, "", nextUrl);
  }

  function switchSection(sectionName, options = {}) {
    if (!sectionMeta[sectionName]) {
      console.warn("Unknown admin console section:", sectionName);
      return false;
    }
    const resolvedSection = sectionName;
    const navSection = sectionMeta[resolvedSection].navTarget || (
      resolvedSection === "overview" ? "overview" : "externalInterfaces"
    );

    document.querySelectorAll(".admin-side-item").forEach((item) => {
      item.classList.toggle("is-active", item.dataset.sectionTarget === navSection);
    });
    document.querySelectorAll(".admin-panel").forEach((panel) => {
      panel.classList.toggle("is-active", panel.dataset.adminPanel === resolvedSection);
    });

    const meta = sectionMeta[resolvedSection];
    setText(eyebrowNode, meta.eyebrow);
    setText(titleNode, meta.title);
    setText(copyNode, meta.copy);
    document.title = `${meta.title} - Shopping System`;
    closeDetail();
    if (options.updateUrl !== false) {
      updateSectionUrl(resolvedSection, Boolean(options.replaceUrl));
    }
    if (resolvedSection === "smtp") {
      loadSmtpProviders();
    }
    if (smtpSectionProviderMap[resolvedSection]) {
      loadSmtpConfig(smtpSectionProviderMap[resolvedSection]);
    }
    if (resolvedSection === "riskApiIp2Location") {
      setIp2LocationConfigCardVisible(false);
      loadIp2LocationQuotaKeys();
    }
    if (resolvedSection === "riskApiIping") {
      loadRiskApiConfig("iping", "iPing 降级 API");
    }
    return true;
  }

  function openDetail(card) {
    if (!card || !detailDrawer) {
      return;
    }
    openDetailContent(
      card.dataset.detailTitle || "模块详情",
      card.dataset.detailBody || "当前模块仅做前端交互展示。"
    );
  }

  function openDetailContent(title, body) {
    if (!detailDrawer) {
      return;
    }
    setText(detailTitle, title);
    setText(detailBody, body);
    detailDrawer.classList.add("is-open");
    detailDrawer.setAttribute("aria-hidden", "false");
  }

  function closeDetail() {
    if (!detailDrawer) {
      return;
    }
    detailDrawer.classList.remove("is-open");
    detailDrawer.setAttribute("aria-hidden", "true");
  }

  function getOAuthConfigNodes(provider) {
    return {
      button: document.getElementById(`admin-${provider}-oauth-config-load`),
      status: document.getElementById(`admin-${provider}-oauth-config-status`),
      fields: document.getElementById(`admin-${provider}-oauth-config-fields`),
      form: document.getElementById(`admin-${provider}-oauth-config-form`),
      clientIdValue: document.getElementById(`admin-${provider}-client-id-value`),
      clientIdMeta: document.getElementById(`admin-${provider}-client-id-meta`),
      clientIdInput: document.getElementById(`admin-${provider}-client-id-input`),
      clientSecretValue: document.getElementById(`admin-${provider}-client-secret-value`),
      clientSecretMeta: document.getElementById(`admin-${provider}-client-secret-meta`),
      clientSecretInput: document.getElementById(`admin-${provider}-client-secret-input`),
      saveButton: document.getElementById(`admin-${provider}-oauth-config-save`)
    };
  }

  function setOAuthConfigStatus(nodes, message, type = "") {
    setText(nodes.status, message);
    nodes.status?.classList.toggle("is-error", type === "error");
    nodes.status?.classList.toggle("is-ok", type === "ok");
  }

  function formatOAuthMeta(field) {
    if (!field) {
      return "-";
    }
    const yamlLine = field.yamlLine || "-";
    const yamlFile = field.yamlFile || "shopping-web/src/main/resources/application.yaml";
    const envName = field.envName || "-";
    const propertyKey = field.propertyKey || "-";
    const windowsEnvTarget = field.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
    return `YAML: ${yamlFile}:${yamlLine} · ENV: ${envName} · TARGET: ${windowsEnvTarget} · KEY: ${propertyKey}`;
  }

  function renderOAuthField(valueNode, metaNode, field) {
    setText(valueNode, field?.maskedValue || "未配置");
    setText(metaNode, formatOAuthMeta(field));
  }

  async function loadOAuthConfig(provider, label) {
    const nodes = getOAuthConfigNodes(provider);
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    setOAuthConfigStatus(nodes, `正在读取 ${label} OAuth2 配置...`);
    try {
      const response = await api.get(`/shopping/admin/api/oauth2/${provider}/config`);
      const data = response.data || {};
      renderOAuthField(nodes.clientIdValue, nodes.clientIdMeta, data.clientId);
      renderOAuthField(nodes.clientSecretValue, nodes.clientSecretMeta, data.clientSecret);
      nodes.fields?.removeAttribute("hidden");
      nodes.form?.removeAttribute("hidden");
      setOAuthConfigStatus(nodes, "已读取脱敏 clientId 和 clientSecret。", "ok");
    } catch (error) {
      setOAuthConfigStatus(nodes, error.message || `读取 ${label} OAuth2 配置失败。`, "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  function clearOAuthConfigInputs(nodes) {
    if (nodes.clientIdInput) {
      nodes.clientIdInput.value = "";
    }
    if (nodes.clientSecretInput) {
      nodes.clientSecretInput.value = "";
    }
  }

  async function saveOAuthConfig(provider, label) {
    const nodes = getOAuthConfigNodes(provider);
    const clientId = nodes.clientIdInput?.value || "";
    const clientSecret = nodes.clientSecretInput?.value || "";
    if (!clientId.trim() && !clientSecret.trim()) {
      setOAuthConfigStatus(nodes, "请至少填写一个需要修改的配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    setOAuthConfigStatus(nodes, `正在保存 ${label} OAuth2 环境变量...`);
    try {
      const response = await api.request(`/shopping/admin/api/oauth2/${provider}/config`, {
        clientId,
        clientSecret
      });
      const data = response.data || {};
      renderOAuthField(nodes.clientIdValue, nodes.clientIdMeta, data.clientId);
      renderOAuthField(nodes.clientSecretValue, nodes.clientSecretMeta, data.clientSecret);
      nodes.fields?.removeAttribute("hidden");
      nodes.form?.removeAttribute("hidden");
      clearOAuthConfigInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      setOAuthConfigStatus(nodes, `已保存到 ${target}，重启应用后 OAuth2 登录客户端生效。`, "ok");
    } catch (error) {
      setOAuthConfigStatus(nodes, error.message || `保存 ${label} OAuth2 配置失败。`, "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function bindOAuthConfig(provider, label) {
    const nodes = getOAuthConfigNodes(provider);
    nodes.button?.addEventListener("click", () => {
      playPress(nodes.button);
      loadOAuthConfig(provider, label);
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      playPress(nodes.saveButton);
      saveOAuthConfig(provider, label);
    });
  }

  function getCaptchaConfigNodes(provider) {
    return {
      button: document.getElementById(`admin-captcha-${provider}-config-load`),
      status: document.getElementById(`admin-captcha-${provider}-config-status`),
      fields: document.getElementById(`admin-captcha-${provider}-config-fields`),
      form: document.getElementById(`admin-captcha-${provider}-config-form`),
      siteKeyValue: document.getElementById(`admin-captcha-${provider}-site-key-value`),
      siteKeyMeta: document.getElementById(`admin-captcha-${provider}-site-key-meta`),
      siteKeyInput: document.getElementById(`admin-captcha-${provider}-site-key-input`),
      secretKeyValue: document.getElementById(`admin-captcha-${provider}-secret-key-value`),
      secretKeyMeta: document.getElementById(`admin-captcha-${provider}-secret-key-meta`),
      secretKeyInput: document.getElementById(`admin-captcha-${provider}-secret-key-input`),
      saveButton: document.getElementById(`admin-captcha-${provider}-config-save`)
    };
  }

  function setCaptchaConfigStatus(nodes, message, type = "") {
    setText(nodes.status, message);
    nodes.status?.classList.toggle("is-error", type === "error");
    nodes.status?.classList.toggle("is-ok", type === "ok");
  }

  function formatCaptchaMeta(field) {
    if (!field) {
      return "-";
    }
    const yamlLine = field.yamlLine || "-";
    const yamlFile = field.yamlFile || "shopping-web/src/main/resources/application.yaml";
    const envName = field.envName || "-";
    const propertyKey = field.propertyKey || "-";
    const windowsEnvTarget = field.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
    return `YAML: ${yamlFile}:${yamlLine} · ENV: ${envName} · TARGET: ${windowsEnvTarget} · KEY: ${propertyKey} · VALUE: 脱敏显示`;
  }

  function renderCaptchaField(valueNode, metaNode, field) {
    setText(valueNode, field?.maskedValue || "未配置");
    setText(metaNode, formatCaptchaMeta(field));
  }

  function renderCaptchaConfig(nodes, data) {
    renderCaptchaField(nodes.siteKeyValue, nodes.siteKeyMeta, data.siteKey);
    renderCaptchaField(nodes.secretKeyValue, nodes.secretKeyMeta, data.secretKey);
    nodes.fields?.removeAttribute("hidden");
    nodes.form?.removeAttribute("hidden");
  }

  async function loadCaptchaConfig(provider, label) {
    const nodes = getCaptchaConfigNodes(provider);
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    setCaptchaConfigStatus(nodes, `正在读取 ${label} 验证码配置...`);
    try {
      const response = await api.get(`/shopping/admin/api/captcha/${provider}/config`);
      renderCaptchaConfig(nodes, response.data || {});
      setCaptchaConfigStatus(nodes, "已读取脱敏 siteKey 和 secretKey。", "ok");
    } catch (error) {
      setCaptchaConfigStatus(nodes, error.message || `读取 ${label} 验证码配置失败。`, "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  function clearCaptchaConfigInputs(nodes) {
    if (nodes.siteKeyInput) {
      nodes.siteKeyInput.value = "";
    }
    if (nodes.secretKeyInput) {
      nodes.secretKeyInput.value = "";
    }
  }

  async function saveCaptchaConfig(provider, label) {
    const nodes = getCaptchaConfigNodes(provider);
    const siteKey = nodes.siteKeyInput?.value || "";
    const secretKey = nodes.secretKeyInput?.value || "";
    if (!siteKey.trim() && !secretKey.trim()) {
      setCaptchaConfigStatus(nodes, "请至少填写一个需要修改的验证码配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    setCaptchaConfigStatus(nodes, `正在保存 ${label} 验证码环境变量...`);
    try {
      const response = await api.request(`/shopping/admin/api/captcha/${provider}/config`, {
        siteKey,
        secretKey
      });
      const data = response.data || {};
      renderCaptchaConfig(nodes, data);
      clearCaptchaConfigInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      setCaptchaConfigStatus(nodes, `已保存到 ${target}，重启应用后验证码服务生效。`, "ok");
    } catch (error) {
      setCaptchaConfigStatus(nodes, error.message || `保存 ${label} 验证码配置失败。`, "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function bindCaptchaConfig(provider, label) {
    const nodes = getCaptchaConfigNodes(provider);
    nodes.button?.addEventListener("click", () => {
      playPress(nodes.button);
      loadCaptchaConfig(provider, label);
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      playPress(nodes.saveButton);
      saveCaptchaConfig(provider, label);
    });
  }

  function getAliyunSmsConfigNodes() {
    return {
      button: document.getElementById("admin-aliyun-sms-config-load"),
      status: document.getElementById("admin-aliyun-sms-config-status"),
      fields: document.getElementById("admin-aliyun-sms-config-fields"),
      form: document.getElementById("admin-aliyun-sms-config-form"),
      accessKeyIdValue: document.getElementById("admin-aliyun-sms-access-key-id-value"),
      accessKeyIdMeta: document.getElementById("admin-aliyun-sms-access-key-id-meta"),
      accessKeyIdInput: document.getElementById("admin-aliyun-sms-access-key-id-input"),
      accessKeySecretValue: document.getElementById("admin-aliyun-sms-access-key-secret-value"),
      accessKeySecretMeta: document.getElementById("admin-aliyun-sms-access-key-secret-meta"),
      accessKeySecretInput: document.getElementById("admin-aliyun-sms-access-key-secret-input"),
      saveButton: document.getElementById("admin-aliyun-sms-config-save")
    };
  }

  function setAliyunSmsConfigStatus(nodes, message, type = "") {
    setText(nodes.status, message);
    nodes.status?.classList.toggle("is-error", type === "error");
    nodes.status?.classList.toggle("is-ok", type === "ok");
  }

  function renderAliyunSmsConfig(nodes, data) {
    renderOAuthField(nodes.accessKeyIdValue, nodes.accessKeyIdMeta, data.accessKeyId);
    renderOAuthField(nodes.accessKeySecretValue, nodes.accessKeySecretMeta, data.accessKeySecret);
    nodes.fields?.removeAttribute("hidden");
    nodes.form?.removeAttribute("hidden");
  }

  async function loadAliyunSmsConfig() {
    const nodes = getAliyunSmsConfigNodes();
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    setAliyunSmsConfigStatus(nodes, "正在读取阿里云短信服务配置...");
    try {
      const response = await api.get("/shopping/admin/api/sms/aliyun/config");
      renderAliyunSmsConfig(nodes, response.data || {});
      setAliyunSmsConfigStatus(nodes, "已读取脱敏 accessKeyId 和 accessKeySecret。", "ok");
    } catch (error) {
      setAliyunSmsConfigStatus(nodes, error.message || "读取阿里云短信服务配置失败。", "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  function clearAliyunSmsConfigInputs(nodes) {
    if (nodes.accessKeyIdInput) {
      nodes.accessKeyIdInput.value = "";
    }
    if (nodes.accessKeySecretInput) {
      nodes.accessKeySecretInput.value = "";
    }
  }

  async function saveAliyunSmsConfig() {
    const nodes = getAliyunSmsConfigNodes();
    const accessKeyId = nodes.accessKeyIdInput?.value || "";
    const accessKeySecret = nodes.accessKeySecretInput?.value || "";
    if (!accessKeyId.trim() && !accessKeySecret.trim()) {
      setAliyunSmsConfigStatus(nodes, "请至少填写一个需要修改的短信服务配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    setAliyunSmsConfigStatus(nodes, "正在保存阿里云短信服务 Windows 系统环境变量...");
    try {
      const response = await api.request("/shopping/admin/api/sms/aliyun/config", {
        accessKeyId,
        accessKeySecret
      });
      const data = response.data || {};
      renderAliyunSmsConfig(nodes, data);
      clearAliyunSmsConfigInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      setAliyunSmsConfigStatus(nodes, `已保存到 ${target}，重启应用后短信客户端生效。`, "ok");
    } catch (error) {
      setAliyunSmsConfigStatus(nodes, error.message || "保存阿里云短信服务配置失败。", "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function bindAliyunSmsConfig() {
    const nodes = getAliyunSmsConfigNodes();
    nodes.button?.addEventListener("click", () => {
      playPress(nodes.button);
      loadAliyunSmsConfig();
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      playPress(nodes.saveButton);
      saveAliyunSmsConfig();
    });
  }

  function getAliyunOssConfigNodes() {
    return {
      button: document.getElementById("admin-aliyun-oss-config-load"),
      status: document.getElementById("admin-aliyun-oss-config-status"),
      fields: document.getElementById("admin-aliyun-oss-config-fields"),
      form: document.getElementById("admin-aliyun-oss-config-form"),
      accessKeyIdValue: document.getElementById("admin-aliyun-oss-access-key-id-value"),
      accessKeyIdMeta: document.getElementById("admin-aliyun-oss-access-key-id-meta"),
      accessKeyIdInput: document.getElementById("admin-aliyun-oss-access-key-id-input"),
      accessKeySecretValue: document.getElementById("admin-aliyun-oss-access-key-secret-value"),
      accessKeySecretMeta: document.getElementById("admin-aliyun-oss-access-key-secret-meta"),
      accessKeySecretInput: document.getElementById("admin-aliyun-oss-access-key-secret-input"),
      saveButton: document.getElementById("admin-aliyun-oss-config-save")
    };
  }

  function setAliyunOssConfigStatus(nodes, message, type = "") {
    setText(nodes.status, message);
    nodes.status?.classList.toggle("is-error", type === "error");
    nodes.status?.classList.toggle("is-ok", type === "ok");
  }

  function renderAliyunOssConfig(nodes, data) {
    renderOAuthField(nodes.accessKeyIdValue, nodes.accessKeyIdMeta, data.accessKeyId);
    renderOAuthField(nodes.accessKeySecretValue, nodes.accessKeySecretMeta, data.accessKeySecret);
    nodes.fields?.removeAttribute("hidden");
    nodes.form?.removeAttribute("hidden");
  }

  async function loadAliyunOssConfig() {
    const nodes = getAliyunOssConfigNodes();
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    setAliyunOssConfigStatus(nodes, "正在读取阿里云 OSS 配置...");
    try {
      const response = await api.get("/shopping/admin/api/oss/aliyun/config");
      renderAliyunOssConfig(nodes, response.data || {});
      setAliyunOssConfigStatus(nodes, "已读取脱敏 accessKeyId 和 accessKeySecret。", "ok");
    } catch (error) {
      setAliyunOssConfigStatus(nodes, error.message || "读取阿里云 OSS 配置失败。", "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  function clearAliyunOssConfigInputs(nodes) {
    if (nodes.accessKeyIdInput) {
      nodes.accessKeyIdInput.value = "";
    }
    if (nodes.accessKeySecretInput) {
      nodes.accessKeySecretInput.value = "";
    }
  }

  async function saveAliyunOssConfig() {
    const nodes = getAliyunOssConfigNodes();
    const accessKeyId = nodes.accessKeyIdInput?.value || "";
    const accessKeySecret = nodes.accessKeySecretInput?.value || "";
    if (!accessKeyId.trim() && !accessKeySecret.trim()) {
      setAliyunOssConfigStatus(nodes, "请至少填写一个需要修改的 OSS 配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    setAliyunOssConfigStatus(nodes, "正在保存阿里云 OSS Windows 系统环境变量...");
    try {
      const response = await api.request("/shopping/admin/api/oss/aliyun/config", {
        accessKeyId,
        accessKeySecret
      });
      const data = response.data || {};
      renderAliyunOssConfig(nodes, data);
      clearAliyunOssConfigInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      setAliyunOssConfigStatus(nodes, `已保存到 ${target}，重启应用后 OSS 客户端生效。`, "ok");
    } catch (error) {
      setAliyunOssConfigStatus(nodes, error.message || "保存阿里云 OSS 配置失败。", "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function bindAliyunOssConfig() {
    const nodes = getAliyunOssConfigNodes();
    nodes.button?.addEventListener("click", () => {
      playPress(nodes.button);
      loadAliyunOssConfig();
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      playPress(nodes.saveButton);
      saveAliyunOssConfig();
    });
  }

  function getSmtpConfigNodes(provider) {
    return {
      button: document.getElementById(`admin-smtp-${provider}-config-load`),
      status: document.getElementById(`admin-smtp-${provider}-config-status`),
      fields: document.getElementById(`admin-smtp-${provider}-config-fields`),
      form: document.getElementById(`admin-smtp-${provider}-config-form`),
      usernameInput: document.getElementById(`admin-smtp-${provider}-username-input`),
      passwordInput: document.getElementById(`admin-smtp-${provider}-password-input`),
      saveButton: document.getElementById(`admin-smtp-${provider}-config-save`)
    };
  }

  function setStatusNode(node, message, type = "") {
    setText(node, message);
    node?.classList.toggle("is-error", type === "error");
    node?.classList.toggle("is-ok", type === "ok");
  }

  function formatSmtpMeta(field) {
    if (!field) {
      return "-";
    }
    const parts = [];
    if (field.yamlFile || field.yamlLine) {
      parts.push(`YAML: ${field.yamlFile || "shopping-web/src/main/resources/application.yaml"}:${field.yamlLine || "-"}`);
    }
    if (field.envName) {
      parts.push(`ENV: ${field.envName}`);
    }
    if (field.windowsEnvTarget) {
      parts.push(`TARGET: ${field.windowsEnvTarget}`);
    }
    if (field.propertyKey) {
      parts.push(`KEY: ${field.propertyKey}`);
    }
    if (field.sensitive) {
      parts.push("VALUE: 脱敏显示");
    }
    return parts.length ? parts.join(" · ") : "-";
  }

  function createSmtpFieldRow(field) {
    const row = document.createElement("div");
    row.className = "admin-oauth-config-row";

    const label = document.createElement("span");
    label.textContent = field?.label || "-";

    const content = document.createElement("div");
    const value = document.createElement("strong");
    value.textContent = field?.maskedValue || "未配置";
    const meta = document.createElement("small");
    meta.textContent = formatSmtpMeta(field);

    content.append(value, meta);
    row.append(label, content);
    return row;
  }

  function renderSmtpFields(container, fields) {
    if (!container) {
      return;
    }
    const rows = Array.isArray(fields) ? fields.map(createSmtpFieldRow) : [];
    container.replaceChildren(...rows);
    container.toggleAttribute("hidden", rows.length === 0);
  }

  async function loadSmtpProviders() {
    const statusNode = document.getElementById("admin-smtp-provider-status");
    setStatusNode(statusNode, "正在读取当前 SMTP 服务商...");
    try {
      const response = await api.get("/shopping/admin/api/smtp/providers");
      const data = response.data || {};
      const providers = Array.isArray(data.providers) ? data.providers : [];
      document.querySelectorAll("[data-smtp-provider]").forEach((card) => {
        card.classList.remove("is-current");
        card.removeAttribute("aria-current");
        const badge = card.querySelector(".admin-smtp-current-badge");
        if (badge) {
          badge.hidden = true;
        }
      });
      providers.forEach((provider) => {
        const card = document.querySelector(`[data-smtp-provider="${provider.provider}"]`);
        const small = card?.querySelector("small");
        const badge = card?.querySelector(".admin-smtp-current-badge");
        card?.classList.toggle("is-current", Boolean(provider.current));
        if (provider.current) {
          card?.setAttribute("aria-current", "true");
        } else {
          card?.removeAttribute("aria-current");
        }
        if (badge) {
          badge.hidden = !provider.current;
        }
        setText(small, provider.description);
      });
      setStatusNode(statusNode, `当前 SMTP 服务商：${data.currentProviderDisplayName || "未识别"}`, "ok");
    } catch (error) {
      setStatusNode(statusNode, error.message || "读取当前 SMTP 服务商失败。", "error");
    }
  }

  async function loadSmtpConfig(provider) {
    const nodes = getSmtpConfigNodes(provider);
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    setStatusNode(nodes.status, "正在读取 SMTP 配置...");
    try {
      const response = await api.get(`/shopping/admin/api/smtp/${provider}/config`);
      const data = response.data || {};
      renderSmtpFields(nodes.fields, data.fields);
      nodes.form?.removeAttribute("hidden");
      const status = data.current
        ? `已读取当前 ${data.displayName} SMTP 配置。`
        : `该服务商未启用，当前使用：${data.currentProviderDisplayName || "未识别"}`;
      setStatusNode(nodes.status, status, data.current ? "ok" : "");
    } catch (error) {
      renderSmtpFields(nodes.fields, []);
      setStatusNode(nodes.status, error.message || "读取 SMTP 配置失败。", "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  function clearSmtpConfigInputs(nodes) {
    if (nodes.usernameInput) {
      nodes.usernameInput.value = "";
    }
    if (nodes.passwordInput) {
      nodes.passwordInput.value = "";
    }
  }

  async function saveSmtpConfig(provider) {
    const nodes = getSmtpConfigNodes(provider);
    const username = nodes.usernameInput?.value || "";
    const password = nodes.passwordInput?.value || "";
    if (!username.trim() && !password.trim()) {
      setStatusNode(nodes.status, "请至少填写一个需要修改的 SMTP 配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    setStatusNode(nodes.status, "正在保存 QQ 邮箱 SMTP Windows 系统环境变量...");
    try {
      const response = await api.request(`/shopping/admin/api/smtp/${provider}/config`, {
        username,
        password
      });
      const data = response.data || {};
      renderSmtpFields(nodes.fields, data.fields);
      nodes.form?.removeAttribute("hidden");
      clearSmtpConfigInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      setStatusNode(nodes.status, `已保存到 ${target}，重启应用后 SMTP 客户端生效。`, "ok");
    } catch (error) {
      setStatusNode(nodes.status, error.message || "保存 QQ 邮箱 SMTP 配置失败。", "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function bindSmtpConfig(provider) {
    const nodes = getSmtpConfigNodes(provider);
    nodes.button?.addEventListener("click", () => {
      playPress(nodes.button);
      loadSmtpConfig(provider);
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      playPress(nodes.saveButton);
      saveSmtpConfig(provider);
    });
  }

  function getRiskApiConfigNodes(provider) {
    return {
      button: document.getElementById(`admin-risk-api-${provider}-config-load`),
      status: document.getElementById(`admin-risk-api-${provider}-config-status`),
      fields: document.getElementById(`admin-risk-api-${provider}-config-fields`),
      form: document.getElementById(`admin-risk-api-${provider}-config-form`),
      inputs: document.getElementById(`admin-risk-api-${provider}-config-inputs`),
      saveButton: document.getElementById(`admin-risk-api-${provider}-config-save`)
    };
  }

  function formatRiskApiMeta(field) {
    if (!field) {
      return "-";
    }
    const yamlLine = field.yamlLine || "-";
    const yamlFile = field.yamlFile || "shopping-web/src/main/resources/application.yaml";
    const envName = field.envName || "-";
    const propertyKey = field.propertyKey || "-";
    const windowsEnvTarget = field.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
    const sensitiveNote = field.sensitive ? " · VALUE: 脱敏显示" : "";
    return `YAML: ${yamlFile}:${yamlLine} · ENV: ${envName} · TARGET: ${windowsEnvTarget} · KEY: ${propertyKey}${sensitiveNote}`;
  }

  function createRiskApiFieldRow(field) {
    const row = document.createElement("div");
    row.className = "admin-oauth-config-row";

    const label = document.createElement("span");
    label.textContent = field?.label || "-";

    const content = document.createElement("div");
    const value = document.createElement("strong");
    value.textContent = field?.maskedValue || "未配置";
    const meta = document.createElement("small");
    meta.textContent = formatRiskApiMeta(field);

    content.append(value, meta);
    row.append(label, content);
    return row;
  }

  function renderRiskApiFields(container, fields) {
    if (!container) {
      return;
    }
    const rows = Array.isArray(fields) ? fields.map(createRiskApiFieldRow) : [];
    container.replaceChildren(...rows);
    container.toggleAttribute("hidden", rows.length === 0);
  }

  function createRiskApiInput(field) {
    const label = document.createElement("label");
    const text = document.createElement("span");
    text.textContent = `新的 ${field?.label || "配置值"}`;

    const input = document.createElement("input");
    input.type = "text";
    input.autocomplete = "off";
    input.placeholder = "留空表示不修改";
    input.dataset.riskApiField = field?.id || "";
    if (field?.propertyKey?.endsWith(".enabled")) {
      input.placeholder = "true 或 false，留空表示不修改";
    }

    label.append(text, input);
    return label;
  }

  function renderRiskApiInputs(container, fields) {
    if (!container) {
      return;
    }
    const inputs = Array.isArray(fields) ? fields.map(createRiskApiInput) : [];
    container.replaceChildren(...inputs);
  }

  function clearRiskApiInputs(nodes) {
    nodes.inputs?.querySelectorAll("[data-risk-api-field]").forEach((input) => {
      input.value = "";
    });
  }

  function collectRiskApiValues(nodes) {
    const values = {};
    nodes.inputs?.querySelectorAll("[data-risk-api-field]").forEach((input) => {
      const fieldId = input.dataset.riskApiField;
      if (fieldId && input.value.trim()) {
        values[fieldId] = input.value;
      }
    });
    return values;
  }

  function renderRiskApiConfig(nodes, data) {
    const fields = Array.isArray(data.fields) ? data.fields : [];
    renderRiskApiFields(nodes.fields, fields);
    renderRiskApiInputs(nodes.inputs, fields);
    nodes.form?.removeAttribute("hidden");
  }

  async function loadRiskApiConfig(provider, label) {
    const nodes = getRiskApiConfigNodes(provider);
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    setStatusNode(nodes.status, `正在读取 ${label} 配置...`);
    try {
      const response = await api.get(`/shopping/admin/api/risk-api/${provider}/config`);
      const data = response.data || {};
      renderRiskApiConfig(nodes, data);
      const prefix = data.propertyPrefix || "-";
      setStatusNode(nodes.status, `已读取 ${prefix} 前缀下的脱敏配置。`, "ok");
    } catch (error) {
      renderRiskApiFields(nodes.fields, []);
      setStatusNode(nodes.status, error.message || `读取 ${label} 配置失败。`, "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  async function saveRiskApiConfig(provider, label) {
    const nodes = getRiskApiConfigNodes(provider);
    const values = collectRiskApiValues(nodes);
    if (Object.keys(values).length === 0) {
      setStatusNode(nodes.status, "请至少填写一个需要修改的 Risk API 配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    setStatusNode(nodes.status, `正在保存 ${label} Windows 系统环境变量...`);
    try {
      const response = await api.request(`/shopping/admin/api/risk-api/${provider}/config`, { values });
      const data = response.data || {};
      renderRiskApiConfig(nodes, data);
      clearRiskApiInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      setStatusNode(nodes.status, `已保存到 ${target}，重启应用后 Risk API 客户端生效。`, "ok");
    } catch (error) {
      setStatusNode(nodes.status, error.message || `保存 ${label} 配置失败。`, "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function bindRiskApiConfig(provider, label) {
    const nodes = getRiskApiConfigNodes(provider);
    nodes.button?.addEventListener("click", () => {
      playPress(nodes.button);
      loadRiskApiConfig(provider, label);
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      playPress(nodes.saveButton);
      saveRiskApiConfig(provider, label);
    });
  }

  function getIp2LocationQuotaKeyNodes() {
    return {
      mailToolButton: document.getElementById("admin-risk-api-ip2location-mail-tool-open"),
      configToggle: document.getElementById("admin-risk-api-ip2location-config-toggle"),
      configCard: document.getElementById("admin-risk-api-ip2location-config-card"),
      loadButton: document.getElementById("admin-risk-api-ip2location-keys-load"),
      deleteButton: document.getElementById("admin-risk-api-ip2location-keys-delete"),
      status: document.getElementById("admin-risk-api-ip2location-keys-status"),
      db: document.getElementById("admin-risk-api-ip2location-keys-db"),
      prefix: document.getElementById("admin-risk-api-ip2location-keys-prefix"),
      total: document.getElementById("admin-risk-api-ip2location-keys-total"),
      list: document.getElementById("admin-risk-api-ip2location-keys-list"),
      form: document.getElementById("admin-risk-api-ip2location-keys-add-form"),
      input: document.getElementById("admin-risk-api-ip2location-keys-input"),
      accountType: document.getElementById("admin-risk-api-ip2location-keys-account-type"),
      quota: document.getElementById("admin-risk-api-ip2location-keys-quota"),
      addButton: document.getElementById("admin-risk-api-ip2location-keys-add")
    };
  }

  function setIp2LocationConfigCardVisible(visible) {
    const nodes = getIp2LocationQuotaKeyNodes();
    if (nodes.configCard) {
      nodes.configCard.hidden = !visible;
    }
    if (nodes.configToggle) {
      nodes.configToggle.setAttribute("aria-expanded", String(visible));
      nodes.configToggle.textContent = visible ? "隐藏配置" : "API 配置";
    }
  }

  function toggleIp2LocationConfigCard() {
    const nodes = getIp2LocationQuotaKeyNodes();
    const visible = Boolean(nodes.configCard?.hidden);
    setIp2LocationConfigCardVisible(visible);
    if (visible) {
      loadRiskApiConfig("ip2location", "IP2Location API");
    }
  }

  function formatQuotaTtl(ttlSeconds) {
    const ttl = Number(ttlSeconds);
    if (ttl === -1) {
      return "永久";
    }
    if (ttl < 0 || Number.isNaN(ttl)) {
      return "-";
    }
    const days = Math.floor(ttl / 86400);
    const hours = Math.floor((ttl % 86400) / 3600);
    const minutes = Math.floor((ttl % 3600) / 60);
    if (days > 0) {
      return `${days}天 ${hours}小时`;
    }
    if (hours > 0) {
      return `${hours}小时 ${minutes}分钟`;
    }
    return `${minutes}分钟`;
  }

  function createIp2LocationQuotaKeyHeader() {
    const row = document.createElement("div");
    row.className = "admin-risk-api-key-row is-header";
    ["", "API key", "账户类型", "剩余额度", "TTL", "创建分钟"].forEach((text) => {
      const cell = document.createElement("span");
      cell.textContent = text;
      row.append(cell);
    });
    return row;
  }

  function createIp2LocationQuotaKeyRow(item) {
    const row = document.createElement("div");
    row.className = "admin-risk-api-key-row";

    const check = document.createElement("input");
    check.type = "checkbox";
    check.value = item.redisKey || "";
    check.setAttribute("aria-label", `选择 ${item.apiKey || "IP2Location API key"}`);

    const apiKey = document.createElement("strong");
    apiKey.textContent = item.apiKey || "-";
    apiKey.title = item.redisKey || "";

    const accountType = document.createElement("span");
    accountType.textContent = item.accountType || "-";

    const quota = document.createElement("span");
    quota.textContent = String(item.remainingQuota ?? 0);

    const ttl = document.createElement("span");
    ttl.textContent = formatQuotaTtl(item.ttlSeconds);

    const createdAt = document.createElement("span");
    createdAt.textContent = item.createdAtMinute || "-";

    row.append(check, apiKey, accountType, quota, ttl, createdAt);
    return row;
  }

  function renderIp2LocationQuotaKeys(nodes, data) {
    const keys = Array.isArray(data.keys) ? data.keys : [];
    setText(nodes.db, data.redisDatabase || "2");
    setText(nodes.prefix, data.quotaPrefix || "ip2location:quota:");
    const realTotal = data.realTotalQuotaCount ?? 0;
    const aggregateTotal = data.aggregateTotalQuotaCount ?? realTotal;
    setText(nodes.total, realTotal === aggregateTotal ? String(realTotal) : `${realTotal} / count=${aggregateTotal}`);

    if (!nodes.list) {
      return;
    }
    const rows = [createIp2LocationQuotaKeyHeader(), ...keys.map(createIp2LocationQuotaKeyRow)];
    nodes.list.replaceChildren(...rows);
    nodes.list.toggleAttribute("hidden", keys.length === 0);
  }

  async function loadIp2LocationQuotaKeys() {
    const nodes = getIp2LocationQuotaKeyNodes();
    if (!nodes.loadButton) {
      return;
    }
    nodes.loadButton.disabled = true;
    setStatusNode(nodes.status, "正在读取 Redis DB 2 中的 IP2Location API keys...");
    try {
      const response = await api.get("/shopping/admin/api/risk-api/ip2location/keys");
      renderIp2LocationQuotaKeys(nodes, response.data || {});
      const count = Array.isArray(response.data?.keys) ? response.data.keys.length : 0;
      setStatusNode(nodes.status, `已读取 ${count} 个 IP2Location API key。`, "ok");
    } catch (error) {
      setStatusNode(nodes.status, error.message || "读取 IP2Location API keys 失败。", "error");
    } finally {
      nodes.loadButton.disabled = false;
    }
  }

  function parseIp2LocationQuotaAddItems(nodes) {
    const raw = nodes.input?.value || "";
    const defaultAccountType = nodes.accountType?.value || "STARTER";
    const defaultQuotaText = nodes.quota?.value?.trim() || "";
    const defaultQuota = defaultQuotaText ? Number(defaultQuotaText) : null;
    if (defaultQuotaText && (!Number.isInteger(defaultQuota) || defaultQuota < 0)) {
      throw new Error("剩余额度必须是大于等于 0 的整数。");
    }
    const items = raw.split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const parts = line.split(",").map((part) => part.trim()).filter(Boolean);
        const quotaText = parts[2] || "";
        const quota = quotaText ? Number(quotaText) : defaultQuota;
        if (quotaText && (!Number.isInteger(quota) || quota < 0)) {
          throw new Error(`剩余额度格式不正确：${line}`);
        }
        return {
          apiKey: parts[0] || line,
          accountType: parts[1] || defaultAccountType,
          remainingQuota: quota
        };
      });
    if (items.length === 0) {
      throw new Error("请至少填写一个 IP2Location API key。");
    }
    return items;
  }

  async function batchAddIp2LocationQuotaKeys() {
    const nodes = getIp2LocationQuotaKeyNodes();
    let items;
    try {
      items = parseIp2LocationQuotaAddItems(nodes);
    } catch (error) {
      setStatusNode(nodes.status, error.message, "error");
      return;
    }
    if (nodes.addButton) {
      nodes.addButton.disabled = true;
    }
    if (nodes.loadButton) {
      nodes.loadButton.disabled = true;
    }
    setStatusNode(nodes.status, `正在批量添加 ${items.length} 个 IP2Location API key...`);
    try {
      const response = await api.request("/shopping/admin/api/risk-api/ip2location/keys/batch-add", { items });
      const result = response.data || {};
      if (nodes.input) {
        nodes.input.value = "";
      }
      if (nodes.quota) {
        nodes.quota.value = "";
      }
      await loadIp2LocationQuotaKeys();
      setStatusNode(
        nodes.status,
        `已批量添加 ${result.affectedCount || 0} 个，替换旧 key ${result.replacedOldCount || 0} 个，总额度 ${result.totalQuotaCount || 0}。`,
        "ok"
      );
    } catch (error) {
      setStatusNode(nodes.status, error.message || "批量添加 IP2Location API key 失败。", "error");
    } finally {
      if (nodes.addButton) {
        nodes.addButton.disabled = false;
      }
      if (nodes.loadButton) {
        nodes.loadButton.disabled = false;
      }
    }
  }

  function getIp2LocationMailToolNodes() {
    return {
      shell: document.getElementById("admin-ip2location-mail-tool"),
      closeButton: document.getElementById("admin-ip2location-mail-tool-close"),
      backdrop: document.querySelector("[data-ip2location-mail-tool-close]"),
      checkInput: document.getElementById("admin-ip2location-mail-check-input"),
      checkThreads: document.getElementById("admin-ip2location-mail-check-threads"),
      checkRun: document.getElementById("admin-ip2location-mail-check-run"),
      checkStatus: document.getElementById("admin-ip2location-mail-check-status"),
      checkRegistered: document.getElementById("admin-ip2location-mail-check-registered"),
      checkUnregistered: document.getElementById("admin-ip2location-mail-check-unregistered"),
      checkFailed: document.getElementById("admin-ip2location-mail-check-failed"),
      verifyInput: document.getElementById("admin-ip2location-mail-verify-input"),
      verifyThreads: document.getElementById("admin-ip2location-mail-verify-threads"),
      verifyRun: document.getElementById("admin-ip2location-mail-verify-run"),
      verifyStatus: document.getElementById("admin-ip2location-mail-verify-status"),
      verifyFound: document.getElementById("admin-ip2location-mail-verify-found"),
      verifyNotFound: document.getElementById("admin-ip2location-mail-verify-not-found"),
      verifyFailed: document.getElementById("admin-ip2location-mail-verify-failed")
    };
  }

  function setIp2LocationMailToolOpen(open) {
    const nodes = getIp2LocationMailToolNodes();
    if (!nodes.shell) {
      return;
    }
    nodes.shell.hidden = !open;
    nodes.shell.setAttribute("aria-hidden", String(!open));
    if (open) {
      window.setTimeout(() => nodes.checkInput?.focus(), 0);
    }
  }

  function normalizeThreadPoolSize(input) {
    const raw = Number(input?.value || 4);
    const value = Number.isInteger(raw) ? raw : 4;
    const normalized = Math.max(1, Math.min(16, value));
    if (input) {
      input.value = String(normalized);
    }
    return normalized;
  }

  function parseMailCredentialLines(input, maxLines) {
    const lines = (input?.value || "")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);
    if (lines.length === 0) {
      throw new Error("请至少填写一行邮箱凭证。");
    }
    if (lines.length > maxLines) {
      throw new Error(`本次最多处理 ${maxLines} 行邮箱凭证。`);
    }
    return lines;
  }

  function appendMailResultText(parent, text) {
    if (!text) {
      return;
    }
    const small = document.createElement("small");
    small.textContent = text;
    parent.append(small);
  }

  function renderMailResultList(container, items, options = {}) {
    if (!container) {
      return;
    }
    const list = document.createElement("div");
    list.className = "admin-ip2location-mail-result-list";
    const rows = Array.isArray(items) ? items : [];
    if (rows.length === 0) {
      const empty = document.createElement("div");
      empty.className = "admin-ip2location-mail-result-item";
      appendMailResultText(empty, "暂无结果");
      list.append(empty);
      container.replaceChildren(list);
      return;
    }
    rows.forEach((item) => {
      const row = document.createElement("div");
      row.className = "admin-ip2location-mail-result-item";

      const title = document.createElement("strong");
      title.textContent = `#${item.lineNumber || "-"} ${item.email || "-"}`;
      row.append(title);

      if (options.includeVerifyUrl && item.verifyUrl) {
        const link = document.createElement("a");
        link.href = item.verifyUrl;
        link.target = "_blank";
        link.rel = "noopener";
        link.textContent = item.verifyUrl;
        row.append(link);
      }

      appendMailResultText(row, `clientId: ${item.clientId || "-"}`);
      if (item.verifyToken) {
        appendMailResultText(row, `verifyToken: ${item.verifyToken}`);
      }
      if (item.folderName || item.receivedAt) {
        appendMailResultText(row, `folder: ${item.folderName || "-"} · receivedAt: ${item.receivedAt || "-"}`);
      }
      if (item.sender || item.subject) {
        appendMailResultText(row, `mail: ${item.sender || "-"} · ${item.subject || "-"}`);
      }
      appendMailResultText(row, `reason: ${item.reason || "-"}`);
      list.append(row);
    });
    container.replaceChildren(list);
  }

  function clearMailToolResults(nodes) {
    renderMailResultList(nodes.checkRegistered, []);
    renderMailResultList(nodes.checkUnregistered, []);
    renderMailResultList(nodes.checkFailed, []);
    renderMailResultList(nodes.verifyFound, [], { includeVerifyUrl: true });
    renderMailResultList(nodes.verifyNotFound, []);
    renderMailResultList(nodes.verifyFailed, []);
  }

  async function runIp2LocationRegistrationCheck() {
    const nodes = getIp2LocationMailToolNodes();
    let credentialLines;
    try {
      credentialLines = parseMailCredentialLines(nodes.checkInput, 100);
    } catch (error) {
      setStatusNode(nodes.checkStatus, error.message, "error");
      return;
    }
    const threadPoolSize = normalizeThreadPoolSize(nodes.checkThreads);
    nodes.checkRun.disabled = true;
    setStatusNode(nodes.checkStatus, `正在用 ${threadPoolSize} 个线程鉴定 ${credentialLines.length} 个邮箱...`);
    try {
      const response = await api.request("/shopping/admin/api/risk-api/ip2location/registration-check", {
        credentialLines,
        threadPoolSize
      });
      const data = response.data || {};
      renderMailResultList(nodes.checkRegistered, data.registered || []);
      renderMailResultList(nodes.checkUnregistered, data.unregistered || []);
      renderMailResultList(nodes.checkFailed, data.failed || []);
      setStatusNode(
        nodes.checkStatus,
        `已完成：已注册 ${(data.registered || []).length}，未注册 ${(data.unregistered || []).length}，失败 ${(data.failed || []).length}。`,
        "ok"
      );
    } catch (error) {
      setStatusNode(nodes.checkStatus, error.message || "批量鉴定失败。", "error");
    } finally {
      nodes.checkRun.disabled = false;
    }
  }

  async function runIp2LocationVerifyLinkRead() {
    const nodes = getIp2LocationMailToolNodes();
    let credentialLines;
    try {
      credentialLines = parseMailCredentialLines(nodes.verifyInput, 10);
    } catch (error) {
      setStatusNode(nodes.verifyStatus, error.message, "error");
      return;
    }
    const threadPoolSize = normalizeThreadPoolSize(nodes.verifyThreads);
    nodes.verifyRun.disabled = true;
    setStatusNode(nodes.verifyStatus, `正在用 ${threadPoolSize} 个线程读取 ${credentialLines.length} 个验证 URL...`);
    try {
      const response = await api.request("/shopping/admin/api/risk-api/ip2location/verify-links", {
        credentialLines,
        threadPoolSize
      });
      const data = response.data || {};
      renderMailResultList(nodes.verifyFound, data.found || [], { includeVerifyUrl: true });
      renderMailResultList(nodes.verifyNotFound, data.notFound || []);
      renderMailResultList(nodes.verifyFailed, data.failed || []);
      setStatusNode(
        nodes.verifyStatus,
        `已完成：找到 ${(data.found || []).length}，未找到 ${(data.notFound || []).length}，失败 ${(data.failed || []).length}。`,
        "ok"
      );
    } catch (error) {
      setStatusNode(nodes.verifyStatus, error.message || "批量获取验证 URL 失败。", "error");
    } finally {
      nodes.verifyRun.disabled = false;
    }
  }

  function selectedIp2LocationQuotaKeys(nodes) {
    return Array.from(nodes.list?.querySelectorAll("input[type='checkbox']:checked") || [])
      .map((input) => input.value)
      .filter(Boolean);
  }

  async function batchDeleteIp2LocationQuotaKeys() {
    const nodes = getIp2LocationQuotaKeyNodes();
    const redisKeys = selectedIp2LocationQuotaKeys(nodes);
    if (redisKeys.length === 0) {
      setStatusNode(nodes.status, "请选择需要删除的 IP2Location API key。", "error");
      return;
    }
    if (nodes.deleteButton) {
      nodes.deleteButton.disabled = true;
    }
    if (nodes.loadButton) {
      nodes.loadButton.disabled = true;
    }
    setStatusNode(nodes.status, `正在批量删除 ${redisKeys.length} 个 IP2Location API key...`);
    try {
      const response = await api.request("/shopping/admin/api/risk-api/ip2location/keys/batch-delete", { redisKeys });
      const result = response.data || {};
      await loadIp2LocationQuotaKeys();
      setStatusNode(
        nodes.status,
        `已批量删除 ${result.affectedCount || 0} 个 IP2Location API key，总额度 ${result.totalQuotaCount || 0}。`,
        "ok"
      );
    } catch (error) {
      setStatusNode(nodes.status, error.message || "批量删除 IP2Location API key 失败。", "error");
    } finally {
      if (nodes.deleteButton) {
        nodes.deleteButton.disabled = false;
      }
      if (nodes.loadButton) {
        nodes.loadButton.disabled = false;
      }
    }
  }

  function bindIp2LocationQuotaKeys() {
    const nodes = getIp2LocationQuotaKeyNodes();
    nodes.mailToolButton?.addEventListener("click", () => {
      playPress(nodes.mailToolButton);
      const mailNodes = getIp2LocationMailToolNodes();
      clearMailToolResults(mailNodes);
      setIp2LocationMailToolOpen(true);
    });
    nodes.configToggle?.addEventListener("click", () => {
      playPress(nodes.configToggle);
      toggleIp2LocationConfigCard();
    });
    nodes.loadButton?.addEventListener("click", () => {
      playPress(nodes.loadButton);
      loadIp2LocationQuotaKeys();
    });
    nodes.deleteButton?.addEventListener("click", () => {
      playPress(nodes.deleteButton);
      batchDeleteIp2LocationQuotaKeys();
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      playPress(nodes.addButton);
      batchAddIp2LocationQuotaKeys();
    });
  }

  function bindIp2LocationMailTool() {
    const nodes = getIp2LocationMailToolNodes();
    nodes.closeButton?.addEventListener("click", () => {
      playPress(nodes.closeButton);
      setIp2LocationMailToolOpen(false);
    });
    nodes.backdrop?.addEventListener("click", () => {
      setIp2LocationMailToolOpen(false);
    });
    nodes.checkRun?.addEventListener("click", () => {
      playPress(nodes.checkRun);
      runIp2LocationRegistrationCheck();
    });
    nodes.verifyRun?.addEventListener("click", () => {
      playPress(nodes.verifyRun);
      runIp2LocationVerifyLinkRead();
    });
  }

  async function loadSession() {
    try {
      const response = await api.get("/shopping/admin/session/me");
      const user = response.data || {};
      if (!user.authenticated) {
        window.location.assign("/shopping/admin/login");
        return false;
      }
      setText(accountNode, user.username || "管理员");
      setText(emailNode, user.email || "-");
      setText(phoneNode, user.phone || "-");
      return true;
    } catch (_) {
      window.location.assign("/shopping/admin/login");
      return false;
    }
  }

  document.querySelectorAll("[data-section-target]").forEach((trigger) => {
    trigger.addEventListener("click", () => {
      playPress(trigger);
      switchSection(trigger.dataset.sectionTarget);
    });
  });

  document.querySelectorAll(".admin-console-card").forEach((card) => {
    card.setAttribute("tabindex", "0");
    card.setAttribute("role", "button");
    card.addEventListener("click", () => {
      if (card.dataset.sectionTarget) {
        return;
      }
      playPress(card);
      openDetail(card);
    });
    card.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        playPress(card);
        if (card.dataset.sectionTarget) {
          switchSection(card.dataset.sectionTarget);
          return;
        }
        openDetail(card);
      }
    });
  });

  document.querySelectorAll(".admin-spring-button, .admin-side-item").forEach((button) => {
    button.addEventListener("pointerdown", () => button.classList.add("is-pressing"));
    button.addEventListener("pointerup", () => button.classList.remove("is-pressing"));
    button.addEventListener("pointerleave", () => button.classList.remove("is-pressing"));
    button.addEventListener("pointercancel", () => button.classList.remove("is-pressing"));
  });

  detailClose?.addEventListener("click", () => {
    playPress(detailClose);
    closeDetail();
  });

  bindOAuthConfig("github", "GitHub");
  bindOAuthConfig("google", "Google");
  bindOAuthConfig("microsoft", "Microsoft");
  bindCaptchaConfig("turnstile", "Cloudflare Turnstile");
  bindCaptchaConfig("recaptcha", "Google reCAPTCHA");
  bindCaptchaConfig("hcaptcha", "hCaptcha");
  bindAliyunSmsConfig();
  bindAliyunOssConfig();
  bindSmtpConfig("qq");
  bindRiskApiConfig("ip2location", "IP2Location API");
  bindRiskApiConfig("iping", "iPing 降级 API");
  bindIp2LocationQuotaKeys();
  bindIp2LocationMailTool();

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      setIp2LocationMailToolOpen(false);
      closeDetail();
    }
  });

  window.addEventListener("popstate", () => {
    switchSection(getSectionFromLocation(), { updateUrl: false });
  });

  document.addEventListener("click", (event) => {
    if (!detailDrawer?.classList.contains("is-open")) {
      return;
    }
    if (event.target.closest(".admin-detail-drawer") || event.target.closest(".admin-console-card")) {
      return;
    }
    closeDetail();
  });

  logoutButton?.addEventListener("click", async () => {
    logoutButton.disabled = true;
    transition?.prewarm?.(transitionSource);
    try {
      const response = await api.request("/shopping/admin/logout", {});
      const redirectPath = response.data?.redirectPath || "/shopping/admin/login";
      if (transition?.beginExit) {
        await transition.beginExit({ source: transitionSource, to: redirectPath });
        return;
      }
      window.location.assign(redirectPath);
    } catch (_) {
      logoutButton.disabled = false;
    }
  });

  transition?.prewarm?.(transitionSource);
  const enterPromise = transition?.playEnter?.(document.querySelectorAll("[data-admin-target]"));
  enterPromise?.finally?.(() => transition?.prewarm?.(transitionSource));
  const initialSection = getSectionFromLocation();
  window.history?.replaceState?.({ adminSection: initialSection }, "", window.location.href);
  switchSection(initialSection, { replaceUrl: true });
  loadSession();
})();
