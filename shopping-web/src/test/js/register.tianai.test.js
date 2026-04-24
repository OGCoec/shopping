const assert = require("assert");

const {
  buildTianaiRotatePercentage,
  buildTianaiTrackPayload,
  buildTianaiWordClickPayload
} = require("../../main/resources/static/js/user/register.js");

const sliderPayload = buildTianaiTrackPayload({
  bgImageWidth: 278,
  bgImageHeight: 200,
  templateImageWidth: 52,
  templateImageHeight: 52,
  startTime: "2026-04-21T05:00:00.000Z",
  stopTime: "2026-04-21T05:00:01.234Z",
  trackList: [
    { x: 0.2, y: 0, t: 0, type: "DOWN" },
    { x: 95.7, y: 0, t: 456, type: "MOVE" },
    { x: 120.1, y: 0, t: 1234, type: "UP" }
  ],
  left: 120.1,
  top: 0
});

assert.strictEqual(sliderPayload.startTime, Date.parse("2026-04-21T05:00:00.000Z"));
assert.ok(
  sliderPayload.stopTime - sliderPayload.startTime >= 320,
  "slider payload should satisfy Tianai minimum drag duration"
);
assert.ok(
  sliderPayload.trackList.length >= 10,
  "slider payload should include enough track points for Tianai behavior validation"
);
assert.strictEqual(
  sliderPayload.trackList[0].type,
  "DOWN",
  "slider payload should start with DOWN"
);
assert.strictEqual(
  sliderPayload.trackList[sliderPayload.trackList.length - 1].type,
  "UP",
  "slider payload should end with UP"
);
assert.ok(
  sliderPayload.trackList.some((track) => track.y !== sliderPayload.trackList[0].y),
  "slider payload should include vertical variation to avoid machine-like tracks"
);
assert.ok(
  sliderPayload.trackList.every((track, index, list) => index === 0 || track.t >= list[index - 1].t),
  "slider payload track times should be monotonic"
);
assert.strictEqual(sliderPayload.left, 120);

assert.strictEqual(
  buildTianaiRotatePercentage(0, 320),
  "0",
  "rotate percentage should start at 0"
);
assert.strictEqual(
  buildTianaiRotatePercentage(160, 320),
  "0.5",
  "rotate percentage should be derived from drag progress"
);
assert.strictEqual(
  buildTianaiRotatePercentage(400, 320),
  "1",
  "rotate percentage should clamp to 1 when dragged beyond the track"
);

const clickPayload = buildTianaiWordClickPayload({
  bgImageWidth: 278,
  bgImageHeight: 200,
  startTime: 1713675600000,
  stopTime: 1713675601350,
  clickCoords: [
    { x: 15.4, y: 33.6 },
    { x: 105.2, y: 88.9 }
  ]
});

assert.strictEqual(clickPayload.startTime, 1713675600000);
assert.strictEqual(clickPayload.stopTime, 1713675601350);
assert.deepStrictEqual(
  clickPayload.trackList,
  [
    { x: 15, y: 34, t: 450, type: "CLICK" },
    { x: 105, y: 89, t: 900, type: "CLICK" }
  ],
  "word click payload should emit one CLICK event per selected coordinate"
);

console.log("register.tianai.test.js passed");
