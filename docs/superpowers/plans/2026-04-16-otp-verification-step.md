# OTP 验证码步骤页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 点击“使用一次性验证码登录”后，切换到独立的验证码输入步骤页，并根据当前入口显示“邮箱验证码”或“短信验证码”说明。

**Architecture:** 保持现有 `login.html` / `login.css` / `login.js` 三文件结构，不引入框架。新增一个 `otp-login-view` 视图容器，由现有 `authView` 状态扩展为 `email` / `phone` / `password` / `otp` 四态；在进入 OTP 页时保存当前标识类型与标识值，用于渲染文案、重发按钮和“使用密码继续”返回逻辑。

**Tech Stack:** HTML5, CSS3, Vanilla JavaScript, Maven Spring Boot static resources

---

## 文件结构与职责

- **Modify:** `src/main/resources/static/login.html`
  - 新增 `otp-login-view` 容器
  - 包含 OTP 标题、说明文案、验证码输入框、继续按钮、重发入口、“或”分隔线、“使用密码继续”按钮

- **Modify:** `src/main/resources/static/css/login.css`
  - 新增 OTP 视图样式，保持与当前右侧登录环境一致
  - 复用当前圆角输入框、深色继续按钮、浅灰辅助按钮风格

- **Modify:** `src/main/resources/static/js/login.js`
  - 扩展 `authView` 切换逻辑支持 `otp`
  - 在点击 `#otp-login-btn` 时进入 OTP 页
  - 根据邮箱/手机号入口生成不同说明文案
  - 处理 OTP 提交、重发、返回密码页

---

### Task 1: 添加 OTP 步骤页 HTML 结构

**Files:**
- Modify: `src/main/resources/static/login.html`
- Test: `src/main/resources/static/login.html`

- [ ] **Step 1: 写失败断言**

```js
console.assert(document.getElementById('otp-login-view'), '缺少 otp-login-view 容器');
console.assert(document.getElementById('otp-code-input'), '缺少 otp-code-input 输入框');
console.assert(document.getElementById('back-to-password-btn'), '缺少 back-to-password-btn 按钮');
```

- [ ] **Step 2: 运行断言确认失败**

Run: 打开 `http://localhost:8080/login.html` 后，在 DevTools Console 执行 Step 1 断言。
Expected: FAIL，提示缺少 OTP 相关节点。

- [ ] **Step 3: 在密码页后面新增 OTP 视图容器**

```html
<div id="otp-login-view" class="login-view is-hidden">
  <h2 class="otp-step-title">检查您的收件箱</h2>

  <p class="otp-step-subtitle" id="otp-step-subtitle">
    输入我们刚刚向您的邮箱发送的验证码
  </p>

  <div class="form-group otp-field-group">
    <label id="otp-code-label" for="otp-code-input">验证码</label>
    <input
      type="text"
      id="otp-code-input"
      class="otp-code-input"
      placeholder="请输入验证码"
      inputmode="numeric"
      autocomplete="one-time-code"
    />
  </div>

  <div class="error-msg" id="otp-error-msg"></div>

  <button type="submit" class="btn-login" id="btn-otp-continue">
    <span class="btn-text">继续</span>
    <div class="btn-hover-content">
      <span>继续</span>
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
        stroke-linecap="round" stroke-linejoin="round">
        <line x1="5" y1="12" x2="19" y2="12" />
        <polyline points="12 5 19 12 12 19" />
      </svg>
    </div>
  </button>

  <button type="button" id="otp-resend-btn" class="otp-resend-btn">重新发送验证码</button>

  <div class="password-alt-divider" aria-hidden="true">
    <span></span>
    <em>或</em>
    <span></span>
  </div>

  <button type="button" id="back-to-password-btn" class="otp-secondary-btn">使用密码继续</button>
</div>
```

- [ ] **Step 4: 重新运行断言确认通过**

Run: 在 Console 重新执行 Step 1 的断言。
Expected: PASS。

- [ ] **Step 5: Commit**

```text
跳过：当前工作目录不是 git 仓库，不执行 commit。
```

---

### Task 2: 为 OTP 视图补齐样式并保持原页面环境

**Files:**
- Modify: `src/main/resources/static/css/login.css`
- Test: `src/main/resources/static/css/login.css`

- [ ] **Step 1: 写失败断言**

```js
const otpView = document.getElementById('otp-login-view');
console.assert(otpView && getComputedStyle(otpView).display === 'none', 'OTP 视图默认应隐藏');
```

- [ ] **Step 2: 运行断言确认当前仅有默认结构，无完整样式**

Run: 刷新页面，在 Console 执行 Step 1。
Expected: OTP 节点存在但没有完整视觉样式，或 display 行为尚未接入切换逻辑。

- [ ] **Step 3: 添加 OTP 样式**

```css
.otp-step-title {
  font-size: 32px;
  line-height: 1.15;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 14px;
  letter-spacing: -0.4px;
}

.otp-step-subtitle {
  text-align: center;
  font-size: 14px;
  line-height: 1.7;
  color: #555;
  margin: 0 0 22px;
}

.otp-field-group {
  margin-bottom: 16px;
}

.otp-code-input {
  width: 100%;
  height: 50px;
  border-radius: 25px;
  border: 1.5px solid #d8d8d8;
  background: transparent;
  color: #1a1a2e;
  font-size: 15px;
  padding: 0 18px;
  outline: none;
}

.otp-code-input:focus {
  border-color: #5b21b6;
}

.otp-resend-btn {
  display: block;
  width: 100%;
  border: none;
  background: transparent;
  color: #1a1a2e;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  margin: 18px 0 20px;
}

.otp-secondary-btn {
  width: 100%;
  height: 50px;
  border-radius: 25px;
  border: 1.5px solid #d8d8d8;
  background: rgba(255, 255, 255, 0.22);
  color: #333;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}
```

- [ ] **Step 4: 样式回归检查**

Run:
```js
const otpInput = document.getElementById('otp-code-input');
console.assert(getComputedStyle(otpInput).borderRadius === '25px', 'OTP 输入框应为圆角胶囊');
```
Expected: PASS。

- [ ] **Step 5: Commit**

```text
跳过：当前工作目录不是 git 仓库，不执行 commit。
```

---

### Task 3: 扩展状态机，支持 OTP 视图切换

**Files:**
- Modify: `src/main/resources/static/js/login.js`
- Test: `src/main/resources/static/js/login.js`

- [ ] **Step 1: 写失败断言**

```js
console.assert(document.getElementById('otp-login-view').classList.contains('is-hidden'), 'OTP 视图初始化时应隐藏');
```

- [ ] **Step 2: 运行断言确认当前无 OTP 切换路径**

Run: 刷新页面，在 Console 执行 Step 1，然后手动点击“使用一次性验证码登录”。
Expected: 仍然只是展示错误消息，不会切到 OTP 页。

- [ ] **Step 3: 扩展视图节点与 `setAuthView()`**

```js
const otpLoginView = document.getElementById('otp-login-view');
const otpCodeInput = document.getElementById('otp-code-input');
const otpResendBtn = document.getElementById('otp-resend-btn');
const backToPasswordBtn = document.getElementById('back-to-password-btn');
const otpStepSubtitle = document.getElementById('otp-step-subtitle');

let currentIdentifierType = 'email';
let currentIdentifierValue = '';

function setAuthView(view) {
  authView = view;

  if (!emailLoginView || !phoneLoginView || !passwordLoginView || !otpLoginView) return;

  const isEmail = view === 'email';
  const isPhone = view === 'phone';
  const isPassword = view === 'password';
  const isOtp = view === 'otp';

  emailLoginView.classList.toggle('is-hidden', !isEmail);
  emailLoginView.classList.toggle('is-active', isEmail);
  phoneLoginView.classList.toggle('is-hidden', !isPhone);
  phoneLoginView.classList.toggle('is-active', isPhone);
  passwordLoginView.classList.toggle('is-hidden', !isPassword);
  passwordLoginView.classList.toggle('is-active', isPassword);
  otpLoginView.classList.toggle('is-hidden', !isOtp);
  otpLoginView.classList.toggle('is-active', isOtp);
}
```

- [ ] **Step 4: 运行最小切换验证**

Run:
```js
setAuthView('otp');
console.assert(document.getElementById('otp-login-view').classList.contains('is-active'), 'OTP 视图应激活');
console.assert(document.getElementById('password-login-view').classList.contains('is-hidden'), '密码视图应隐藏');
```
Expected: PASS。

- [ ] **Step 5: Commit**

```text
跳过：当前工作目录不是 git 仓库，不执行 commit。
```

---

### Task 4: 从密码页进入 OTP 页，并根据邮箱/手机号生成说明文案

**Files:**
- Modify: `src/main/resources/static/js/login.js`
- Test: `src/main/resources/static/js/login.js`

- [ ] **Step 1: 写失败断言**

```js
setAuthView('password');
document.getElementById('otp-login-btn').click();
console.assert(document.getElementById('otp-login-view').classList.contains('is-active'), '点击 OTP 按钮后应进入 OTP 页');
```

- [ ] **Step 2: 运行断言确认失败**

Run: 在已进入密码页后执行 Step 1。
Expected: FAIL，因为当前只显示“已发送”错误消息。

- [ ] **Step 3: 用独立函数实现 OTP 页跳转与文案渲染**

```js
function openOtpStep() {
  if (!otpStepSubtitle || !otpCodeInput) return;

  const destination = currentIdentifierValue || identifierValue.textContent || '';

  otpStepSubtitle.textContent = currentIdentifierType === 'phone'
    ? `输入我们刚刚向 ${destination} 发送的短信验证码`
    : `输入我们刚刚向 ${destination} 发送的邮箱验证码`;

  const otpErrorMessage = document.getElementById('otp-error-msg');
  if (otpErrorMessage) otpErrorMessage.style.display = 'none';

  otpCodeInput.value = '';
  setAuthView('otp');
  otpCodeInput.focus();
}

function goToPasswordStep(identifierType, identifierText) {
  currentIdentifierType = identifierType;
  currentIdentifierValue = identifierText;
  identifierLabel.textContent = identifierType === 'phone' ? '手机号码' : '电子邮件地址';
  identifierValue.textContent = identifierText;
  passwordInput.value = '';
  passwordInput.type = 'password';
  setAuthView('password');
  passwordInput.focus();
}

if (otpLoginBtn) {
  otpLoginBtn.addEventListener('click', openOtpStep);
}
```

- [ ] **Step 4: 验证邮箱与手机号文案**

Run:
```js
currentIdentifierType = 'email';
currentIdentifierValue = 'demo@example.com';
openOtpStep();
console.assert(document.getElementById('otp-step-subtitle').textContent.includes('邮箱验证码'), '邮箱文案应出现邮箱验证码');

currentIdentifierType = 'phone';
currentIdentifierValue = '+86 13800138000';
openOtpStep();
console.assert(document.getElementById('otp-step-subtitle').textContent.includes('短信验证码'), '手机号文案应出现短信验证码');
```
Expected: PASS。

- [ ] **Step 5: Commit**

```text
跳过：当前工作目录不是 git 仓库，不执行 commit。
```

---

### Task 5: OTP 页交互——继续、重发、返回密码页

**Files:**
- Modify: `src/main/resources/static/js/login.js`
- Test: `src/main/resources/static/js/login.js`

- [ ] **Step 1: 写失败断言**

```js
setAuthView('otp');
document.getElementById('back-to-password-btn').click();
console.assert(document.getElementById('password-login-view').classList.contains('is-active'), '返回密码页失败');
```

- [ ] **Step 2: 运行断言确认失败**

Run: 在 Console 执行 Step 1。
Expected: FAIL，因为当前返回按钮没有绑定事件。

- [ ] **Step 3: 补齐 OTP 三个交互**

```js
if (backToPasswordBtn) {
  backToPasswordBtn.addEventListener('click', () => {
    setAuthView('password');
    if (passwordInput) passwordInput.focus();
  });
}

if (otpResendBtn) {
  otpResendBtn.addEventListener('click', () => {
    const otpErrorMessage = document.getElementById('otp-error-msg');
    if (otpErrorMessage) {
      otpErrorMessage.textContent = currentIdentifierType === 'phone'
        ? '短信验证码已重新发送（演示）'
        : '邮箱验证码已重新发送（演示）';
      otpErrorMessage.style.display = 'block';
    }
  });
}

if (loginForm) {
  loginForm.addEventListener('submit', (e) => {
    e.preventDefault();

    if (authView === 'otp') {
      const otpErrorMessage = document.getElementById('otp-error-msg');
      const rawOtp = otpCodeInput ? otpCodeInput.value.trim() : '';

      if (otpErrorMessage) otpErrorMessage.style.display = 'none';

      if (!/^\d{4,8}$/.test(rawOtp)) {
        if (otpErrorMessage) {
          otpErrorMessage.textContent = '请输入有效验证码';
          otpErrorMessage.style.display = 'block';
        }
        triggerLoginError();
        return;
      }

      if (otpErrorMessage) {
        otpErrorMessage.textContent = '验证码校验通过（演示）';
        otpErrorMessage.style.display = 'block';
      }
      return;
    }

    // 其余既有 email / phone / password 分支保持原样
  });
}
```

- [ ] **Step 4: 验证三项交互**

Run:
1) 进入 OTP 页，点“使用密码继续” -> 应回到密码页。  
2) 进入 OTP 页，点“重新发送验证码” -> 应显示重发提示。  
3) OTP 页输入非法值 -> 显示“请输入有效验证码”。

Expected: 三项都正确。

- [ ] **Step 5: Commit**

```text
跳过：当前工作目录不是 git 仓库，不执行 commit。
```

---

### Task 6: 最终验证与运行目录同步

**Files:**
- Modify: 无
- Test: `src/main/resources/static/login.html`, `src/main/resources/static/css/login.css`, `src/main/resources/static/js/login.js`

- [ ] **Step 1: 校验 JS 语法**

Run: `node --check src/main/resources/static/js/login.js`
Expected: 无输出，退出码 0。

- [ ] **Step 2: 功能回归检查**

```text
[ ] 邮箱 -> 密码 -> 使用一次性验证码登录 -> OTP 页
[ ] 手机号 -> 密码 -> 使用一次性验证码登录 -> OTP 页
[ ] OTP 页说明文案随入口变化（邮箱验证码 / 短信验证码）
[ ] OTP 页“重新发送验证码”文案随入口变化
[ ] OTP 页“使用密码继续”返回密码页
[ ] 原有邮箱、手机号、密码页布局未被破坏
```

- [ ] **Step 3: 同步到运行目录**

Run:
```bash
cp "C:/Users/damn/Desktop/shopping/src/main/resources/static/login.html" "C:/Users/damn/Desktop/shopping/target/classes/static/login.html"
cp "C:/Users/damn/Desktop/shopping/src/main/resources/static/css/login.css" "C:/Users/damn/Desktop/shopping/target/classes/static/css/login.css"
cp "C:/Users/damn/Desktop/shopping/src/main/resources/static/js/login.js" "C:/Users/damn/Desktop/shopping/target/classes/static/js/login.js"
```
Expected: 无报错。

- [ ] **Step 4: Commit**

```text
跳过：当前工作目录不是 git 仓库，不执行 commit。
```

---

## 自检（Spec coverage / Placeholder / Consistency）

- **Spec coverage:** 已覆盖“点击使用一次性验证码登录进入新页面”“无论邮箱还是电话都显示对应说明”“等待输入验证码”“重新发送”“使用密码继续返回”。
- **Placeholder scan:** 无 TBD / TODO / “自行实现”。
- **Type consistency:** `otp-login-view`, `otp-code-input`, `otp-step-subtitle`, `otp-resend-btn`, `back-to-password-btn`, `currentIdentifierType`, `currentIdentifierValue` 命名在所有任务中一致。
