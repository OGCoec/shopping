const DISPLACEMENT_SIZE = 64;
const DISPLACEMENT_ZERO_BYTE = 128;
const DISPLACEMENT_ENCODE_RANGE = 5;
const MOUSE_BRUSH_RADIUS = 0.04;
const MOUSE_DISSIPATION = 0.97;
const MOUSE_MAX_VELOCITY = 1.2;

export class HoverDisplacementField {
  constructor(gl) {
    this.gl = gl;
    this.texture = createDisplacementTexture(gl);
    this.size = DISPLACEMENT_SIZE;
    const pixelCount = this.size * this.size;
    this.height = new Float32Array(pixelCount);
    this.velocity = new Float32Array(pixelCount);
    this.nextHeight = new Float32Array(pixelCount);
    this.nextVelocity = new Float32Array(pixelCount);
    this.pixels = new Uint8Array(pixelCount * 4);
    this.mouse = {
      x: 0.5,
      y: 0.5,
      velocity: 0,
      isInside: false
    };
    this.lastPointer = null;
    this.resetPixels();
    this.upload();
  }

  handlePointerMove(event, frameEl) {
    if (!frameEl) {
      return;
    }
    const rect = frameEl.getBoundingClientRect();
    if (
      event.clientX < rect.left ||
      event.clientX > rect.right ||
      event.clientY < rect.top ||
      event.clientY > rect.bottom
    ) {
      this.handlePointerLeave();
      return;
    }
    const x = clamp((event.clientX - rect.left) / Math.max(1, rect.width), 0, 1);
    const y = clamp(1 - (event.clientY - rect.top) / Math.max(1, rect.height), 0, 1);
    const last = this.lastPointer;
    const distance = last ? Math.hypot(x - last.x, y - last.y) : 0;
    this.mouse.x = x;
    this.mouse.y = y;
    this.mouse.isInside = true;
    this.mouse.velocity = last ? Math.min(distance * 60, MOUSE_MAX_VELOCITY) : 0;
    this.lastPointer = { x, y };
  }

  handlePointerLeave() {
    this.lastPointer = null;
    this.mouse.isInside = false;
    this.mouse.velocity = 0;
  }

  updateAndUpload() {
    this.update();
    this.upload();
  }

  dispose() {
    this.gl.deleteTexture(this.texture);
  }

  resetPixels() {
    for (let index = 0; index < this.size * this.size; index += 1) {
      const offset = index * 4;
      this.pixels[offset] = DISPLACEMENT_ZERO_BYTE;
      this.pixels[offset + 1] = DISPLACEMENT_ZERO_BYTE;
      this.pixels[offset + 2] = DISPLACEMENT_ZERO_BYTE;
      this.pixels[offset + 3] = 255;
    }
  }

  update() {
    const brushX = this.mouse.x * (this.size - 1);
    const brushY = this.mouse.y * (this.size - 1);
    const brushRadius = MOUSE_BRUSH_RADIUS * this.size;

    for (let y = 0; y < this.size; y += 1) {
      for (let x = 0; x < this.size; x += 1) {
        const index = y * this.size + x;
        const left = this.height[y * this.size + Math.max(0, x - 1)];
        const right = this.height[y * this.size + Math.min(this.size - 1, x + 1)];
        const top = this.height[Math.max(0, y - 1) * this.size + x];
        const bottom = this.height[Math.min(this.size - 1, y + 1) * this.size + x];
        const average = (left + right + top + bottom) * 0.25;
        let nextSpeed = this.velocity[index] + (average - this.height[index]) * 0.5;
        let nextValue = this.height[index];

        nextSpeed *= MOUSE_DISSIPATION;
        nextValue += nextSpeed;

        if (this.mouse.isInside && this.mouse.velocity > 0) {
          const distanceToMouse = Math.hypot(x - brushX, y - brushY);
          const brush = smoothstep(brushRadius, 0, distanceToMouse);
          nextValue += brush * this.mouse.velocity;
        }

        this.nextHeight[index] = clamp(nextValue * 0.98, -DISPLACEMENT_ENCODE_RANGE, DISPLACEMENT_ENCODE_RANGE);
        this.nextVelocity[index] = clamp(nextSpeed * 0.98, -DISPLACEMENT_ENCODE_RANGE, DISPLACEMENT_ENCODE_RANGE);
      }
    }

    this.height.set(this.nextHeight);
    this.velocity.set(this.nextVelocity);
    this.mouse.velocity = 0;
  }

  upload() {
    const gl = this.gl;
    for (let index = 0; index < this.height.length; index += 1) {
      const encoded = DISPLACEMENT_ZERO_BYTE + clamp(
        this.height[index] / DISPLACEMENT_ENCODE_RANGE,
        -1,
        1
      ) * 127;
      const offset = index * 4;
      const value = Math.round(encoded);
      this.pixels[offset] = value;
      this.pixels[offset + 1] = value;
      this.pixels[offset + 2] = value;
      this.pixels[offset + 3] = 255;
    }

    gl.bindTexture(gl.TEXTURE_2D, this.texture);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, 0);
    gl.texSubImage2D(
      gl.TEXTURE_2D,
      0,
      0,
      0,
      this.size,
      this.size,
      gl.RGBA,
      gl.UNSIGNED_BYTE,
      this.pixels
    );
  }
}

function createDisplacementTexture(gl) {
  const texture = gl.createTexture();
  if (!texture) {
    throw new Error("Failed to create displacement texture");
  }
  const pixelCount = DISPLACEMENT_SIZE * DISPLACEMENT_SIZE;
  const pixels = new Uint8Array(pixelCount * 4);
  for (let index = 0; index < pixelCount; index += 1) {
    const offset = index * 4;
    pixels[offset] = DISPLACEMENT_ZERO_BYTE;
    pixels[offset + 1] = DISPLACEMENT_ZERO_BYTE;
    pixels[offset + 2] = DISPLACEMENT_ZERO_BYTE;
    pixels[offset + 3] = 255;
  }
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, 0);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texImage2D(
    gl.TEXTURE_2D,
    0,
    gl.RGBA,
    DISPLACEMENT_SIZE,
    DISPLACEMENT_SIZE,
    0,
    gl.RGBA,
    gl.UNSIGNED_BYTE,
    pixels
  );
  gl.bindTexture(gl.TEXTURE_2D, null);
  return texture;
}

function smoothstep(edge0, edge1, value) {
  const t = clamp((value - edge0) / (edge1 - edge0), 0, 1);
  return t * t * (3 - 2 * t);
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}
