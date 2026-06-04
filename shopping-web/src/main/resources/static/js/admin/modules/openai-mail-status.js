(function (root) {
  const dom = root.AdminDom;
  const api = root.AdminApi;
  const router = root.AdminRouter;

  const CREATE_JOB_PATH = "/shopping/admin/api/mail/openai/status-check-jobs";
  const DEFAULT_POLL_MILLIS = 2000;
  const DEFAULT_THREAD_POOL_SIZE = 4;
  const MIN_THREAD_POOL_SIZE = 1;
  const MAX_THREAD_POOL_SIZE = 64;
  const STATUS_LABELS = {
    NOT_REGISTERED: "Not registered",
    REGISTERED_NORMAL: "Registered normal",
    DETECTED_EVIDENCE_FOUND: "Detected evidence",
    MICROSOFT_ACCOUNT_ABUSE: "Microsoft abuse",
    DUPLICATE_EMAIL: "Duplicate email",
    INVALID_CREDENTIAL_FORMAT: "Invalid credential",
    TOKEN_REFRESH_FAILED: "Token refresh failed",
    IMAP_AUTH_FAILED: "IMAP auth failed",
    IMAP_ERROR: "IMAP error",
    MAIL_SCAN_TIMEOUT: "Scan timeout"
  };

  let mounted = false;
  let activeJobId = "";
  let pollTimer = null;
  let pollDelayMillis = DEFAULT_POLL_MILLIS;
  let submittedCredentialLines = [];

  function getNodes() {
    return {
      credentials: document.getElementById("admin-openai-mail-credentials"),
      threadPool: document.getElementById("admin-openai-mail-thread-pool"),
      importFile: document.getElementById("admin-openai-mail-file"),
      importButton: document.getElementById("admin-openai-mail-import"),
      runButton: document.getElementById("admin-openai-mail-run"),
      clearButton: document.getElementById("admin-openai-mail-clear"),
      status: document.getElementById("admin-openai-mail-status"),
      jobId: document.getElementById("admin-openai-mail-job-id"),
      jobState: document.getElementById("admin-openai-mail-job-state"),
      requested: document.getElementById("admin-openai-mail-requested"),
      processed: document.getElementById("admin-openai-mail-processed"),
      running: document.getElementById("admin-openai-mail-running"),
      queued: document.getElementById("admin-openai-mail-queued"),
      threads: document.getElementById("admin-openai-mail-threads"),
      elapsed: document.getElementById("admin-openai-mail-elapsed"),
      summaryNotRegistered: document.getElementById("admin-openai-mail-summary-not-registered"),
      summaryRegisteredNormal: document.getElementById("admin-openai-mail-summary-registered-normal"),
      summaryDetected: document.getElementById("admin-openai-mail-summary-detected"),
      summaryDuplicate: document.getElementById("admin-openai-mail-summary-duplicate"),
      summaryFailed: document.getElementById("admin-openai-mail-summary-failed"),
      resultsCount: document.getElementById("admin-openai-mail-results-count"),
      results: document.getElementById("admin-openai-mail-results"),
      normalCredentials: document.getElementById("admin-openai-mail-normal-credentials"),
      normalCopyButton: document.getElementById("admin-openai-mail-normal-copy"),
      normalCount: document.getElementById("admin-openai-mail-normal-count"),
      imapAuthCredentials: document.getElementById("admin-openai-mail-imap-auth-credentials"),
      imapAuthCopyButton: document.getElementById("admin-openai-mail-imap-auth-copy"),
      imapAuthCount: document.getElementById("admin-openai-mail-imap-auth-count")
    };
  }

  function credentialLines(textarea) {
    return credentialLinesFromText(textarea?.value || "");
  }

  function credentialLinesFromText(text) {
    return String(text || "")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);
  }

  function normalizeThreadPoolInput(input) {
    const rawValue = String(input?.value || "").trim();
    let value = rawValue ? Number(rawValue) : DEFAULT_THREAD_POOL_SIZE;
    if (!Number.isFinite(value)) {
      value = DEFAULT_THREAD_POOL_SIZE;
    }
    value = Math.trunc(value);
    value = Math.min(MAX_THREAD_POOL_SIZE, Math.max(MIN_THREAD_POOL_SIZE, value));
    if (input && String(input.value) !== String(value)) {
      input.value = String(value);
    }
    return value;
  }

  function requestedThreadPool(input) {
    return normalizeThreadPoolInput(input);
  }

  function readTextFile(file) {
    if (typeof file?.text === "function") {
      return file.text();
    }
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.addEventListener("load", () => resolve(String(reader.result || "")));
      reader.addEventListener("error", () => reject(reader.error || new Error("Failed to read txt file.")));
      reader.readAsText(file);
    });
  }

  function setCopyButtonState(button, count) {
    if (button) {
      button.disabled = count <= 0;
    }
  }

  function setBusy(busy) {
    const nodes = getNodes();
    if (nodes.runButton) {
      nodes.runButton.disabled = busy;
    }
    if (nodes.credentials) {
      nodes.credentials.disabled = busy;
    }
    if (nodes.threadPool) {
      nodes.threadPool.disabled = busy;
    }
    if (nodes.importButton) {
      nodes.importButton.disabled = busy;
    }
    if (nodes.importFile) {
      nodes.importFile.disabled = busy;
    }
  }

  function stopPolling() {
    if (pollTimer) {
      window.clearTimeout(pollTimer);
      pollTimer = null;
    }
  }

  function schedulePoll() {
    stopPolling();
    if (!activeJobId) {
      return;
    }
    pollTimer = window.setTimeout(() => pollJob(activeJobId), pollDelayMillis);
  }

  function formatElapsed(ms) {
    const value = Number(ms || 0);
    if (!Number.isFinite(value) || value <= 0) {
      return "0s";
    }
    const seconds = Math.floor(value / 1000);
    const minutes = Math.floor(seconds / 60);
    const restSeconds = seconds % 60;
    if (minutes > 0) {
      return `${minutes}m ${restSeconds}s`;
    }
    return `${seconds}s`;
  }

  function statusLabel(status) {
    return STATUS_LABELS[status] || status || "-";
  }

  function setJobState(node, status) {
    if (!node) {
      return;
    }
    const normalized = String(status || "IDLE");
    node.textContent = normalized;
    node.dataset.state = normalized;
  }

  function setText(node, value) {
    dom.setText(node, value == null || value === "" ? "-" : String(value));
  }

  function createCell(text, className = "") {
    const cell = document.createElement("span");
    if (className) {
      cell.className = className;
    }
    cell.textContent = text == null || text === "" ? "-" : String(text);
    return cell;
  }

  function createHeader() {
    const row = document.createElement("div");
    row.className = "admin-openai-mail-result-row is-header";
    ["Line", "Email", "Status", "Sender", "Subject", "Received", "Evidence", "Route", "Reason"].forEach((text) => {
      row.append(createCell(text));
    });
    return row;
  }

  function createResultRow(item) {
    const row = document.createElement("div");
    row.className = "admin-openai-mail-result-row";
    row.dataset.status = item?.status || "";
    row.append(
      createCell(item?.lineNumber),
      createCell(item?.email, "is-email"),
      createCell(statusLabel(item?.status), "is-status"),
      createCell(item?.sender),
      createCell(item?.subject),
      createCell(item?.receivedAt),
      createCell(item?.evidencePhrase),
      createCell(item?.imapRoute),
      createCell(item?.reason)
    );
    return row;
  }

  function renderResults(nodes, items) {
    const rows = Array.isArray(items) ? items : [];
    setText(nodes.resultsCount, `${rows.length} rows`);
    if (!nodes.results) {
      return;
    }
    if (!rows.length) {
      const empty = document.createElement("div");
      empty.className = "admin-openai-mail-result-empty";
      empty.textContent = "No result rows yet.";
      nodes.results.replaceChildren(empty);
      return;
    }
    nodes.results.replaceChildren(createHeader(), ...rows.map(createResultRow));
  }

  function credentialLinesForStatus(items, status) {
    const rows = Array.isArray(items) ? items : [];
    return rows
      .filter((item) => item?.status === status)
      .map((item) => submittedCredentialLines[Number(item?.lineNumber || 0) - 1] || "")
      .filter(Boolean);
  }

  function renderCredentialExport(textarea, countNode, copyButton, lines) {
    const values = Array.isArray(lines) ? lines : [];
    if (textarea) {
      textarea.value = values.join("\n");
    }
    setText(countNode, `${values.length} lines`);
    setCopyButtonState(copyButton, values.length);
  }

  function renderCredentialExports(nodes, items) {
    const normalLines = credentialLinesForStatus(items, "REGISTERED_NORMAL");
    const imapAuthLines = credentialLinesForStatus(items, "IMAP_AUTH_FAILED");
    renderCredentialExport(nodes.normalCredentials, nodes.normalCount, nodes.normalCopyButton, normalLines);
    renderCredentialExport(nodes.imapAuthCredentials, nodes.imapAuthCount, nodes.imapAuthCopyButton, imapAuthLines);
  }

  function renderSummary(nodes, summary = {}) {
    setText(nodes.summaryNotRegistered, summary.notRegistered ?? 0);
    setText(nodes.summaryRegisteredNormal, summary.registeredNormal ?? 0);
    setText(nodes.summaryDetected, summary.detectedEvidenceFound ?? 0);
    setText(nodes.summaryDuplicate, summary.duplicate ?? 0);
    setText(nodes.summaryFailed, summary.failed ?? 0);
  }

  function renderJob(data = {}) {
    const nodes = getNodes();
    setText(nodes.jobId, data.jobId || activeJobId || "-");
    setJobState(nodes.jobState, data.status || "IDLE");
    setText(nodes.requested, data.requestedCount ?? 0);
    setText(nodes.processed, data.processedCount ?? 0);
    setText(nodes.running, data.runningCount ?? 0);
    setText(nodes.queued, data.queuedCount ?? 0);
    setText(nodes.threads, data.threadPoolSize ?? 0);
    setText(nodes.elapsed, formatElapsed(data.elapsedMillis));
    renderSummary(nodes, data.summary || {});
    renderResults(nodes, data.results || []);
    renderCredentialExports(nodes, data.results || []);
  }

  function clearView() {
    stopPolling();
    activeJobId = "";
    pollDelayMillis = DEFAULT_POLL_MILLIS;
    submittedCredentialLines = [];
    const nodes = getNodes();
    if (nodes.credentials) {
      nodes.credentials.disabled = false;
    }
    if (nodes.threadPool) {
      nodes.threadPool.disabled = false;
    }
    if (nodes.importButton) {
      nodes.importButton.disabled = false;
    }
    if (nodes.importFile) {
      nodes.importFile.disabled = false;
    }
    if (nodes.runButton) {
      nodes.runButton.disabled = false;
    }
    dom.setStatusNode(nodes.status, "Paste Outlook or Hotmail credentials, one per line. The backend creates one job and runs at most 64 IMAP checks concurrently.");
    renderJob({});
  }

  async function createJob() {
    const nodes = getNodes();
    const lines = credentialLines(nodes.credentials);
    stopPolling();
    activeJobId = "";
    submittedCredentialLines = lines;
    setBusy(true);
    renderJob({});
    dom.setStatusNode(nodes.status, "Creating OpenAI mailbox status job...");
    try {
      const response = await api.request(CREATE_JOB_PATH, {
        credentialLines: lines,
        threadPoolSize: requestedThreadPool(nodes.threadPool)
      });
      const data = response.data || {};
      activeJobId = data.jobId || "";
      pollDelayMillis = Math.max(1000, Number(data.pollAfterMillis || DEFAULT_POLL_MILLIS));
      setJobState(nodes.jobState, data.status || "RUNNING");
      setText(nodes.jobId, activeJobId || "-");
      setText(nodes.requested, data.requestedCount ?? 0);
      setText(nodes.processed, 0);
      setText(nodes.running, 0);
      setText(nodes.queued, data.acceptedCount ?? 0);
      setText(nodes.threads, data.threadPoolSize ?? 0);
      dom.setStatusNode(nodes.status, `Job created. Backend accepted ${data.acceptedCount ?? 0} accounts with ${data.threadPoolSize ?? "-"} threads.`, "ok");
      await pollJob(activeJobId);
    } catch (error) {
      setBusy(false);
      dom.setStatusNode(nodes.status, error.message || "Failed to create OpenAI mailbox status job.", "error");
    }
  }

  async function importCredentialFile() {
    const nodes = getNodes();
    const file = nodes.importFile?.files?.[0];
    if (!file) {
      return;
    }
    if (file.name && !/\.txt$/i.test(file.name) && file.type && !/^text\//i.test(file.type)) {
      nodes.importFile.value = "";
      dom.setStatusNode(nodes.status, "Please choose a txt credential file.", "error");
      return;
    }
    try {
      dom.setStatusNode(nodes.status, `Reading ${file.name || "txt file"}...`);
      const text = String(await readTextFile(file)).replace(/^\uFEFF/, "");
      if (nodes.credentials) {
        nodes.credentials.value = text;
      }
      clearView();
      const lineCount = credentialLinesFromText(text).length;
      const type = lineCount > 0 ? "ok" : "error";
      dom.setStatusNode(nodes.status, `Imported ${lineCount} credential lines from ${file.name || "txt file"}.`, type);
    } catch (error) {
      dom.setStatusNode(nodes.status, error.message || "Failed to import txt credential file.", "error");
    } finally {
      if (nodes.importFile) {
        nodes.importFile.value = "";
      }
    }
  }

  async function copyCredentialExport(textarea, emptyMessage, copiedMessage) {
    const nodes = getNodes();
    const text = String(textarea?.value || "");
    if (!text) {
      dom.setStatusNode(nodes.status, emptyMessage, "error");
      return;
    }
    try {
      if (typeof navigator.clipboard?.writeText === "function") {
        await navigator.clipboard.writeText(text);
      } else {
        textarea?.focus();
        textarea?.select();
        document.execCommand?.("copy");
      }
      dom.setStatusNode(nodes.status, copiedMessage, "ok");
    } catch (_) {
      textarea?.focus();
      textarea?.select();
      document.execCommand?.("copy");
      dom.setStatusNode(nodes.status, copiedMessage, "ok");
    }
  }

  async function pollJob(jobId) {
    if (!jobId) {
      setBusy(false);
      return;
    }
    try {
      const response = await api.get(`${CREATE_JOB_PATH}/${encodeURIComponent(jobId)}`);
      const data = response.data || {};
      renderJob(data);
      if (data.status === "COMPLETED") {
        stopPolling();
        activeJobId = "";
        setBusy(false);
        dom.setStatusNode(getNodes().status, `Job completed. Processed ${data.processedCount ?? 0} rows.`, "ok");
        return;
      }
      dom.setStatusNode(getNodes().status, `Job running. Processed ${data.processedCount ?? 0}/${data.requestedCount ?? 0}.`);
      schedulePoll();
    } catch (error) {
      stopPolling();
      activeJobId = "";
      setBusy(false);
      dom.setStatusNode(getNodes().status, error.message || "Failed to poll OpenAI mailbox status job.", "error");
    }
  }

  function mount() {
    if (mounted) {
      return;
    }
    mounted = true;
    const nodes = getNodes();
    nodes.threadPool?.addEventListener("change", () => normalizeThreadPoolInput(nodes.threadPool));
    nodes.threadPool?.addEventListener("blur", () => normalizeThreadPoolInput(nodes.threadPool));
    nodes.importButton?.addEventListener("click", () => {
      dom.playPress(nodes.importButton);
      nodes.importFile?.click();
    });
    nodes.importFile?.addEventListener("change", () => {
      importCredentialFile();
    });
    nodes.runButton?.addEventListener("click", () => {
      dom.playPress(nodes.runButton);
      createJob();
    });
    nodes.clearButton?.addEventListener("click", () => {
      dom.playPress(nodes.clearButton);
      if (nodes.credentials) {
        nodes.credentials.value = "";
      }
      clearView();
    });
    nodes.normalCopyButton?.addEventListener("click", () => {
      dom.playPress(nodes.normalCopyButton);
      copyCredentialExport(
        nodes.normalCredentials,
        "No REGISTERED_NORMAL credential lines to copy.",
        "REGISTERED_NORMAL credential lines copied."
      );
    });
    nodes.imapAuthCopyButton?.addEventListener("click", () => {
      dom.playPress(nodes.imapAuthCopyButton);
      copyCredentialExport(
        nodes.imapAuthCredentials,
        "No IMAP_AUTH_FAILED credential lines to copy.",
        "IMAP_AUTH_FAILED credential lines copied."
      );
    });
    router.register("openAiMailStatus", () => {
      if (!activeJobId) {
        renderJob({});
      }
    });
  }

  root.AdminOpenAiMailStatusModule = { mount };
})(window);
