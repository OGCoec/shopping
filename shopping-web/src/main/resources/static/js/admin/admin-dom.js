(function (root) {
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

  function setStatusNode(node, message, type = "") {
    setText(node, message);
    node?.classList.toggle("is-error", type === "error");
    node?.classList.toggle("is-ok", type === "ok");
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

  root.AdminDom = {
    setText,
    playPress,
    setStatusNode,
    formatOAuthMeta,
    renderOAuthField
  };
})(window);
