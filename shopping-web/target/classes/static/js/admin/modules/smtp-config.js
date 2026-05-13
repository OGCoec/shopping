(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;
  const router = root.AdminRouter;

  function getNodes(provider) {
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

  function formatMeta(field) {
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

  function createFieldRow(field) {
    const row = document.createElement("div");
    row.className = "admin-oauth-config-row";

    const label = document.createElement("span");
    label.textContent = field?.label || "-";

    const content = document.createElement("div");
    const value = document.createElement("strong");
    value.textContent = field?.maskedValue || "未配置";
    const meta = document.createElement("small");
    meta.textContent = formatMeta(field);

    content.append(value, meta);
    row.append(label, content);
    return row;
  }

  function renderFields(container, fields) {
    if (!container) {
      return;
    }
    const rows = Array.isArray(fields) ? fields.map(createFieldRow) : [];
    container.replaceChildren(...rows);
    container.toggleAttribute("hidden", rows.length === 0);
  }

  async function loadProviders() {
    const statusNode = document.getElementById("admin-smtp-provider-status");
    dom.setStatusNode(statusNode, "正在读取当前 SMTP 服务商...");
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
        dom.setText(small, provider.description);
      });
      dom.setStatusNode(statusNode, `当前 SMTP 服务商：${data.currentProviderDisplayName || "未识别"}`, "ok");
    } catch (error) {
      dom.setStatusNode(statusNode, error.message || "读取当前 SMTP 服务商失败。", "error");
    }
  }

  async function loadConfig(provider) {
    const nodes = getNodes(provider);
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    dom.setStatusNode(nodes.status, "正在读取 SMTP 配置...");
    try {
      const response = await api.get(`/shopping/admin/api/smtp/${provider}/config`);
      const data = response.data || {};
      renderFields(nodes.fields, data.fields);
      nodes.form?.removeAttribute("hidden");
      const status = data.current
        ? `已读取当前 ${data.displayName} SMTP 配置。`
        : `该服务商未启用，当前使用：${data.currentProviderDisplayName || "未识别"}`;
      dom.setStatusNode(nodes.status, status, data.current ? "ok" : "");
    } catch (error) {
      renderFields(nodes.fields, []);
      dom.setStatusNode(nodes.status, error.message || "读取 SMTP 配置失败。", "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  function clearInputs(nodes) {
    if (nodes.usernameInput) {
      nodes.usernameInput.value = "";
    }
    if (nodes.passwordInput) {
      nodes.passwordInput.value = "";
    }
  }

  async function saveConfig(provider) {
    const nodes = getNodes(provider);
    const username = nodes.usernameInput?.value || "";
    const password = nodes.passwordInput?.value || "";
    if (!username.trim() && !password.trim()) {
      dom.setStatusNode(nodes.status, "请至少填写一个需要修改的 SMTP 配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    dom.setStatusNode(nodes.status, "正在保存 QQ 邮箱 SMTP Windows 系统环境变量...");
    try {
      const response = await api.request(`/shopping/admin/api/smtp/${provider}/config`, {
        username,
        password
      });
      const data = response.data || {};
      renderFields(nodes.fields, data.fields);
      nodes.form?.removeAttribute("hidden");
      clearInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      dom.setStatusNode(nodes.status, `已保存到 ${target}，重启应用后 SMTP 客户端生效。`, "ok");
    } catch (error) {
      dom.setStatusNode(nodes.status, error.message || "保存 QQ 邮箱 SMTP 配置失败。", "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function bindConfig(provider) {
    const nodes = getNodes(provider);
    nodes.button?.addEventListener("click", () => {
      dom.playPress(nodes.button);
      loadConfig(provider);
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      dom.playPress(nodes.saveButton);
      saveConfig(provider);
    });
  }

  function mount() {
    bindConfig("qq");
    router.register("smtp", () => loadProviders());
    router.register("smtpQq", () => loadConfig("qq"));
  }

  root.AdminSmtpConfigModule = { mount };
})(window);
