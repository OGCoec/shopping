const assert = require("assert");

const nodes = {
  "register-captcha-image": { src: "" },
  "register-captcha-error-msg": { textContent: "", style: { display: "none" } },
  "register-captcha-modal": { style: { display: "none" } },
  "register-captcha-code": { value: "" }
};

global.document = {
  getElementById(id) {
    return nodes[id] || null;
  }
};

let lastFetchUrl = null;

global.fetch = async (url) => {
  lastFetchUrl = url;
  return {
    ok: true,
    async json() {
      return {
        uuid: "same-uuid",
        image: "data:image/png;base64,test"
      };
    }
  };
};

const { createRegisterHutoolCaptcha } = require("../../main/resources/static/js/user/auth/register/register-hutool-captcha.js");

(async () => {
  const pendingRegisterPayload = {
    email: "user@example.com",
    deviceFingerprint: "ua|lang|platform|1920|1080|Asia/Shanghai"
  };

  const api = createRegisterHutoolCaptcha({
    requestRegisterEmailCodeDelivery() {
      throw new Error("not used in this test");
    },
    getPendingRegisterPayload() {
      return pendingRegisterPayload;
    }
  });

  await api.loadRegisterCaptcha("same-uuid");

  assert.strictEqual(
    lastFetchUrl,
    "/shopping/user/register/hutoolcaptcha?uuid=same-uuid&email=user%40example.com&deviceFingerprint=ua%7Clang%7Cplatform%7C1920%7C1080%7CAsia%2FShanghai",
    "Hutool captcha refresh should carry uuid, email and device fingerprint"
  );

  console.log("register.hutool-captcha.test.js passed");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
