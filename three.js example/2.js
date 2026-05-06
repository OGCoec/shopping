import React, { useEffect, useRef, useState } from 'react';
import { Mail, Lock, ArrowRight, Sparkles, Github } from 'lucide-react';

export default function App() {
    const mountRef = useRef(null);
    const [isThreeLoaded, setIsThreeLoaded] = useState(false);

    // 表单状态
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    // 动态加载 Three.js 脚本
    useEffect(() => {
        const scriptId = 'three-js-script';
        if (document.getElementById(scriptId)) {
            setIsThreeLoaded(true);
            return;
        }

        const script = document.createElement('script');
        script.id = scriptId;
        script.src = 'https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js';
        script.onload = () => setIsThreeLoaded(true);
        document.head.appendChild(script);

        return () => {
            // 在组件完全卸载时，可以选择清理 script。但在单页应用中通常保留以供复用。
        };
    }, []);

    // Three.js 场景初始化和动画循环
    useEffect(() => {
        if (!isThreeLoaded || !mountRef.current) return;

        const THREE = window.THREE;
        const container = mountRef.current;

        // 1. 基础场景设置
        const scene = new THREE.Scene();
        scene.background = new THREE.Color('#03050f'); // 深色渐变背景的基础色
        scene.fog = new THREE.FogExp2('#03050f', 0.0006); // 增加雾效，实现深远处的景深渐隐感

        const camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 2500);
        camera.position.z = 1000;

        const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
        renderer.setSize(window.innerWidth, window.innerHeight);
        renderer.setPixelRatio(window.devicePixelRatio);
        container.appendChild(renderer.domElement);

        // 2. 创建发光贴图 (无需外部图片，使用 Canvas 动态生成实现伪 Bloom 效果)
        const createParticleTexture = () => {
            const canvas = document.createElement('canvas');
            canvas.width = 64;
            canvas.height = 64;
            const context = canvas.getContext('2d');
            const gradient = context.createRadialGradient(32, 32, 0, 32, 32, 32);
            // 中心高亮纯白，向外逐渐变成半透明，配合 AdditiveBlending 实现发光
            gradient.addColorStop(0, 'rgba(255, 255, 255, 1)');
            gradient.addColorStop(0.2, 'rgba(255, 255, 255, 0.8)');
            gradient.addColorStop(0.5, 'rgba(255, 255, 255, 0.2)');
            gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
            context.fillStyle = gradient;
            context.fillRect(0, 0, 64, 64);
            return new THREE.CanvasTexture(canvas);
        };

        // 3. 粒子系统设置
        const particlesCount = 4000;
        const geometry = new THREE.BufferGeometry();
        const posArray = new Float32Array(particlesCount * 3);
        const colorsArray = new Float32Array(particlesCount * 3);

        const colors = [
            new THREE.Color('#00f0ff'), // 赛博青
            new THREE.Color('#8a2be2'), // 霓虹紫
            new THREE.Color('#ffffff'), // 纯白点缀
            new THREE.Color('#0055ff')  // 深空蓝
        ];

        for (let i = 0; i < particlesCount; i++) {
            // 随机分布在巨大的空间内
            posArray[i * 3] = (Math.random() - 0.5) * 3000;
            posArray[i * 3 + 1] = (Math.random() - 0.5) * 3000;
            posArray[i * 3 + 2] = (Math.random() - 0.5) * 3000;

            // 随机分配设定好的科技感颜色
            const color = colors[Math.floor(Math.random() * colors.length)];
            colorsArray[i * 3] = color.r;
            colorsArray[i * 3 + 1] = color.g;
            colorsArray[i * 3 + 2] = color.b;
        }

        geometry.setAttribute('position', new THREE.BufferAttribute(posArray, 3));
        geometry.setAttribute('color', new THREE.BufferAttribute(colorsArray, 3));

        const material = new THREE.PointsMaterial({
            size: 20,
            map: createParticleTexture(),
            transparent: true,
            blending: THREE.AdditiveBlending, // 核心：加法混合实现多粒子叠加发光(Bloom)
            depthWrite: false, // 避免透明物体互相遮挡
            vertexColors: true,
            opacity: 0.9,
            sizeAttenuation: true
        });

        const particlesMesh = new THREE.Points(geometry, material);
        scene.add(particlesMesh);

        // 4. 鼠标视差交互逻辑
        let mouseX = 0;
        let mouseY = 0;
        let targetX = 0;
        let targetY = 0;

        const windowHalfX = window.innerWidth / 2;
        const windowHalfY = window.innerHeight / 2;

        const onDocumentMouseMove = (event) => {
            mouseX = (event.clientX - windowHalfX);
            mouseY = (event.clientY - windowHalfY);
        };
        document.addEventListener('mousemove', onDocumentMouseMove);

        const onWindowResize = () => {
            camera.aspect = window.innerWidth / window.innerHeight;
            camera.updateProjectionMatrix();
            renderer.setSize(window.innerWidth, window.innerHeight);
        };
        window.addEventListener('resize', onWindowResize);

        // 5. 动画渲染循环
        let animationFrameId;
        const animate = () => {
            animationFrameId = requestAnimationFrame(animate);

            // 目标位置插值（缓动视差）
            targetX = mouseX * 0.4;
            targetY = mouseY * 0.4;

            // 相机根据鼠标移动产生视差偏移
            camera.position.x += (targetX - camera.position.x) * 0.02;
            camera.position.y += (-targetY - camera.position.y) * 0.02;
            camera.lookAt(scene.position);

            // 粒子星空整体的缓慢自转
            particlesMesh.rotation.y -= 0.0005;
            particlesMesh.rotation.x -= 0.0002;

            // 粒子随着时间产生微微的浮动
            const time = Date.now() * 0.00005;
            particlesMesh.position.y = Math.sin(time) * 20;

            renderer.render(scene, camera);
        };

        animate();

        // 清理函数
        return () => {
            cancelAnimationFrame(animationFrameId);
            document.removeEventListener('mousemove', onDocumentMouseMove);
            window.removeEventListener('resize', onWindowResize);
            if (container.contains(renderer.domElement)) {
                container.removeChild(renderer.domElement);
            }
            geometry.dispose();
            material.dispose();
        };
    }, [isThreeLoaded]);

    // 处理登录提交
    const handleLogin = (e) => {
        e.preventDefault();
        setIsLoading(true);
        // 模拟网络请求
        setTimeout(() => {
            setIsLoading(false);
            // alert 在沙箱中可能被阻拦，使用控制台打印代替
            console.log('Login attempt with:', email, password);
        }, 1500);
    };

    return (
        <div className="relative w-full h-screen overflow-hidden bg-[#03050f] font-sans text-white">
            {/* Three.js 3D 背景层 */}
            <div
                ref={mountRef}
                className="absolute inset-0 z-0 pointer-events-none"
            />

            {/* 前端 UI 玻璃拟态层 */}
            <div className="relative z-10 w-full h-full flex flex-col items-center justify-center p-4">

                {/* 卡片外部的环境发光晕染 (CSS 渐变光源) */}
                <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[120%] max-w-[600px] h-[600px] bg-gradient-to-br from-cyan-500/20 via-transparent to-purple-600/20 rounded-full blur-[100px] -z-10 pointer-events-none" />

                <div className="w-full max-w-md">
                    {/* Logo 或顶部标语 */}
                    <div className="flex justify-center mb-8">
                        <div className="flex items-center gap-2 px-4 py-2 rounded-full bg-white/5 border border-white/10 backdrop-blur-md">
                            <Sparkles className="w-5 h-5 text-cyan-400" />
                            <span className="text-sm font-medium tracking-wider text-cyan-100">NEXUS CORE ALIGNMENT</span>
                        </div>
                    </div>

                    {/* 核心登录卡片 (玻璃拟态 Glassmorphism) */}
                    <div className="relative p-8 sm:p-10 bg-white/[0.03] backdrop-blur-2xl border border-white/10 rounded-[2.5rem] shadow-[0_8px_32px_0_rgba(0,0,0,0.4)] overflow-hidden">

                        {/* 卡片内部的高亮边角装饰 */}
                        <div className="absolute top-0 left-0 w-full h-[1px] bg-gradient-to-r from-transparent via-cyan-500/50 to-transparent" />
                        <div className="absolute top-0 left-0 w-[1px] h-full bg-gradient-to-b from-transparent via-cyan-500/20 to-transparent" />

                        <h2 className="text-3xl font-bold mb-2 bg-clip-text text-transparent bg-gradient-to-r from-cyan-300 to-purple-400">
                            Welcome Back
                        </h2>
                        <p className="text-gray-400 mb-8 text-sm">
                            Authenticate to access the neural network.
                        </p>

                        <form onSubmit={handleLogin} className="space-y-5">
                            {/* 邮箱输入框 */}
                            <div className="relative group">
                                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-500 group-focus-within:text-cyan-400 transition-colors">
                                    <Mail className="w-5 h-5" />
                                </div>
                                <input
                                    type="email"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    className="w-full bg-black/20 border border-white/5 rounded-2xl py-3.5 pl-12 pr-4 text-white placeholder-gray-500 focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/50 transition-all duration-300"
                                    placeholder="Email Address"
                                    required
                                />
                            </div>

                            {/* 密码输入框 */}
                            <div className="relative group">
                                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-500 group-focus-within:text-purple-400 transition-colors">
                                    <Lock className="w-5 h-5" />
                                </div>
                                <input
                                    type="password"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    className="w-full bg-black/20 border border-white/5 rounded-2xl py-3.5 pl-12 pr-4 text-white placeholder-gray-500 focus:outline-none focus:border-purple-500/50 focus:ring-1 focus:ring-purple-500/50 transition-all duration-300"
                                    placeholder="Password"
                                    required
                                />
                            </div>

                            <div className="flex items-center justify-between text-sm">
                                <label className="flex items-center gap-2 cursor-pointer group">
                                    <input type="checkbox" className="w-4 h-4 rounded border-gray-600 bg-black/30 text-cyan-500 focus:ring-cyan-500/50 focus:ring-offset-0 transition-colors" />
                                    <span className="text-gray-400 group-hover:text-gray-300 transition-colors">Remember me</span>
                                </label>
                                <a href="#" className="text-cyan-400 hover:text-cyan-300 transition-colors hover:underline">
                                    Forgot Password?
                                </a>
                            </div>

                            {/* 提交按钮 */}
                            <button
                                type="submit"
                                disabled={isLoading}
                                className="relative w-full group overflow-hidden rounded-2xl mt-4"
                            >
                                {/* 按钮背景动画层 */}
                                <div className="absolute inset-0 bg-gradient-to-r from-cyan-600 via-purple-600 to-cyan-600 opacity-80 group-hover:opacity-100 transition-opacity duration-300" />
                                <div className="absolute inset-0 opacity-0 group-hover:opacity-20 bg-[linear-gradient(45deg,transparent_25%,rgba(255,255,255,0.3)_50%,transparent_75%,transparent_100%)] bg-[length:250%_250%,100%_100%] animate-[shimmer_1.5s_infinite]" />

                                <div className="relative py-4 flex items-center justify-center gap-2 font-semibold text-white tracking-wide transition-transform duration-300 group-active:scale-95">
                                    {isLoading ? 'AUTHENTICATING...' : 'SIGN IN'}
                                    {!isLoading && <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />}
                                </div>
                            </button>
                        </form>

                        {/* 社交/第三方登录 */}
                        <div className="mt-8">
                            <div className="relative">
                                <div className="absolute inset-0 flex items-center">
                                    <div className="w-full border-t border-white/10"></div>
                                </div>
                                <div className="relative flex justify-center text-sm">
                                    <span className="px-2 bg-transparent text-gray-500 bg-[#0b0e1a] backdrop-blur-xl">Or continue with</span>
                                </div>
                            </div>

                            <div className="mt-6 flex gap-4">
                                <button className="flex-1 py-3 bg-white/5 hover:bg-white/10 border border-white/10 rounded-xl flex items-center justify-center transition-colors duration-300 text-gray-300 hover:text-white">
                                    <Github className="w-5 h-5" />
                                </button>
                                <button className="flex-1 py-3 bg-white/5 hover:bg-white/10 border border-white/10 rounded-xl flex items-center justify-center transition-colors duration-300 text-gray-300 hover:text-white">
                                    <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24"><path d="M24 4.557c-.883.392-1.832.656-2.828.775 1.017-.609 1.798-1.574 2.165-2.724-.951.564-2.005.974-3.127 1.195-.897-.957-2.178-1.555-3.594-1.555-3.179 0-5.515 2.966-4.797 6.045-4.091-.205-7.719-2.165-10.148-5.144-1.29 2.213-.669 5.108 1.523 6.574-.806-.026-1.566-.247-2.229-.616-.054 2.281 1.581 4.415 3.949 4.89-.693.188-1.452.232-2.224.084.626 1.956 2.444 3.379 4.6 3.419-2.07 1.623-4.678 2.348-7.29 2.04 2.179 1.397 4.768 2.212 7.548 2.212 9.142 0 14.307-7.721 13.995-14.646.962-.695 1.797-1.562 2.457-2.549z" /></svg>
                                </button>
                            </div>
                        </div>

                    </div>

                    <div className="mt-6 text-center">
                        <p className="text-gray-400 text-sm">
                            Don't have an account? <a href="#" className="text-purple-400 font-semibold hover:text-purple-300 transition-colors">Request Access</a>
                        </p>
                    </div>
                </div>
            </div>

            <style>{`
        @keyframes shimmer {
          100% { background-position: -200% 0; }
        }
      `}</style>
        </div>
    );
}