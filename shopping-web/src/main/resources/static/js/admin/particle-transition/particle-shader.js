export function createParticleMaterial(THREE, basePointSize) {
  return new THREE.ShaderMaterial({
    blending: THREE.NormalBlending,
    depthWrite: false,
    transparent: true,
    uniforms: {
      pointSize: { value: basePointSize }
    },
    vertexShader: `
      uniform float pointSize;
      attribute float alpha;
      attribute vec3 particleColor;
      varying vec3 vColor;
      varying float vAlpha;

      void main() {
        vColor = particleColor;
        vAlpha = alpha;
        gl_PointSize = pointSize;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      varying vec3 vColor;
      varying float vAlpha;

      void main() {
        if (vAlpha <= 0.01) {
          discard;
        }
        gl_FragColor = vec4(vColor, vAlpha);
      }
    `
  });
}
