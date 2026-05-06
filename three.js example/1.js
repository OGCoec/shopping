import React, { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import { Brain, Cpu, Network, Sparkles, ArrowRight, Shield, Zap, ChevronRight, Menu, X } from 'lucide-react';

// --- CSS 样式定义 (包含动画和特殊效果) ---
const CustomStyles = () => (
    <style>{`
    @keyframes fadeInUp {
      from { 
        opacity: 0; 
        transform: translateY(30px) scale(0.95); 
      }
      to { 
        opacity: 1; 
        transform: translateY(0) scale(1); 
      }
    }
    
    @keyframes float {
      0% { transform: translateY(0px); }
      50% { transform: translateY(-10px); }
      100% { transform: translateY(0px); }
    }

    .animate-fade-in-up {
      animation: fadeInUp 0.8s cubic-bezier(0.2, 0.8, 0.2, 1) forwards;
      opacity: 0;
    }

    .animate-float {
      animation: float 6s ease-in-out infinite;
    }

    .delay-100 { animation-delay: 100ms; }
    .delay-200 { animation-delay: 200ms; }
    .delay-300 { animation-delay: 300ms; }
    .delay-400 { animation-delay: 400ms; }
    .delay-500 { animation-delay: 500ms; }

    .glass-panel {
      background: rgba(255, 255, 255, 0.03);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid rgba(255, 255, 255, 0.08);
      box-shadow: 0 4px 30px rgba(0, 0, 0, 0.1);
    }

    .glass-card {
      background: linear-gradient(145deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.01) 100%);
      backdrop-filter: blur(10px);
      border: 1px solid rgba(255, 255, 255, 0.05);
      transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    }

    .glass-card:hover {
      transform: translateY(-8px);
      border-color: rgba(99, 102, 241, 0.4);
      box-shadow: 0 10px 40px -10px rgba(99, 102, 241, 0.3);
      background: linear-gradient(145deg, rgba(255,255,255,0.08) 0%, rgba(255,255,255,0.02) 100%);
    }

    .glow-button {
      background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
      box-shadow: 0 0 20px rgba(99, 102, 241, 0.5);
      transition: all 0.3s ease;
    }

    .glow-button:hover {
      box-shadow: 0 0 35px rgba(99, 102, 241, 0.8);
      transform: scale(1.05);
    }
    
    .text-gradient {
      background: linear-gradient(to right, #ffffff, #a5b4fc);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .text-gradient-primary {
      background: linear-gradient(to right, #818cf8, #c084fc);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }
  `}</style>
);

// --- Three.js 背景组件 ---
const NeuralBackground = () => {
    const mountRef = useRef(null);

    useEffect(() => {
        let animationFrameId;

        // 1. 初始化场景、相机和渲染器
        const scene = new THREE.Scene();
        // 使用正交相机或透视相机，这里使用透视增强空间感
        const camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 1, 4000);
        camera.position.z = 1000;

        const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
        renderer.setPixelRatio(window.devicePixelRatio);
        renderer.setSize(window.innerWidth, window.innerHeight);
        // 将渲染器的 DOM 元素附加到 ref 上
        mountRef.current.appendChild(renderer.domElement);

        // 2. 创建神经网络粒子和连线
        const group = new THREE.Group();
        scene.add(group);

        const particleCount = 250; // 粒子数量，影响性能和密度
        const maxDistance = 150;    // 节点之间产生连线的最大距离

        // 粒子几何体
        const particlesGeometry = new THREE.BufferGeometry();
        const particlesPositions = new Float32Array(particleCount * 3);
        const particlesData = []; // 存储粒子的运动向量

        const range = 800; // 分布范围
        for (let i = 0; i < particleCount; i++) {
            const x = (Math.random() - 0.5) * range * 2;
            const y = (Math.random() - 0.5) * range * 2;
            const z = (Math.random() - 0.5) * range * 2;

            particlesPositions[i * 3] = x;
            particlesPositions[i * 3 + 1] = y;
            particlesPositions[i * 3 + 2] = z;

            // 赋予每个粒子一个微小的随机运动速度
            particlesData.push({
                velocity: new THREE.Vector3(-0.5 + Math.random(), -0.5 + Math.random(), -0.5 + Math.random()).normalize().multiplyScalar(Math.random() * 0.5 + 0.1),
                numConnections: 0
            });
        }

        particlesGeometry.setAttribute('position', new THREE.BufferAttribute(particlesPositions, 3));

        // 粒子材质 (发光点)
        const particleMaterial = new THREE.PointsMaterial({
            color: 0x818cf8, // Indigo 400
            size: 4,
            blending: THREE.AdditiveBlending,
            transparent: true,
            sizeAttenuation: true,
            opacity: 0.8
        });

        const particles = new THREE.Points(particlesGeometry, particleMaterial);
        group.add(particles);

        // 连线几何体
        const linesGeometry = new THREE.BufferGeometry();
        // 预先分配足够大的数组空间存放线段顶点 (保守估计最大连线数)
        const maxConnections = (particleCount * (particleCount - 1)) / 2;
        const linesPositions = new Float32Array(maxConnections * 6); // 每条线2个点，每个点3个坐标
        const linesColors = new Float32Array(maxConnections * 6);    // 支持连线渐变或随距离变淡

        linesGeometry.setAttribute('position', new THREE.BufferAttribute(linesPositions, 3).setUsage(THREE.DynamicDrawUsage));
        linesGeometry.setAttribute('color', new THREE.BufferAttribute(linesColors, 3).setUsage(THREE.DynamicDrawUsage));

        const linesMaterial = new THREE.LineBasicMaterial({
            vertexColors: true,
            blending: THREE.AdditiveBlending,
            transparent: true,
            opacity: 0.3
        });

        const linesMesh = new THREE.LineSegments(linesGeometry, linesMaterial);
        group.add(linesMesh);

        // 3. 鼠标交互变量
        let mouseX = 0;
        let mouseY = 0;
        let targetX = 0;
        let targetY = 0;

        const windowHalfX = window.innerWidth / 2;
        const windowHalfY = window.innerHeight / 2;

        const onDocumentMouseMove = (event) => {
            // 归一化鼠标坐标 (-1 到 1)
            mouseX = (event.clientX - windowHalfX);
            mouseY = (event.clientY - windowHalfY);
        };

        document.addEventListener('mousemove', onDocumentMouseMove, false);

        // 4. 动画循环
        const animate = () => {
            animationFrameId = requestAnimationFrame(animate);

            // 平滑移动相机目标角度 (视差效果)
            targetX = mouseX * 0.001;
            targetY = mouseY * 0.001;

            group.rotation.x += 0.05 * (targetY - group.rotation.x);
            group.rotation.y += 0.05 * (targetX - group.rotation.y);

            // 整体缓慢自转
            group.rotation.y += 0.001;

            // 更新粒子位置
            let vertexpos = 0;
            let colorpos = 0;
            let numConnected = 0;

            const positions = particles.geometry.attributes.position.array;

            // 重置连接数
            for (let i = 0; i < particleCount; i++) {
                particlesData[i].numConnections = 0;
            }

            for (let i = 0; i < particleCount; i++) {
                const particleData = particlesData[i];

                // 移动粒子
                positions[i * 3] += particleData.velocity.x;
                positions[i * 3 + 1] += particleData.velocity.y;
                positions[i * 3 + 2] += particleData.velocity.z;

                // 边界反弹
                if (Math.abs(positions[i * 3]) > range) particleData.velocity.x *= -1;
                if (Math.abs(positions[i * 3 + 1]) > range) particleData.velocity.y *= -1;
                if (Math.abs(positions[i * 3 + 2]) > range) particleData.velocity.z *= -1;

                // 检查与其他粒子的距离并连线
                for (let j = i + 1; j < particleCount; j++) {
                    const particleDataB = particlesData[j];

                    const dx = positions[i * 3] - positions[j * 3];
                    const dy = positions[i * 3 + 1] - positions[j * 3 + 1];
                    const dz = positions[i * 3 + 2] - positions[j * 3 + 2];
                    const distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq < maxDistance * maxDistance) {
                        particleData.numConnections++;
                        particleDataB.numConnections++;

                        const alpha = 1.0 - Math.sqrt(distSq) / maxDistance;

                        // 设置线段起点
                        linesPositions[vertexpos++] = positions[i * 3];
                        linesPositions[vertexpos++] = positions[i * 3 + 1];
                        linesPositions[vertexpos++] = positions[i * 3 + 2];

                        // 设置线段终点
                        linesPositions[vertexpos++] = positions[j * 3];
                        linesPositions[vertexpos++] = positions[j * 3 + 1];
                        linesPositions[vertexpos++] = positions[j * 3 + 2];

                        // 设置颜色 (基础色 + 透明度衰减)
                        const baseColor = new THREE.Color(0x6366f1); // Indigo 500

                        linesColors[colorpos++] = baseColor.r * alpha;
                        linesColors[colorpos++] = baseColor.g * alpha;
                        linesColors[colorpos++] = baseColor.b * alpha;

                        linesColors[colorpos++] = baseColor.r * alpha;
                        linesColors[colorpos++] = baseColor.g * alpha;
                        linesColors[colorpos++] = baseColor.b * alpha;

                        numConnected++;
                    }
                }
            }

            // 更新几何体数据
            linesMesh.geometry.setDrawRange(0, numConnected * 2);
            linesMesh.geometry.attributes.position.needsUpdate = true;
            linesMesh.geometry.attributes.color.needsUpdate = true;
            particles.geometry.attributes.position.needsUpdate = true;

            renderer.render(scene, camera);
        };

        animate();

        // 5. 处理窗口大小变化
        const handleResize = () => {
            camera.aspect = window.innerWidth / window.innerHeight;
            camera.updateProjectionMatrix();
            renderer.setSize(window.innerWidth, window.innerHeight);
        };
        window.addEventListener('resize', handleResize);

        // 6. 清理函数
        return () => {
            window.removeEventListener('resize', handleResize);
            document.removeEventListener('mousemove', onDocumentMouseMove);
            cancelAnimationFrame(animationFrameId);
            if (mountRef.current && renderer.domElement) {
                mountRef.current.removeChild(renderer.domElement);
            }
            geometryDispose(particlesGeometry, linesGeometry);
            materialDispose(particleMaterial, linesMaterial);
            renderer.dispose();
        };
    }, []);

    // 辅助函数清理 Three.js 内存
    const geometryDispose = (...geometries) => { geometries.forEach(g => g.dispose()); };
    const materialDispose = (...materials) => { materials.forEach(m => m.dispose()); };

    return (
        <div
            ref={mountRef}
            className="fixed top-0 left-0 w-full h-full -z-10 pointer-events-none"
            style={{ background: 'radial-gradient(circle at 50% 50%, #1e1b4b 0%, #000000 100%)' }} // 深邃暗色背景
        />
    );
};

// --- 主应用组件 (UI) ---
const App = () => {
    const [isScrolled, setIsScrolled] = useState(false);
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

    // 监听滚动改变导航栏背景
    useEffect(() => {
        const handleScroll = () => {
            setIsScrolled(window.scrollY > 50);
        };
        window.addEventListener('scroll', handleScroll);
        return () => window.removeEventListener('scroll', handleScroll);
    }, []);

    const features = [
        {
            icon: <Brain className="w-8 h-8 text-indigo-400 mb-4" />,
            title: "认知级大模型",
            desc: "搭载最新一代亿级参数神经引擎，具备深度语境理解与复杂逻辑推理能力。"
        },
        {
            icon: <Zap className="w-8 h-8 text-purple-400 mb-4" />,
            title: "毫秒级响应",
            desc: "全球分布式 GPU 算力网络支撑，流式输出，让灵感无需等待。"
        },
        {
            icon: <Network className="w-8 h-8 text-blue-400 mb-4" />,
            title: "多模态融合",
            desc: "打破数据边界，原生支持文本、图像、代码及结构化数据的综合处理。"
        },
        {
            icon: <Shield className="w-8 h-8 text-emerald-400 mb-4" />,
            title: "企业级私有化",
            desc: "军工级数据加密，支持 VPC 专属部署，确保企业核心资产绝对安全。"
        }
    ];

    return (
        <div className="min-h-screen text-slate-200 font-sans selection:bg-indigo-500/30 overflow-x-hidden">
            <CustomStyles />
            <NeuralBackground />

            {/* 导航栏 */}
            <nav className={`fixed w-full z-50 transition-all duration-300 ${isScrolled ? 'glass-panel py-3' : 'bg-transparent py-6'}`}>
                <div className="max-w-7xl mx-auto px-6 md:px-12 flex justify-between items-center">
                    <div className="flex items-center gap-2 cursor-pointer">
                        <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-indigo-600 to-purple-500 flex items-center justify-center shadow-lg shadow-indigo-500/30 animate-float">
                            <Sparkles className="w-5 h-5 text-white" />
                        </div>
                        <span className="text-xl font-bold tracking-tight text-white">Nexura<span className="text-indigo-400">.ai</span></span>
                    </div>

                    {/* 桌面端链接 */}
                    <div className="hidden md:flex items-center gap-8 text-sm font-medium">
                        <a href="#features" className="hover:text-white transition-colors">产品能力</a>
                        <a href="#solutions" className="hover:text-white transition-colors">解决方案</a>
                        <a href="#pricing" className="hover:text-white transition-colors">定价</a>
                        <a href="#docs" className="hover:text-white transition-colors">开发者文档</a>
                    </div>

                    <div className="hidden md:flex items-center gap-4">
                        <button className="text-sm font-medium hover:text-white transition-colors">登录</button>
                        <button className="glow-button px-6 py-2 rounded-full text-white text-sm font-semibold flex items-center gap-2">
                            免费开始 <ArrowRight className="w-4 h-4" />
                        </button>
                    </div>

                    {/* 移动端菜单按钮 */}
                    <button className="md:hidden text-gray-300 hover:text-white" onClick={() => setMobileMenuOpen(!mobileMenuOpen)}>
                        {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
                    </button>
                </div>
            </nav>

            {/* 移动端菜单 */}
            {mobileMenuOpen && (
                <div className="fixed inset-0 z-40 bg-black/90 backdrop-blur-xl flex flex-col items-center justify-center gap-8 text-lg font-medium">
                    <a href="#features" className="hover:text-indigo-400 transition-colors" onClick={() => setMobileMenuOpen(false)}>产品能力</a>
                    <a href="#solutions" className="hover:text-indigo-400 transition-colors" onClick={() => setMobileMenuOpen(false)}>解决方案</a>
                    <a href="#pricing" className="hover:text-indigo-400 transition-colors" onClick={() => setMobileMenuOpen(false)}>定价</a>
                    <button className="glow-button mt-4 px-8 py-3 rounded-full text-white font-semibold">
                        免费开始
                    </button>
                </div>
            )}

            {/* 英雄区域 (Hero Section) */}
            <main className="relative pt-32 pb-20 lg:pt-48 lg:pb-32 overflow-hidden flex flex-col items-center justify-center min-h-[90vh]">
                <div className="max-w-7xl mx-auto px-6 md:px-12 text-center relative z-10">

                    <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full glass-panel mb-8 animate-fade-in-up">
                        <span className="flex h-2 w-2 relative">
                            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75"></span>
                            <span className="relative inline-flex rounded-full h-2 w-2 bg-indigo-500"></span>
                        </span>
                        <span className="text-xs md:text-sm font-medium text-indigo-200">Nexura Engine 4.0 现已全球发布</span>
                    </div>

                    <h1 className="text-5xl md:text-7xl lg:text-8xl font-extrabold tracking-tight mb-8 leading-tight animate-fade-in-up delay-100">
                        赋能未来的 <br className="hidden md:block" />
                        <span className="text-gradient-primary">智能交互中枢</span>
                    </h1>

                    <p className="mt-4 max-w-2xl mx-auto text-lg md:text-xl text-slate-400 mb-12 animate-fade-in-up delay-200 leading-relaxed">
                        将前沿的生成式 AI 技术无缝集成至您的业务工作流。
                        通过我们强大的 API，瞬间让您的应用具备人类级别的认知与创造力。
                    </p>

                    <div className="flex flex-col sm:flex-row items-center justify-center gap-4 animate-fade-in-up delay-300">
                        <button className="glow-button w-full sm:w-auto px-8 py-4 rounded-full text-white font-semibold text-lg flex items-center justify-center gap-2 group">
                            获取 API 密钥
                            <ChevronRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
                        </button>
                        <button className="w-full sm:w-auto px-8 py-4 rounded-full glass-panel font-semibold text-white hover:bg-white/10 transition-colors flex items-center justify-center gap-2">
                            <Cpu className="w-5 h-5" />
                            探索开发文档
                        </button>
                    </div>

                    {/* 底部数据展示 */}
                    <div className="mt-24 grid grid-cols-2 md:grid-cols-4 gap-8 max-w-4xl mx-auto border-t border-white/10 pt-12 animate-fade-in-up delay-400">
                        {[
                            { label: '每日 API 调用', value: '10B+' },
                            { label: '平均响应时间', value: '120ms' },
                            { label: '支持语言', value: '100+' },
                            { label: '稳定运行时间', value: '99.99%' },
                        ].map((stat, index) => (
                            <div key={index} className="text-center">
                                <div className="text-3xl font-bold text-white mb-1">{stat.value}</div>
                                <div className="text-sm text-slate-400">{stat.label}</div>
                            </div>
                        ))}
                    </div>

                </div>
            </main>

            {/* 特性介绍区域 (Features Section) */}
            <section id="features" className="py-24 relative z-10">
                <div className="max-w-7xl mx-auto px-6 md:px-12">
                    <div className="text-center mb-16 animate-fade-in-up">
                        <h2 className="text-3xl md:text-5xl font-bold mb-4 text-white">重塑生产力的核心引擎</h2>
                        <p className="text-slate-400 max-w-2xl mx-auto">突破传统边界，以全栈式 AI 能力矩阵，为您的业务增长提供源源不断的智能动力。</p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                        {features.map((feature, index) => (
                            <div
                                key={index}
                                className={`glass-card p-8 rounded-2xl animate-fade-in-up delay-${(index + 1) * 100}`}
                            >
                                <div className="bg-white/5 w-16 h-16 rounded-xl flex items-center justify-center mb-6 border border-white/10">
                                    {feature.icon}
                                </div>
                                <h3 className="text-xl font-bold text-white mb-3">{feature.title}</h3>
                                <p className="text-slate-400 text-sm leading-relaxed">
                                    {feature.desc}
                                </p>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* 呼吁行动区域 (CTA) */}
            <section className="py-24 relative z-10">
                <div className="max-w-5xl mx-auto px-6 md:px-12">
                    <div className="glass-panel rounded-3xl p-10 md:p-16 text-center relative overflow-hidden">
                        {/* 背景装饰光晕 */}
                        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] bg-indigo-500/20 blur-[100px] rounded-full pointer-events-none"></div>

                        <h2 className="text-3xl md:text-5xl font-bold text-white mb-6 relative z-10">准备好步入 AI 时代了吗？</h2>
                        <p className="text-slate-300 mb-10 max-w-xl mx-auto relative z-10">
                            只需几行代码，即可在您的产品中集成世界领先的 AI 能力。现在注册，享受首月百万免费 Token。
                        </p>
                        <div className="relative z-10">
                            <button className="glow-button px-10 py-4 rounded-full text-white font-bold text-lg">
                                创建免费账户
                            </button>
                        </div>
                    </div>
                </div>
            </section>

            {/* 简易页脚 */}
            <footer className="border-t border-white/10 py-12 relative z-10 bg-black/40 backdrop-blur-md">
                <div className="max-w-7xl mx-auto px-6 md:px-12 flex flex-col md:flex-row justify-between items-center gap-6">
                    <div className="flex items-center gap-2">
                        <Sparkles className="w-5 h-5 text-indigo-500" />
                        <span className="font-bold text-white">Nexura.ai</span>
                    </div>
                    <div className="text-sm text-slate-500">
                        © 2026 Nexura AI Technologies. 保留所有权利。
                    </div>
                    <div className="flex gap-6 text-sm text-slate-400">
                        <a href="#" className="hover:text-white transition-colors">隐私政策</a>
                        <a href="#" className="hover:text-white transition-colors">服务条款</a>
                    </div>
                </div>
            </footer>
        </div>
    );
};

export default App;