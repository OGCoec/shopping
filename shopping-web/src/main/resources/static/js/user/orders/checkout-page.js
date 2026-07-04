(function () {
  const CHECKOUT_BASE = "/shopping/user/checkout/";
  const ORDER_PAGE_BASE = "/shopping/user/orders";
  const orderApi = window.ShoppingOrderApi;

  const statusEl = document.getElementById("checkout-status");
  const viewEl = document.getElementById("checkout-view");
  const summaryEl = document.getElementById("checkout-summary");
  const productEl = document.getElementById("checkout-product");
  const formEl = document.getElementById("checkout-form");
  const quantityInput = document.getElementById("checkout-quantity");
  const couponSummaryEl = document.getElementById("checkout-coupon-summary");
  const couponListEl = document.getElementById("checkout-coupon-list");
  const amountsEl = document.getElementById("checkout-amounts");
  const submitButton = document.getElementById("checkout-submit");
  const MAX_QUANTITY = 999;

  function safeImageUrl(value) {
    return window.ShoppingSecurityUrls?.safeImageUrl?.(value, "", {
      allowData: false,
      allowBlob: false,
      allowAnyHttps: true,
      allowLocalHttp: true,
      allowedPathPrefixes: ["/shopping/"]
    }) || "";
  }

  const state = {
    skuId: "",
    quantity: 1,
    selectedUserCouponId: "",
    preview: null,
    idempotencyKey: createIdempotencyKey(),
    previewToken: 0
  };

  function setStatus(message, type = "") {
    if (!statusEl) {
      return;
    }
    statusEl.textContent = message || "";
    statusEl.hidden = !message;
    statusEl.classList.toggle("is-error", type === "error");
    statusEl.classList.toggle("is-ok", type === "ok");
  }

  function readSkuId() {
    const path = String(window.location.pathname || "");
    if (!path.startsWith(CHECKOUT_BASE)) {
      return "";
    }
    try {
      return decodeURIComponent(path.slice(CHECKOUT_BASE.length).split("/")[0]).trim();
    } catch (_) {
      return "";
    }
  }

  function readQuantity() {
    const value = new URLSearchParams(window.location.search || "").get("quantity");
    return normalizeQuantity(value);
  }

  function sanitizeQuantityText(value) {
    return String(value ?? "").replace(/[^\d]/g, "").replace(/^0+(?=\d)/, "");
  }

  function normalizeQuantity(value) {
    const cleaned = sanitizeQuantityText(value);
    if (!cleaned) {
      return 1;
    }
    const number = Number(cleaned);
    if (!Number.isFinite(number)) {
      return 1;
    }
    return Math.min(MAX_QUANTITY, Math.max(1, Math.floor(number)));
  }

  function syncQuantityInput(options = {}) {
    if (!quantityInput) {
      return;
    }
    const previousQuantity = state.quantity;
    const nextQuantity = normalizeQuantity(quantityInput.value);
    quantityInput.value = String(nextQuantity);
    state.quantity = nextQuantity;
    if (options.reload && nextQuantity !== previousQuantity) {
      loadPreview({ silent: true });
    }
  }

  function blockInvalidQuantityKey(event) {
    if (event.ctrlKey || event.metaKey || event.altKey || event.key.length !== 1) {
      return;
    }
    if (!/^\d$/.test(event.key)) {
      event.preventDefault();
    }
  }

  function pasteQuantity(event) {
    event.preventDefault();
    const pasted = event.clipboardData?.getData("text") || "";
    const start = quantityInput.selectionStart ?? quantityInput.value.length;
    const end = quantityInput.selectionEnd ?? quantityInput.value.length;
    quantityInput.value = `${quantityInput.value.slice(0, start)}${pasted}${quantityInput.value.slice(end)}`;
    syncQuantityInput({ reload: true });
  }

  async function loadPreview(options = {}) {
    const token = ++state.previewToken;
    if (!state.skuId) {
      showError("商品规格无效");
      return;
    }
    if (!options.silent) {
      setStatus("正在加载订单预览");
    }
    submitButton.disabled = true;
    try {
      const preview = await orderApi.preview({
        skuId: state.skuId,
        quantity: state.quantity,
        selectedUserCouponId: state.selectedUserCouponId
      });
      if (token !== state.previewToken) {
        return;
      }
      state.preview = preview || {};
      state.selectedUserCouponId = String(state.preview.selectedUserCouponId || "");
      renderPreview();
      submitButton.disabled = false;
      setStatus("", "ok");
    } catch (error) {
      if (token !== state.previewToken) {
        return;
      }
      state.preview = null;
      renderUnavailablePreview();
      setStatus(error.message || "订单预览加载失败", "error");
    }
  }

  function renderPreview() {
    viewEl.hidden = false;
    const preview = state.preview || {};
    summaryEl.textContent = `${preview.skuName || "商品规格"} · ${state.quantity} 件`;
    renderProduct(preview);
    renderCoupons(preview);
    renderAmounts(preview);
    quantityInput.value = String(state.quantity);
    document.title = "确认订单 - Shopping";
  }

  function renderUnavailablePreview() {
    viewEl.hidden = false;
    productEl.replaceChildren(emptyNode("订单预览暂不可用"));
    couponSummaryEl.textContent = "-";
    couponListEl.replaceChildren();
    amountsEl.replaceChildren();
    submitButton.disabled = true;
  }

  function renderProduct(preview) {
    productEl.replaceChildren();
    productEl.dataset.skuId = String(preview?.skuId || state.skuId);
    const media = document.createElement("div");
    media.className = "checkout-product-media";
    const imageUrl = safeImageUrl(preview?.skuImageUrl);
    if (imageUrl) {
      const image = document.createElement("img");
      image.src = imageUrl;
      image.alt = String(preview?.skuName || "订单商品");
      image.loading = "lazy";
      media.appendChild(image);
    } else {
      media.textContent = "NO IMAGE";
    }
    const body = document.createElement("div");
    body.className = "checkout-product-body";
    const title = document.createElement("h2");
    title.textContent = String(preview?.skuName || "商品规格");
    const meta = document.createElement("p");
    meta.textContent = `${preview?.hotSku ? "热门库存" : "普通库存"} · 单价 ${formatMoney(preview?.salePriceYuan)}`;
    body.append(title, meta);
    productEl.append(media, body);
  }

  function renderCoupons(preview) {
    couponListEl.replaceChildren();
    const available = Array.isArray(preview?.availableCoupons) ? preview.availableCoupons : [];
    const unavailable = Array.isArray(preview?.unavailableCoupons) ? preview.unavailableCoupons : [];
    couponSummaryEl.textContent = `${available.length} 张可用，${unavailable.length} 张不可用`;
    couponListEl.appendChild(couponOption(null, "不使用优惠券", "按原价提交订单", true));
    available.forEach((coupon) => {
      couponListEl.appendChild(couponOption(
        coupon.userCouponId,
        coupon.name || "优惠券",
        `优惠 ${formatMoney(coupon.discountAmountYuan)} · ${coupon.reason || "可用于当前订单"}`,
        true
      ));
    });
    unavailable.forEach((coupon) => {
      couponListEl.appendChild(couponOption(
        coupon.userCouponId,
        coupon.name || "优惠券",
        coupon.reason || "当前订单不可用",
        false
      ));
    });
  }

  function couponOption(userCouponId, title, subtitle, enabled) {
    const id = String(userCouponId || "");
    const label = document.createElement("label");
    label.className = "checkout-coupon-option";
    label.dataset.userCouponId = id;
    label.dataset.enabled = String(enabled);
    const input = document.createElement("input");
    input.type = "radio";
    input.name = "checkout-user-coupon";
    input.value = id;
    input.disabled = !enabled;
    input.checked = state.selectedUserCouponId === id;
    const body = document.createElement("span");
    body.className = "checkout-coupon-copy";
    const strong = document.createElement("strong");
    strong.textContent = title;
    const small = document.createElement("small");
    small.textContent = subtitle || "";
    body.append(strong, small);
    label.append(input, body);
    return label;
  }

  function renderAmounts(preview) {
    amountsEl.replaceChildren();
    [
      ["商品金额", formatMoney(preview?.totalAmountYuan)],
      ["优惠金额", `-${formatMoney(preview?.discountAmountYuan)}`],
      ["应付金额", formatMoney(preview?.payAmountYuan)]
    ].forEach(([label, value]) => {
      const row = document.createElement("div");
      row.className = "checkout-amount-row";
      const name = document.createElement("span");
      name.textContent = label;
      const amount = document.createElement("strong");
      amount.textContent = value;
      row.append(name, amount);
      amountsEl.appendChild(row);
    });
  }

  async function createOrder() {
    submitButton.disabled = true;
    setStatus("正在提交订单");
    try {
      const created = await orderApi.create({
        skuId: state.skuId,
        quantity: state.quantity,
        userCouponId: state.selectedUserCouponId,
        idempotencyKey: state.idempotencyKey
      });
      const orderNo = String(created?.orderNo || "");
      if (!orderNo) {
        throw new Error("订单号为空");
      }
      window.location.assign(`${ORDER_PAGE_BASE}/${encodeURIComponent(orderNo)}`);
    } catch (error) {
      submitButton.disabled = false;
      setStatus(error.message || "订单提交失败", "error");
    }
  }

  function createIdempotencyKey() {
    if (window.crypto?.randomUUID) {
      return `checkout-${window.crypto.randomUUID()}`;
    }
    return `checkout-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }

  function emptyNode(message) {
    const node = document.createElement("div");
    node.className = "order-empty";
    node.textContent = message;
    return node;
  }

  function showError(message) {
    viewEl.hidden = true;
    setStatus(message, "error");
  }

  function formatMoney(value) {
    const number = Number(value);
    return Number.isFinite(number) ? `¥${number.toFixed(2)}` : "¥0.00";
  }

  quantityInput?.addEventListener("keydown", blockInvalidQuantityKey);
  quantityInput?.addEventListener("input", () => syncQuantityInput());
  quantityInput?.addEventListener("paste", pasteQuantity);
  quantityInput?.addEventListener("change", () => syncQuantityInput({ reload: true }));
  quantityInput?.addEventListener("blur", () => syncQuantityInput());

  couponListEl?.addEventListener("change", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement) || target.name !== "checkout-user-coupon") {
      return;
    }
    state.selectedUserCouponId = target.value;
    loadPreview({ silent: true });
  });

  formEl?.addEventListener("submit", (event) => {
    event.preventDefault();
    createOrder();
  });

  async function startPage() {
    const pageGate = window.ShoppingPageAccessGate;
    if (pageGate?.ready) {
      const allowed = await pageGate.ready();
      if (allowed === false) {
        return;
      }
    }
    state.skuId = readSkuId();
    state.quantity = readQuantity();
    quantityInput.value = String(state.quantity);
    await loadPreview();
  }

  startPage().catch((error) => {
    showError(error.message || "确认订单页面加载失败");
  });
})();
