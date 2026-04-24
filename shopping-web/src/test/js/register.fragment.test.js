const assert = require("assert");
const fs = require("fs");
const path = require("path");

const fragmentPath = path.resolve(__dirname, "../../main/resources/static/fragments/register-view.html");
const fragment = fs.readFileSync(fragmentPath, "utf8");

assert.ok(
  fragment.includes('id="register-hcaptcha-modal"'),
  "register fragment should include hCaptcha modal"
);
assert.ok(
  fragment.includes('id="register-hcaptcha-container"'),
  "register fragment should include hCaptcha container"
);
assert.ok(
  fragment.includes('id="register-hcaptcha-error-msg"'),
  "register fragment should include hCaptcha error area"
);
assert.ok(
  !fragment.includes('id="register-turnstile-cancel"'),
  "turnstile cancel button should be removed"
);
assert.ok(
  !fragment.includes('id="register-turnstile-refresh"'),
  "turnstile refresh button should be removed"
);
assert.ok(
  !fragment.includes('id="register-hcaptcha-cancel"'),
  "hCaptcha cancel button should not exist"
);
assert.ok(
  !fragment.includes('id="register-hcaptcha-refresh"'),
  "hCaptcha refresh button should not exist"
);

console.log("register.fragment.test.js passed");
