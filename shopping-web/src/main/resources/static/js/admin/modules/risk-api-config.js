(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;
  const router = root.AdminRouter;

  function getNodes(provider) {
    return {
      button: document.getElementById(`admin-risk-api-${provider}-config-load`),
      status: document.getElementById(`admin-risk-api-${provider}-config-status`),
      fields: document.getElementById(`admin-risk-api-${provider}-config-fields`),
      form: document.getElementById(`admin-risk-api-${provider}-config-form`),
      inputs: document.getElementById(`admin-risk-api-${provider}-config-inputs`),
      saveButton: document.getElementById(`admin-risk-api-${provider}-config-save`)
    };
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
    const sensitiveNote = field.sensitive ? " · VALUE: 脱敏显示" : "";
    return `YAML: ${yamlFile}:${yamlLine} · ENV: ${envName} · TARGET: ${windowsEnvTarget} · KEY: ${propertyKey}${sensitiveNote}`;
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

  function createInput(field) {
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

  function renderInputs(container, fields) {
    if (!container) {
      return;
    }
    const inputs = Array.isArray(fields) ? fields.map(createInput) : [];
    container.replaceChildren(...inputs);
  }

  function clearInputs(nodes) {
    nodes.inputs?.querySelectorAll("[data-risk-api-field]").forEach((input) => {
      input.value = "";
    });
  }

  function collectValues(nodes) {
    const values = {};
    nodes.inputs?.querySelectorAll("[data-risk-api-field]").forEach((input) => {
      const fieldId = input.dataset.riskApiField;
      if (fieldId && input.value.trim()) {
        values[fieldId] = input.value;
      }
    });
    return values;
  }

  function renderConfig(nodes, data) {
    const fields = Array.isArray(data.fields) ? data.fields : [];
    renderFields(nodes.fields, fields);
    renderInputs(nodes.inputs, fields);
    nodes.form?.removeAttribute("hidden");
  }

  async function loadConfig(provider, label) {
    const nodes = getNodes(provider);
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    dom.setStatusNode(nodes.status, `正在读取 ${label} 配置...`);
    try {
      const response = await api.get(`/shopping/admin/api/risk-api/${provider}/config`);
      const data = response.data || {};
      renderConfig(nodes, data);
      const prefix = data.propertyPrefix || "-";
      dom.setStatusNode(nodes.status, `已读取 ${prefix} 前缀下的脱敏配置。`, "ok");
    } catch (error) {
      renderFields(nodes.fields, []);
      dom.setStatusNode(nodes.status, error.message || `读取 ${label} 配置失败。`, "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  async function saveConfig(provider, label) {
    const nodes = getNodes(provider);
    const values = collectValues(nodes);
    if (Object.keys(values).length === 0) {
      dom.setStatusNode(nodes.status, "请至少填写一个需要修改的 Risk API 配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    dom.setStatusNode(nodes.status, `正在保存 ${label} Windows 系统环境变量...`);
    try {
      const response = await api.request(`/shopping/admin/api/risk-api/${provider}/config`, { values });
      const data = response.data || {};
      renderConfig(nodes, data);
      clearInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      dom.setStatusNode(nodes.status, `已保存到 ${target}，重启应用后 Risk API 客户端生效。`, "ok");
    } catch (error) {
      dom.setStatusNode(nodes.status, error.message || `保存 ${label} 配置失败。`, "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function bindConfig(provider, label) {
    const nodes = getNodes(provider);
    nodes.button?.addEventListener("click", () => {
      dom.playPress(nodes.button);
      loadConfig(provider, label);
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      dom.playPress(nodes.saveButton);
      saveConfig(provider, label);
    });
  }

  function mount() {
    bindConfig("ip2location", "IP2Location API");
    bindConfig("iping", "iPing 降级 API");
    router.register("riskApiIping", () => loadConfig("iping", "iPing 降级 API"));
  }

  root.AdminRiskApiConfigModule = { mount, loadConfig };
})(window);
