const DEFAULT_TRANSITION_MS = 1400;

export class FallbackImageStage {
  constructor(stageEl, options = {}) {
    this.stageEl = stageEl;
    this.transitionMs = positiveNumber(options.transitionMs, DEFAULT_TRANSITION_MS);
    this.root = document.createElement("div");
    this.root.className = "admin-product-ripple-carousel-fallback";
    this.current = this.createImage();
    this.next = this.createImage();
    this.currentUrl = "";
    this.current.classList.add("is-active");
    this.root.append(this.current, this.next);
    this.stageEl.appendChild(this.root);
    this.transitionToken = 0;
  }

  createImage() {
    const image = document.createElement("img");
    image.className = "admin-product-ripple-carousel-fallback-image";
    image.alt = "";
    return image;
  }

  async setImage(url) {
    this.transitionToken += 1;
    this.currentUrl = url || "";
    this.current.src = url || "";
    this.current.classList.add("is-active");
    this.next.classList.remove("is-active");
    this.next.removeAttribute("src");
  }

  async transitionTo(url, options = {}) {
    if (options.immediate || !url || this.currentUrl === url) {
      await this.setImage(url);
      return;
    }
    const token = ++this.transitionToken;
    this.next.src = url;
    await waitForImage(this.next);
    if (token !== this.transitionToken) {
      return;
    }
    this.next.classList.add("is-active");
    this.current.classList.remove("is-active");
    await delay(this.transitionMs);
    if (token !== this.transitionToken) {
      return;
    }
    const previous = this.current;
    this.current = this.next;
    this.next = previous;
    this.currentUrl = url;
    this.next.classList.remove("is-active");
    this.next.removeAttribute("src");
  }

  preload(url, _options = {}) {
    return prewarmImage(url).catch(() => null);
  }

  destroy() {
    this.transitionToken += 1;
    this.root.remove();
  }
}

export function prewarmImage(url) {
  const normalized = String(url || "").trim();
  if (!normalized) {
    return Promise.resolve(null);
  }
  return new Promise((resolve) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => resolve(null);
    image.src = normalized;
    if (image.decode) {
      image.decode().then(() => resolve(image)).catch(() => null);
    }
  });
}

function waitForImage(image) {
  if (!image || image.complete) {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    image.addEventListener("load", resolve, { once: true });
    image.addEventListener("error", resolve, { once: true });
  });
}

function delay(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function positiveNumber(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : fallback;
}
