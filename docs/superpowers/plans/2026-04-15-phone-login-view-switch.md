# 登录页手机号视图切换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 点击“使用手机号登录”后，右侧登录区域从邮箱/密码视图切换为手机号登录视图（样式接近 OpenAI 电话登录），并支持切回邮箱登录。

**Architecture:** 保持现有 HTML/CSS/JS 分离，不引入新框架。通过“视图容器 + JS 状态切换 + CSS 显隐类”实现邮箱视图与手机号视图互斥显示。表单提交逻辑按当前视图分流，避免邮箱校验误伤手机号流程。

**Tech Stack:** HTML5, CSS3, Vanilla JavaScript（现有静态资源结构）

---

## 文件结构与职责

- **Modify:** `src/main/resources/static/login.html`
  - 把右侧登录内容拆成两个视图容器：`email-login-view` 与 `phone-login-view`
  - 在手机号视图中加入国家区号、手机号输入、继续按钮、切回邮箱登录链接

- **Modify:** `src/main/resources/static/css/login.css`
  - 新增视图切换样式（`login-view`, `is-hidden`, `is-active`）
  - 新增手机号视图样式（输入区、区号选择、按钮、辅助文案）
  - 保持现有按钮间距与右侧居中布局不破坏

- **Modify:** `src/main/resources/static/js/login.js`
  - 新增登录视图状态（`authView`）与切换函数（`setAuthView`）
  - 绑定 `#btn-phone` / `#switch-to-email` 点击事件
  - 在 submit 中按视图分流校验逻辑（email/password vs phone）

---

### Task 1: HTML 拆分为邮箱视图与手机号视图

**Files:**
- Modify: `src/main/resources/static/login.html`
- Test: `src/main/resources/static/login.html`（浏览器手工断言）

- [ ] **Step 1: 先写失败断言（当前页面应失败）**

```js
// 在浏览器控制台执行（改代码前）
console.assert(document.getElementById('phone-login-view'), '缺少 phone-login-view 容器');
console.assert(document.getElementById('switch-to-email'), '缺少切回邮箱入口');
```

- [ ] **Step 2: 运行断言，确认失败**

Run: 打开 `http://localhost:8080/login.html` 后在 DevTools Console 执行上面断言
Expected: ASSERT 失败（元素不存在）

- [ ] **Step 3: 最小化修改 HTML 结构**

```html
<!-- 现有 form 内部改为双视图 -->
<div id="email-login-view" class="login-view is-active">
  <!-- 现有邮箱、密码、记住我、错误信息、立即登录按钮保留在这里 -->
</div>

<div id="phone-login-view" class="login-view is-hidden">
  <div class="phone-header">使用手机号登录</div>
  <div class="phone-subtitle">输入手机号以继续</div>

  <div class="form-group">
    <label for="phone-number">手机号</label>
    <div class="phone-input-row">
      <select id="phone-country-code" aria-label="国家区号">
        <option value="+86">+86</option>
        <option value="+1">+1</option>
        <option value="+44">+44</option>
      </select>
      <input type="tel" id="phone-number" placeholder="请输入手机号" autocomplete="tel" />
    </div>
  </div>

  <div class="error-msg" id="phone-error-msg"></div>

  <button type="button" class="btn-login" id="btn-phone-continue">
    <span class="btn-text">继续</span>
    <div class="btn-hover-content"><span>继续</span></div>
  </button>

  <a href="#" id="switch-to-email" class="switch-link">改用邮箱登录</a>
</div>
```

- [ ] **Step 4: 重新运行断言，确认通过**

Run: 在浏览器 Console 重新执行 Step 1 断言
Expected: PASS（不再报错）

- [ ] **Step 5: Commit**

```bash
# 若仓库已初始化 git
# git add src/main/resources/static/login.html
# git commit -m "feat: add phone login view container in login form"
```

---

### Task 2: CSS 增加视图切换与手机号样式（保持分离）

**Files:**
- Modify: `src/main/resources/static/css/login.css`
- Test: `src/main/resources/static/css/login.css`（浏览器样式检查）

- [ ] **Step 1: 先写失败断言（当前应失败）**

```js
// 控制台执行：检查关键 class 是否已生效（改前应不存在行为）
const phoneView = document.getElementById('phone-login-view');
console.assert(phoneView && getComputedStyle(phoneView).display === 'none', '手机号视图默认应隐藏');
```

- [ ] **Step 2: 运行断言，确认失败或不满足设计目标**

Run: 在 Console 执行 Step 1
Expected: 失败或 display 不是目标值

- [ ] **Step 3: 写最小 CSS 实现**

```css
.login-view.is-hidden { display: none; }
.login-view.is-active { display: block; }

.phone-header {
  text-align: center;
  font-size: 24px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 8px;
}

.phone-subtitle {
  text-align: center;
  font-size: 14px;
  color: #888;
  margin-bottom: 28px;
}

.phone-input-row {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 12px;
  align-items: center;
}

#phone-country-code,
#phone-number {
  height: 48px;
  border: none;
  border-bottom: 1.5px solid #e0e0e0;
  background: transparent;
  font-size: 15px;
  outline: none;
}

.switch-link {
  display: inline-block;
  margin-top: 16px;
  font-size: 13px;
  color: #5b21b6;
  text-decoration: none;
}
```

- [ ] **Step 4: 重新检查样式结果**

Run: 刷新页面并在 Console 执行
```js
const phoneView = document.getElementById('phone-login-view');
console.assert(getComputedStyle(phoneView).display === 'none', '手机号视图默认隐藏应生效');
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
# git add src/main/resources/static/css/login.css
# git commit -m "style: add phone login view styles and toggle classes"
```

---

### Task 3: JS 视图切换与按钮事件绑定

**Files:**
- Modify: `src/main/resources/static/js/login.js`
- Test: `src/main/resources/static/js/login.js`（浏览器控制台断言）

- [ ] **Step 1: 先写失败断言（改前应失败）**

```js
// 目标：点击手机号按钮后 phone 视图激活
const btnPhone = document.getElementById('btn-phone');
btnPhone.click();
console.assert(document.getElementById('phone-login-view').classList.contains('is-active'), 'phone 视图未激活');
```

- [ ] **Step 2: 运行断言，确认失败**

Run: 页面加载后在 Console 执行 Step 1
Expected: ASSERT 失败（当前无切换逻辑）

- [ ] **Step 3: 写最小实现代码**

```js
const emailLoginView = document.getElementById('email-login-view');
const phoneLoginView = document.getElementById('phone-login-view');
const phoneBtn = document.getElementById('btn-phone');
const switchToEmailBtn = document.getElementById('switch-to-email');
let authView = 'email';

function setAuthView(view) {
  authView = view;
  const isPhone = view === 'phone';

  emailLoginView.classList.toggle('is-hidden', isPhone);
  emailLoginView.classList.toggle('is-active', !isPhone);

  phoneLoginView.classList.toggle('is-hidden', !isPhone);
  phoneLoginView.classList.toggle('is-active', isPhone);
}

if (phoneBtn) {
  phoneBtn.addEventListener('click', () => setAuthView('phone'));
}

if (switchToEmailBtn) {
  switchToEmailBtn.addEventListener('click', (e) => {
    e.preventDefault();
    setAuthView('email');
  });
}
```

- [ ] **Step 4: 重新运行断言，确认通过**

Run: Console 执行
```js
document.getElementById('btn-phone').click();
console.assert(document.getElementById('phone-login-view').classList.contains('is-active'), 'phone 视图应激活');
console.assert(document.getElementById('email-login-view').classList.contains('is-hidden'), 'email 视图应隐藏');
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
# git add src/main/resources/static/js/login.js
# git commit -m "feat: add auth view state and phone/email view toggle"
```

---

### Task 4: Submit 校验按视图分流（邮箱与手机号互不干扰）

**Files:**
- Modify: `src/main/resources/static/js/login.js`
- Test: `src/main/resources/static/js/login.js`（手工回归）

- [ ] **Step 1: 先写失败用例（改前应失败）**

```js
// 目标：phone 视图下不触发邮箱格式错误
setAuthView('phone');
document.getElementById('login-form').dispatchEvent(new Event('submit', { cancelable: true }));
console.assert(document.getElementById('error-msg').style.display !== 'block', 'phone 视图不应显示邮箱错误');
```

- [ ] **Step 2: 运行失败用例确认现状**

Run: Console 执行 Step 1
Expected: 失败（当前 submit 只按邮箱逻辑）

- [ ] **Step 3: 写最小分流实现**

```js
// loginForm submit 内部最前面
if (authView === 'phone') {
  const phoneInput = document.getElementById('phone-number');
  const phoneErr = document.getElementById('phone-error-msg');
  const raw = phoneInput.value.trim();

  phoneErr.style.display = 'none';

  if (!/^\d{6,15}$/.test(raw)) {
    phoneErr.textContent = '请输入有效手机号';
    phoneErr.style.display = 'block';
    return;
  }

  // 仅前端占位：后续可接短信验证码流程
  phoneErr.textContent = '手机号校验通过（演示）';
  phoneErr.style.display = 'block';
  return;
}
```

- [ ] **Step 4: 回归验证两条路径都正确**

Run:
1) 切换 phone 视图，提交空手机号 -> 应显示 phone 错误，不显示 email 错误。  
2) 切回 email 视图，输入非法邮箱 -> 仍显示原 email 错误。

Expected: 两条路径互不串线

- [ ] **Step 5: Commit**

```bash
# git add src/main/resources/static/js/login.js
# git commit -m "fix: split submit validation by auth view"
```

---

### Task 5: 最终验证（样式与交互）

**Files:**
- Modify: 无
- Test: `src/main/resources/static/login.html`, `src/main/resources/static/css/login.css`, `src/main/resources/static/js/login.js`

- [ ] **Step 1: 执行语法检查**

```bash
node --check src/main/resources/static/js/login.js
```

- [ ] **Step 2: 执行功能检查清单**

```text
[ ] 点击“使用手机号登录” -> 右侧切到手机号视图
[ ] 点击“改用邮箱登录” -> 切回邮箱视图
[ ] 手机号视图提交不会触发邮箱校验
[ ] 邮箱视图提交保持原有校验与动画
[ ] 按钮间距、颜色、整体居中未被破坏
```

- [ ] **Step 3: 记录验证结果**

```text
在 PR/变更说明中记录：
- 通过项
- 失败项
- 若失败，回到对应 Task 修复
```

- [ ] **Step 4: Commit（验证通过后）**

```bash
# git add src/main/resources/static/login.html src/main/resources/static/css/login.css src/main/resources/static/js/login.js
# git commit -m "feat: switch right panel to phone login view on phone button click"
```

---

## 自检（Spec coverage / Placeholder / Consistency）

- 覆盖性：已覆盖“点击电话登录切换右侧视图”“样式类似电话登录”“HTML/CSS/JS 分离”。
- 占位符扫描：无 TBD/TODO；每步都给了具体代码或命令。
- 一致性：`authView`, `setAuthView`, `email-login-view`, `phone-login-view` 命名在各任务一致。
