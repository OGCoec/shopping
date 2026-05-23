import { FallbackImageStage, prewarmImage } from "./product-carousel-fallback-stage.js";
import { WebglRippleStage, prewarmWebglRippleStage } from "./product-carousel-webgl-stage.js";

const DEFAULT_INTERVAL_MS = 5000;
const DEFAULT_TRANSITION_MS = 1400;
const PREWARM_IMAGE_COUNT = 2;

export function createProductImageRippleCarousel(container, options = {}) {
  return new ProductImageRippleCarousel(container, options);
}

export async function prewarmProductImageRippleCarousel(options = {}) {
  const images = normalizeImages(options.images);
  if (!images.length || typeof document === "undefined") {
    return;
  }
  await Promise.all([
    prewarmWebglRippleStage().catch(() => null),
    ...images.slice(0, PREWARM_IMAGE_COUNT).map((url) => prewarmImage(url).catch(() => null))
  ]);
}

class ProductImageRippleCarousel {
  constructor(container, options = {}) {
    this.container = container || document.body;
    this.images = normalizeImages(options.images);
    this.currentIndex = clampIndex(options.initialIndex, this.images.length);
    this.intervalMs = positiveNumber(options.intervalMs, DEFAULT_INTERVAL_MS);
    this.transitionMs = positiveNumber(options.transitionMs, DEFAULT_TRANSITION_MS);
    this.title = String(options.title || "Product images");
    this.inline = Boolean(options.inline);
    this.timerId = null;
    this.root = null;
    this.overlay = null;
    this.stageEl = null;
    this.counterEl = null;
    this.navButtons = [];
    this.indicatorWrap = null;
    this.indicators = [];
    this.stage = null;
    this.stageMode = "";
    this.ready = false;
    this.paused = false;
    this.destroyed = false;
    this.initializing = false;
    this.transitionInFlight = false;
    this.keydownHandler = (event) => this.onKeydown(event);
  }

  open() {
    if (this.inline) {
      return this.mount();
    }
    if (this.root || !this.images.length) {
      return this;
    }
    this.buildDom();
    this.updateUi();
    this.setLoading(true);
    this.container.appendChild(this.root);
    document.addEventListener("keydown", this.keydownHandler);
    this.root.focus({ preventScroll: true });
    this.initialize();
    return this;
  }

  mount() {
    if (this.root || !this.images.length) {
      return this;
    }
    this.buildDom();
    this.updateUi();
    this.setLoading(true);
    this.container.appendChild(this.root);
    this.root.addEventListener("keydown", this.keydownHandler);
    this.initialize();
    return this;
  }

  destroy() {
    this.destroyed = true;
    this.clearTimer();
    document.removeEventListener("keydown", this.keydownHandler);
    this.root?.removeEventListener("keydown", this.keydownHandler);
    this.stage?.destroy();
    this.stage = null;
    this.root?.remove();
    this.root = null;
    this.overlay = null;
    this.stageEl = null;
    this.counterEl = null;
    this.navButtons = [];
    this.indicatorWrap = null;
    this.indicators = [];
    this.transitionInFlight = false;
  }

  buildDom() {
    const root = document.createElement("div");
    if (this.inline) {
      root.className = "admin-product-ripple-carousel-inline";
    } else {
      root.className = "admin-product-ripple-carousel-overlay";
      root.tabIndex = -1;
      root.setAttribute("role", "dialog");
      root.setAttribute("aria-modal", "true");
    }

    const panel = document.createElement("div");
    panel.className = "admin-product-ripple-carousel-panel";
    if (this.inline) {
      panel.classList.add("admin-product-ripple-carousel-inline-panel");
    }

    const header = document.createElement("div");
    header.className = "admin-product-ripple-carousel-header";
    const title = document.createElement("div");
    title.className = "admin-product-ripple-carousel-title";
    title.textContent = this.title;
    const meta = document.createElement("div");
    meta.className = "admin-product-ripple-carousel-meta";
    this.counterEl = document.createElement("span");
    this.counterEl.className = "admin-product-ripple-carousel-counter";
    meta.appendChild(this.counterEl);
    if (!this.inline) {
      meta.appendChild(this.iconButton("X", "Close carousel", () => this.destroy()));
    }
    header.append(title, meta);

    const stageShell = document.createElement("div");
    stageShell.className = "admin-product-ripple-carousel-stage-shell";
    this.stageEl = document.createElement("div");
    this.stageEl.className = "admin-product-ripple-carousel-stage";
    const previous = this.navButton("<", "Previous image", () => this.goTo(this.currentIndex - 1));
    previous.classList.add("is-previous");
    const next = this.navButton(">", "Next image", () => this.goTo(this.currentIndex + 1));
    next.classList.add("is-next");
    this.navButtons = [previous, next];
    stageShell.append(this.stageEl, previous, next);

    this.indicatorWrap = document.createElement("div");
    this.indicatorWrap.className = "admin-product-ripple-carousel-indicators";
    this.indicatorWrap.addEventListener("pointerleave", () => this.resume());
    this.indicatorWrap.addEventListener("focusout", () => {
      window.setTimeout(() => {
        if (!this.indicatorWrap?.contains(document.activeElement)) {
          this.resume();
        }
      }, 0);
    });
    this.images.forEach((_, index) => {
      const button = document.createElement("button");
      button.className = "admin-product-ripple-carousel-indicator";
      button.type = "button";
      button.textContent = String(index + 1);
      button.setAttribute("aria-label", `Show image ${index + 1}`);
      button.addEventListener("pointerenter", () => {
        this.selectIndicator(index);
      });
      button.addEventListener("focus", () => {
        this.selectIndicator(index);
      });
      button.addEventListener("click", () => {
        this.selectIndicator(index);
      });
      this.indicators.push(button);
      this.indicatorWrap.appendChild(button);
    });

    panel.append(header, stageShell, this.indicatorWrap);
    root.appendChild(panel);
    if (!this.inline) {
      root.addEventListener("click", (event) => {
        if (event.target === root) {
          this.destroy();
        }
      });
    }
    this.root = root;
    this.overlay = this.inline ? null : root;
    this.setControlsDisabled(true);
  }

  async initialize() {
    if (this.initializing || this.destroyed) {
      return;
    }
    this.initializing = true;
    let webglStage = null;
    try {
      webglStage = new WebglRippleStage(this.stageEl, { transitionMs: this.transitionMs });
      await webglStage.init(this.images[this.currentIndex]);
      if (this.destroyed) {
        webglStage.destroy();
        return;
      }
      this.stage = webglStage;
      this.stageMode = "webgl";
      await this.preloadAllStageTextures();
    } catch (_) {
      webglStage?.destroy();
      if (this.destroyed || !this.stageEl) {
        return;
      }
      await this.activateFallback(this.images[this.currentIndex]);
    } finally {
      this.initializing = false;
    }
    if (this.destroyed) {
      return;
    }
    this.ready = true;
    this.setControlsDisabled(false);
    this.setLoading(false);
    this.updateUi();
    this.preloadNext();
    this.resetTimer();
  }

  async activateFallback(url) {
    if (!this.stageEl) {
      return;
    }
    this.stage?.destroy();
    this.stageEl.innerHTML = "";
    this.stage = new FallbackImageStage(this.stageEl, { transitionMs: this.transitionMs });
    this.stageMode = "fallback";
    await this.stage.setImage(url);
  }

  async goTo(index, options = {}) {
    if (this.destroyed || !this.images.length) {
      return;
    }
    const targetIndex = modulo(index, this.images.length);
    if (!this.ready || !this.stage) {
      return;
    }
    if (this.transitionInFlight) {
      return;
    }
    if (targetIndex === this.currentIndex) {
      return;
    }
    const previousIndex = this.currentIndex;
    const targetUrl = this.images[targetIndex];
    this.clearTimer();
    this.currentIndex = targetIndex;
    this.updateUi();
    const immediate = Boolean(options.immediate || this.images.length < 2);
    this.transitionInFlight = true;
    try {
      await this.stage.transitionTo(targetUrl, { immediate });
    } catch (_) {
      const previousUrl = this.images[previousIndex] || targetUrl;
      await this.activateFallback(previousUrl);
      await this.stage.transitionTo(targetUrl, { immediate });
    } finally {
      this.transitionInFlight = false;
    }
    if (this.destroyed) {
      return;
    }
    this.preloadNext();
    this.resetTimer();
  }

  async preloadAllStageTextures() {
    if (!this.stage || this.stageMode !== "webgl" || this.images.length < 2) {
      return;
    }
    await preloadStageTextures(this.images, 2, (url) => this.stage.preload(url, { strict: true }));
  }

  pause() {
    this.paused = true;
    this.clearTimer();
  }

  resume() {
    if (!this.paused) {
      return;
    }
    this.paused = false;
    this.resetTimer();
  }

  resetTimer() {
    this.clearTimer();
    if (this.destroyed || this.paused || this.transitionInFlight || !this.ready || this.images.length < 2) {
      return;
    }
    this.timerId = window.setTimeout(() => {
      this.goTo(this.currentIndex + 1);
    }, this.intervalMs);
  }

  clearTimer() {
    if (this.timerId) {
      window.clearTimeout(this.timerId);
      this.timerId = null;
    }
  }

  setControlsDisabled(disabled) {
    const shouldDisable = Boolean(disabled);
    this.navButtons.forEach((button) => {
      if (button) {
        button.disabled = shouldDisable || this.images.length < 2;
      }
    });
    this.indicators.forEach((button) => {
      if (button) {
        button.disabled = shouldDisable;
      }
    });
  }

  preloadNext() {
    if (!this.stage || this.images.length < 2) {
      return;
    }
    const nextUrl = this.images[modulo(this.currentIndex + 1, this.images.length)];
    this.stage.preload?.(nextUrl);
  }

  updateUi() {
    if (this.counterEl) {
      this.counterEl.textContent = `${this.currentIndex + 1} / ${this.images.length}`;
    }
    this.indicators.forEach((button, index) => {
      const active = index === this.currentIndex;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-current", active ? "true" : "false");
    });
  }

  setLoading(loading) {
    if (!this.stageEl) {
      return;
    }
    this.stageEl.classList.toggle("is-loading", Boolean(loading));
    let marker = this.stageEl.querySelector(".admin-product-ripple-carousel-loading");
    if (loading && !marker) {
      marker = document.createElement("div");
      marker.className = "admin-product-ripple-carousel-loading";
      marker.textContent = "Loading";
      this.stageEl.appendChild(marker);
    } else if (!loading) {
      marker?.remove();
    }
  }

  selectIndicator(index) {
    if (this.destroyed || !this.ready || this.transitionInFlight) {
      return;
    }
    this.pause();
    this.goTo(index, { resetTimer: false });
  }

  onKeydown(event) {
    if (this.destroyed || !this.root) {
      return;
    }
    if (event.key === "Escape") {
      if (this.inline) {
        return;
      }
      event.preventDefault();
      this.destroy();
      return;
    }
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      this.goTo(this.currentIndex - 1);
      return;
    }
    if (event.key === "ArrowRight") {
      event.preventDefault();
      this.goTo(this.currentIndex + 1);
    }
  }

  iconButton(text, label, handler) {
    const button = document.createElement("button");
    button.className = "admin-product-ripple-carousel-close";
    button.type = "button";
    button.textContent = text;
    button.setAttribute("aria-label", label);
    button.addEventListener("click", handler);
    return button;
  }

  navButton(text, label, handler) {
    const button = document.createElement("button");
    button.className = "admin-product-ripple-carousel-nav";
    button.type = "button";
    button.textContent = text;
    button.setAttribute("aria-label", label);
    button.disabled = this.images.length < 2;
    button.addEventListener("click", handler);
    return button;
  }
}

function normalizeImages(images) {
  const seen = new Set();
  const normalized = [];
  (Array.isArray(images) ? images : []).forEach((url) => {
    const value = String(url || "").trim();
    if (value && !seen.has(value)) {
      seen.add(value);
      normalized.push(value);
    }
  });
  return normalized;
}

async function preloadStageTextures(urls, concurrency, loadTexture) {
  const list = normalizeImages(urls);
  if (!list.length || typeof loadTexture !== "function") {
    return;
  }
  const requestedLimit = Math.floor(Number(concurrency) || 1);
  const limit = Math.max(1, Math.min(requestedLimit, list.length));
  let cursor = 0;
  let failed = false;
  const workers = Array.from({ length: limit }, async () => {
    while (!failed) {
      const index = cursor++;
      if (index >= list.length) {
        return;
      }
      try {
        await loadTexture(list[index]);
      } catch (error) {
        failed = true;
        throw error;
      }
    }
  });
  await Promise.all(workers);
}

function clampIndex(index, length) {
  if (!length) {
    return 0;
  }
  const parsed = Number.parseInt(String(index ?? "0"), 10);
  return modulo(Number.isFinite(parsed) ? parsed : 0, length);
}

function modulo(value, length) {
  return ((value % length) + length) % length;
}

function positiveNumber(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : fallback;
}
