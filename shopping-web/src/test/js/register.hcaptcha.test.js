const assert = require("assert");

const nodes = {
  "register-hcaptcha-modal": { style: { display: "none" } },
  "register-hcaptcha-error-msg": { textContent: "", style: { display: "none" } },
  "register-hcaptcha-container": { innerHTML: "", style: {} }
};

global.window = {
  hcaptcha: null
};

global.document = {
  getElementById(id) {
    return nodes[id] || null;
  }
};

global.requestAnimationFrame = (callback) => {
  setTimeout(callback, 0);
};

let renderOptions = null;
let resetCalls = 0;

window.hcaptcha = {
  render(container, options) {
    renderOptions = options;
    return 7;
  },
  reset(widgetId) {
    resetCalls += 1;
    assert.strictEqual(widgetId, 7, "hCaptcha should reset the rendered widget");
  }
};

const {
  HCAPTCHA_AUTO_RETRY_DELAY_MS,
  renderHCaptcha
} = require("../../main/resources/static/js/user/register.js");

async function wait(ms) {
  await new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

(async () => {
  await renderHCaptcha("test-site-key");
  assert.ok(renderOptions, "renderHCaptcha should render an hCaptcha widget");

  renderOptions["error-callback"]();
  await wait(HCAPTCHA_AUTO_RETRY_DELAY_MS + 30);

  assert.strictEqual(
    resetCalls,
    1,
    "first hCaptcha render failure should auto-reset once"
  );
  assert.strictEqual(
    nodes["register-hcaptcha-error-msg"].style.display,
    "none",
    "first hCaptcha render failure should not immediately show a terminal error"
  );

  renderOptions["error-callback"]();
  await wait(HCAPTCHA_AUTO_RETRY_DELAY_MS + 30);

  assert.strictEqual(
    resetCalls,
    1,
    "hCaptcha auto-reset should not loop forever after repeated failures"
  );
  assert.strictEqual(
    nodes["register-hcaptcha-error-msg"].style.display,
    "block",
    "second hCaptcha render failure should surface an error"
  );

  console.log("register.hcaptcha.test.js passed");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
