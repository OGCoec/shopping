const assert = require("assert");

const {
  getConcatRenderData,
  buildConcatLayerMarkup,
  resetCaptchaImageVisibility
} = require("../../main/resources/static/js/user/register.js");

const payload = {
  backgroundImage: "full-image",
  backgroundImageHeight: 303,
  data: {
    topImage: "top-image",
    bottomImage: "bottom-image",
    topHeight: 204,
    bottomHeight: 99,
    movingLayer: "BOTTOM"
  }
};

const renderData = getConcatRenderData(payload);

assert.strictEqual(renderData.useLayerImages, true, "CONCAT should prefer backend-provided layer images");
assert.strictEqual(renderData.topImage, "top-image");
assert.strictEqual(renderData.bottomImage, "bottom-image");
assert.strictEqual(renderData.movingLayer, "BOTTOM");
assert.strictEqual(renderData.topHeight, 204);
assert.strictEqual(renderData.bottomHeight, 99);

const markup = buildConcatLayerMarkup(renderData);
assert.ok(markup.includes("top-image"), "top layer markup should use backend topImage");
assert.ok(markup.includes("bottom-image"), "bottom layer markup should use backend bottomImage");
assert.ok(!markup.includes("full-image"), "CONCAT should not re-slice payload.backgroundImage when layer images exist");

const bgState = { display: "none", visibility: "hidden" };
const tplState = { display: "none" };
resetCaptchaImageVisibility(bgState, tplState, false);
assert.strictEqual(bgState.display, "block");
assert.strictEqual(bgState.visibility, "visible");
assert.strictEqual(tplState.display, "block");

console.log("register.concat.test.js passed");
