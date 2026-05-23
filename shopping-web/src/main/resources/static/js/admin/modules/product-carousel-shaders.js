export const carouselVertexShaderSource = `
attribute vec2 aPosition;
varying vec2 vUv;

void main() {
  vUv = aPosition * 0.5 + 0.5;
  gl_Position = vec4(aPosition, 0.0, 1.0);
}
`;

export const carouselFragmentShaderSource = `
precision highp float;

uniform sampler2D uTexture;
uniform sampler2D uTextureNext;
uniform sampler2D uDisplacementMap;
uniform float uDistortionStrength;
uniform float uProgress;
uniform float uOpacity;
uniform vec2 uMeshSize;
uniform vec2 uImageSizeCurrent;
uniform vec2 uImageSizeNext;

varying vec2 vUv;

float sampleDisplacement(vec2 uv) {
  return (texture2D(uDisplacementMap, uv).r - 0.5019608) * 10.0;
}

vec2 backgroundCoverUv(vec2 meshSize, vec2 imageSize, vec2 uv) {
  if (imageSize.x <= 0.0 || imageSize.y <= 0.0 || meshSize.x <= 0.0 || meshSize.y <= 0.0) {
    return uv;
  }

  float meshRatio = meshSize.x / meshSize.y;
  float imageRatio = imageSize.x / imageSize.y;

  if (meshRatio > imageRatio) {
    return vec2(uv.x, (uv.y - 0.5) * (imageRatio / meshRatio) + 0.5);
  }

  return vec2((uv.x - 0.5) * (meshRatio / imageRatio) + 0.5, uv.y);
}

void main() {
  vec2 uv = vUv;
  vec2 texUvCurrent = backgroundCoverUv(uMeshSize, uImageSizeCurrent, uv);
  vec2 texUvNext = backgroundCoverUv(uMeshSize, uImageSizeNext, uv);
  vec2 center = vec2(0.5);
  float dist = distance(uv, center);

  vec2 texelSize = vec2(1.0 / 64.0);
  float hL = sampleDisplacement(uv - vec2(texelSize.x, 0.0));
  float hR = sampleDisplacement(uv + vec2(texelSize.x, 0.0));
  float hT = sampleDisplacement(uv - vec2(0.0, texelSize.y));
  float hB = sampleDisplacement(uv + vec2(0.0, texelSize.y));
  vec2 mouseNormalOffset = vec2(hL - hR, hT - hB);

  float waveProgress = smoothstep(0.0, 1.0, uProgress);
  float rippleRadius = waveProgress;
  float waveIntensity = smoothstep(0.0, 0.2, uProgress) * smoothstep(1.0, 0.6, uProgress);
  float wavePattern = sin((dist - rippleRadius) * 8.0) * waveIntensity * 0.15;
  vec2 waveNormalOffset = normalize(uv - center + vec2(0.0001)) * wavePattern;
  vec2 distortion = (mouseNormalOffset + waveNormalOffset) * uDistortionStrength;

  float rippleThickness = 0.15;
  float mask = smoothstep(rippleRadius - rippleThickness, rippleRadius, dist);
  float maskWave = sin((dist - rippleRadius) * 6.0) * 0.5 + 0.5;
  mask = mix(mask, mask * (1.0 - maskWave * 0.35), waveIntensity);
  mask = clamp(mask, 0.0, 1.0);

  vec4 tex1 = texture2D(uTexture, texUvCurrent + distortion);
  vec4 tex2 = texture2D(uTextureNext, texUvNext + distortion);
  vec4 finalColor = mix(tex2, tex1, mask);

  vec2 totalNormal = mouseNormalOffset + waveNormalOffset;
  finalColor.rgb *= clamp(1.0 - length(totalNormal) * 0.6, 0.6, 1.0);
  finalColor.rgb += vec3(0.0) * 0.7 * waveIntensity;
  finalColor.rgb += vec3(0.0) * 0.2;
  finalColor.a *= uOpacity;

  gl_FragColor = finalColor;
}
`;

export function createCarouselProgram(gl) {
  const program = gl.createProgram();
  if (!program) {
    throw new Error("Failed to create WebGL program");
  }

  const vertexShader = createShader(gl, gl.VERTEX_SHADER, carouselVertexShaderSource);
  const fragmentShader = createShader(gl, gl.FRAGMENT_SHADER, carouselFragmentShaderSource);
  gl.attachShader(program, vertexShader);
  gl.attachShader(program, fragmentShader);
  gl.linkProgram(program);
  gl.deleteShader(vertexShader);
  gl.deleteShader(fragmentShader);

  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const info = gl.getProgramInfoLog(program) || "Unknown program link error";
    gl.deleteProgram(program);
    throw new Error(info);
  }
  return program;
}

function createShader(gl, type, source) {
  const shader = gl.createShader(type);
  if (!shader) {
    throw new Error("Failed to create WebGL shader");
  }
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const info = gl.getShaderInfoLog(shader) || "Unknown shader compile error";
    gl.deleteShader(shader);
    throw new Error(info);
  }
  return shader;
}
