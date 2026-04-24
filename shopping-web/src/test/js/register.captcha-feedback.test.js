const assert = require("assert");

const {
  CAPTCHA_SUCCESS_FEEDBACK_MIN_MS,
  getCaptchaSuccessFeedbackDelay
} = require("../../main/resources/static/js/user/register.js");

assert.strictEqual(
  getCaptchaSuccessFeedbackDelay(1_000, 1_000),
  CAPTCHA_SUCCESS_FEEDBACK_MIN_MS,
  "captcha success feedback should keep the full duration when callback just fired"
);
assert.strictEqual(
  getCaptchaSuccessFeedbackDelay(1_000, 1_320),
  CAPTCHA_SUCCESS_FEEDBACK_MIN_MS - 320,
  "captcha success feedback should subtract elapsed time"
);
assert.strictEqual(
  getCaptchaSuccessFeedbackDelay(1_000, 1_000 + CAPTCHA_SUCCESS_FEEDBACK_MIN_MS + 200),
  0,
  "captcha success feedback should not go below zero"
);

console.log("register.captcha-feedback.test.js passed");
