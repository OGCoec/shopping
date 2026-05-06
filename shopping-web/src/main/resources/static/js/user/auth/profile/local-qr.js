(function (root) {
  const VERSION = 10;
  const SIZE = 21 + (VERSION - 1) * 4;
  const DATA_BLOCK_LENGTHS = [68, 68, 69, 69];
  const ERROR_CODEWORDS_PER_BLOCK = 18;
  const DATA_CODEWORDS = DATA_BLOCK_LENGTHS.reduce((sum, value) => sum + value, 0);
  const ALIGNMENT_POSITIONS = [6, 28, 50];
  const ECC_FORMAT_BITS_L = 1;
  const GF_EXP = [];
  const GF_LOG = [];

  initGaloisTables();

  function render(target, text, options = {}) {
    if (!target) {
      throw new Error("QR target is required.");
    }
    const modules = makeQrModules(String(text || ""));
    const border = options.border ?? 4;
    const scale = options.scale ?? 4;
    const canvas = document.createElement("canvas");
    const size = modules.length + border * 2;
    canvas.width = size * scale;
    canvas.height = size * scale;
    const context = canvas.getContext("2d");

    context.fillStyle = "#fff";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.fillStyle = "#000";
    for (let y = 0; y < modules.length; y += 1) {
      for (let x = 0; x < modules.length; x += 1) {
        if (modules[y][x]) {
          context.fillRect((x + border) * scale, (y + border) * scale, scale, scale);
        }
      }
    }

    target.textContent = "";
    target.appendChild(canvas);
  }

  function makeQrModules(text) {
    const dataCodewords = makeDataCodewords(text);
    const codewords = addErrorCorrectionAndInterleave(dataCodewords);
    const base = createBaseMatrix();
    let bestModules = null;
    let bestScore = Number.POSITIVE_INFINITY;

    for (let mask = 0; mask < 8; mask += 1) {
      const modules = cloneMatrix(base.modules);
      drawCodewords(modules, base.functionModules, codewords, mask);
      drawFormatBits(modules, base.functionModules, mask);
      drawVersionBits(modules, base.functionModules);
      const score = scoreMask(modules);
      if (score < bestScore) {
        bestScore = score;
        bestModules = modules;
      }
    }

    return bestModules;
  }

  function makeDataCodewords(text) {
    const bytes = new TextEncoder().encode(text);
    const bits = [];
    const capacityBits = DATA_CODEWORDS * 8;

    appendBits(bits, 0x4, 4);
    appendBits(bits, bytes.length, 16);
    bytes.forEach((value) => appendBits(bits, value, 8));
    if (bits.length > capacityBits) {
      throw new Error("QR payload is too long.");
    }

    appendBits(bits, 0, Math.min(4, capacityBits - bits.length));
    while (bits.length % 8 !== 0) {
      bits.push(false);
    }

    const codewords = [];
    for (let index = 0; index < bits.length; index += 8) {
      let value = 0;
      for (let offset = 0; offset < 8; offset += 1) {
        value = (value << 1) | (bits[index + offset] ? 1 : 0);
      }
      codewords.push(value);
    }

    for (let pad = 0; codewords.length < DATA_CODEWORDS; pad += 1) {
      codewords.push(pad % 2 === 0 ? 0xec : 0x11);
    }
    return codewords;
  }

  function addErrorCorrectionAndInterleave(dataCodewords) {
    const blocks = [];
    let offset = 0;
    DATA_BLOCK_LENGTHS.forEach((length) => {
      const data = dataCodewords.slice(offset, offset + length);
      offset += length;
      blocks.push({
        data,
        error: reedSolomonRemainder(data, ERROR_CODEWORDS_PER_BLOCK)
      });
    });

    const result = [];
    const maxDataLength = Math.max(...DATA_BLOCK_LENGTHS);
    for (let index = 0; index < maxDataLength; index += 1) {
      blocks.forEach((block) => {
        if (index < block.data.length) {
          result.push(block.data[index]);
        }
      });
    }
    for (let index = 0; index < ERROR_CODEWORDS_PER_BLOCK; index += 1) {
      blocks.forEach((block) => result.push(block.error[index]));
    }
    return result;
  }

  function createBaseMatrix() {
    const modules = newMatrix(false);
    const functionModules = newMatrix(false);

    drawFinderPattern(modules, functionModules, 0, 0);
    drawFinderPattern(modules, functionModules, SIZE - 7, 0);
    drawFinderPattern(modules, functionModules, 0, SIZE - 7);
    drawAlignmentPatterns(modules, functionModules);
    drawTimingPatterns(modules, functionModules);
    drawFormatBits(modules, functionModules, 0);
    drawVersionBits(modules, functionModules);
    setFunctionModule(modules, functionModules, 8, SIZE - 8, true);
    return { modules, functionModules };
  }

  function drawFinderPattern(modules, functionModules, left, top) {
    for (let dy = -1; dy <= 7; dy += 1) {
      for (let dx = -1; dx <= 7; dx += 1) {
        const x = left + dx;
        const y = top + dy;
        if (!isInBounds(x, y)) {
          continue;
        }
        const dark = dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6
          && (dx === 0 || dx === 6 || dy === 0 || dy === 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
        setFunctionModule(modules, functionModules, x, y, dark);
      }
    }
  }

  function drawAlignmentPatterns(modules, functionModules) {
    ALIGNMENT_POSITIONS.forEach((x) => {
      ALIGNMENT_POSITIONS.forEach((y) => {
        const overlapsFinder = (x === 6 && y === 6) || (x === 6 && y === SIZE - 7) || (x === SIZE - 7 && y === 6);
        if (!overlapsFinder) {
          drawAlignmentPattern(modules, functionModules, x, y);
        }
      });
    });
  }

  function drawAlignmentPattern(modules, functionModules, centerX, centerY) {
    for (let dy = -2; dy <= 2; dy += 1) {
      for (let dx = -2; dx <= 2; dx += 1) {
        const dark = Math.max(Math.abs(dx), Math.abs(dy)) !== 1;
        setFunctionModule(modules, functionModules, centerX + dx, centerY + dy, dark);
      }
    }
  }

  function drawTimingPatterns(modules, functionModules) {
    for (let index = 8; index < SIZE - 8; index += 1) {
      const dark = index % 2 === 0;
      setFunctionModule(modules, functionModules, index, 6, dark);
      setFunctionModule(modules, functionModules, 6, index, dark);
    }
  }

  function drawFormatBits(modules, functionModules, mask) {
    const bits = getFormatBits(ECC_FORMAT_BITS_L, mask);
    for (let index = 0; index <= 5; index += 1) {
      setFunctionModule(modules, functionModules, 8, index, getBit(bits, index));
    }
    setFunctionModule(modules, functionModules, 8, 7, getBit(bits, 6));
    setFunctionModule(modules, functionModules, 8, 8, getBit(bits, 7));
    setFunctionModule(modules, functionModules, 7, 8, getBit(bits, 8));
    for (let index = 9; index < 15; index += 1) {
      setFunctionModule(modules, functionModules, 14 - index, 8, getBit(bits, index));
    }
    for (let index = 0; index < 8; index += 1) {
      setFunctionModule(modules, functionModules, SIZE - 1 - index, 8, getBit(bits, index));
    }
    for (let index = 8; index < 15; index += 1) {
      setFunctionModule(modules, functionModules, 8, SIZE - 15 + index, getBit(bits, index));
    }
    setFunctionModule(modules, functionModules, 8, SIZE - 8, true);
  }

  function drawVersionBits(modules, functionModules) {
    const bits = getVersionBits(VERSION);
    for (let index = 0; index < 18; index += 1) {
      const dark = getBit(bits, index);
      const a = SIZE - 11 + (index % 3);
      const b = Math.floor(index / 3);
      setFunctionModule(modules, functionModules, a, b, dark);
      setFunctionModule(modules, functionModules, b, a, dark);
    }
  }

  function drawCodewords(modules, functionModules, codewords, mask) {
    const bits = [];
    codewords.forEach((codeword) => appendBits(bits, codeword, 8));

    let bitIndex = 0;
    let upward = true;
    for (let right = SIZE - 1; right >= 1; right -= 2) {
      if (right === 6) {
        right -= 1;
      }
      for (let vertical = 0; vertical < SIZE; vertical += 1) {
        const y = upward ? SIZE - 1 - vertical : vertical;
        for (let offset = 0; offset < 2; offset += 1) {
          const x = right - offset;
          if (functionModules[y][x]) {
            continue;
          }
          let dark = bitIndex < bits.length && bits[bitIndex];
          bitIndex += 1;
          if (maskApplies(mask, x, y)) {
            dark = !dark;
          }
          modules[y][x] = dark;
        }
      }
      upward = !upward;
    }
  }

  function scoreMask(modules) {
    let penalty = 0;
    penalty += scoreRuns(modules, true);
    penalty += scoreRuns(modules, false);
    penalty += scoreBlocks(modules);
    penalty += scoreFinderLikePatterns(modules);
    penalty += scoreBalance(modules);
    return penalty;
  }

  function scoreRuns(modules, horizontal) {
    let penalty = 0;
    for (let major = 0; major < SIZE; major += 1) {
      let runColor = false;
      let runLength = 0;
      for (let minor = 0; minor < SIZE; minor += 1) {
        const color = horizontal ? modules[major][minor] : modules[minor][major];
        if (minor === 0 || color !== runColor) {
          if (runLength >= 5) {
            penalty += 3 + runLength - 5;
          }
          runColor = color;
          runLength = 1;
        } else {
          runLength += 1;
        }
      }
      if (runLength >= 5) {
        penalty += 3 + runLength - 5;
      }
    }
    return penalty;
  }

  function scoreBlocks(modules) {
    let penalty = 0;
    for (let y = 0; y < SIZE - 1; y += 1) {
      for (let x = 0; x < SIZE - 1; x += 1) {
        const color = modules[y][x];
        if (color === modules[y][x + 1] && color === modules[y + 1][x] && color === modules[y + 1][x + 1]) {
          penalty += 3;
        }
      }
    }
    return penalty;
  }

  function scoreFinderLikePatterns(modules) {
    const pattern = [true, false, true, true, true, false, true, false, false, false, false];
    const reverse = pattern.slice().reverse();
    let penalty = 0;
    for (let y = 0; y < SIZE; y += 1) {
      for (let x = 0; x <= SIZE - pattern.length; x += 1) {
        if (matchesPattern(modules, x, y, true, pattern) || matchesPattern(modules, x, y, true, reverse)) {
          penalty += 40;
        }
      }
    }
    for (let x = 0; x < SIZE; x += 1) {
      for (let y = 0; y <= SIZE - pattern.length; y += 1) {
        if (matchesPattern(modules, x, y, false, pattern) || matchesPattern(modules, x, y, false, reverse)) {
          penalty += 40;
        }
      }
    }
    return penalty;
  }

  function scoreBalance(modules) {
    let dark = 0;
    modules.forEach((row) => row.forEach((value) => {
      if (value) {
        dark += 1;
      }
    }));
    const total = SIZE * SIZE;
    return Math.floor(Math.abs(dark * 20 - total * 10) / total) * 10;
  }

  function matchesPattern(modules, x, y, horizontal, pattern) {
    for (let index = 0; index < pattern.length; index += 1) {
      const value = horizontal ? modules[y][x + index] : modules[y + index][x];
      if (value !== pattern[index]) {
        return false;
      }
    }
    return true;
  }

  function reedSolomonRemainder(data, degree) {
    const generator = reedSolomonGenerator(degree);
    const buffer = data.concat(new Array(degree).fill(0));
    for (let index = 0; index < data.length; index += 1) {
      const factor = buffer[index];
      if (factor === 0) {
        continue;
      }
      for (let genIndex = 1; genIndex < generator.length; genIndex += 1) {
        buffer[index + genIndex] ^= gfMultiply(generator[genIndex], factor);
      }
    }
    return buffer.slice(data.length);
  }

  function reedSolomonGenerator(degree) {
    let result = [1];
    for (let index = 0; index < degree; index += 1) {
      const next = new Array(result.length + 1).fill(0);
      result.forEach((coefficient, coefficientIndex) => {
        next[coefficientIndex] ^= coefficient;
        next[coefficientIndex + 1] ^= gfMultiply(coefficient, GF_EXP[index]);
      });
      result = next;
    }
    return result;
  }

  function gfMultiply(left, right) {
    if (left === 0 || right === 0) {
      return 0;
    }
    return GF_EXP[GF_LOG[left] + GF_LOG[right]];
  }

  function initGaloisTables() {
    let value = 1;
    for (let index = 0; index < 255; index += 1) {
      GF_EXP[index] = value;
      GF_LOG[value] = index;
      value <<= 1;
      if (value & 0x100) {
        value ^= 0x11d;
      }
    }
    for (let index = 255; index < 512; index += 1) {
      GF_EXP[index] = GF_EXP[index - 255];
    }
  }

  function getFormatBits(eccFormatBits, mask) {
    const data = (eccFormatBits << 3) | mask;
    const remainder = bchRemainder(data << 10, 0x537);
    return ((data << 10) | remainder) ^ 0x5412;
  }

  function getVersionBits(version) {
    return (version << 12) | bchRemainder(version << 12, 0x1f25);
  }

  function bchRemainder(value, polynomial) {
    let result = value;
    const polynomialLength = bitLength(polynomial);
    while (bitLength(result) >= polynomialLength) {
      result ^= polynomial << (bitLength(result) - polynomialLength);
    }
    return result;
  }

  function maskApplies(mask, x, y) {
    switch (mask) {
      case 0:
        return (x + y) % 2 === 0;
      case 1:
        return y % 2 === 0;
      case 2:
        return x % 3 === 0;
      case 3:
        return (x + y) % 3 === 0;
      case 4:
        return (Math.floor(y / 2) + Math.floor(x / 3)) % 2 === 0;
      case 5:
        return ((x * y) % 2) + ((x * y) % 3) === 0;
      case 6:
        return (((x * y) % 2) + ((x * y) % 3)) % 2 === 0;
      case 7:
        return (((x + y) % 2) + ((x * y) % 3)) % 2 === 0;
      default:
        return false;
    }
  }

  function setFunctionModule(modules, functionModules, x, y, dark) {
    if (!isInBounds(x, y)) {
      return;
    }
    modules[y][x] = dark;
    functionModules[y][x] = true;
  }

  function appendBits(bits, value, length) {
    for (let index = length - 1; index >= 0; index -= 1) {
      bits.push(((value >>> index) & 1) !== 0);
    }
  }

  function getBit(value, index) {
    return ((value >>> index) & 1) !== 0;
  }

  function bitLength(value) {
    let result = 0;
    while (value !== 0) {
      result += 1;
      value >>>= 1;
    }
    return result;
  }

  function newMatrix(value) {
    return Array.from({ length: SIZE }, () => new Array(SIZE).fill(value));
  }

  function cloneMatrix(matrix) {
    return matrix.map((row) => row.slice());
  }

  function isInBounds(x, y) {
    return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
  }

  root.ShoppingLocalQr = { render };
})(typeof globalThis !== "undefined" ? globalThis : window);
