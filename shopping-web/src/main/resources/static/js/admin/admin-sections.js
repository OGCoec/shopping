(function (root) {
  const defaultSection = "overview";
  const consoleBasePath = "/shopping/admin/console";
  const panelOutletId = "admin-panel-outlet";
  const sections = {
      "overview": {
          "route": "overview",
          "eyebrow": "Dashboard",
          "title": "管理概览",
          "copy": "查看管理员身份、登录状态和核心管理入口。",
          "navTarget": "overview",
          "fragment": "/shopping/admin/panels/overview.html",
          "module": null,
          "modules": []
      },
      "externalInterfaces": {
          "route": "external-interfaces",
          "eyebrow": "External APIs",
          "title": "外部接口管理",
          "copy": "管理第三方登录、邮件、验证码、短信、OSS 和 Risk API。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/external-interfaces.html",
          "module": null,
          "modules": []
      },
      "openAiMailStatus": {
          "route": "mail/openai/status",
          "eyebrow": "External APIs / OpenAI Mail",
          "title": "OpenAI mailbox status",
          "copy": "Batch check Outlook and Hotmail mailbox evidence for OpenAI or ChatGPT account status.",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/openai-mail-status.html",
          "module": "AdminOpenAiMailStatusModule",
          "modules": [
              "AdminOpenAiMailStatusModule"
          ]
      },
      "kiroMailStatus": {
          "route": "mail/kiro/status",
          "eyebrow": "External APIs / Kiro Mail",
          "title": "Kiro mailbox status",
          "copy": "Batch check Outlook and Hotmail mailbox evidence for Kiro account restrictions.",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/kiro-mail-status.html",
          "module": "AdminKiroMailStatusModule",
          "modules": [
              "AdminKiroMailStatusModule"
          ]
      },
      "riskCreditScore": {
          "route": "risk-credit-score",
          "eyebrow": "Risk Credit",
          "title": "IP 与设备指纹信用分管理",
          "copy": "批量查询和调整 IP、设备指纹信用分，后端接口接入前先提供管理页面。",
          "navTarget": "riskCreditScore",
          "fragment": "/shopping/admin/panels/risk-credit-score.html",
          "module": null,
          "modules": []
      },
      "riskIpScore": {
          "route": "risk-credit-score/ip",
          "eyebrow": "Risk Credit / IP",
          "title": "IP 分数",
          "copy": "选择 IPv4 或 IPv6 分数管理入口。",
          "navTarget": "riskCreditScore",
          "fragment": "/shopping/admin/panels/risk-ip-score.html",
          "module": null,
          "modules": []
      },
      "riskIpScoreIpv4": {
          "route": "risk-credit-score/ip/ipv4",
          "eyebrow": "Risk Credit / IP / IPv4",
          "title": "IPv4 IP 分数",
          "copy": "按国家和 L1-L6 分数区间查询 IPv4 信誉画像。",
          "navTarget": "riskCreditScore",
          "fragment": "/shopping/admin/panels/risk-ip-score-ipv4.html",
          "module": "AdminRiskIpScoreModule",
          "modules": [
              "AdminRiskIpScoreModule"
          ]
      },
      "riskIpScoreIpv6": {
          "route": "risk-credit-score/ip/ipv6",
          "eyebrow": "Risk Credit / IP / IPv6",
          "title": "IPv6 IP 分数",
          "copy": "按国家和 L1-L6 分数区间查询 IPv6 信誉画像。",
          "navTarget": "riskCreditScore",
          "fragment": "/shopping/admin/panels/risk-ip-score-ipv6.html",
          "module": "AdminRiskIpScoreModule",
          "modules": [
              "AdminRiskIpScoreModule"
          ]
      },
      "riskDeviceScore": {
          "route": "risk-credit-score/device",
          "eyebrow": "Risk Credit / Device",
          "title": "设备分数",
          "copy": "按设备指纹、deviceId 和 L1-L6 分数区间查询设备风险画像。",
          "navTarget": "riskCreditScore",
          "fragment": "/shopping/admin/panels/risk-device-score.html",
          "module": "AdminRiskDeviceScoreModule",
          "modules": [
              "AdminRiskDeviceScoreModule"
          ]
      },
      "accountManagement": {
          "route": "account-management",
          "eyebrow": "Accounts",
          "title": "账号管理",
          "copy": "管理账号信用分、主动停用和被动风控停用记录。",
          "navTarget": "accountManagement",
          "fragment": "/shopping/admin/panels/account-management.html",
          "module": null,
          "modules": []
      },
      "accountCredit": {
          "route": "account-management/credit",
          "eyebrow": "Accounts / Credit",
          "title": "账号信用分管理",
          "copy": "查看账号当前信用分、历史流水，并记录管理员人工加分或扣分。",
          "navTarget": "accountManagement",
          "fragment": "/shopping/admin/panels/account-credit.html",
          "module": "AdminAccountCreditModule",
          "modules": [
              "AdminAccountCreditModule"
          ]
      },
      "accountTermination": {
          "route": "account-management/termination",
          "eyebrow": "Accounts / Termination",
          "title": "账号停用管理",
          "copy": "选择主动停用或被动风控停用管理入口。",
          "navTarget": "accountManagement",
          "fragment": "/shopping/admin/panels/account-termination.html",
          "module": null,
          "modules": []
      },
      "accountTerminationSelf": {
          "route": "account-management/termination/self",
          "eyebrow": "Accounts / Termination / Self",
          "title": "主动停用管理",
          "copy": "查看主动停用记录，7 天内未清理账号可由管理员恢复。",
          "navTarget": "accountManagement",
          "fragment": "/shopping/admin/panels/account-termination-self.html",
          "module": "AdminAccountTerminationModule",
          "modules": [
              "AdminAccountTerminationModule"
          ]
      },
      "accountTerminationRisk": {
          "route": "account-management/termination/risk",
          "eyebrow": "Accounts / Termination / Risk",
          "title": "被动停用管理",
          "copy": "查看因信用分和违规次数触发的风控停用记录。",
          "navTarget": "accountManagement",
          "fragment": "/shopping/admin/panels/account-termination-risk.html",
          "module": "AdminAccountTerminationModule",
          "modules": [
              "AdminAccountTerminationModule"
          ]
      },
      "productCategories": {
          "route": "product-categories",
          "eyebrow": "Products / Categories",
          "title": "商品分类管理",
          "copy": "维护多级商品分类树，管理分类添加、修改、启用、禁用和删除。",
          "navTarget": "productCategories",
          "fragment": "/shopping/admin/panels/product-categories.html",
          "module": "AdminProductCategoriesModule",
          "modules": [
              "AdminProductCategoriesModule"
          ]
      },
      "products": {
          "route": "products",
          "eyebrow": "Products",
          "title": "商品管理",
          "copy": "在启用的叶子分类下创建和管理商品 SPU。",
          "navTarget": "products",
          "fragment": "/shopping/admin/panels/products.html",
          "module": "AdminProductsModule",
          "modules": [
              "AdminProductsModule"
          ]
      },
      "coupons": {
          "route": "coupons",
          "eyebrow": "Coupons",
          "title": "优惠券管理",
          "copy": "查询优惠券模板，查看领取用户邮箱和使用状态。",
          "navTarget": "coupons",
          "fragment": "/shopping/admin/panels/coupons.html",
          "module": "AdminCouponsModule",
          "modules": [
              "AdminCouponsModule"
          ]
      },
      "orders": {
          "route": "orders",
          "eyebrow": "Orders",
          "title": "订单管理",
          "copy": "实时查看 Redis 和 DB 中的订单，按状态筛选并按订单号进入详情页。",
          "navTarget": "orders",
          "fragment": "/shopping/admin/panels/orders.html",
          "module": "AdminOrdersModule",
          "modules": [
              "AdminOrdersModule"
          ]
      },
      "cardSecrets": {
          "route": "card-secrets",
          "eyebrow": "Card Secrets",
          "title": "卡密管理",
          "copy": "配置卡密 AES/HMAC 环境变量，并在商品 SKU 页面导入一行一个的卡密。",
          "navTarget": "cardSecrets",
          "fragment": "/shopping/admin/panels/card-secrets.html",
          "module": "AdminCardSecretsModule",
          "modules": [
              "AdminCardSecretsModule"
          ]
      },
      "oauth2": {
          "route": "oauth2",
          "eyebrow": "OAuth2",
          "title": "第三方登录 OAuth2",
          "copy": "选择 GitHub、Google、Microsoft OAuth2 登录服务。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/oauth2.html",
          "module": null,
          "modules": []
      },
      "oauth2Github": {
          "route": "oauth2/github",
          "eyebrow": "OAuth2 / GitHub",
          "title": "GitHub OAuth2 service",
          "copy": "管理 GitHub OAuth2 第三方登录服务。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/oauth2-github.html",
          "module": "AdminOAuthConfigModule",
          "modules": [
              "AdminOAuthConfigModule"
          ]
      },
      "oauth2Google": {
          "route": "oauth2/google",
          "eyebrow": "OAuth2 / Google",
          "title": "Google OAuth2 service",
          "copy": "管理 Google OAuth2 第三方登录服务。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/oauth2-google.html",
          "module": "AdminOAuthConfigModule",
          "modules": [
              "AdminOAuthConfigModule"
          ]
      },
      "oauth2Microsoft": {
          "route": "oauth2/microsoft",
          "eyebrow": "OAuth2 / Microsoft",
          "title": "Microsoft OAuth2 service",
          "copy": "管理 Microsoft OAuth2 第三方登录服务。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/oauth2-microsoft.html",
          "module": "AdminOAuthConfigModule",
          "modules": [
              "AdminOAuthConfigModule"
          ]
      },
      "smtp": {
          "route": "smtp",
          "eyebrow": "SMTP",
          "title": "邮件服务 SMTP",
          "copy": "选择当前邮件发送使用的 SMTP 服务商。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/smtp.html",
          "module": "AdminSmtpConfigModule",
          "modules": [
              "AdminSmtpConfigModule"
          ]
      },
      "smtpQq": {
          "route": "smtp/qq",
          "eyebrow": "SMTP / QQ",
          "title": "QQ 邮箱 SMTP",
          "copy": "查看 QQ 邮箱 SMTP 邮件发送配置。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/smtp-qq.html",
          "module": "AdminSmtpConfigModule",
          "modules": [
              "AdminSmtpConfigModule"
          ]
      },
      "captcha": {
          "route": "captcha",
          "eyebrow": "Captcha",
          "title": "第三方验证码",
          "copy": "查看 Cloudflare Turnstile、Google reCAPTCHA、hCaptcha 等当前验证码类型。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/captcha.html",
          "module": null,
          "modules": []
      },
      "captchaTurnstile": {
          "route": "captcha/turnstile",
          "eyebrow": "Captcha / Cloudflare",
          "title": "Cloudflare Turnstile service",
          "copy": "管理 Cloudflare Turnstile 验证码的 siteKey 和 secretKey。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/captcha-turnstile.html",
          "module": "AdminCaptchaConfigModule",
          "modules": [
              "AdminCaptchaConfigModule"
          ]
      },
      "captchaRecaptcha": {
          "route": "captcha/recaptcha",
          "eyebrow": "Captcha / Google",
          "title": "Google reCAPTCHA service",
          "copy": "管理 Google reCAPTCHA 验证码的 siteKey 和 secretKey。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/captcha-recaptcha.html",
          "module": "AdminCaptchaConfigModule",
          "modules": [
              "AdminCaptchaConfigModule"
          ]
      },
      "captchaHcaptcha": {
          "route": "captcha/hcaptcha",
          "eyebrow": "Captcha / hCaptcha",
          "title": "hCaptcha service",
          "copy": "管理 hCaptcha 验证码的 siteKey 和 secretKey。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/captcha-hcaptcha.html",
          "module": "AdminCaptchaConfigModule",
          "modules": [
              "AdminCaptchaConfigModule"
          ]
      },
      "sms": {
          "route": "sms",
          "eyebrow": "SMS",
          "title": "短信服务",
          "copy": "选择当前项目调用的短信服务。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/sms.html",
          "module": null,
          "modules": []
      },
      "smsAliyun": {
          "route": "sms/aliyun",
          "eyebrow": "SMS / Aliyun",
          "title": "阿里云 Dypnsapi 短信服务",
          "copy": "管理阿里云 Dypnsapi 短信服务的服务器环境变量。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/sms-aliyun.html",
          "module": "AdminSmsConfigModule",
          "modules": [
              "AdminSmsConfigModule"
          ]
      },
      "oss": {
          "route": "oss",
          "eyebrow": "OSS",
          "title": "对象存储服务",
          "copy": "选择当前项目调用的对象存储服务。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/oss.html",
          "module": null,
          "modules": []
      },
      "ossAliyun": {
          "route": "oss/aliyun",
          "eyebrow": "OSS / Aliyun",
          "title": "阿里云 OSS 对象存储服务",
          "copy": "管理阿里云 OSS 对象存储服务的服务器环境变量。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/oss-aliyun.html",
          "module": "AdminOssConfigModule",
          "modules": [
              "AdminOssConfigModule"
          ]
      },
      "ipRisk": {
          "route": "ip-risk",
          "eyebrow": "Risk API",
          "title": "Risk API",
          "copy": "选择当前项目使用的 IP2Location 和 iPing 降级 API。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/ip-risk.html",
          "module": null,
          "modules": []
      },
      "riskApiIp2Location": {
          "route": "ip-risk/ip2location",
          "eyebrow": "Risk API / IP2Location",
          "title": "IP2Location API",
          "copy": "管理 Redis 中的 IP2Location API Keys，API URL 配置按需展开。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/risk-api-ip2-location.html",
          "module": "AdminRiskApiConfigModule",
          "modules": [
              "AdminRiskApiConfigModule",
              "AdminIp2LocationQuotaKeysModule"
          ]
      },
      "riskApiIp2LocationBinLookup": {
          "route": "ip-risk/ip2location/bin",
          "eyebrow": "Risk API / IP2Location / Local BIN",
          "title": "IP2Location BIN 查询",
          "copy": "按 IPv4 通配模式查询本地 IP2Location BIN，不消耗外部 API 额度。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/risk-api-ip2location-bin-lookup.html",
          "module": "AdminIp2LocationBinLookupModule",
          "modules": [
              "AdminIp2LocationBinLookupModule"
          ]
      },
      "riskApiIping": {
          "route": "ip-risk/iping",
          "eyebrow": "Risk API / iPing",
          "title": "iPing 降级 API",
          "copy": "管理 iPing 降级 API 的 Redis 配置。",
          "navTarget": "externalInterfaces",
          "fragment": "/shopping/admin/panels/risk-api-iping.html",
          "module": "AdminRiskApiConfigModule",
          "modules": [
              "AdminRiskApiConfigModule"
          ]
      }
  };

  const sectionRouteMap = {};
  const routeSectionMap = {};
  Object.keys(sections).forEach((sectionName) => {
    const route = sections[sectionName].route;
    sectionRouteMap[sectionName] = route;
    routeSectionMap[route] = sectionName;
  });

  function get(sectionName) {
    return sections[sectionName] || null;
  }

  function has(sectionName) {
    return Boolean(get(sectionName));
  }

  function getSectionsForModules(moduleNames) {
    const names = new Set((moduleNames || []).filter(Boolean));
    if (!names.size) {
      return [];
    }
    return Object.keys(sections).filter((sectionName) => {
      return (sections[sectionName].modules || []).some((moduleName) => names.has(moduleName));
    });
  }

  root.AdminSections = {
    defaultSection,
    consoleBasePath,
    panelOutletId,
    sections,
    sectionRouteMap,
    routeSectionMap,
    get,
    has,
    getSectionsForModules
  };
})(window);
