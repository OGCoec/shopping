const assert = require("assert");
const fs = require("fs");
const path = require("path");

const registerEntryFragmentPath = path.resolve(__dirname, "../../main/resources/static/fragments/register-view.html");
const registerPasswordFragmentPath = path.resolve(__dirname, "../../main/resources/static/fragments/register-password-view.html");
const registerEntryFragment = fs.readFileSync(registerEntryFragmentPath, "utf8");
const registerPasswordFragment = fs.readFileSync(registerPasswordFragmentPath, "utf8");

assert.ok(
  registerEntryFragment.includes('id="register-entry-email"'),
  "register entry fragment should include the email field"
);
assert.ok(
  registerEntryFragment.includes('id="btn-register-email-continue"'),
  "register entry fragment should include the continue button"
);
assert.ok(
  registerPasswordFragment.includes('id="register-hcaptcha-modal"'),
  "register password fragment should include hCaptcha modal"
);
assert.ok(
  registerPasswordFragment.includes('id="register-hcaptcha-container"'),
  "register password fragment should include hCaptcha container"
);
assert.ok(
  registerPasswordFragment.includes('id="register-hcaptcha-error-msg"'),
  "register password fragment should include hCaptcha error area"
);
assert.ok(
  !registerPasswordFragment.includes('id="register-turnstile-cancel"'),
  "turnstile cancel button should be removed"
);
assert.ok(
  !registerPasswordFragment.includes('id="register-turnstile-refresh"'),
  "turnstile refresh button should be removed"
);
assert.ok(
  !registerPasswordFragment.includes('id="register-hcaptcha-cancel"'),
  "hCaptcha cancel button should not exist"
);
assert.ok(
  !registerPasswordFragment.includes('id="register-hcaptcha-refresh"'),
  "hCaptcha refresh button should not exist"
);

console.log("register.fragment.test.js passed");
