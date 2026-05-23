import { HoverDisplacementField } from "./product-carousel-hover-displacement.js";
import { createCarouselProgram } from "./product-carousel-shaders.js";

const DEFAULT_TRANSITION_MS = 1400;
const MAX_PIXEL_RATIO = 1.5;
const DISTORTION_STRENGTH = 0.2;

export class WebglRippleStage {
  constructor(stageEl, options = {}) {
    this.stageEl = stageEl;
    this.transitionMs = positiveNumber(options.transitionMs, DEFAULT_TRANSITION_MS);
    this.canvas = null;
    this.gl = null;
    this.program = null;
    this.positionBuffer = null;
    this.positionLocation = -1;
    this.uniforms = null;
    this.displacement = null;
    this.textures = new Map();
    this.pendingTextures = new Map();
    this.currentUrl = "";
    this.currentTexture = null;
    this.transition = null;
    this.transitionResolve = null;
    this.transitionToken = 0;
    this.destroyed = false;
    this.animationFrameId = null;
    this.resizeObserver = null;
    this.resizeHandler = () => this.resize();
    this.pointerMoveHandler = (event) => this.displacement?.handlePointerMove(event, this.canvas);
    this.pointerLeaveHandler = () => this.displacement?.handlePointerLeave();
  }

  async init(initialUrl) {
    this.canvas = document.createElement("canvas");
    this.canvas.className = "admin-product-ripple-carousel-canvas";
    this.stageEl.appendChild(this.canvas);
    this.gl = this.canvas.getContext("webgl", {
      alpha: false,
      antialias: true,
      premultipliedAlpha: false
    });
    if (!this.gl) {
      throw new Error("WebGL is unavailable");
    }

    this.program = createCarouselProgram(this.gl);
    this.positionBuffer = createPositionBuffer(this.gl);
    this.positionLocation = this.gl.getAttribLocation(this.program, "aPosition");
    this.uniforms = getUniforms(this.gl, this.program);
    this.displacement = new HoverDisplacementField(this.gl);
    this.currentTexture = await this.getTexture(initialUrl);
    this.currentUrl = initialUrl;
    this.observeResize();
    this.canvas.addEventListener("pointermove", this.pointerMoveHandler);
    this.canvas.addEventListener("pointerleave", this.pointerLeaveHandler);
    this.resize();
    this.startRenderLoop();
  }

  async setImage(url) {
    this.cancelTransition();
    const texture = await this.getTexture(url);
    this.currentTexture = texture;
    this.currentUrl = url || "";
    this.transition = null;
  }

  async transitionTo(url, options = {}) {
    if (!url || url === this.currentUrl || options.immediate) {
      await this.setImage(url || this.currentUrl);
      return;
    }
    const token = ++this.transitionToken;
    this.cancelTransition(false);
    const nextTexture = await this.getTexture(url);
    if (token !== this.transitionToken) {
      return;
    }
    const fromTexture = this.currentTexture;
    if (!fromTexture) {
      await this.setImage(url);
      return;
    }
    this.transition = {
      fromTexture,
      toTexture: nextTexture,
      targetUrl: url,
      startedAt: performance.now(),
      duration: this.transitionMs,
      token
    };
    return new Promise((resolve) => {
      this.transitionResolve = resolve;
    });
  }

  preload(url, options = {}) {
    if (!url) {
      return Promise.resolve(null);
    }
    const promise = this.getTexture(url);
    return options.strict ? promise : promise.catch(() => null);
  }

  observeResize() {
    if (window.ResizeObserver) {
      this.resizeObserver = new ResizeObserver(() => this.resize());
      this.resizeObserver.observe(this.stageEl);
    } else {
      window.addEventListener("resize", this.resizeHandler);
    }
  }

  resize() {
    if (!this.canvas || !this.gl || !this.stageEl) {
      return;
    }
    const rect = this.stageEl.getBoundingClientRect();
    const width = Math.max(1, Math.round(rect.width));
    const height = Math.max(1, Math.round(rect.height));
    const pixelRatio = Math.min(window.devicePixelRatio || 1, MAX_PIXEL_RATIO);
    const bufferWidth = Math.max(1, Math.round(width * pixelRatio));
    const bufferHeight = Math.max(1, Math.round(height * pixelRatio));
    if (this.canvas.width !== bufferWidth || this.canvas.height !== bufferHeight) {
      this.canvas.width = bufferWidth;
      this.canvas.height = bufferHeight;
    }
    this.canvas.style.width = `${width}px`;
    this.canvas.style.height = `${height}px`;
    this.gl.viewport(0, 0, bufferWidth, bufferHeight);
  }

  startRenderLoop() {
    const render = (time) => {
      this.render(time);
      this.animationFrameId = window.requestAnimationFrame(render);
    };
    this.animationFrameId = window.requestAnimationFrame(render);
  }

  render(time) {
    if (!this.gl || !this.program || !this.positionBuffer || !this.currentTexture || !this.uniforms || !this.displacement) {
      return;
    }
    const transitionState = this.resolveTransition(time);
    const fromTexture = transitionState.fromTexture || this.currentTexture;
    const toTexture = transitionState.toTexture || fromTexture;
    this.displacement.updateAndUpload();

    const gl = this.gl;
    gl.clearColor(0.07, 0.09, 0.07, 1);
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.useProgram(this.program);
    gl.bindBuffer(gl.ARRAY_BUFFER, this.positionBuffer);
    gl.enableVertexAttribArray(this.positionLocation);
    gl.vertexAttribPointer(this.positionLocation, 2, gl.FLOAT, false, 0, 0);

    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, fromTexture.texture);
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, toTexture.texture);
    gl.activeTexture(gl.TEXTURE2);
    gl.bindTexture(gl.TEXTURE_2D, this.displacement.texture);

    gl.uniform1i(this.uniforms.texture, 0);
    gl.uniform1i(this.uniforms.textureNext, 1);
    gl.uniform1i(this.uniforms.displacementMap, 2);
    gl.uniform1f(this.uniforms.distortionStrength, DISTORTION_STRENGTH);
    gl.uniform1f(this.uniforms.progress, transitionState.progress);
    gl.uniform1f(this.uniforms.opacity, 1);
    gl.uniform2f(this.uniforms.meshSize, this.canvas.width, this.canvas.height);
    gl.uniform2f(this.uniforms.imageSizeCurrent, fromTexture.width, fromTexture.height);
    gl.uniform2f(this.uniforms.imageSizeNext, toTexture.width, toTexture.height);
    gl.drawArrays(gl.TRIANGLES, 0, 6);
  }

  resolveTransition(time) {
    const active = this.transition;
    if (!active) {
      return {
        fromTexture: this.currentTexture,
        toTexture: this.currentTexture,
        progress: 0
      };
    }
    const rawProgress = clamp((time - active.startedAt) / active.duration, 0, 1);
    if (rawProgress >= 1) {
      this.currentTexture = active.toTexture;
      this.currentUrl = active.targetUrl;
      this.transition = null;
      this.resolveTransitionPromise();
      return {
        fromTexture: this.currentTexture,
        toTexture: this.currentTexture,
        progress: 0
      };
    }
    return {
      fromTexture: active.fromTexture,
      toTexture: active.toTexture,
      progress: easeOutCubic(rawProgress)
    };
  }

  getTexture(url) {
    const normalized = String(url || "").trim();
    if (!normalized) {
      return Promise.reject(new Error("Missing image URL"));
    }
    if (this.textures.has(normalized)) {
      return Promise.resolve(this.textures.get(normalized));
    }
    if (this.pendingTextures.has(normalized)) {
      return this.pendingTextures.get(normalized);
    }
    const promise = loadImage(normalized)
      .then((image) => {
        if (this.destroyed || !this.gl) {
          throw new Error("WebGL stage destroyed");
        }
        return createImageTexture(this.gl, image);
      })
      .then((texture) => {
        this.textures.set(normalized, texture);
        return texture;
      })
      .finally(() => {
        this.pendingTextures.delete(normalized);
      });
    this.pendingTextures.set(normalized, promise);
    return promise;
  }

  cancelTransition(incrementToken = true) {
    if (incrementToken) {
      this.transitionToken += 1;
    }
    this.transition = null;
    this.resolveTransitionPromise();
  }

  resolveTransitionPromise() {
    if (this.transitionResolve) {
      this.transitionResolve();
      this.transitionResolve = null;
    }
  }

  destroy() {
    this.destroyed = true;
    this.cancelTransition();
    if (this.animationFrameId) {
      window.cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
    this.resizeObserver?.disconnect();
    window.removeEventListener("resize", this.resizeHandler);
    this.canvas?.removeEventListener("pointermove", this.pointerMoveHandler);
    this.canvas?.removeEventListener("pointerleave", this.pointerLeaveHandler);
    this.textures.forEach(({ texture }) => this.gl?.deleteTexture(texture));
    this.textures.clear();
    this.pendingTextures.clear();
    this.displacement?.dispose();
    if (this.gl) {
      this.gl.deleteBuffer(this.positionBuffer);
      this.gl.deleteProgram(this.program);
    }
    this.canvas?.remove();
    this.canvas = null;
    this.gl = null;
    this.program = null;
    this.positionBuffer = null;
    this.displacement = null;
    this.currentTexture = null;
  }
}

export function prewarmWebglRippleStage() {
  if (typeof document === "undefined") {
    return Promise.resolve();
  }
  const canvas = document.createElement("canvas");
  canvas.width = 1;
  canvas.height = 1;
  const gl = canvas.getContext("webgl", { alpha: false, antialias: false });
  if (!gl) {
    return Promise.resolve();
  }
  const program = createCarouselProgram(gl);
  const positionBuffer = createPositionBuffer(gl);
  const displacement = new HoverDisplacementField(gl);
  const texture = createSinglePixelTexture(gl);
  gl.useProgram(program);
  gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
  const positionLocation = gl.getAttribLocation(program, "aPosition");
  gl.enableVertexAttribArray(positionLocation);
  gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0);
  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, texture.texture);
  gl.activeTexture(gl.TEXTURE1);
  gl.bindTexture(gl.TEXTURE_2D, texture.texture);
  gl.activeTexture(gl.TEXTURE2);
  gl.bindTexture(gl.TEXTURE_2D, displacement.texture);
  gl.drawArrays(gl.TRIANGLES, 0, 6);
  gl.deleteTexture(texture.texture);
  displacement.dispose();
  gl.deleteBuffer(positionBuffer);
  gl.deleteProgram(program);
  return Promise.resolve();
}

function createPositionBuffer(gl) {
  const buffer = gl.createBuffer();
  if (!buffer) {
    throw new Error("Failed to create position buffer");
  }
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(
    gl.ARRAY_BUFFER,
    new Float32Array([
      -1, -1,
      1, -1,
      -1, 1,
      -1, 1,
      1, -1,
      1, 1
    ]),
    gl.STATIC_DRAW
  );
  return buffer;
}

function getUniforms(gl, program) {
  return {
    texture: gl.getUniformLocation(program, "uTexture"),
    textureNext: gl.getUniformLocation(program, "uTextureNext"),
    displacementMap: gl.getUniformLocation(program, "uDisplacementMap"),
    distortionStrength: gl.getUniformLocation(program, "uDistortionStrength"),
    progress: gl.getUniformLocation(program, "uProgress"),
    opacity: gl.getUniformLocation(program, "uOpacity"),
    meshSize: gl.getUniformLocation(program, "uMeshSize"),
    imageSizeCurrent: gl.getUniformLocation(program, "uImageSizeCurrent"),
    imageSizeNext: gl.getUniformLocation(program, "uImageSizeNext")
  };
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.decoding = "async";
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`Failed to load image: ${src}`));
    image.src = corsTextureUrl(src);
  });
}

function corsTextureUrl(src) {
  try {
    const url = new URL(src, window.location.href);
    if (url.origin !== window.location.origin && !isSignedOssUrl(url)) {
      url.searchParams.set("admin-carousel-cors", "1");
    }
    return url.href;
  } catch (_) {
    return src;
  }
}

function isSignedOssUrl(url) {
  const signedKeys = new Set([
    "signature",
    "expires",
    "ossaccesskeyid",
    "security-token",
    "x-oss-signature",
    "x-oss-signature-version",
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

function createImageTexture(gl, image) {
  const texture = gl.createTexture();
  if (!texture) {
    throw new Error("Failed to create image texture");
  }
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, 1);
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

function createSinglePixelTexture(gl) {
  const texture = gl.createTexture();
  if (!texture) {
    throw new Error("Failed to create image texture");
  }
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texImage2D(
    gl.TEXTURE_2D,
    0,
    gl.RGBA,
    1,
    1,
    0,
    gl.RGBA,
    gl.UNSIGNED_BYTE,
    new Uint8Array([255, 255, 255, 255])
  );
  return {
    texture,
    width: 1,
    height: 1
  };
}

function positiveNumber(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : fallback;
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function easeOutCubic(value) {
  return 1 - Math.pow(1 - value, 3);
}
