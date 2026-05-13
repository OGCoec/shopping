(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;

  function getNodes(provider) {
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

  function setStatus(nodes, message, type = "") {
    dom.setText(nodes.status, message);
    nodes.status?.classList.toggle("is-error", type === "error");
    nodes.status?.classList.toggle("is-ok", type === "ok");
  }

  async function load(provider, label) {
    const nodes = getNodes(provider);
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    setStatus(nodes, `正在读取 ${label} OAuth2 配置...`);
    try {
      const response = await api.get(`/shopping/admin/api/oauth2/${provider}/config`);
      const data = response.data || {};
      dom.renderOAuthField(nodes.clientIdValue, nodes.clientIdMeta, data.clientId);
      dom.renderOAuthField(nodes.clientSecretValue, nodes.clientSecretMeta, data.clientSecret);
      nodes.fields?.removeAttribute("hidden");
      nodes.form?.removeAttribute("hidden");
      setStatus(nodes, "已读取脱敏 clientId 和 clientSecret。", "ok");
    } catch (error) {
      setStatus(nodes, error.message || `读取 ${label} OAuth2 配置失败。`, "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  function clearInputs(nodes) {
    if (nodes.clientIdInput) {
      nodes.clientIdInput.value = "";
    }
    if (nodes.clientSecretInput) {
      nodes.clientSecretInput.value = "";
    }
  }

  async function save(provider, label) {
    const nodes = getNodes(provider);
    const clientId = nodes.clientIdInput?.value || "";
    const clientSecret = nodes.clientSecretInput?.value || "";
    if (!clientId.trim() && !clientSecret.trim()) {
      setStatus(nodes, "请至少填写一个需要修改的配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    setStatus(nodes, `正在保存 ${label} OAuth2 环境变量...`);
    try {
      const response = await api.request(`/shopping/admin/api/oauth2/${provider}/config`, {
        clientId,
        clientSecret
      });
      const data = response.data || {};
      dom.renderOAuthField(nodes.clientIdValue, nodes.clientIdMeta, data.clientId);
      dom.renderOAuthField(nodes.clientSecretValue, nodes.clientSecretMeta, data.clientSecret);
      nodes.fields?.removeAttribute("hidden");
      nodes.form?.removeAttribute("hidden");
      clearInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      setStatus(nodes, `已保存到 ${target}，重启应用后 OAuth2 登录客户端生效。`, "ok");
    } catch (error) {
      setStatus(nodes, error.message || `保存 ${label} OAuth2 配置失败。`, "error");
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
    bind("github", "GitHub");
    bind("google", "Google");
    bind("microsoft", "Microsoft");
  }

  root.AdminOAuthConfigModule = { mount };
})(window);
