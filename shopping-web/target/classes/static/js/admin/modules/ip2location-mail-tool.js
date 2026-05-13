(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;

  function getNodes() {
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

  function setOpen(open) {
    const nodes = getNodes();
    if (!nodes.shell) { return; }
    nodes.shell.hidden = !open;
    nodes.shell.setAttribute("aria-hidden", String(!open));
    if (open) { window.setTimeout(() => nodes.checkInput?.focus(), 0); }
  }

  function normalizeThreadPoolSize(input) {
    const raw = Number(input?.value || 4);
    const value = Number.isInteger(raw) ? raw : 4;
    const normalized = Math.max(1, Math.min(16, value));
    if (input) { input.value = String(normalized); }
    return normalized;
  }

  function parseCredentialLines(input, maxLines) {
    const lines = (input?.value || "").split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    if (lines.length === 0) { throw new Error("请至少填写一行邮箱凭证。"); }
    if (lines.length > maxLines) { throw new Error(`本次最多处理 ${maxLines} 行邮箱凭证。`); }
    return lines;
  }

  function appendText(parent, text) {
    if (!text) { return; }
    const small = document.createElement("small");
    small.textContent = text;
    parent.append(small);
  }

  function renderResultList(container, items, options = {}) {
    if (!container) { return; }
    const list = document.createElement("div");
    list.className = "admin-ip2location-mail-result-list";
    const rows = Array.isArray(items) ? items : [];
    if (rows.length === 0) {
      const empty = document.createElement("div");
      empty.className = "admin-ip2location-mail-result-item";
      appendText(empty, "暂无结果");
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
      appendText(row, `clientId: ${item.clientId || "-"}`);
      if (item.verifyToken) { appendText(row, `verifyToken: ${item.verifyToken}`); }
      if (item.folderName || item.receivedAt) { appendText(row, `folder: ${item.folderName || "-"} · receivedAt: ${item.receivedAt || "-"}`); }
      if (item.sender || item.subject) { appendText(row, `mail: ${item.sender || "-"} · ${item.subject || "-"}`); }
      appendText(row, `reason: ${item.reason || "-"}`);
      list.append(row);
    });
    container.replaceChildren(list);
  }

  function clearResults() {
    const n = getNodes();
    renderResultList(n.checkRegistered, []);
    renderResultList(n.checkUnregistered, []);
    renderResultList(n.checkFailed, []);
    renderResultList(n.verifyFound, [], { includeVerifyUrl: true });
    renderResultList(n.verifyNotFound, []);
    renderResultList(n.verifyFailed, []);
  }

  async function runRegistrationCheck() {
    const nodes = getNodes();
    let credentialLines;
    try { credentialLines = parseCredentialLines(nodes.checkInput, 100); }
    catch (error) { dom.setStatusNode(nodes.checkStatus, error.message, "error"); return; }
    const threadPoolSize = normalizeThreadPoolSize(nodes.checkThreads);
    nodes.checkRun.disabled = true;
    dom.setStatusNode(nodes.checkStatus, `正在用 ${threadPoolSize} 个线程鉴定 ${credentialLines.length} 个邮箱...`);
    try {
      const response = await api.request("/shopping/admin/api/risk-api/ip2location/registration-check", { credentialLines, threadPoolSize });
      const data = response.data || {};
      renderResultList(nodes.checkRegistered, data.registered || []);
      renderResultList(nodes.checkUnregistered, data.unregistered || []);
      renderResultList(nodes.checkFailed, data.failed || []);
      dom.setStatusNode(nodes.checkStatus, `已完成：已注册 ${(data.registered || []).length}，未注册 ${(data.unregistered || []).length}，失败 ${(data.failed || []).length}。`, "ok");
    } catch (error) {
      dom.setStatusNode(nodes.checkStatus, error.message || "批量鉴定失败。", "error");
    } finally { nodes.checkRun.disabled = false; }
  }

  async function runVerifyLinkRead() {
    const nodes = getNodes();
    let credentialLines;
    try { credentialLines = parseCredentialLines(nodes.verifyInput, 10); }
    catch (error) { dom.setStatusNode(nodes.verifyStatus, error.message, "error"); return; }
    const threadPoolSize = normalizeThreadPoolSize(nodes.verifyThreads);
    nodes.verifyRun.disabled = true;
    dom.setStatusNode(nodes.verifyStatus, `正在用 ${threadPoolSize} 个线程读取 ${credentialLines.length} 个验证 URL...`);
    try {
      const response = await api.request("/shopping/admin/api/risk-api/ip2location/verify-links", { credentialLines, threadPoolSize });
      const data = response.data || {};
      renderResultList(nodes.verifyFound, data.found || [], { includeVerifyUrl: true });
      renderResultList(nodes.verifyNotFound, data.notFound || []);
      renderResultList(nodes.verifyFailed, data.failed || []);
      dom.setStatusNode(nodes.verifyStatus, `已完成：找到 ${(data.found || []).length}，未找到 ${(data.notFound || []).length}，失败 ${(data.failed || []).length}。`, "ok");
    } catch (error) {
      dom.setStatusNode(nodes.verifyStatus, error.message || "批量获取验证 URL 失败。", "error");
    } finally { nodes.verifyRun.disabled = false; }
  }

  function mount() {
    const nodes = getNodes();
    nodes.closeButton?.addEventListener("click", () => { dom.playPress(nodes.closeButton); setOpen(false); });
    nodes.backdrop?.addEventListener("click", () => { setOpen(false); });
    nodes.checkRun?.addEventListener("click", () => { dom.playPress(nodes.checkRun); runRegistrationCheck(); });
    nodes.verifyRun?.addEventListener("click", () => { dom.playPress(nodes.verifyRun); runVerifyLinkRead(); });
  }

  root.AdminIp2LocationMailToolModule = { mount, setOpen, clearResults };
})(window);
