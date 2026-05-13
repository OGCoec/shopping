(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;

  function getNodes() {
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

  function setStatus(nodes, message, type = "") {
    dom.setText(nodes.status, message);
    nodes.status?.classList.toggle("is-error", type === "error");
    nodes.status?.classList.toggle("is-ok", type === "ok");
  }

  function renderConfig(nodes, data) {
    dom.renderOAuthField(nodes.accessKeyIdValue, nodes.accessKeyIdMeta, data.accessKeyId);
    dom.renderOAuthField(nodes.accessKeySecretValue, nodes.accessKeySecretMeta, data.accessKeySecret);
    nodes.fields?.removeAttribute("hidden");
    nodes.form?.removeAttribute("hidden");
  }

  async function load() {
    const nodes = getNodes();
    if (!nodes.button) {
      return;
    }
    nodes.button.disabled = true;
    setStatus(nodes, "正在读取阿里云 OSS 配置...");
    try {
      const response = await api.get("/shopping/admin/api/oss/aliyun/config");
      renderConfig(nodes, response.data || {});
      setStatus(nodes, "已读取脱敏 accessKeyId 和 accessKeySecret。", "ok");
    } catch (error) {
      setStatus(nodes, error.message || "读取阿里云 OSS 配置失败。", "error");
    } finally {
      nodes.button.disabled = false;
    }
  }

  function clearInputs(nodes) {
    if (nodes.accessKeyIdInput) {
      nodes.accessKeyIdInput.value = "";
    }
    if (nodes.accessKeySecretInput) {
      nodes.accessKeySecretInput.value = "";
    }
  }

  async function save() {
    const nodes = getNodes();
    const accessKeyId = nodes.accessKeyIdInput?.value || "";
    const accessKeySecret = nodes.accessKeySecretInput?.value || "";
    if (!accessKeyId.trim() && !accessKeySecret.trim()) {
      setStatus(nodes, "请至少填写一个需要修改的 OSS 配置值。", "error");
      return;
    }
    if (nodes.saveButton) {
      nodes.saveButton.disabled = true;
    }
    if (nodes.button) {
      nodes.button.disabled = true;
    }
    setStatus(nodes, "正在保存阿里云 OSS Windows 系统环境变量...");
    try {
      const response = await api.request("/shopping/admin/api/oss/aliyun/config", {
        accessKeyId,
        accessKeySecret
      });
      const data = response.data || {};
      renderConfig(nodes, data);
      clearInputs(nodes);
      const target = data.windowsEnvTarget || "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
      setStatus(nodes, `已保存到 ${target}，重启应用后 OSS 客户端生效。`, "ok");
    } catch (error) {
      setStatus(nodes, error.message || "保存阿里云 OSS 配置失败。", "error");
    } finally {
      if (nodes.saveButton) {
        nodes.saveButton.disabled = false;
      }
      if (nodes.button) {
        nodes.button.disabled = false;
      }
    }
  }

  function mount() {
    const nodes = getNodes();
    nodes.button?.addEventListener("click", () => {
      dom.playPress(nodes.button);
      load();
    });
    nodes.form?.addEventListener("submit", (event) => {
      event.preventDefault();
      dom.playPress(nodes.saveButton);
      save();
    });
  }

  root.AdminOssConfigModule = { mount };
})(window);
