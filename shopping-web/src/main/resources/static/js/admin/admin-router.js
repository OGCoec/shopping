(function (root) {
  const dom = root.AdminDom;
  const modal = root.AdminModal;
  const eyebrowNode = document.getElementById("admin-section-eyebrow");
  const titleNode = document.getElementById("admin-section-title");
  const copyNode = document.getElementById("admin-section-copy");

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
    riskCreditScore: {
      eyebrow: "Risk Credit",
      title: "IP 与设备指纹信用分管理",
      copy: "批量查询和调整 IP、设备指纹信用分，后端接口接入前先提供管理页面。",
      navTarget: "riskCreditScore"
    },
    riskIpScore: {
      eyebrow: "Risk Credit / IP",
      title: "IP 分数",
      copy: "选择 IPv4 或 IPv6 分数管理入口。",
      navTarget: "riskCreditScore"
    },
    riskIpScoreIpv4: {
      eyebrow: "Risk Credit / IP / IPv4",
      title: "IPv4 IP 分数",
      copy: "按国家和 L1-L6 分数区间查询 IPv4 信誉画像。",
      navTarget: "riskCreditScore"
    },
    riskIpScoreIpv6: {
      eyebrow: "Risk Credit / IP / IPv6",
      title: "IPv6 IP 分数",
      copy: "按国家和 L1-L6 分数区间查询 IPv6 信誉画像。",
      navTarget: "riskCreditScore"
    },
    riskDeviceScore: {
      eyebrow: "Risk Credit / Device",
      title: "设备分数",
      copy: "按设备指纹、deviceId 和 L1-L6 分数区间查询设备风险画像。",
      navTarget: "riskCreditScore"
    },
    accountManagement: {
      eyebrow: "Accounts",
      title: "账号管理",
      copy: "管理账号信用分、主动停用和被动风控停用记录。",
      navTarget: "accountManagement"
    },
    accountCredit: {
      eyebrow: "Accounts / Credit",
      title: "账号信用分管理",
      copy: "查看账号当前信用分、历史流水，并记录管理员人工加分或扣分。",
      navTarget: "accountManagement"
    },
    accountTermination: {
      eyebrow: "Accounts / Termination",
      title: "账号停用管理",
      copy: "选择主动停用或被动风控停用管理入口。",
      navTarget: "accountManagement"
    },
    accountTerminationSelf: {
      eyebrow: "Accounts / Termination / Self",
      title: "主动停用管理",
      copy: "查看主动停用记录，7 天内未清理账号可由管理员恢复。",
      navTarget: "accountManagement"
    },
    accountTerminationRisk: {
      eyebrow: "Accounts / Termination / Risk",
      title: "被动停用管理",
      copy: "查看因信用分和违规次数触发的风控停用记录。",
      navTarget: "accountManagement"
    },
    productCategories: {
      eyebrow: "Products / Categories",
      title: "商品分类管理",
      copy: "维护最多三级商品分类树，管理分类添加、修改、启用、禁用和删除。",
      navTarget: "productCategories"
    },
    products: {
      eyebrow: "Products",
      title: "商品管理",
      copy: "在启用的叶子分类下创建和管理商品 SPU。",
      navTarget: "products"
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
      copy: "管理阿里云 Dypnsapi 短信服务的服务器环境变量。"
    },
    oss: {
      eyebrow: "OSS",
      title: "对象存储服务",
      copy: "选择当前项目调用的对象存储服务。"
    },
    ossAliyun: {
      eyebrow: "OSS / Aliyun",
      title: "阿里云 OSS 对象存储服务",
      copy: "管理阿里云 OSS 对象存储服务的服务器环境变量。"
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
      copy: "管理 iPing 降级 API 的 Redis 配置。"
    }
  };

  const defaultSection = "overview";
  const consoleBasePath = "/shopping/admin/console";

  const sectionRouteMap = {
    overview: "overview",
    externalInterfaces: "external-interfaces",
    riskCreditScore: "risk-credit-score",
    riskIpScore: "risk-credit-score/ip",
    riskIpScoreIpv4: "risk-credit-score/ip/ipv4",
    riskIpScoreIpv6: "risk-credit-score/ip/ipv6",
    riskDeviceScore: "risk-credit-score/device",
    accountManagement: "account-management",
    accountCredit: "account-management/credit",
    accountTermination: "account-management/termination",
    accountTerminationSelf: "account-management/termination/self",
    accountTerminationRisk: "account-management/termination/risk",
    productCategories: "product-categories",
    products: "products",
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
    "risk-credit-score": "riskCreditScore",
    "risk-credit-score/ip": "riskIpScore",
    "risk-credit-score/ip/ipv4": "riskIpScoreIpv4",
    "risk-credit-score/ip/ipv6": "riskIpScoreIpv6",
    "risk-credit-score/device": "riskDeviceScore",
    "account-management": "accountManagement",
    "account-management/credit": "accountCredit",
    "account-management/termination": "accountTermination",
    "account-management/termination/self": "accountTerminationSelf",
    "account-management/termination/risk": "accountTerminationRisk",
    "product-categories": "productCategories",
    products: "products",
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

  const enterCallbacks = {};

  function register(sectionName, callback) {
    if (!enterCallbacks[sectionName]) {
      enterCallbacks[sectionName] = [];
    }
    enterCallbacks[sectionName].push(callback);
  }

  function getSectionFromLocation() {
    const normalizedPath = window.location.pathname.replace(/\/+$/, "");
    if (normalizedPath === consoleBasePath) {
      return defaultSection;
    }
    if (normalizedPath.startsWith(consoleBasePath + "/")) {
      const routeValue = decodeURIComponent(normalizedPath.slice(consoleBasePath.length + 1));
      if (routeValue === "products" || routeValue.startsWith("products/")) {
        return "products";
      }
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
    if (sectionName === "products" && replace && window.location.pathname.startsWith(`${consoleBasePath}/products/`)) {
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
    dom.setText(eyebrowNode, meta.eyebrow);
    dom.setText(titleNode, meta.title);
    dom.setText(copyNode, meta.copy);
    document.title = `${meta.title} - Shopping System`;
    modal.closeDetail();

    if (options.updateUrl !== false) {
      updateSectionUrl(resolvedSection, Boolean(options.replaceUrl));
    }

    const callbacks = enterCallbacks[resolvedSection];
    if (callbacks) {
      callbacks.forEach((cb) => cb());
    }

    return true;
  }

  window.addEventListener("popstate", () => {
    switchSection(getSectionFromLocation(), { updateUrl: false });
  });

  root.AdminRouter = {
    switchSection,
    register,
    getSectionFromLocation,
    sectionMeta,
    defaultSection
  };
})(window);
