(function (root, factory) {
  const api = factory(root);
  root.ShoppingLoginVisuals = api;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function (root) {
  const Pretext = {
    _canvas: typeof document !== "undefined" ? document.createElement("canvas") : null,
    _ctx: null,
    prepare(text, font) {
      if (!this._canvas) {
        return { preparedSegments: [], font };
      }
      if (!this._ctx) {
        this._ctx = this._canvas.getContext("2d");
      }
      this._ctx.font = font;
      const segments = text.split(/(\s+)/).filter(Boolean);
      return {
        preparedSegments: segments.map((segment) => ({
          text: segment,
          width: this._ctx.measureText(segment).width
        })),
        font
      };
    },
    layout(prepared, containerWidth, lineHeight) {
      const lines = [];
      let currentLine = { text: "", width: 0 };

      prepared.preparedSegments.forEach((segment) => {
        if (currentLine.width + segment.width > containerWidth && currentLine.text !== "") {
          lines.push(currentLine);
          currentLine = { text: "", width: 0 };
        }
        currentLine.text += segment.text;
        currentLine.width += segment.width;
      });

      lines.push(currentLine);
      return { lines, height: lines.length * lineHeight };
    }
  };

  let mouseX = 0;
  let mouseY = 0;
  let isTyping = false;
  let isLookingAtEachOther = false;
  let activeTrackingField = null;
  let isPhoneCodeFocused = false;
  let isPasswordPrivacyMode = false;
  let isPurpleBlinking = false;
  let isBlackBlinking = false;
  let isLoginError = false;
  let typingTimer = null;
  let errorRecoverTimer = null;

  let pointerTrackingBound = false;
  let pretextResizeBound = false;
  let formFocusTrackingBound = false;
  const inputBindings = new WeakSet();
  const startedBlinkCharacters = new Set();
  const typingFieldIds = new Set([
    "email",
    "phone-number",
    "register-entry-email",
    "register-email",
    "register-username",
    "register-phone-required-input"
  ]);
  const privacyFieldIds = new Set([
    "password-input",
    "register-password",
    "register-confirm"
  ]);

  let pCanvas = null;
  let pCtx = null;
  let pWidth = 0;
  let pHeight = 0;
  let particles = [];
  let numParticles = 180;
  let particleFrameRequestId = null;
  let lastParticleFrameTime = 0;
  let visualsInitialized = false;

  const BASE_PARTICLES = 180;
  const TARGET_FPS = 48;
  const FRAME_INTERVAL = 1000 / TARGET_FPS;
  const PARTICLE_INTERACTION_DISTANCE = 120;
  const PARTICLE_INTERACTION_DISTANCE_SQ = PARTICLE_INTERACTION_DISTANCE * PARTICLE_INTERACTION_DISTANCE;
  const RESIZE_DEBOUNCE_MS = 200;
  const RESIZE_REINIT_THRESHOLD_PX = 500;
  const HUE_SPEED_MIN = 0.2;
  const HUE_SPEED_MAX = 1.2;
  const shakeIds = ["purple-eyes", "black-eyes", "orange-eyes", "yellow-eyes", "yellow-mouth"];
  const PRIVACY_BODY_TRANSFORMS = {
    purple: "skewX(8deg) translateX(-8px)",
    black: "skewX(7deg) translateX(-4px)",
    orange: "skewX(3deg) translateX(-4px)",
    yellow: "skewX(6deg) translateX(2px)"
  };
  const PRIVACY_FACE_ANCHORS = {
    purpleEyesLeft: "34px",
    purpleEyesTop: "34px",
    blackEyesLeft: "18px",
    blackEyesTop: "28px",
    orangeEyesLeft: "72px",
    orangeEyesTop: "88px",
    yellowEyesLeft: "42px",
    yellowEyesTop: "38px",
    yellowMouthLeft: "34px",
    yellowMouthTop: "88px",
    yellowMouthTransform: "rotate(-6deg)"
  };
  let resizeDebounceTimer = null;

  function bindPointerTracking() {
    if (pointerTrackingBound || typeof document === "undefined") {
      return;
    }
    pointerTrackingBound = true;
    document.addEventListener("mousemove", (event) => {
      mouseX = event.clientX;
      mouseY = event.clientY;
      if (!isTyping && !isLoginError) {
        updateCharacters();
      }
    });
  }

  function bindTypingInput(input) {
    if (!input || inputBindings.has(input)) {
      return;
    }
    inputBindings.add(input);
    input.addEventListener("focus", () => {
      activeTrackingField = input;
      setTyping(true);
    });
    input.addEventListener("blur", () => {
      if (activeTrackingField === input) {
        activeTrackingField = null;
      }
      setTyping(false);
    });
    input.addEventListener("input", () => updateCharacters());
  }

  function setTyping(typing) {
    isTyping = typing;
    if (typing) {
      isLookingAtEachOther = true;
      clearTimeout(typingTimer);
      typingTimer = setTimeout(() => {
        isLookingAtEachOther = false;
        updateCharacters();
      }, 800);
    } else {
      isLookingAtEachOther = false;
    }
    updateCharacters();
  }

  function setPasswordPrivacyMode(enabled) {
    if (isPasswordPrivacyMode === enabled) {
      return;
    }
    isPasswordPrivacyMode = enabled;
    if (enabled) {
      setTyping(false);
      return;
    }
    updateCharacters();
  }

  function resolveFocusedFieldMode(target) {
    if (!(target instanceof HTMLElement) || target.tagName !== "INPUT") {
      return "";
    }
    const id = target.id || "";
    if (privacyFieldIds.has(id)) {
      return "privacy";
    }
    if (typingFieldIds.has(id)) {
      return "typing";
    }
    return "";
  }

  function applyFocusModeFromActiveElement() {
    if (typeof document === "undefined") {
      return;
    }
    const activeElement = document.activeElement;
    const mode = resolveFocusedFieldMode(activeElement);
    if (mode === "privacy") {
      if (activeElement?.type === "text") {
        activeTrackingField = null;
        setPasswordPrivacyMode(true);
        return;
      }
      setPasswordPrivacyMode(false);
      activeTrackingField = activeElement;
      setTyping(true);
      return;
    }
    setPasswordPrivacyMode(false);
    activeTrackingField = mode === "typing" ? activeElement : null;
    setTyping(mode === "typing");
  }

  function bindFormFocusTracking() {
    if (formFocusTrackingBound || typeof document === "undefined") {
      return;
    }
    formFocusTrackingBound = true;

    document.addEventListener("focusin", () => {
      applyFocusModeFromActiveElement();
    });

    document.addEventListener("focusout", () => {
      setTimeout(() => {
        applyFocusModeFromActiveElement();
      }, 0);
    });

    document.addEventListener("input", (event) => {
      if (resolveFocusedFieldMode(event.target) !== "typing") {
        return;
      }
      activeTrackingField = event.target;
      updateCharacters();
    });
  }

  function ensureParticleCanvas() {
    if (typeof document === "undefined") {
      return;
    }
    if (!pCanvas) {
      pCanvas = document.getElementById("canvas-container");
      pCtx = pCanvas ? pCanvas.getContext("2d") : null;
    }
  }

  function getAdaptiveParticleCount() {
    const cores = navigator.hardwareConcurrency || 8;
    const memory = navigator.deviceMemory || 8;
    const isLowPerformanceDevice = cores <= 4 || memory <= 4;
    return isLowPerformanceDevice ? Math.round(BASE_PARTICLES * 0.6) : BASE_PARTICLES;
  }

  function resizeParticles() {
    ensureParticleCanvas();
    if (!pCanvas) {
      return null;
    }
    const previousWidth = pWidth || window.innerWidth;
    const previousHeight = pHeight || window.innerHeight;
    pWidth = pCanvas.width = window.innerWidth;
    pHeight = pCanvas.height = window.innerHeight;
    return {
      previousWidth,
      previousHeight,
      nextWidth: pWidth,
      nextHeight: pHeight
    };
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function scaleParticles(previousWidth, previousHeight, nextWidth, nextHeight) {
    if (!particles.length || previousWidth <= 0 || previousHeight <= 0) {
      return;
    }

    const scaleX = nextWidth / previousWidth;
    const scaleY = nextHeight / previousHeight;

    particles.forEach((particle) => {
      particle.x = clamp(particle.x * scaleX, 0, nextWidth);
      particle.baseX = clamp(particle.baseX * scaleX, 0, nextWidth);
      particle.y = clamp(particle.y * scaleY, 0, nextHeight);
      particle.baseY = clamp(particle.baseY * scaleY, 0, nextHeight);
    });
  }

  function resizeParticlesNow() {
    const previousParticleCount = particles.length;
    numParticles = getAdaptiveParticleCount();
    const size = resizeParticles();
    if (!size) {
      return;
    }

    const { previousWidth, previousHeight, nextWidth, nextHeight } = size;
    const widthDelta = Math.abs(nextWidth - previousWidth);
    const heightDelta = Math.abs(nextHeight - previousHeight);
    const shouldRebuild =
      previousParticleCount !== numParticles ||
      widthDelta > RESIZE_REINIT_THRESHOLD_PX ||
      heightDelta > RESIZE_REINIT_THRESHOLD_PX;

    if (shouldRebuild) {
      initParticles();
      return;
    }

    scaleParticles(previousWidth, previousHeight, nextWidth, nextHeight);
  }

  class DonutParticle {
    constructor() {
      this.x = Math.random() * pWidth;
      this.y = Math.random() * pHeight;
      this.baseX = this.x;
      this.baseY = this.y;
      const baseSize = Math.random() * 8 + 4;
      this.size = baseSize * (Math.random() * 2 + 0.5);
      const baseThickness = Math.random() * 2 + 1.5;
      const originalInnerRadius = Math.max(0, this.size - baseThickness / 2);
      const innerRadius = originalInnerRadius * Math.random();
      this.thickness = Math.max(0.5, (this.size - innerRadius) * 2);
      this.alpha = Math.random() * 0.1 + 0.1;
      this.hue = Math.random() * 360;
      this.hueSpeed = Math.random() * (HUE_SPEED_MAX - HUE_SPEED_MIN) + HUE_SPEED_MIN;
    }

    update() {
      const dx = mouseX - this.x;
      const dy = mouseY - this.y;
      const distSq = dx * dx + dy * dy;

      if (distSq < PARTICLE_INTERACTION_DISTANCE_SQ) {
        const distance = Math.sqrt(distSq) || 1;
        const force = (PARTICLE_INTERACTION_DISTANCE - distance) / PARTICLE_INTERACTION_DISTANCE;
        const directionX = dx / distance;
        const directionY = dy / distance;
        this.x -= directionX * force * 8;
        this.y -= directionY * force * 8;
      } else {
        this.x -= (this.x - this.baseX) * 0.04;
        this.y -= (this.y - this.baseY) * 0.04;
      }

      this.hue = (this.hue + this.hueSpeed) % 360;
    }

    draw() {
      pCtx.beginPath();
      pCtx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
      pCtx.strokeStyle = `hsla(${this.hue}, 85%, 60%, ${this.alpha})`;
      pCtx.lineWidth = this.thickness;
      pCtx.stroke();
    }
  }

  function initParticles() {
    ensureParticleCanvas();
    particles = [];
    for (let index = 0; index < numParticles; index += 1) {
      particles.push(new DonutParticle());
    }
  }

  function animateParticles(timestamp = 0) {
    if (!pCtx) {
      return;
    }
    if (timestamp - lastParticleFrameTime < FRAME_INTERVAL) {
      particleFrameRequestId = requestAnimationFrame(animateParticles);
      return;
    }

    lastParticleFrameTime = timestamp;
    pCtx.clearRect(0, 0, pWidth, pHeight);
    particles.forEach((particle) => {
      particle.update();
      particle.draw();
    });
    particleFrameRequestId = requestAnimationFrame(animateParticles);
  }

  function stopParticlesAnimation() {
    if (particleFrameRequestId !== null) {
      cancelAnimationFrame(particleFrameRequestId);
      particleFrameRequestId = null;
    }
  }

  function startParticlesAnimation() {
    ensureParticleCanvas();
    if (!pCanvas || particleFrameRequestId !== null) {
      return;
    }
    lastParticleFrameTime = 0;
    particleFrameRequestId = requestAnimationFrame(animateParticles);
  }

  function renderWithPretext() {
    if (typeof document === "undefined") {
      return;
    }

    const titleText = "欢迎回来";
    const subtitleText = "请输入您的登录信息";
    const titlePrepared = Pretext.prepare(titleText, '700 28px "Inter", "PingFang SC", sans-serif');
    const subtitlePrepared = Pretext.prepare(subtitleText, '400 14px "Inter", "PingFang SC", sans-serif');

    function renderLines(id, lines, offset) {
      const element = document.getElementById(id);
      if (!element) {
        return;
      }

      element.innerHTML = "";
      let charIndex = 0;
      lines.forEach((line) => {
        const lineNode = document.createElement("div");
        lineNode.className = "pt-line";
        [...line.text].forEach((char) => {
          const span = document.createElement("span");
          span.className = "pt-char";
          span.textContent = char;
          if (char === " ") {
            span.style.width = "0.25em";
          }
          lineNode.appendChild(span);
          setTimeout(() => span.classList.add("visible"), (offset + charIndex++) * 30 + 300);
        });
        element.appendChild(lineNode);
      });
    }

    function updateLayout() {
      const container = document.querySelector(".form-container");
      if (!container) {
        return;
      }
      const width = container.clientWidth - 40;
      const titleLayout = Pretext.layout(titlePrepared, width, 36);
      const subtitleLayout = Pretext.layout(subtitlePrepared, width, 20);
      renderLines("pretext-title", titleLayout.lines, 0);
      renderLines("pretext-subtitle", subtitleLayout.lines, titleText.length);
    }

    if (!pretextResizeBound) {
      pretextResizeBound = true;
      window.addEventListener("resize", updateLayout);
    }
    updateLayout();
  }

  function resolveCharacterTargetPoint() {
    if (
      isTyping
      && activeTrackingField instanceof HTMLElement
      && activeTrackingField.isConnected
    ) {
      const rect = activeTrackingField.getBoundingClientRect();
      return {
        x: rect.left + rect.width * 0.78,
        y: rect.top + rect.height * 0.5,
        source: "field"
      };
    }

    return {
      x: mouseX,
      y: mouseY,
      source: "pointer"
    };
  }

  function calcPosition(element, targetPoint) {
    const rect = element.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 3;
    const dx = targetPoint.x - centerX;
    const dy = targetPoint.y - centerY;
    const isFieldTarget = targetPoint.source === "field";
    return {
      faceX: Math.max(-18, Math.min(18, dx / (isFieldTarget ? 14 : 20))),
      faceY: Math.max(-12, Math.min(12, dy / (isFieldTarget ? 22 : 30))),
      bodySkew: Math.max(-8, Math.min(8, -dx / (isFieldTarget ? 90 : 120)))
    };
  }

  function calcPupilOffset(element, maxDistance, targetPoint) {
    const rect = element.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    const dx = targetPoint.x - centerX;
    const dy = targetPoint.y - centerY;
    const distance = Math.min(Math.sqrt(dx * dx + dy * dy), maxDistance);
    const angle = Math.atan2(dy, dx);
    return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance };
  }

  function updateCharacters() {
    if (typeof document === "undefined") {
      return;
    }

    const purple = document.getElementById("char-purple");
    const black = document.getElementById("char-black");
    const orange = document.getElementById("char-orange");
    const yellow = document.getElementById("char-yellow");
    if (!purple || !black || !orange || !yellow) {
      return;
    }

    const targetPoint = resolveCharacterTargetPoint();
    const isFieldTarget = targetPoint.source === "field";
    const purplePosition = calcPosition(purple, targetPoint);
    const blackPosition = calcPosition(black, targetPoint);
    const orangePosition = calcPosition(orange, targetPoint);
    const yellowPosition = calcPosition(yellow, targetPoint);
    const isLookingAway = isPhoneCodeFocused || isPasswordPrivacyMode;
    const isShowingPassword = isPasswordPrivacyMode;

    purple.style.transform = isShowingPassword
      ? PRIVACY_BODY_TRANSFORMS.purple
      : (isLookingAway
        ? "skewX(-14deg) translateX(-20px)"
        : (isTyping
          ? `skewX(${purplePosition.bodySkew - (isFieldTarget ? 14 : 12)}deg) translateX(${isFieldTarget ? 56 : 40}px)`
          : `skewX(${purplePosition.bodySkew}deg)`));
    purple.style.height = (isLookingAway || isTyping) ? "410px" : "370px";
    black.style.transform = isShowingPassword
      ? PRIVACY_BODY_TRANSFORMS.black
      : (isLookingAway
        ? "skewX(12deg) translateX(-10px)"
        : (isTyping
          ? `skewX(${blackPosition.bodySkew - (isFieldTarget ? 10 : 8)}deg) translateX(${isFieldTarget ? 18 : 0}px)`
          : `skewX(${blackPosition.bodySkew}deg)`));

    if (isShowingPassword) {
      orange.style.transform = PRIVACY_BODY_TRANSFORMS.orange;
      yellow.style.transform = PRIVACY_BODY_TRANSFORMS.yellow;
    } else {
      orange.style.transform = isTyping
        ? `skewX(${orangePosition.bodySkew - (isFieldTarget ? 4 : 2)}deg) translateX(${isFieldTarget ? 12 : 0}px)`
        : `skewX(${orangePosition.bodySkew}deg)`;
      yellow.style.transform = isTyping
        ? `skewX(${yellowPosition.bodySkew - (isFieldTarget ? 6 : 3)}deg) translateX(${isFieldTarget ? 22 : 0}px)`
        : `skewX(${yellowPosition.bodySkew}deg)`;
    }

    const purpleEyeLeft = document.getElementById("purple-eye-l");
    const purpleEyeRight = document.getElementById("purple-eye-r");
    const blackEyeLeft = document.getElementById("black-eye-l");
    const blackEyeRight = document.getElementById("black-eye-r");
    if (!purpleEyeLeft || !purpleEyeRight || !blackEyeLeft || !blackEyeRight) {
      return;
    }

    purpleEyeLeft.style.height = isPurpleBlinking ? "2px" : "18px";
    purpleEyeRight.style.height = isPurpleBlinking ? "2px" : "18px";
    blackEyeLeft.style.height = isBlackBlinking ? "2px" : "16px";
    blackEyeRight.style.height = isBlackBlinking ? "2px" : "16px";

    const purpleEyes = document.getElementById("purple-eyes");
    const blackEyes = document.getElementById("black-eyes");
    const orangeEyes = document.getElementById("orange-eyes");
    const yellowEyes = document.getElementById("yellow-eyes");
    const yellowMouth = document.getElementById("yellow-mouth");
    const purplePupilLeft = document.getElementById("purple-pupil-l");
    const purplePupilRight = document.getElementById("purple-pupil-r");
    const blackPupilLeft = document.getElementById("black-pupil-l");
    const blackPupilRight = document.getElementById("black-pupil-r");
    const orangePupilLeft = document.getElementById("orange-pupil-l");
    const orangePupilRight = document.getElementById("orange-pupil-r");
    const yellowPupilLeft = document.getElementById("yellow-pupil-l");
    const yellowPupilRight = document.getElementById("yellow-pupil-r");
    if (
      !purpleEyes
      || !blackEyes
      || !orangeEyes
      || !yellowEyes
      || !yellowMouth
      || !purplePupilLeft
      || !purplePupilRight
      || !blackPupilLeft
      || !blackPupilRight
      || !orangePupilLeft
      || !orangePupilRight
      || !yellowPupilLeft
      || !yellowPupilRight
    ) {
      return;
    }

    const resetPupilTransforms = () => {
      [
        purplePupilLeft,
        purplePupilRight,
        blackPupilLeft,
        blackPupilRight,
        orangePupilLeft,
        orangePupilRight,
        yellowPupilLeft,
        yellowPupilRight
      ].forEach((node) => {
        node.style.transform = "";
      });
    };

    const purplePupilOffset = calcPupilOffset(purpleEyeLeft, 3.8, targetPoint);
    const blackPupilOffset = calcPupilOffset(blackEyeLeft, 3.2, targetPoint);
    const orangePupilOffset = calcPupilOffset(orangeEyes, 6, targetPoint);
    const yellowPupilOffset = calcPupilOffset(yellowEyes, 5, targetPoint);
    purplePupilLeft.style.transform = `translate(${purplePupilOffset.x}px, ${purplePupilOffset.y}px)`;
    purplePupilRight.style.transform = `translate(${purplePupilOffset.x}px, ${purplePupilOffset.y}px)`;
    blackPupilLeft.style.transform = `translate(${blackPupilOffset.x}px, ${blackPupilOffset.y}px)`;
    blackPupilRight.style.transform = `translate(${blackPupilOffset.x}px, ${blackPupilOffset.y}px)`;
    orangePupilLeft.style.transform = `translate(${orangePupilOffset.x}px, ${orangePupilOffset.y}px)`;
    orangePupilRight.style.transform = `translate(${orangePupilOffset.x}px, ${orangePupilOffset.y}px)`;
    yellowPupilLeft.style.transform = `translate(${yellowPupilOffset.x}px, ${yellowPupilOffset.y}px)`;
    yellowPupilRight.style.transform = `translate(${yellowPupilOffset.x}px, ${yellowPupilOffset.y}px)`;

    if (isLoginError) {
      resetPupilTransforms();
      purpleEyes.style.left = "30px";
      purpleEyes.style.top = "55px";
      blackEyes.style.left = "15px";
      blackEyes.style.top = "40px";
      orangeEyes.style.left = "60px";
      orangeEyes.style.top = "95px";
      yellowEyes.style.left = "35px";
      yellowEyes.style.top = "45px";
      yellowMouth.style.left = "30px";
      yellowMouth.style.top = "92px";
      yellowMouth.style.transform = "rotate(-8deg)";
      return;
    }

    if (isShowingPassword) {
      resetPupilTransforms();
      purpleEyes.style.left = PRIVACY_FACE_ANCHORS.purpleEyesLeft;
      purpleEyes.style.top = PRIVACY_FACE_ANCHORS.purpleEyesTop;
      blackEyes.style.left = PRIVACY_FACE_ANCHORS.blackEyesLeft;
      blackEyes.style.top = PRIVACY_FACE_ANCHORS.blackEyesTop;
      orangeEyes.style.left = PRIVACY_FACE_ANCHORS.orangeEyesLeft;
      orangeEyes.style.top = PRIVACY_FACE_ANCHORS.orangeEyesTop;
      yellowEyes.style.left = PRIVACY_FACE_ANCHORS.yellowEyesLeft;
      yellowEyes.style.top = PRIVACY_FACE_ANCHORS.yellowEyesTop;
      yellowMouth.style.left = PRIVACY_FACE_ANCHORS.yellowMouthLeft;
      yellowMouth.style.top = PRIVACY_FACE_ANCHORS.yellowMouthTop;
      yellowMouth.style.transform = PRIVACY_FACE_ANCHORS.yellowMouthTransform;
      return;
    }

    if (isLookingAway) {
      resetPupilTransforms();
      purpleEyes.style.left = "20px";
      purpleEyes.style.top = "25px";
      blackEyes.style.left = "10px";
      blackEyes.style.top = "20px";
      orangeEyes.style.left = "50px";
      orangeEyes.style.top = "75px";
      yellowEyes.style.left = "20px";
      yellowEyes.style.top = "30px";
      yellowMouth.style.left = "32px";
      yellowMouth.style.top = "90px";
      yellowMouth.style.transform = "rotate(-4deg)";
      return;
    }

    purpleEyes.style.left = `${45 + purplePosition.faceX}px`;
    purpleEyes.style.top = `${40 + purplePosition.faceY}px`;
    blackEyes.style.left = `${26 + blackPosition.faceX}px`;
    blackEyes.style.top = `${32 + blackPosition.faceY}px`;
    orangeEyes.style.left = `${82 + orangePosition.faceX}px`;
    orangeEyes.style.top = `${90 + orangePosition.faceY}px`;
    yellowEyes.style.left = `${52 + yellowPosition.faceX}px`;
    yellowEyes.style.top = `${40 + yellowPosition.faceY}px`;
    yellowMouth.style.left = `${40 + yellowPosition.faceX}px`;
    yellowMouth.style.top = `${88 + yellowPosition.faceY}px`;
    yellowMouth.style.transform = "";
  }

  function blinkOnce(character) {
    const setBlink = (value) => {
      if (character === "purple") {
        isPurpleBlinking = value;
      } else {
        isBlackBlinking = value;
      }
    };

    setBlink(true);
    updateCharacters();
    setTimeout(() => {
      setBlink(false);
      updateCharacters();
    }, 130 + Math.random() * 50);
  }

  function scheduleRandomBlink(character) {
    if (startedBlinkCharacters.has(character)) {
      return;
    }
    startedBlinkCharacters.add(character);

    const scheduleNext = () => {
      const nextDelay = 2500 + Math.random() * 3500;
      setTimeout(() => {
        if (!document.hidden) {
          blinkOnce(character);
          if (Math.random() < 0.2) {
            setTimeout(() => blinkOnce(character), 140 + Math.random() * 120);
          }
        }
        scheduleNext();
      }, nextDelay);
    };

    scheduleNext();
  }

  function triggerLoginError() {
    if (errorRecoverTimer) {
      clearTimeout(errorRecoverTimer);
      errorRecoverTimer = null;
    }

    const shakeElements = shakeIds.map((id) => document.getElementById(id));
    shakeElements.forEach((element) => {
      if (element) {
        element.classList.remove("shake-head");
      }
    });
    void document.body.offsetHeight;
    isLoginError = true;
    updateCharacters();
    setTimeout(() => {
      shakeElements.forEach((element) => {
        if (element) {
          element.classList.add("shake-head");
        }
      });
    }, 350);
    errorRecoverTimer = setTimeout(() => {
      isLoginError = false;
      errorRecoverTimer = null;
      shakeElements.forEach((element) => {
        if (element) {
          element.classList.remove("shake-head");
        }
      });
      updateCharacters();
    }, 2500);
  }

  function initializeVisuals(options = {}) {
    if (visualsInitialized) {
      startParticlesAnimation();
      updateCharacters();
      return;
    }
    visualsInitialized = true;

    bindPointerTracking();
    bindTypingInput(options.emailInput);
    bindTypingInput(options.phoneNumberInput);
    bindFormFocusTracking();

    numParticles = getAdaptiveParticleCount();
    resizeParticles();
    initParticles();
    startParticlesAnimation();
    renderWithPretext();
    scheduleRandomBlink("purple");
    scheduleRandomBlink("black");
    updateCharacters();
  }

  function handleVisualVisibilityChange(hidden) {
    if (hidden) {
      stopParticlesAnimation();
      return;
    }
    startParticlesAnimation();
  }

  function handleVisualResize() {
    if (resizeDebounceTimer !== null) {
      clearTimeout(resizeDebounceTimer);
    }
    resizeDebounceTimer = setTimeout(() => {
      resizeDebounceTimer = null;
      resizeParticlesNow();
      updateCharacters();
    }, RESIZE_DEBOUNCE_MS);
  }

  return {
    initializeVisuals,
    handleVisualResize,
    handleVisualVisibilityChange,
    triggerLoginError,
    updateCharacters,
    applyFocusModeFromActiveElement
  };
});
