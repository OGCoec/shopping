import React, { useRef, useEffect, useState } from 'react';

// ==========================================
// 1. WebGL 着色器源码
// ==========================================

// 顶点着色器：渲染一个覆盖整个 Canvas 的矩形
const vsSource = `
  attribute vec2 a_position;
  void main() {
    gl_Position = vec4(a_position, 0.0, 1.0);
  }
`;

// 片段着色器：核心计算，引入了全息镭射色彩
const fsSource = `
  precision highp float;
  uniform vec2 u_resolution;
  uniform vec2 u_mouse;
  uniform float u_time;

  // 宇宙/全息调色板 (Cosine based palette)
  // 产生类似钛金属烤蓝或 CD 光盘表面的过渡色
  vec3 palette(float t) {
    vec3 a = vec3(0.5, 0.5, 0.5);
    vec3 b = vec3(0.5, 0.5, 0.5);
    vec3 c = vec3(1.0, 1.0, 1.0);
    vec3 d = vec3(0.263, 0.416, 0.557); // 控制颜色的初始偏移
    return a + b * cos(6.28318 * (c * t + d));
  }

  void main() {
    // 获取当前像素坐标
    vec2 p = gl_FragCoord.xy;
    // WebGL 的 Y 轴是向上的，反转鼠标 Y 轴以匹配屏幕坐标
    vec2 m = vec2(u_mouse.x, u_resolution.y - u_mouse.y);

    // 计算当前像素到鼠标的距离
    float dist = distance(p, m);

    // 1. 环境光感应 (Proximity Glow)
    float glowRadius = 350.0;
    float proximity = 1.0 - smoothstep(0.0, glowRadius, dist);

    // 2. 金属流动纹理 (Fluid Noise)
    vec2 uv = p / u_resolution;
    float noise = sin(uv.x * 15.0 + u_time * 1.2) * cos(uv.y * 15.0 - u_time * 0.8) * 0.5 + 0.5;

    // 3. 各向异性高光与角度计算
    float angle = atan(p.y - m.y, p.x - m.x);
    // 将角度规整化到 0 ~ 1 之间，用于提取颜色
    float normalizedAngle = angle / 6.28318 + 0.5; 
    
    float anisotropic = sin(angle * 6.0 + u_time * 2.0) * 0.5 + 0.5;

    // 综合光照强度
    float lightIntensity = proximity * (0.4 + 0.6 * noise) * (0.3 + 0.7 * anisotropic);
    float sharpHighlight = pow(lightIntensity, 1.5);

    // ================= 核心颜色修改 =================
    // 根据鼠标相对角度、时间和噪声提取调色板中的颜色（镭射效果）
    float colorPhase = normalizedAngle * 2.0 + noise * 0.5 + u_time * 0.15;
    vec3 iridescentColor = palette(colorPhase);

    // 基础颜色设定
    vec3 darkMetal = vec3(0.12, 0.12, 0.14); // 按钮未激活时的暗金属边框
    vec3 brightMetal = vec3(1.0, 1.0, 1.0);  // 耀眼的白银高光核心
    
    // 将金属白和镭射彩色进行混合，让高光外围带有丰富的色散
    // 75%为彩色，保留25%的纯白以提升中心点的高光质感
    vec3 highlightColor = mix(brightMetal, iridescentColor, 0.75); 

    // 颜色混合
    vec3 color = darkMetal;
    color = mix(color, highlightColor, sharpHighlight * 2.5);
    
    // 环境泛光边缘也透出微弱的全息彩色
    color += proximity * iridescentColor * 0.15;

    gl_FragColor = vec4(color, 1.0);
  }
`;

// ==========================================
// 2. WebGL 辅助函数
// ==========================================
function compileShader(gl, type, source) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
        console.error('Shader compile error:', gl.getShaderInfoLog(shader));
        gl.deleteShader(shader);
        return null;
    }
    return shader;
}

function createProgram(gl, vs, fs) {
    const program = gl.createProgram();
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
        console.error('Program link error:', gl.getProgramInfoLog(program));
        return null;
    }
    return program;
}

// ==========================================
// 3. 核心组件：MetalButton
// ==========================================
const MetalButton = ({ children, onClick }) => {
    const canvasRef = useRef(null);
    const containerRef = useRef(null);
    const mousePosRef = useRef({ x: -1000, y: -1000 }); // 初始位置放远一点，避免默认发光

    useEffect(() => {
        const canvas = canvasRef.current;
        const container = containerRef.current;
        const gl = canvas.getContext('webgl');
        if (!gl) return;

        // 初始化 Shader Program
        const vs = compileShader(gl, gl.VERTEX_SHADER, vsSource);
        const fs = compileShader(gl, gl.FRAGMENT_SHADER, fsSource);
        const program = createProgram(gl, vs, fs);
        gl.useProgram(program);

        // 设置顶点数据 (两个三角形拼成一个矩形覆盖全屏)
        const positionBuffer = gl.createBuffer();
        gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
        gl.bufferData(
            gl.ARRAY_BUFFER,
            new Float32Array([-1, -1, 1, -1, -1, 1, -1, 1, 1, -1, 1, 1]),
            gl.STATIC_DRAW
        );
        const positionLocation = gl.getAttribLocation(program, 'a_position');
        gl.enableVertexAttribArray(positionLocation);
        gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0);

        // 获取 Uniform 变量地址
        const resolutionLoc = gl.getUniformLocation(program, 'u_resolution');
        const mouseLoc = gl.getUniformLocation(program, 'u_mouse');
        const timeLoc = gl.getUniformLocation(program, 'u_time');

        let animationFrameId;
        let startTime = Date.now();

        // 适应高分屏及尺寸变化
        const resizeCanvas = () => {
            const rect = container.getBoundingClientRect();
            const dpr = window.devicePixelRatio || 1;
            canvas.width = rect.width * dpr;
            canvas.height = rect.height * dpr;
            gl.viewport(0, 0, canvas.width, canvas.height);
            gl.uniform2f(resolutionLoc, canvas.width, canvas.height);
        };

        window.addEventListener('resize', resizeCanvas);
        resizeCanvas();

        // 渲染循环
        const render = () => {
            const dpr = window.devicePixelRatio || 1;
            const currentTime = (Date.now() - startTime) / 1000.0;

            // 更新时间
            gl.uniform1f(timeLoc, currentTime);
            // 更新鼠标坐标（乘以 dpr 以匹配画布物理像素）
            gl.uniform2f(mouseLoc, mousePosRef.current.x * dpr, mousePosRef.current.y * dpr);

            gl.drawArrays(gl.TRIANGLES, 0, 6);
            animationFrameId = requestAnimationFrame(render);
        };
        render();

        // 全局鼠标追踪：即使鼠标不在按钮上，光效也会向鼠标方向聚集
        const handleMouseMove = (e) => {
            const rect = container.getBoundingClientRect();
            // 计算鼠标相对于按钮左上角的坐标
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            mousePosRef.current = { x, y };
        };

        // 使用 window 监听以实现“环境感知”
        window.addEventListener('mousemove', handleMouseMove);

        return () => {
            window.removeEventListener('resize', resizeCanvas);
            window.removeEventListener('mousemove', handleMouseMove);
            cancelAnimationFrame(animationFrameId);
        };
    }, []);

    return (
        <button
            ref={containerRef}
            onClick={onClick}
            className="group relative p-[1.5px] rounded-2xl overflow-hidden focus:outline-none transition-transform hover:scale-[1.02] active:scale-[0.98]"
        >
            {/* 底层 WebGL 画布：负责渲染金属光影边框 */}
            <canvas
                ref={canvasRef}
                className="absolute inset-0 w-full h-full z-0 block"
                style={{ pointerEvents: 'none' }}
            />

            {/* 按钮主体：使用深色背景遮盖住中间区域，仅露出边缘 1.5px 的 WebGL 光影作为边框 */}
            <div className="relative z-10 flex items-center justify-center bg-[#09090b] h-full w-full rounded-[14px] px-8 py-4 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)] transition-colors duration-300 group-hover:bg-[#111114]">
                {children}
            </div>
        </button>
    );
};

// ==========================================
// 4. 主页面展示
// ==========================================
export default function App() {
    return (
        <div className="min-h-screen bg-black flex flex-col items-center justify-center p-8 font-sans text-slate-200">
            <div className="text-center mb-16 max-w-lg">
                <h1 className="text-3xl font-light tracking-tight text-white mb-4">
                    Holographic Metal Effect
                </h1>
                <p className="text-slate-500 text-sm">
                    现在移动鼠标，你应该能看到高光中折射出了类似 CD 背面或者烤蓝钛金属的“镭射彩虹”色彩。
                </p>
            </div>

            <div className="flex flex-col sm:flex-row gap-6">
                <MetalButton onClick={() => console.log('Clicked!')}>
                    <span className="text-sm font-medium tracking-wide bg-gradient-to-b from-white to-slate-400 bg-clip-text text-transparent">
                        Get Started
                    </span>
                </MetalButton>

                <MetalButton>
                    <span className="text-sm font-medium tracking-wide text-slate-300 flex items-center gap-2">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M9 18l6-6-6-6" />
                        </svg>
                        View Documentation
                    </span>
                </MetalButton>
            </div>
        </div>
    );
}