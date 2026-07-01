(function () {
  const STORAGE_KEY = "shopping.ai.chat.session.v1";
  const MODELS_PATH = "/shopping/user/api/ai/models";
  const STREAM_PATH = "/shopping/user/api/ai/chat/stream";
  const COMPRESS_PATH = "/shopping/user/api/ai/chat/compress";
  const DEFAULT_TRIGGER_TOKENS = 52000;
  const FALLBACK_MODEL = "kiro-haiku-4.5";

  const state = loadState();
  let compressionTriggerTokens = DEFAULT_TRIGGER_TOKENS;
  let sending = false;

  const root = document.createElement("section");
  root.className = "console-ai-root";
  root.innerHTML = [
    '<button class="console-ai-launcher" type="button" aria-expanded="false">AI 购物助手</button>',
    '<aside class="console-ai-panel" aria-label="AI 购物助手" aria-hidden="true">',
    '  <header class="console-ai-header">',
    '    <div>',
    '      <h2 class="console-ai-title">问商品、热点和券</h2>',
    '      <p class="console-ai-subtitle">短期上下文保存在当前浏览器 sessionStorage，关闭会话后不进服务器存储。</p>',
    '    </div>',
    '    <div class="console-ai-header-actions">',
    '      <button class="console-ai-clear" type="button">清空</button>',
    '      <button class="console-ai-icon-button" type="button" aria-label="关闭 AI 对话框">×</button>',
    '    </div>',
    '  </header>',
    '  <div class="console-ai-model-row">',
    '    <label for="console-ai-model">模型</label>',
    '    <select class="console-ai-model-select" id="console-ai-model"></select>',
    '  </div>',
    '  <div class="console-ai-messages" role="log" aria-live="polite"></div>',
    '  <div class="console-ai-status"></div>',
    '  <form class="console-ai-form">',
    '    <textarea class="console-ai-input" rows="2" placeholder="例如：热点商品有哪些？这张优惠券还剩多少？"></textarea>',
    '    <button class="console-ai-send" type="submit">发送</button>',
    '  </form>',
    '</aside>'
  ].join("");
  document.body.appendChild(root);

  const launcher = root.querySelector(".console-ai-launcher");
  const panel = root.querySelector(".console-ai-panel");
  const closeButton = root.querySelector(".console-ai-icon-button");
  const clearButton = root.querySelector(".console-ai-clear");
  const modelSelect = root.querySelector(".console-ai-model-select");
  const messagesEl = root.querySelector(".console-ai-messages");
  const statusEl = root.querySelector(".console-ai-status");
  const form = root.querySelector(".console-ai-form");
  const input = root.querySelector(".console-ai-input");
  const sendButton = root.querySelector(".console-ai-send");

  launcher.addEventListener("click", () => setOpen(!panel.classList.contains("is-open")));
  closeButton.addEventListener("click", () => setOpen(false));
  clearButton.addEventListener("click", clearConversation);
  modelSelect.addEventListener("change", () => {
    state.modelKey = modelSelect.value || FALLBACK_MODEL;
    saveState();
  });
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      form.requestSubmit();
    }
  });
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    sendMessage();
  });

  renderMessages();
  setBusy(false);
  loadModels();

  function setOpen(open) {
    panel.classList.toggle("is-open", open);
    panel.setAttribute("aria-hidden", open ? "false" : "true");
    launcher.setAttribute("aria-expanded", open ? "true" : "false");
    if (open) {
      input.focus();
      scrollToBottom();
    }
  }

  async function loadModels() {
    const client = authClient();
    if (!client?.fetchWithAuth) {
      setStatus("认证客户端不可用，无法加载 AI 模型。", true);
      return;
    }
    try {
      const response = await client.fetchWithAuth(MODELS_PATH, {
        method: "GET",
        credentials: "same-origin",
        headers: { Accept: "application/json" }
      });
      if (!response.ok) {
        setStatus("AI 模型列表加载失败。", true);
        return;
      }
      const payload = await response.json();
      compressionTriggerTokens = payload?.compressionTriggerTokens || DEFAULT_TRIGGER_TOKENS;
      const models = Array.isArray(payload?.models) ? payload.models : [];
      renderModels(models, payload?.defaultModelKey || FALLBACK_MODEL);
    } catch (_) {
      setStatus("AI 模型列表加载失败。", true);
    }
  }

  function renderModels(models, defaultModelKey) {
    modelSelect.innerHTML = "";
    const enabledModels = models.length ? models : [{ modelKey: FALLBACK_MODEL, displayName: "Kiro Haiku 4.5" }];
    enabledModels.forEach((model) => {
      const option = document.createElement("option");
      option.value = model.modelKey;
      option.textContent = model.displayName || model.modelKey;
      modelSelect.appendChild(option);
    });
    if (!state.modelKey || !enabledModels.some((model) => model.modelKey === state.modelKey)) {
      state.modelKey = defaultModelKey || enabledModels[0].modelKey;
      saveState();
    }
    modelSelect.value = state.modelKey;
  }

  async function sendMessage() {
    if (sending) {
      return;
    }
    const text = input.value.trim();
    if (!text) {
      return;
    }
    input.value = "";
    state.messages.push({ role: "user", content: text });
    saveState();
    renderMessages();
    await sendCurrentContext(false);
  }

  async function sendCurrentContext(retriedAfterCompression) {
    const client = authClient();
    if (!client?.fetchWithAuth) {
      setStatus("认证客户端不可用。", true);
      return;
    }
    setBusy(true);
    setStatus("正在整理上下文...");
    try {
      await compressIfNeeded();
      const assistantMessage = { role: "assistant", content: "" };
      state.messages.push(assistantMessage);
      renderMessages();
      setStatus("AI 正在查询业务数据...");
      const response = await client.fetchWithAuth(STREAM_PATH, {
        method: "POST",
        credentials: "same-origin",
        headers: {
          Accept: "text/event-stream",
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          modelKey: state.modelKey || FALLBACK_MODEL,
          messages: state.messages.filter((message) => message.content && message.content.trim()),
          summary: state.summary || "",
          clientConversationId: state.clientConversationId
        })
      });
      if (!response.ok) {
        const errorText = await response.text().catch(() => "");
        if (response.status === 413 && !retriedAfterCompression) {
          state.messages = state.messages.filter((message) => message !== assistantMessage);
          await compressNow();
          await sendCurrentContext(true);
          return;
        }
        assistantMessage.content = readableError(errorText) || "AI 请求失败，请稍后再试。";
        setStatus(assistantMessage.content, true);
        saveState();
        renderMessages();
        return;
      }
      setStatus("AI 正在回答...");
      await readEventStream(response, (eventName, data) => {
        if (eventName === "message") {
          assistantMessage.content += data;
          renderMessages();
          saveState();
        } else if (eventName === "error") {
          const payload = parseJson(data);
          assistantMessage.content = payload?.message || "AI 服务暂时不可用，请稍后再试。";
          setStatus(assistantMessage.content, true);
          renderMessages();
          saveState();
        } else if (eventName === "done") {
          setStatus("回答完成。");
        }
      });
      if (!assistantMessage.content.trim()) {
        assistantMessage.content = "没有收到 AI 回答，请稍后再试。";
      }
      saveState();
      renderMessages();
    } catch (_) {
      setStatus("AI 对话失败，请稍后再试。", true);
      const last = state.messages[state.messages.length - 1];
      if (last?.role === "assistant" && !last.content) {
        last.content = "AI 对话失败，请稍后再试。";
        saveState();
        renderMessages();
      }
    } finally {
      setBusy(false);
    }
  }

  async function compressIfNeeded() {
    if (estimateTokens(state.summary || "", state.messages) < compressionTriggerTokens) {
      return;
    }
    await compressNow();
  }

  async function compressNow() {
    const client = authClient();
    if (!client?.fetchWithAuth || state.messages.length <= 20) {
      return;
    }
    setStatus("上下文较长，正在压缩旧消息...");
    const response = await client.fetchWithAuth(COMPRESS_PATH, {
      method: "POST",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        modelKey: state.modelKey || FALLBACK_MODEL,
        messages: state.messages.filter((message) => message.content && message.content.trim()),
        summary: state.summary || ""
      })
    });
    if (!response.ok) {
      throw new Error("Compression failed");
    }
    const payload = await response.json();
    state.summary = payload?.summary || state.summary || "";
    state.messages = Array.isArray(payload?.retainedMessages)
      ? payload.retainedMessages
      : state.messages.slice(-20);
    saveState();
    renderMessages();
  }

  async function readEventStream(response, onEvent) {
    const reader = response.body?.getReader?.();
    if (!reader) {
      return;
    }
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    while (true) {
      const result = await reader.read();
      if (result.done) {
        break;
      }
      buffer += decoder.decode(result.value, { stream: true });
      buffer = consumeSseBuffer(buffer, onEvent);
    }
    buffer += decoder.decode();
    consumeSseBuffer(buffer + "\n\n", onEvent);
  }

  function consumeSseBuffer(buffer, onEvent) {
    let normalized = buffer.replace(/\r\n/g, "\n");
    let boundary = normalized.indexOf("\n\n");
    while (boundary >= 0) {
      const block = normalized.substring(0, boundary);
      normalized = normalized.substring(boundary + 2);
      dispatchSseBlock(block, onEvent);
      boundary = normalized.indexOf("\n\n");
    }
    return normalized;
  }

  function dispatchSseBlock(block, onEvent) {
    if (!block.trim()) {
      return;
    }
    let eventName = "message";
    const dataLines = [];
    block.split("\n").forEach((line) => {
      if (line.startsWith("event:")) {
        eventName = line.substring("event:".length).trim() || "message";
      } else if (line.startsWith("data:")) {
        dataLines.push(line.substring("data:".length).replace(/^ /, ""));
      }
    });
    onEvent(eventName, dataLines.join("\n"));
  }

  function renderMessages() {
    messagesEl.innerHTML = "";
    if (!state.messages.length) {
      const empty = document.createElement("div");
      empty.className = "console-ai-empty";
      const title = document.createElement("strong");
      title.textContent = "可以直接问库存、时间和规则";
      const detail = document.createElement("span");
      detail.textContent = "例如：商品分类有哪些？热点商品有哪些？这张优惠券使用规则是什么？";
      empty.append(title, detail);
      messagesEl.appendChild(empty);
      return;
    }
    state.messages.forEach((message) => {
      const row = document.createElement("article");
      const role = message.role === "user" ? "user" : "assistant";
      row.className = `console-ai-message console-ai-message--${role}`;
      const label = document.createElement("div");
      label.className = "console-ai-message-label";
      label.textContent = role === "user" ? "你" : "AI";
      const bubble = document.createElement("div");
      bubble.className = "console-ai-bubble";
      bubble.textContent = message.content || " ";
      row.append(label, bubble);
      messagesEl.appendChild(row);
    });
    scrollToBottom();
  }

  function clearConversation() {
    if (sending) {
      return;
    }
    state.messages = [];
    state.summary = "";
    state.clientConversationId = createConversationId();
    saveState();
    renderMessages();
    setStatus("已清空当前浏览器会话。");
  }

  function setBusy(value) {
    sending = value;
    input.disabled = value;
    sendButton.disabled = value;
    modelSelect.disabled = value;
  }

  function setStatus(message, error) {
    statusEl.textContent = message || "";
    statusEl.classList.toggle("is-error", Boolean(error));
  }

  function scrollToBottom() {
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function authClient() {
    return window.ShoppingAuthClient || null;
  }

  function loadState() {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : {};
      return {
        messages: Array.isArray(parsed.messages) ? parsed.messages : [],
        summary: typeof parsed.summary === "string" ? parsed.summary : "",
        modelKey: typeof parsed.modelKey === "string" ? parsed.modelKey : FALLBACK_MODEL,
        clientConversationId: typeof parsed.clientConversationId === "string"
          ? parsed.clientConversationId
          : createConversationId()
      };
    } catch (_) {
      return {
        messages: [],
        summary: "",
        modelKey: FALLBACK_MODEL,
        clientConversationId: createConversationId()
      };
    }
  }

  function saveState() {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  function createConversationId() {
    if (window.crypto?.randomUUID) {
      return window.crypto.randomUUID();
    }
    return `ai-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function estimateTokens(summary, messages) {
    let total = estimateTextTokens(summary);
    messages.forEach((message) => {
      total += 4 + estimateTextTokens(message.content || "");
    });
    return total;
  }

  function estimateTextTokens(text) {
    let ascii = 0;
    let nonAscii = 0;
    String(text || "").split("").forEach((char) => {
      if (/\s/.test(char)) {
        return;
      }
      if (char.charCodeAt(0) <= 127) {
        ascii += 1;
      } else {
        nonAscii += 1;
      }
    });
    return Math.max(1, Math.ceil(ascii / 4) + nonAscii);
  }

  function parseJson(value) {
    try {
      return JSON.parse(value);
    } catch (_) {
      return null;
    }
  }

  function readableError(value) {
    const payload = parseJson(value);
    return payload?.message || payload?.error || value;
  }
})();
