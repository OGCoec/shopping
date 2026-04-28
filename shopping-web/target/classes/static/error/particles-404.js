import * as THREE from "/error/three.module.min.js";

const SIM_SIZE = 256;
const SIM_LENGTH = SIM_SIZE * SIM_SIZE;
const DENSITY = 230;
const PARTICLE_SCALE = 0.59;
const RING_WIDTH = 0.011;
const RING_WIDTH_2 = 0.107;
const RING_DISPLACEMENT = 0.53;
const DOMAIN_SIZE = 500;
const DOMAIN_HALF = DOMAIN_SIZE / 2;

const SIM_VERTEX_SHADER = `
  void main() {
    gl_Position = vec4(position, 1.0);
  }
`;

const SIM_FRAGMENT_SHADER = `
  precision highp float;

  uniform sampler2D uPosition;
  uniform sampler2D uPosRefs;
  uniform vec2 uRingPos;
  uniform float uTime;
  uniform float uDeltaTime;
  uniform float uRingRadius;
  uniform float uRingWidth;
  uniform float uRingWidth2;
  uniform float uRingDisplacement;

  vec2 hash(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453123);
  }

  float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
      mix(hash(i + vec2(0.0, 0.0)).x, hash(i + vec2(1.0, 0.0)).x, u.x),
      mix(hash(i + vec2(0.0, 1.0)).x, hash(i + vec2(1.0, 1.0)).x, u.x),
      u.y
    ) * 2.0 - 1.0;
  }

  void main() {
    vec2 simTexCoords = gl_FragCoord.xy / vec2(${SIM_SIZE.toFixed(1)}, ${SIM_SIZE.toFixed(1)});
    vec4 pFrame = texture2D(uPosition, simTexCoords);
    vec2 refPos = texture2D(uPosRefs, simTexCoords).xy;

    float scale = pFrame.z;
    float velocity = pFrame.w;
    float time = uTime * 0.5;
    vec2 currentPos = refPos;
    vec2 pos = pFrame.xy * 0.8;

    float dist = distance(currentPos.xy, uRingPos);
    float noise0 = noise(currentPos.xy * 0.2 + vec2(18.4924, 72.9744) + time * 0.5);
    float dist1 = distance(currentPos.xy + (noise0 * 0.005), uRingPos);

    float t = smoothstep(uRingRadius - (uRingWidth * 2.0), uRingRadius, dist) - smoothstep(uRingRadius, uRingRadius + uRingWidth, dist1);
    float t2 = smoothstep(uRingRadius - (uRingWidth2 * 2.0), uRingRadius, dist) - smoothstep(uRingRadius, uRingRadius + uRingWidth2, dist1);
    float t3 = smoothstep(uRingRadius + uRingWidth2, uRingRadius, dist);

    t = pow(t, 2.0);
    t2 = pow(t2, 3.0);
    t += t2 * 3.0;
    t += t3 * 0.4;
    t += noise(currentPos.xy * 30.0 + vec2(11.4924, 12.9744) + time * 0.5) * t3 * 0.5;

    float nS = noise(currentPos.xy * 2.0 + vec2(18.4924, 72.9744) + time * 0.5);
    t += pow((nS + 1.5) * 0.5, 2.0) * 0.6;

    float noise1 = noise(currentPos.xy * 4.0 + vec2(88.494, 32.4397) + time * 0.35);
    float noise2 = noise(currentPos.xy * 4.0 + vec2(50.904, 120.947) + time * 0.35);
    float noise3 = noise(currentPos.xy * 20.0 + vec2(18.4924, 72.9744) + time * 0.5);
    float noise4 = noise(currentPos.xy * 20.0 + vec2(50.904, 120.947) + time * 0.5);

    vec2 disp = vec2(noise1, noise2) * 0.03;
    disp += vec2(noise3, noise4) * 0.005;
    disp.x += sin((refPos.x * 20.0) + (time * 4.0)) * 0.02 * clamp(dist, 0.0, 1.0);
    disp.y += cos((refPos.y * 20.0) + (time * 3.0)) * 0.02 * clamp(dist, 0.0, 1.0);

    pos -= (uRingPos - (currentPos + disp)) * pow(t2, 0.75) * uRingDisplacement;

    float scaleDiff = t - scale;
    scale += scaleDiff * 0.2;

    vec2 finalPos = currentPos + disp + (pos * 0.25);
    velocity *= 0.5;
    velocity += scale * 0.25;

    gl_FragColor = vec4(finalPos, scale, velocity);
  }
`;

const RENDER_VERTEX_SHADER = `
  precision highp float;

  attribute vec4 seeds;

  uniform sampler2D uPosition;
  uniform float uTime;
  uniform float uParticleScale;
  uniform float uPixelRatio;

  varying vec4 vSeeds;
  varying float vVelocity;
  varying vec2 vLocalPos;
  varying vec2 vScreenPos;
  varying float vScale;

  void main() {
    vec4 pos = texture2D(uPosition, uv);
    vSeeds = seeds;
    vVelocity = pos.w;
    vScale = pos.z;
    vLocalPos = pos.xy;

    vec4 viewSpace = modelViewMatrix * vec4(vec3(pos.xy, 0.0), 1.0);
    gl_Position = projectionMatrix * viewSpace;
    vScreenPos = gl_Position.xy;
    gl_PointSize = ((vScale * 7.0) * (uPixelRatio * 0.5) * uParticleScale);
  }
`;

const RENDER_FRAGMENT_SHADER = `
  precision highp float;

  varying vec4 vSeeds;
  varying vec2 vScreenPos;
  varying vec2 vLocalPos;
  varying float vScale;
  varying float vVelocity;

  uniform vec3 uColor1;
  uniform vec3 uColor2;
  uniform vec3 uColor3;
  uniform vec2 uRingPos;
  uniform float uAlpha;
  uniform float uTime;

  float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
  }

  float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
      mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
      mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
      u.y
    ) * 2.0 - 1.0;
  }

  float sdRoundBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
  }

  vec2 rotate(vec2 v, float a) {
    float s = sin(a);
    float c = cos(a);
    mat2 m = mat2(c, s, -s, c);
    return m * v;
  }

  void main() {
    float noiseAngle = noise(vLocalPos * 10.0 + vec2(18.4924, 72.9744) + uTime * 0.85);
    float noiseColor = noise(vLocalPos * 2.0 + vec2(74.664, 91.556) + uTime * 0.5);
    noiseColor = (noiseColor + 1.0) * 0.5;

    float angle = atan(vLocalPos.y - uRingPos.y, vLocalPos.x - uRingPos.x);

    vec2 uv = gl_PointCoord.xy - vec2(0.5);
    uv.y *= -1.0;
    uv = rotate(uv, -angle + (noiseAngle * 0.5));

    float h = 0.8;
    float progress = smoothstep(0.0, 0.75, pow(noiseColor, 2.0));
    vec3 col = mix(
      mix(uColor1, uColor2, progress / h),
      mix(uColor2, uColor3, (progress - h) / (1.0 - h)),
      step(h, progress)
    );

    float rounded = sdRoundBox(uv, vec2(0.5, 0.2), vec4(0.25));
    rounded = smoothstep(0.1, 0.0, rounded);

    float a = uAlpha * rounded * smoothstep(0.1, 0.2, vScale);
    if (a < 0.01) {
      discard;
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), clamp(a, 0.0, 1.0));
  }
`;

function mapRange(value, inMin, inMax, outMin, outMax) {
  return ((value - inMin) * (outMax - outMin)) / (inMax - inMin) + outMin;
}

function createValueNoise1D() {
  const size = 256;
  const values = Array.from({ length: size }, () => Math.random());

  return (value) => {
    const index = Math.floor(value);
    const fraction = value - index;
    const eased = fraction * fraction * (3 - 2 * fraction);
    const current = ((index % size) + size) % size;
    const next = (current + 1) % size;
    return values[current] * (1 - eased) + values[next] * eased;
  };
}

function createPoissonLikePoints(width, height, minDistance, maxDistance, tries) {
  const points = [];
  const active = [];
  const cellSize = minDistance / Math.SQRT2;
  const gridWidth = Math.ceil(width / cellSize);
  const gridHeight = Math.ceil(height / cellSize);
  const grid = new Array(gridWidth * gridHeight);
  const minDistanceSq = minDistance * minDistance;

  const addPoint = (point) => {
    points.push(point);
    active.push(point);
    const gridX = Math.floor(point.x / cellSize);
    const gridY = Math.floor(point.y / cellSize);
    grid[gridX + gridY * gridWidth] = points.length - 1;
  };

  const isValid = (point) => {
    if (point.x < 0 || point.x >= width || point.y < 0 || point.y >= height) {
      return false;
    }

    const gridX = Math.floor(point.x / cellSize);
    const gridY = Math.floor(point.y / cellSize);
    for (let y = Math.max(0, gridY - 2); y <= Math.min(gridHeight - 1, gridY + 2); y++) {
      for (let x = Math.max(0, gridX - 2); x <= Math.min(gridWidth - 1, gridX + 2); x++) {
        const pointIndex = grid[x + y * gridWidth];
        if (pointIndex === undefined) continue;
        const existing = points[pointIndex];
        const dx = existing.x - point.x;
        const dy = existing.y - point.y;
        if (dx * dx + dy * dy < minDistanceSq) {
          return false;
        }
      }
    }

    return true;
  };

  addPoint({ x: Math.random() * width, y: Math.random() * height });

  while (active.length > 0 && points.length < SIM_LENGTH) {
    const activeIndex = Math.floor(Math.random() * active.length);
    const source = active[activeIndex];
    let accepted = false;

    for (let i = 0; i < tries; i++) {
      const angle = Math.random() * Math.PI * 2;
      const distance = minDistance + Math.random() * (maxDistance - minDistance);
      const candidate = {
        x: source.x + Math.cos(angle) * distance,
        y: source.y + Math.sin(angle) * distance,
      };

      if (isValid(candidate)) {
        addPoint(candidate);
        accepted = true;
        break;
      }
    }

    if (!accepted) {
      active.splice(activeIndex, 1);
    }
  }

  return points;
}

function createPositionTexture(points) {
  const data = new Float32Array(SIM_LENGTH * 4);

  points.forEach((point, index) => {
    const offset = index * 4;
    data[offset] = (point.x - DOMAIN_HALF) / DOMAIN_HALF;
    data[offset + 1] = (point.y - DOMAIN_HALF) / DOMAIN_HALF;
    data[offset + 2] = 0;
    data[offset + 3] = 0;
  });

  const texture = new THREE.DataTexture(data, SIM_SIZE, SIM_SIZE, THREE.RGBAFormat, THREE.FloatType);
  texture.minFilter = THREE.NearestFilter;
  texture.magFilter = THREE.NearestFilter;
  texture.wrapS = THREE.ClampToEdgeWrapping;
  texture.wrapT = THREE.ClampToEdgeWrapping;
  texture.needsUpdate = true;
  return texture;
}

function createRenderTarget() {
  return new THREE.WebGLRenderTarget(SIM_SIZE, SIM_SIZE, {
    format: THREE.RGBAFormat,
    type: THREE.FloatType,
    minFilter: THREE.NearestFilter,
    magFilter: THREE.NearestFilter,
    wrapS: THREE.ClampToEdgeWrapping,
    wrapT: THREE.ClampToEdgeWrapping,
    depthBuffer: false,
    stencilBuffer: false,
  });
}

function initParticlesBackground(container) {
  if (!container) return;

  const renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: true,
    powerPreference: "high-performance",
    preserveDrawingBuffer: true,
    stencil: false,
    precision: "highp",
  });

  renderer.setClearColor(0xffffff, 0);
  renderer.setPixelRatio(window.devicePixelRatio || 1);
  renderer.domElement.style.position = "absolute";
  renderer.domElement.style.inset = "0";
  renderer.domElement.style.width = "100%";
  renderer.domElement.style.height = "100%";
  container.appendChild(renderer.domElement);

  const hasFloatRenderTarget = renderer.capabilities.isWebGL2 || !!renderer.extensions.get("WEBGL_color_buffer_float") || !!renderer.extensions.get("EXT_color_buffer_float");

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(40, 1, 0.1, 1000);
  camera.position.z = 3.1;

  const minDistance = mapRange(DENSITY, 0, 300, 10, 2);
  const maxDistance = mapRange(DENSITY, 0, 300, 11, 3);
  const points = createPoissonLikePoints(DOMAIN_SIZE, DOMAIN_SIZE, minDistance, maxDistance, 20);
  const pointCount = Math.min(points.length, SIM_LENGTH);
  const simulationPoints = points.slice(0, pointCount);
  const posTex = createPositionTexture(simulationPoints);

  let rt1 = hasFloatRenderTarget ? createRenderTarget() : null;
  let rt2 = hasFloatRenderTarget ? createRenderTarget() : null;
  let everRendered = false;

  const simScene = new THREE.Scene();
  const simCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  const simMaterial = new THREE.ShaderMaterial({
    uniforms: {
      uPosition: { value: posTex },
      uPosRefs: { value: posTex },
      uRingPos: { value: new THREE.Vector2(0, 0) },
      uRingRadius: { value: 0.175 },
      uDeltaTime: { value: 0 },
      uRingWidth: { value: RING_WIDTH },
      uRingWidth2: { value: RING_WIDTH_2 },
      uRingDisplacement: { value: RING_DISPLACEMENT },
      uTime: { value: 0 },
    },
    vertexShader: SIM_VERTEX_SHADER,
    fragmentShader: SIM_FRAGMENT_SHADER,
  });
  const simGeometry = new THREE.PlaneGeometry(2, 2);
  const simMesh = new THREE.Mesh(simGeometry, simMaterial);
  simScene.add(simMesh);

  if (rt1 && rt2) {
    renderer.setRenderTarget(rt1);
    renderer.clear();
    renderer.setRenderTarget(rt2);
    renderer.clear();
    renderer.setRenderTarget(null);
  }

  const positions = new Float32Array(pointCount * 3);
  const uvs = new Float32Array(pointCount * 2);
  const seeds = new Float32Array(pointCount * 4);

  for (let i = 0; i < pointCount; i++) {
    const x = i % SIM_SIZE;
    const y = Math.floor(i / SIM_SIZE);
    uvs[i * 2] = x / SIM_SIZE;
    uvs[i * 2 + 1] = y / SIM_SIZE;
    seeds[i * 4] = Math.random();
    seeds[i * 4 + 1] = Math.random();
    seeds[i * 4 + 2] = Math.random();
    seeds[i * 4 + 3] = Math.random();
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute("uv", new THREE.BufferAttribute(uvs, 2));
  geometry.setAttribute("seeds", new THREE.BufferAttribute(seeds, 4));

  const renderMaterial = new THREE.ShaderMaterial({
    uniforms: {
      uPosition: { value: posTex },
      uTime: { value: 0 },
      uColor1: { value: new THREE.Color("#2c64ed") },
      uColor2: { value: new THREE.Color("#f84242") },
      uColor3: { value: new THREE.Color("#ffcf03") },
      uAlpha: { value: 1 },
      uRingPos: { value: new THREE.Vector2(0, 0) },
      uParticleScale: { value: PARTICLE_SCALE },
      uPixelRatio: { value: window.devicePixelRatio || 1 },
    },
    vertexShader: RENDER_VERTEX_SHADER,
    fragmentShader: RENDER_FRAGMENT_SHADER,
    transparent: true,
    depthTest: false,
    depthWrite: false,
  });

  const particles = new THREE.Points(geometry, renderMaterial);
  particles.scale.set(5, 5, 5);
  scene.add(particles);

  const raycaster = new THREE.Raycaster();
  const mouse = new THREE.Vector2(0, 0);
  const intersectionPoint = new THREE.Vector3(0, 0, 0);
  const ringPos = new THREE.Vector2(0, 0);
  const cursorPos = new THREE.Vector2(0, 0);
  const cursorNoise = createValueNoise1D();
  let mouseIsOver = false;

  const raycastPlane = new THREE.Mesh(
    new THREE.PlaneGeometry(12.5, 12.5),
    new THREE.MeshBasicMaterial({ visible: false, side: THREE.DoubleSide })
  );
  scene.add(raycastPlane);

  let animationId = 0;
  let visible = true;
  let skipFrame = false;
  const clock = new THREE.Clock();

  const resize = () => {
    const width = container.offsetWidth || window.innerWidth;
    const height = container.offsetHeight || window.innerHeight;
    const nextPixelRatio = window.devicePixelRatio || 1;

    renderer.setPixelRatio(nextPixelRatio);
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderMaterial.uniforms.uPixelRatio.value = nextPixelRatio;
    renderMaterial.uniforms.uParticleScale.value = (width / nextPixelRatio / 2000) * PARTICLE_SCALE;
  };

  const handleMouseMove = (event) => {
    const rect = container.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;

    mouseIsOver = x >= 0 && x <= rect.width && y >= 0 && y <= rect.height;
    mouse.x = (x / rect.width) * 2 - 1;
    mouse.y = -(y / rect.height) * 2 + 1;
  };

  const animate = () => {
    animationId = requestAnimationFrame(animate);
    if (!visible) return;

    const delta = clock.getDelta();
    const time = clock.elapsedTime;

    if (!skipFrame) {
      raycaster.setFromCamera(mouse, camera);
      const intersections = raycaster.intersectObject(raycastPlane);
      if (mouseIsOver && intersections.length > 0) {
        intersectionPoint.copy(intersections[0].point);
      }
    }
    skipFrame = !skipFrame;

    // 保留原站的圆环缓动：目标点有轻微随机游走，圆环用低插值系数追踪鼠标，避免交互过硬。
    const noiseX = (cursorNoise(time * 0.66 + 94.234) - 0.5) * 2;
    const noiseY = (cursorNoise(time * 0.75 + 21.028) - 0.5) * 2;
    if (mouseIsOver) {
      cursorPos.set(intersectionPoint.x * 0.175 + noiseX * 0.1, intersectionPoint.y * 0.175 + noiseY * 0.1);
    } else {
      cursorPos.set(noiseX * 0.2, noiseY * 0.1);
    }
    const followStrength = mouseIsOver ? 0.02 : 0.01;
    ringPos.set(ringPos.x + (cursorPos.x - ringPos.x) * followStrength, ringPos.y + (cursorPos.y - ringPos.y) * followStrength);

    const ringRadius = 0.175 + Math.sin(time) * 0.03 + Math.cos(time * 3) * 0.02;

    if (rt1 && rt2) {
      simMaterial.uniforms.uPosition.value = everRendered ? rt1.texture : posTex;
      simMaterial.uniforms.uPosRefs.value = posTex;
      simMaterial.uniforms.uTime.value = time;
      simMaterial.uniforms.uDeltaTime.value = delta;
      simMaterial.uniforms.uRingRadius.value = ringRadius;
      simMaterial.uniforms.uRingPos.value.copy(ringPos);
      simMaterial.uniforms.uRingWidth.value = RING_WIDTH;
      simMaterial.uniforms.uRingWidth2.value = RING_WIDTH_2;
      simMaterial.uniforms.uRingDisplacement.value = RING_DISPLACEMENT;

      renderer.setRenderTarget(rt2);
      renderer.render(simScene, simCamera);
      renderer.setRenderTarget(null);

      renderMaterial.uniforms.uPosition.value = everRendered ? rt2.texture : posTex;
      renderMaterial.uniforms.uTime.value = time;
      renderMaterial.uniforms.uRingPos.value.copy(ringPos);
      renderer.render(scene, camera);

      const next = rt1;
      rt1 = rt2;
      rt2 = next;
      everRendered = true;
    } else {
      renderMaterial.uniforms.uPosition.value = posTex;
      renderMaterial.uniforms.uTime.value = time;
      renderMaterial.uniforms.uRingPos.value.copy(ringPos);
      renderer.render(scene, camera);
    }
  };

  const observer = new IntersectionObserver((entries) => {
    const [entry] = entries;
    visible = entry?.isIntersecting ?? true;
  });

  resize();
  window.addEventListener("resize", resize);
  window.addEventListener("mousemove", handleMouseMove);
  observer.observe(container);
  animate();

  return () => {
    cancelAnimationFrame(animationId);
    observer.disconnect();
    window.removeEventListener("resize", resize);
    window.removeEventListener("mousemove", handleMouseMove);
    renderer.setRenderTarget(null);
    scene.remove(particles);
    scene.remove(raycastPlane);
    simScene.remove(simMesh);
    raycastPlane.geometry.dispose();
    raycastPlane.material.dispose();
    geometry.dispose();
    renderMaterial.dispose();
    simGeometry.dispose();
    simMaterial.dispose();
    posTex.dispose();
    rt1?.dispose();
    rt2?.dispose();
    renderer.dispose();
    renderer.domElement.remove();
  };
}

window.addEventListener("DOMContentLoaded", () => {
  initParticlesBackground(document.getElementById("particles-background"));
});
