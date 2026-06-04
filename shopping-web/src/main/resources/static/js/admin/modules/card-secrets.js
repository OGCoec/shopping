(function (root) {
  const CONFIG_PATH = "/shopping/admin/api/card-secrets/crypto-config";
  const GENERATE_PATH = "/shopping/admin/api/card-secrets/crypto-config/generate";

  const state = {
    mounted: false,
    busy: false
  };

  const el = {};

  function $(id) {
    return document.getElementById(id);
  }

  function adminApi() {
    if (!root.AdminApi) {
      throw new Error("AdminApi is not ready.");
    }
    return root.AdminApi;
  }

  function mount() {
    if (state.mounted) {
      return;
    }
    const panel = document.querySelector("[data-admin-panel='cardSecrets']");
    if (!panel) {
      return;
    }
    Object.assign(el, {
      load: $("admin-card-secret-config-load"),
      status: $("admin-card-secret-config-status"),
      fields: $("admin-card-secret-config-fields"),
      activeVersionValue: $("admin-card-secret-active-version-value"),
      activeVersionMeta: $("admin-card-secret-active-version-meta"),
      aesValue: $("admin-card-secret-aes-value"),
      aesMeta: $("admin-card-secret-aes-meta"),
      hmacValue: $("admin-card-secret-hmac-value"),
      hmacMeta: $("admin-card-secret-hmac-meta"),
      envTargetValue: $("admin-card-secret-env-target-value"),
      envTargetMeta: $("admin-card-secret-env-target-meta"),
      configForm: $("admin-card-secret-config-form"),
      activeVersionInput: $("admin-card-secret-active-version-input"),
      aesInput: $("admin-card-secret-aes-input"),
      hmacInput: $("admin-card-secret-hmac-input"),
      save: $("admin-card-secret-config-save"),
      generateForm: $("admin-card-secret-generate-form"),
      generateVersionInput: $("admin-card-secret-generate-version-input"),
      generateActivateInput: $("admin-card-secret-generate-activate-input"),
      generateSave: $("admin-card-secret-generate-save")
    });
    if (!el.load || !el.configForm || !el.generateForm) {
      return;
    }
    state.mounted = true;
    bindEvents();
  }

  function bindEvents() {
    el.load.addEventListener("click", () => loadConfig());
    el.configForm.addEventListener("submit", (event) => {
      event.preventDefault();
      saveConfig();
    });
    el.generateForm.addEventListener("submit", (event) => {
      event.preventDefault();
      generateKeys();
    });
    root.AdminRouter?.register?.("cardSecrets", loadConfig);
  }

  function setBusy(busy) {
    state.busy = Boolean(busy);
    [el.load, el.save, el.generateSave, el.activeVersionInput, el.aesInput, el.hmacInput, el.generateVersionInput, el.generateActivateInput]
      .forEach((node) => {
        if (node) {
          node.disabled = state.busy;
        }
      });
  }

  function setStatus(message, type = "") {
    if (!el.status) {
      return;
    }
    el.status.textContent = message || "";
    el.status.classList.toggle("is-error", type === "error");
    el.status.classList.toggle("is-ok", type === "ok");
  }

  function setText(node, text) {
    if (node) {
      node.textContent = text == null || text === "" ? "-" : String(text);
    }
  }

  function configuredText(field) {
    return field?.configured ? "已配置" : "未配置";
  }

  function fieldMeta(field) {
    const parts = [];
    if (field?.envName) {
      parts.push(field.envName);
    }
    if (field?.requiredDecodedBytes != null) {
      parts.push(`要求 ${field.requiredDecodedBytes} 字节`);
    }
    if (field?.minDecodedBytes != null) {
      parts.push(`至少 ${field.minDecodedBytes} 字节`);
    }
    parts.push(configuredText(field));
    return parts.join(" / ");
  }

  function renderConfig(data) {
    const config = data || {};
    const aes = config.aesKey || {};
    const hmac = config.hmacKey || {};
    setText(el.activeVersionValue, config.activeKeyVersion || "v1");
    setText(el.activeVersionMeta, config.activeKeyVersionEnvName || "CARD_SECRET_ACTIVE_KEY_VERSION");
    setText(el.aesValue, aes.maskedValue || configuredText(aes));
    setText(el.aesMeta, fieldMeta(aes));
    setText(el.hmacValue, hmac.maskedValue || configuredText(hmac));
    setText(el.hmacMeta, fieldMeta(hmac));
    setText(el.envTargetValue, config.envTarget || config.windowsEnvTarget || "-");
    setText(el.envTargetMeta, config.envStoreType || "-");
    if (el.activeVersionInput && config.activeKeyVersion) {
      el.activeVersionInput.value = config.activeKeyVersion;
    }
    if (el.generateVersionInput && config.activeKeyVersion) {
      el.generateVersionInput.value = config.activeKeyVersion;
    }
  }

  async function loadConfig() {
    if (state.busy) {
      return;
    }
    setBusy(true);
    setStatus("正在读取卡密加密配置...");
    try {
      const response = await adminApi().get(CONFIG_PATH);
      renderConfig(response.data || {});
      setStatus("卡密加密配置已刷新。", "ok");
    } catch (error) {
      setStatus(error.message || "卡密加密配置读取失败。", "error");
    } finally {
      setBusy(false);
    }
  }

  function trimValue(node) {
    return String(node?.value || "").trim();
  }

  function clearSecretInputs() {
    if (el.aesInput) {
      el.aesInput.value = "";
    }
    if (el.hmacInput) {
      el.hmacInput.value = "";
    }
  }

  async function saveConfig() {
    if (state.busy) {
      return;
    }
    const activeKeyVersion = trimValue(el.activeVersionInput);
    const aesKeyBase64 = trimValue(el.aesInput);
    const hmacKeyBase64 = trimValue(el.hmacInput);
    if (!activeKeyVersion || !aesKeyBase64 || !hmacKeyBase64) {
      setStatus("请填写激活版本、AES Key Base64 和 HMAC Key Base64。", "error");
      return;
    }
    setBusy(true);
    setStatus("正在写入卡密加密环境变量...");
    try {
      const response = await adminApi().request(CONFIG_PATH, {
        activeKeyVersion,
        aesKeyBase64,
        hmacKeyBase64
      });
      clearSecretInputs();
      renderConfig(response.data || {});
      setStatus("卡密加密配置已写入，重启后端服务后稳定生效。", "ok");
    } catch (error) {
      setStatus(error.message || "卡密加密配置保存失败。", "error");
    } finally {
      setBusy(false);
    }
  }

  async function generateKeys() {
    if (state.busy) {
      return;
    }
    const keyVersion = trimValue(el.generateVersionInput);
    if (!keyVersion) {
      setStatus("请填写需要生成的密钥版本。", "error");
      return;
    }
    setBusy(true);
    setStatus("正在生成并写入卡密加密密钥...");
    try {
      const response = await adminApi().request(GENERATE_PATH, {
        keyVersion,
        activate: Boolean(el.generateActivateInput?.checked)
      });
      clearSecretInputs();
      renderConfig(response.data || {});
      setStatus("新密钥已生成并写入，重启后端服务后稳定生效。", "ok");
    } catch (error) {
      setStatus(error.message || "生成卡密加密密钥失败。", "error");
    } finally {
      setBusy(false);
    }
  }

  root.AdminCardSecretsModule = { mount };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mount);
  } else {
    mount();
  }
})(window);
