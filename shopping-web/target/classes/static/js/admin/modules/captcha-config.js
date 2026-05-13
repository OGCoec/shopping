(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;

  function getNodes(provider) {
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

  function setStatus(nodes, message, type = "") {
    dom.setText(nodes.status, message);
    nodes.status?.classList.toggle("is-error", type === "error");
    nodes.status?.classList.toggle("is-ok", type === "ok");
  }

  function formatMeta(field) {
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

  function renderField(valueNode, metaNode, field) {
    dom.setText(valueNode, field?.maskedValue || "未配置");
    dom.setText(metaNode, formatMeta(field));
  }

  function renderConfig(nodes, data) {
    renderField(nodes.siteKeyValue, nodes.siteKeyMeta, data.siteKey);
    renderField(nodes.secretKeyValue, nodes.secretKeyMeta, data.secretKey);
    nodes.fields?.removeAttribute("hidden");
    nodes.form?.removeAttribute("hidden");
  }

  async function load(provider, label) {
    const nodes = getNodes(provider);
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    setStatus(nodes, `正在读取 ${label} 验证码配置...`);
    try {
      const response = await api.get(`/shopping/admin/api/captcha/${provider}/config`);
      renderConfig(nodes, response.data || {});
      setStatus(nodes, "已读取脱敏 siteKey 和 secretKey。", "ok");
    } catch (error) {
      setStatus(nodes, error.message || `读取 ${label} 验证码配置失败。`, "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  function clearInputs(nodes) {
    if (nodes.siteKeyInput) {
      nodes.siteKeyInput.value = "";
    }
    if (nodes.secretKeyInput) {
      nodes.secretKeyInput.value = "";
    }
  }

  async function save(provider, label) {
    const nodes = getNodes(provider);
    const siteKey = nodes.siteKeyInput?.value || "";
    const secretKey = nodes.secretKeyInput?.value || "";
    if (!siteKey.trim() && !secretKey.trim()) {
      setStatus(nodes, "请至少填写一个需要修改的验证码配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    setStatus(nodes, `正在保存 ${label} 验证码环境变量...`);
    try {
      const response = await api.request(`/shopping/admin/api/captcha/${provider}/config`, {
        siteKey,
        secretKey
      });
      const data = response.data || {};
      renderConfig(nodes, data);
      clearInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      setStatus(nodes, `已保存到 ${target}，重启应用后验证码服务生效。`, "ok");
    } catch (error) {
      setStatus(nodes, error.message || `保存 ${label} 验证码配置失败。`, "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function bind(provider, label) {
    const nodes = getNodes(provider);
    nodes.button?.addEventListener("click", () => {
      dom.playPress(nodes.button);
      load(provider, label);
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      dom.playPress(nodes.saveButton);
      save(provider, label);
    });
  }

  function mount() {
    bind("turnstile", "Cloudflare Turnstile");
    bind("recaptcha", "Google reCAPTCHA");
    bind("hcaptcha", "hCaptcha");
  }

  root.AdminCaptchaConfigModule = { mount };
})(window);
