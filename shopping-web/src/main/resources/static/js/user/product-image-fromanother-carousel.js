const DEFAULT_INTERVAL_MS = 5000;
const DEFAULT_TRANSITION_MS = 1200;
const FALLBACK_TRANSITION_MS = 420;
const WHEEL_THROTTLE_MS = 300;
const DESKTOP_MEDIA = "(min-width: 1200px)";
const SWIPE_THRESHOLD_PX = 50;

const vertexShaderSource = `
  attribute vec2 aPosition;
  attribute vec2 aUv;
  varying vec2 vUv;

  void main() {
    vUv = aUv;
    gl_Position = vec4(aPosition, 0.0, 1.0);
  }
`;

const fragmentShaderSource = `
  precision highp float;

  uniform sampler2D uCurrentTexture;
  uniform sampler2D uNextTexture;
  uniform vec2 uMeshSize;
  uniform vec2 uCurrentImageSize;
  uniform vec2 uNextImageSize;
  uniform float uDistortionStrength;
  uniform float uProgress;
  varying vec2 vUv;

  vec2 backgroundCoverUv(vec2 screenSize, vec2 imageSize, vec2 uv) {
    float screenRatio = screenSize.x / screenSize.y;
    float imageRatio = imageSize.x / imageSize.y;
    vec2 newSize = screenRatio < imageRatio
      ? vec2(imageSize.x * screenSize.y / imageSize.y, screenSize.y)
      : vec2(screenSize.x, imageSize.y * screenSize.x / imageSize.x);
    vec2 newOffset = (screenRatio < imageRatio
      ? vec2((newSize.x - screenSize.x) / 2.0, 0.0)
      : vec2(0.0, (newSize.y - screenSize.y) / 2.0)) / newSize;
    return uv * screenSize / newSize + newOffset;
  }

  vec3 permute(vec3 x) {
    return mod(((x * 34.0) + 1.0) * x, 289.0);
  }

  float snoise(vec2 v) {
    const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
    vec2 i = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod(i, 289.0);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m;
    m = m * m;
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
  }

  void main() {
    vec2 currentUv = backgroundCoverUv(uMeshSize, uCurrentImageSize, vUv);
    vec2 nextUv = backgroundCoverUv(uMeshSize, uNextImageSize, vUv);
    vec4 currentColor = texture2D(uCurrentTexture, currentUv);
    vec4 nextColor = texture2D(uNextTexture, nextUv);

    if (uProgress <= 0.0) {
      gl_FragColor = currentColor;
      return;
    }

    vec2 center = vec2(0.5);
    float noiseValue = snoise(vUv * 2.2);
    float dist = distance(vUv, center);
    float distortedDist = dist - noiseValue * uDistortionStrength;
    float rippleRadius = uProgress * 0.85;
    float edgeWidth = 0.05;
    float mask = smoothstep(rippleRadius, rippleRadius - edgeWidth, distortedDist);

    gl_FragColor = mix(currentColor, nextColor, mask);
  }
`;

export function createProductImageFromAnotherCarousel(container, options = {}) {
  return new ProductImageFromAnotherCarousel(container, options);
}

class ProductImageFromAnotherCarousel {
  constructor(container, options = {}) {
    this.container = container || document.body;
    this.images = normalizeImages(options.images);
    this.currentIndex = clampIndex(options.initialIndex, this.images.length);
    this.intervalMs = positiveNumber(options.intervalMs, DEFAULT_INTERVAL_MS);
    this.transitionMs = positiveNumber(options.transitionMs, DEFAULT_TRANSITION_MS);
    this.title = String(options.title || "Product images");
    this.root = null;
    this.stageEl = null;
    this.canvas = null;
    this.counterEl = null;
    this.loadingEl = null;
    this.navButtons = [];
    this.indicators = [];
    this.renderer = null;
    this.fallbackStage = null;
    this.timerId = null;
    this.animationFrameId = 0;
    this.wheelTimestamp = 0;
    this.touchStart = null;
    this.touchLocked = false;
    this.ready = false;
    this.destroyed = false;
    this.transitionInFlight = false;
    this.reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches || false;
    this.keydownHandler = (event) => this.onKeydown(event);
    this.wheelHandler = (event) => this.onWheel(event);
    this.touchStartHandler = (event) => this.onTouchStart(event);
    this.touchMoveHandler = (event) => this.onTouchMove(event);
    this.touchEndHandler = (event) => this.onTouchEnd(event);
  }

  mount() {
    if (this.root || !this.images.length) {
      return this;
    }
    this.buildDom();
    this.container.appendChild(this.root);
    this.bindEvents();
    this.initialize();
    return this;
  }

  open() {
    return this.mount();
  }

  destroy() {
    this.destroyed = true;
    this.clearTimer();
    this.unbindEvents();
    if (this.animationFrameId) {
      window.cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = 0;
    }
    this.renderer?.dispose();
    this.renderer = null;
    this.fallbackStage?.destroy();
    this.fallbackStage = null;
    this.root?.remove();
    this.root = null;
    this.stageEl = null;
    this.canvas = null;
    this.counterEl = null;
    this.loadingEl = null;
    this.navButtons = [];
    this.indicators = [];
  }

  buildDom() {
    const root = document.createElement("div");
    root.className = "product-fromanother-carousel";
    root.tabIndex = 0;

    const header = document.createElement("div");
    header.className = "product-fromanother-carousel-header";
    const title = document.createElement("div");
    title.className = "product-fromanother-carousel-title";
    title.textContent = this.title;
    this.counterEl = document.createElement("span");
    this.counterEl.className = "product-fromanother-carousel-counter";
    header.append(title, this.counterEl);

    const stageShell = document.createElement("div");
    stageShell.className = "product-fromanother-carousel-stage-shell";
    this.stageEl = document.createElement("div");
    this.stageEl.className = "product-fromanother-carousel-stage";
    this.canvas = document.createElement("canvas");
    this.canvas.className = "product-fromanother-carousel-canvas";
    this.stageEl.appendChild(this.canvas);
    this.loadingEl = document.createElement("div");
    this.loadingEl.className = "product-fromanother-carousel-loading";
    this.loadingEl.textContent = "Loading images";
    this.stageEl.appendChild(this.loadingEl);

    const previous = this.navButton("<", "Previous image", () => this.goTo(this.currentIndex - 1));
    previous.classList.add("is-previous");
    const next = this.navButton(">", "Next image", () => this.goTo(this.currentIndex + 1));
    next.classList.add("is-next");
    this.navButtons = [previous, next];
    stageShell.append(this.stageEl, previous, next);

    const indicators = document.createElement("div");
    indicators.className = "product-fromanother-carousel-indicators";
    this.images.forEach((imageUrl, index) => {
      const button = document.createElement("button");
      button.className = "product-fromanother-carousel-indicator";
      button.type = "button";
      button.setAttribute("aria-label", `Show image ${index + 1}`);
      const thumbnail = document.createElement("img");
      thumbnail.src = imageUrl;
      thumbnail.alt = "";
      thumbnail.loading = "lazy";
      button.appendChild(thumbnail);
      button.addEventListener("click", () => this.goTo(index));
      button.addEventListener("focus", () => this.pause());
      button.addEventListener("blur", () => this.resume());
      this.indicators.push(button);
      indicators.appendChild(button);
    });

    root.append(header, stageShell, indicators);
    this.root = root;
    this.setControlsDisabled(true);
    this.updateUi();
  }

  async initialize() {
    this.setLoading(true);
    this.fallbackStage = new FallbackFadeStage(this.stageEl, { transitionMs: FALLBACK_TRANSITION_MS });
    await this.fallbackStage.setImage(this.images[this.currentIndex], { immediate: true });
    if (this.destroyed) {
      return;
    }

    if (!this.reducedMotion && this.canvas) {
      try {
        this.renderer = new FromAnotherRenderer(this.canvas, this.images);
        await this.renderer.init();
        if (this.destroyed) {
          this.renderer.dispose();
          return;
        }
        this.renderer.render(this.currentIndex, this.currentIndex, 0);
        this.root?.classList.add("is-webgl-ready");
      } catch (_) {
        this.renderer?.dispose();
        this.renderer = null;
        this.canvas?.remove();
        this.canvas = null;
        this.root?.classList.add("is-fallback-ready");
      }
    } else {
      this.canvas?.remove();
      this.canvas = null;
      this.root?.classList.add("is-fallback-ready");
    }

    this.ready = true;
    this.setControlsDisabled(false);
    this.setLoading(false);
    this.updateUi();
    this.preloadNext();
    this.resetTimer();
  }

  async goTo(index, options = {}) {
    if (this.destroyed || !this.ready || !this.images.length || this.transitionInFlight) {
      return;
    }
    const targetIndex = modulo(index, this.images.length);
    if (targetIndex === this.currentIndex) {
      return;
    }
    const fromIndex = this.currentIndex;
    const targetUrl = this.images[targetIndex];
    this.clearTimer();
    this.transitionInFlight = true;
    this.currentIndex = targetIndex;
    this.updateUi();

    try {
      if (this.renderer && !this.reducedMotion && !options.immediate) {
        await this.runWebglTransition(fromIndex, targetIndex);
        await this.fallbackStage?.setImage(targetUrl, { immediate: true });
      } else {
        await this.fallbackStage?.transitionTo(targetUrl, {
          immediate: Boolean(options.immediate || this.reducedMotion || this.images.length < 2)
        });
        this.renderer?.render(targetIndex, targetIndex, 0);
      }
    } catch (_) {
      this.root?.classList.remove("is-webgl-ready");
      this.root?.classList.add("is-fallback-ready");
      this.renderer?.dispose();
      this.renderer = null;
      this.canvas?.remove();
      this.canvas = null;
      await this.fallbackStage?.transitionTo(targetUrl, { immediate: Boolean(options.immediate) });
    } finally {
      this.transitionInFlight = false;
      if (!this.destroyed) {
        this.updateUi();
        this.preloadNext();
        this.resetTimer();
      }
    }
  }

  runWebglTransition(fromIndex, targetIndex) {
    return new Promise((resolve) => {
      const startedAt = performance.now();
      const tick = (now) => {
        if (this.destroyed || !this.renderer) {
          resolve();
          return;
        }
        const linearProgress = clamp((now - startedAt) / this.transitionMs, 0, 1);
        const easedProgress = easePower2InOut(linearProgress);
        this.renderer.render(fromIndex, targetIndex, easedProgress);
        if (linearProgress < 1) {
          this.animationFrameId = window.requestAnimationFrame(tick);
          return;
        }
        this.renderer.render(targetIndex, targetIndex, 0);
        this.animationFrameId = 0;
        resolve();
      };
      if (this.animationFrameId) {
        window.cancelAnimationFrame(this.animationFrameId);
      }
      this.animationFrameId = window.requestAnimationFrame(tick);
    });
  }

  bindEvents() {
    this.root?.addEventListener("keydown", this.keydownHandler);
    this.root?.addEventListener("wheel", this.wheelHandler, { passive: false });
    this.root?.addEventListener("touchstart", this.touchStartHandler, { passive: true });
    this.root?.addEventListener("touchmove", this.touchMoveHandler, { passive: false });
    this.root?.addEventListener("touchend", this.touchEndHandler, { passive: true });
    this.root?.addEventListener("touchcancel", this.touchEndHandler, { passive: true });
    this.root?.addEventListener("pointerenter", () => this.pause());
    this.root?.addEventListener("pointerleave", () => this.resume());
  }

  unbindEvents() {
    this.root?.removeEventListener("keydown", this.keydownHandler);
    this.root?.removeEventListener("wheel", this.wheelHandler);
    this.root?.removeEventListener("touchstart", this.touchStartHandler);
    this.root?.removeEventListener("touchmove", this.touchMoveHandler);
    this.root?.removeEventListener("touchend", this.touchEndHandler);
    this.root?.removeEventListener("touchcancel", this.touchEndHandler);
  }

  onKeydown(event) {
    if (event.key === "ArrowRight" || event.key === "ArrowDown") {
      event.preventDefault();
      this.goTo(this.currentIndex + 1);
    }
    if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
      event.preventDefault();
      this.goTo(this.currentIndex - 1);
    }
  }

  onWheel(event) {
    if (!window.matchMedia(DESKTOP_MEDIA).matches || this.images.length < 2) {
      return;
    }
    const now = Date.now();
    if (now - this.wheelTimestamp < WHEEL_THROTTLE_MS || Math.abs(event.deltaY) < 50) {
      return;
    }
    this.wheelTimestamp = now;
    event.preventDefault();
    this.goTo(event.deltaY > 0 ? this.currentIndex + 1 : this.currentIndex - 1);
  }

  onTouchStart(event) {
    const touch = event.touches?.[0];
    if (!touch) {
      return;
    }
    this.touchStart = { x: touch.clientX, y: touch.clientY };
    this.touchLocked = false;
    this.pause();
  }

  onTouchMove(event) {
    const touch = event.touches?.[0];
    if (!this.touchStart || !touch || window.matchMedia(DESKTOP_MEDIA).matches) {
      return;
    }
    const deltaX = touch.clientX - this.touchStart.x;
    const deltaY = touch.clientY - this.touchStart.y;
    if (!this.touchLocked) {
      this.touchLocked = Math.abs(deltaX) > SWIPE_THRESHOLD_PX && Math.abs(deltaX) >= Math.abs(deltaY) * 1.2;
    }
    if (this.touchLocked) {
      event.preventDefault();
    }
  }

  onTouchEnd(event) {
    const touch = event.changedTouches?.[0];
    if (this.touchStart && touch && !window.matchMedia(DESKTOP_MEDIA).matches) {
      const deltaX = touch.clientX - this.touchStart.x;
      const deltaY = touch.clientY - this.touchStart.y;
      if (
        this.touchLocked &&
        Math.abs(deltaX) >= SWIPE_THRESHOLD_PX &&
        Math.abs(deltaX) >= Math.abs(deltaY) * 1.2
      ) {
        this.goTo(deltaX > 0 ? this.currentIndex - 1 : this.currentIndex + 1);
      }
    }
    this.touchStart = null;
    this.touchLocked = false;
    this.resume();
  }

  navButton(text, label, onClick) {
    const button = document.createElement("button");
    button.className = "product-fromanother-carousel-nav";
    button.type = "button";
    button.textContent = text;
    button.setAttribute("aria-label", label);
    button.addEventListener("click", onClick);
    return button;
  }

  setLoading(loading) {
    if (this.loadingEl) {
      this.loadingEl.hidden = !loading;
    }
  }

  setControlsDisabled(disabled) {
    this.navButtons.forEach((button) => {
      button.disabled = disabled || this.images.length < 2;
    });
    this.indicators.forEach((button) => {
      button.disabled = disabled || this.images.length < 2;
    });
  }

  updateUi() {
    if (this.counterEl) {
      const total = Math.max(this.images.length, 1);
      this.counterEl.textContent = `${Math.min(this.currentIndex + 1, total)} / ${total}`;
    }
    const busy = this.transitionInFlight;
    this.navButtons.forEach((button) => {
      button.disabled = busy || this.images.length < 2;
    });
    this.indicators.forEach((button, index) => {
      button.classList.toggle("is-active", index === this.currentIndex);
      button.setAttribute("aria-current", index === this.currentIndex ? "true" : "false");
      button.disabled = busy || this.images.length < 2;
    });
  }

  preloadNext() {
    if (!this.images.length) {
      return;
    }
    const nextUrl = this.images[modulo(this.currentIndex + 1, this.images.length)];
    this.fallbackStage?.preload(nextUrl);
  }

  pause() {
    this.clearTimer();
  }

  resume() {
    this.resetTimer();
  }

  clearTimer() {
    if (this.timerId) {
      window.clearTimeout(this.timerId);
      this.timerId = null;
    }
  }

  resetTimer() {
    this.clearTimer();
    if (this.destroyed || !this.ready || this.images.length < 2 || this.intervalMs <= 0) {
      return;
    }
    this.timerId = window.setTimeout(() => {
      this.goTo(this.currentIndex + 1);
    }, this.intervalMs);
  }
}

class FromAnotherRenderer {
  constructor(canvas, imageSources) {
    this.canvas = canvas;
    this.imageSources = imageSources;
    this.gl = null;
    this.program = null;
    this.buffer = null;
    this.positionLocation = -1;
    this.uvLocation = -1;
    this.uniforms = null;
    this.textures = [];
  }

  async init() {
    const gl = this.canvas.getContext("webgl", {
      alpha: false,
      antialias: true,
      depth: false,
      premultipliedAlpha: false
    });
    if (!gl) {
      throw new Error("WebGL is not available");
    }
    this.gl = gl;
    this.program = createProgram(gl);
    this.positionLocation = gl.getAttribLocation(this.program, "aPosition");
    this.uvLocation = gl.getAttribLocation(this.program, "aUv");
    this.buffer = createQuadBuffer(gl);
    this.uniforms = {
      currentTexture: getUniform(gl, this.program, "uCurrentTexture"),
      nextTexture: getUniform(gl, this.program, "uNextTexture"),
      meshSize: getUniform(gl, this.program, "uMeshSize"),
      currentImageSize: getUniform(gl, this.program, "uCurrentImageSize"),
      nextImageSize: getUniform(gl, this.program, "uNextImageSize"),
      distortionStrength: getUniform(gl, this.program, "uDistortionStrength"),
      progress: getUniform(gl, this.program, "uProgress")
    };
    this.textures = await Promise.all(this.imageSources.map((src) => createTexture(gl, src)));
  }

  render(currentIndex, nextIndex, progress) {
    if (!this.gl || !this.program || !this.buffer || !this.uniforms || !this.textures.length) {
      return;
    }
    const gl = this.gl;
    const mesh = resizeCanvas(this.canvas, gl);
    const current = this.textures[currentIndex] || this.textures[0];
    const next = this.textures[nextIndex] || current;
    gl.useProgram(this.program);
    gl.bindBuffer(gl.ARRAY_BUFFER, this.buffer);
    gl.enableVertexAttribArray(this.positionLocation);
    gl.vertexAttribPointer(this.positionLocation, 2, gl.FLOAT, false, 16, 0);
    gl.enableVertexAttribArray(this.uvLocation);
    gl.vertexAttribPointer(this.uvLocation, 2, gl.FLOAT, false, 16, 8);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, current.texture);
    gl.uniform1i(this.uniforms.currentTexture, 0);
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, next.texture);
    gl.uniform1i(this.uniforms.nextTexture, 1);
    gl.uniform2f(this.uniforms.meshSize, mesh.width, mesh.height);
    gl.uniform2f(this.uniforms.currentImageSize, current.width, current.height);
    gl.uniform2f(this.uniforms.nextImageSize, next.width, next.height);
    gl.uniform1f(this.uniforms.distortionStrength, 0.08);
    gl.uniform1f(this.uniforms.progress, progress);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }

  dispose() {
    if (!this.gl) {
      return;
    }
    this.textures.forEach(({ texture }) => this.gl.deleteTexture(texture));
    this.textures = [];
    if (this.buffer) {
      this.gl.deleteBuffer(this.buffer);
    }
    if (this.program) {
      this.gl.deleteProgram(this.program);
    }
    this.gl = null;
    this.program = null;
    this.buffer = null;
  }
}

class FallbackFadeStage {
  constructor(stageEl, options = {}) {
    this.stageEl = stageEl;
    this.transitionMs = positiveNumber(options.transitionMs, FALLBACK_TRANSITION_MS);
    this.root = document.createElement("div");
    this.root.className = "product-fromanother-carousel-fallback";
    this.current = this.createImage();
    this.next = this.createImage();
    this.current.classList.add("is-active");
    this.root.append(this.current, this.next);
    this.stageEl.appendChild(this.root);
    this.currentUrl = "";
    this.transitionToken = 0;
  }

  createImage() {
    const image = document.createElement("img");
    image.className = "product-fromanother-carousel-fallback-image";
    image.alt = "";
    return image;
  }

  async setImage(url, _options = {}) {
    this.transitionToken += 1;
    this.currentUrl = url || "";
    this.current.src = url || "";
    this.current.classList.add("is-active");
    this.next.classList.remove("is-active");
    this.next.removeAttribute("src");
    await waitForImage(this.current);
  }

  async transitionTo(url, options = {}) {
    if (options.immediate || !url || this.currentUrl === url) {
      await this.setImage(url, options);
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

  preload(url) {
    return prewarmImage(url);
  }

  destroy() {
    this.transitionToken += 1;
    this.root.remove();
  }
}

function compileShader(gl, type, source) {
  const shader = gl.createShader(type);
  if (!shader) {
    throw new Error("Unable to create WebGL shader");
  }
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const message = gl.getShaderInfoLog(shader) || "Unknown shader error";
    gl.deleteShader(shader);
    throw new Error(message);
  }
  return shader;
}

function createProgram(gl) {
  const vertexShader = compileShader(gl, gl.VERTEX_SHADER, vertexShaderSource);
  const fragmentShader = compileShader(gl, gl.FRAGMENT_SHADER, fragmentShaderSource);
  const program = gl.createProgram();
  if (!program) {
    throw new Error("Unable to create WebGL program");
  }
  gl.attachShader(program, vertexShader);
  gl.attachShader(program, fragmentShader);
  gl.linkProgram(program);
  gl.deleteShader(vertexShader);
  gl.deleteShader(fragmentShader);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const message = gl.getProgramInfoLog(program) || "Unknown program error";
    gl.deleteProgram(program);
    throw new Error(message);
  }
  return program;
}

function createQuadBuffer(gl) {
  const buffer = gl.createBuffer();
  if (!buffer) {
    throw new Error("Unable to create WebGL buffer");
  }
  const vertices = new Float32Array([
    -1, -1, 0, 1,
    1, -1, 1, 1,
    -1, 1, 0, 0,
    1, 1, 1, 0
  ]);
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);
  return buffer;
}

function getUniform(gl, program, name) {
  const uniform = gl.getUniformLocation(program, name);
  if (!uniform) {
    throw new Error(`Missing WebGL uniform: ${name}`);
  }
  return uniform;
}

async function createTexture(gl, src) {
  const image = await loadImage(src);
  const texture = gl.createTexture();
  if (!texture) {
    throw new Error(`Unable to create texture for: ${src}`);
  }
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
  gl.bindTexture(gl.TEXTURE_2D, null);
  return {
    texture,
    width: image.naturalWidth || image.width || 1,
    height: image.naturalHeight || image.height || 1
  };
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.decoding = "async";
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`Unable to load image: ${src}`));
    image.src = corsTextureUrl(src);
  });
}

function corsTextureUrl(src) {
  try {
    const url = new URL(src, window.location.href);
    if (url.origin !== window.location.origin && !isSignedUrl(url)) {
      url.searchParams.set("user-carousel-cors", "1");
    }
    return url.href;
  } catch (_) {
    return src;
  }
}

function isSignedUrl(url) {
  const signedKeys = new Set([
    "signature",
    "expires",
    "ossaccesskeyid",
    "security-token",
    "x-oss-signature",
    "x-oss-signature-version",
    "x-expires",
    "x-oss-expires",
    "x-oss-credential",
    "x-oss-date",
    "x-oss-security-token",
    "x-amz-signature",
    "x-amz-algorithm",
    "x-amz-credential",
    "x-amz-date",
    "x-amz-expires",
    "x-amz-security-token"
  ]);
  for (const key of url.searchParams.keys()) {
    if (signedKeys.has(String(key || "").toLowerCase())) {
      return true;
    }
  }
  return false;
}

function resizeCanvas(canvas, gl) {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const width = Math.max(1, Math.floor(canvas.clientWidth * dpr));
  const height = Math.max(1, Math.floor(canvas.clientHeight * dpr));
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }
  gl.viewport(0, 0, width, height);
  return { width, height };
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

function prewarmImage(url) {
  const normalized = String(url || "").trim();
  if (!normalized) {
    return Promise.resolve(null);
  }
  return new Promise((resolve) => {
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.onload = () => resolve(image);
    image.onerror = () => resolve(null);
    image.src = corsTextureUrl(normalized);
  });
}

function easePower2InOut(value) {
  return value < 0.5 ? 2 * value * value : 1 - Math.pow(-2 * value + 2, 2) / 2;
}

function delay(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function normalizeImages(rawImages) {
  const values = Array.isArray(rawImages) ? rawImages : [];
  return values
    .map((item) => String(item || "").trim())
    .filter(Boolean);
}

function clampIndex(index, length) {
  if (!length) {
    return 0;
  }
  const number = Number(index);
  if (!Number.isFinite(number)) {
    return 0;
  }
  return Math.min(Math.max(Math.floor(number), 0), length - 1);
}

function modulo(index, length) {
  if (!length) {
    return 0;
  }
  return ((index % length) + length) % length;
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function positiveNumber(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : fallback;
}
