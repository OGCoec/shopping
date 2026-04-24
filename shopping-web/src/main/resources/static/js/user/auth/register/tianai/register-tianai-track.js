(function (root, factory) {
  const api = factory();
  root.ShoppingRegisterTianaiTrack = api;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const TIANAI_MIN_DRAG_TRACK_POINTS = 12;
  const TIANAI_MIN_DRAG_DURATION_MS = 320;

  function toTianaiTimestamp(value, fallbackValue) {
    if (typeof value === "number" && Number.isFinite(value)) {
      return Math.round(value);
    }
    if (value instanceof Date) {
      const timestamp = value.getTime();
      if (Number.isFinite(timestamp)) {
        return Math.round(timestamp);
      }
    }
    if (typeof value === "string" && value.trim()) {
      const timestamp = Date.parse(value);
      if (Number.isFinite(timestamp)) {
        return timestamp;
      }
    }
    return Math.round(fallbackValue);
  }

  function normalizeTianaiTrackList(trackItems) {
    if (!Array.isArray(trackItems)) {
      return [];
    }
    return trackItems
      .filter((track) => track && Number.isFinite(Number(track.x)) && Number.isFinite(Number(track.y)))
      .map((track, index) => {
        const timestamp = Number(track.t);
        const trackType = typeof track.type === "string" ? track.type.trim().toUpperCase() : "";
        return {
          x: Math.round(Number(track.x)),
          y: Math.round(Number(track.y)),
          t: Number.isFinite(timestamp) ? Math.round(timestamp) : index,
          type: trackType || "MOVE"
        };
      });
  }

  function addTianaiVerticalVariance(trackItems) {
    if (!Array.isArray(trackItems) || trackItems.length <= 2) {
      return trackItems || [];
    }
    const baseY = trackItems[0].y;
    const hasVerticalVariance = trackItems.some((track) => track.y !== baseY);
    if (hasVerticalVariance) {
      return trackItems;
    }
    return trackItems.map((track, index) => {
      if (index === 0 || index === trackItems.length - 1) {
        return track;
      }
      const progress = index / (trackItems.length - 1);
      const wave = Math.round(Math.sin(progress * Math.PI) * 3);
      const jitter = wave !== 0 ? wave : (index % 2 === 0 ? 1 : -1);
      return {
        ...track,
        y: baseY + jitter
      };
    });
  }

  function buildQualifiedTianaiDragTrackList(trackItems) {
    if (!Array.isArray(trackItems) || trackItems.length === 0) {
      return [];
    }

    const firstTrack = trackItems[0];
    const lastTrack = trackItems[trackItems.length - 1];
    const rawDuration = Math.max(
      0,
      ...trackItems.map((track) => (Number.isFinite(Number(track.t)) ? Number(track.t) : 0))
    );
    const targetCount = Math.max(TIANAI_MIN_DRAG_TRACK_POINTS, trackItems.length);
    const targetDuration = Math.max(TIANAI_MIN_DRAG_DURATION_MS, rawDuration, targetCount - 1);
    const enrichedTracks = [];

    for (let index = 0; index < targetCount; index += 1) {
      if (index === 0) {
        enrichedTracks.push({
          x: Math.round(firstTrack.x),
          y: Math.round(firstTrack.y),
          t: 0,
          type: "DOWN"
        });
        continue;
      }

      if (index === targetCount - 1) {
        enrichedTracks.push({
          x: Math.round(lastTrack.x),
          y: Math.round(lastTrack.y),
          t: targetDuration,
          type: "UP"
        });
        continue;
      }

      const progress = index / (targetCount - 1);
      const sourcePosition = progress * (trackItems.length - 1);
      const leftIndex = Math.floor(sourcePosition);
      const rightIndex = Math.min(trackItems.length - 1, leftIndex + 1);
      const interpolation = sourcePosition - leftIndex;
      const leftTrack = trackItems[leftIndex];
      const rightTrack = trackItems[rightIndex];

      enrichedTracks.push({
        x: Math.round(leftTrack.x + (rightTrack.x - leftTrack.x) * interpolation),
        y: Math.round(leftTrack.y + (rightTrack.y - leftTrack.y) * interpolation),
        t: Math.round(targetDuration * progress),
        type: "MOVE"
      });
    }

    return addTianaiVerticalVariance(enrichedTracks);
  }

  function normalizeTianaiRotateProgress(offset, maxOffset) {
    const numericOffset = Number(offset);
    const numericMaxOffset = Number(maxOffset);
    if (!Number.isFinite(numericOffset) || !Number.isFinite(numericMaxOffset) || numericMaxOffset <= 0) {
      return 0;
    }
    return Math.min(1, Math.max(0, numericOffset / numericMaxOffset));
  }

  function buildTianaiRotatePercentage(offset, maxOffset) {
    const progress = normalizeTianaiRotateProgress(offset, maxOffset);
    return Number(progress.toFixed(6)).toString();
  }

  function buildTianaiTrackPayload({
    bgImageWidth = 0,
    bgImageHeight = 0,
    templateImageWidth = 0,
    templateImageHeight = 0,
    startTime: rawStartTime,
    stopTime: rawStopTime,
    trackList: rawTrackList = [],
    left = 0,
    top = 0
  }) {
    const stopTimestamp = toTianaiTimestamp(rawStopTime, Date.now());
    const normalizedTrackList = normalizeTianaiTrackList(rawTrackList);
    const hasClickTrack = normalizedTrackList.some((track) => track.type === "CLICK");
    const finalTrackList = hasClickTrack
      ? normalizedTrackList
      : buildQualifiedTianaiDragTrackList(normalizedTrackList);
    const lastTrackTime = finalTrackList.length > 0
      ? Math.round(finalTrackList[finalTrackList.length - 1].t || 0)
      : 0;
    const startTimestamp = toTianaiTimestamp(rawStartTime, Math.max(stopTimestamp - lastTrackTime, 0));

    return {
      bgImageWidth: Math.round(Number(bgImageWidth) || 0),
      bgImageHeight: Math.round(Number(bgImageHeight) || 0),
      templateImageWidth: Math.round(Number(templateImageWidth) || 0),
      templateImageHeight: Math.round(Number(templateImageHeight) || 0),
      startTime: startTimestamp,
      stopTime: Math.max(stopTimestamp, startTimestamp + lastTrackTime),
      trackList: finalTrackList,
      left: Math.round(Number(left) || 0),
      top: Math.round(Number(top) || 0)
    };
  }

  function buildTianaiWordClickPayload({
    bgImageWidth = 0,
    bgImageHeight = 0,
    startTime: rawStartTime,
    stopTime: rawStopTime,
    clickCoords: rawClickCoords = []
  }) {
    const stopTimestamp = toTianaiTimestamp(rawStopTime, Date.now());
    const startTimestamp = toTianaiTimestamp(rawStartTime, Math.max(stopTimestamp - 1, 0));
    const clickPoints = Array.isArray(rawClickCoords)
      ? rawClickCoords.filter((point) => point && Number.isFinite(Number(point.x)) && Number.isFinite(Number(point.y)))
      : [];
    const step = clickPoints.length > 0
      ? Math.max(1, Math.round(Math.max(stopTimestamp - startTimestamp, clickPoints.length) / (clickPoints.length + 1)))
      : 1;

    return buildTianaiTrackPayload({
      bgImageWidth,
      bgImageHeight,
      templateImageWidth: 0,
      templateImageHeight: 0,
      startTime: startTimestamp,
      stopTime: stopTimestamp,
      trackList: clickPoints.map((point, index) => ({
        x: point.x,
        y: point.y,
        t: step * (index + 1),
        type: "CLICK"
      })),
      left: 0,
      top: 0
    });
  }

  return {
    TIANAI_MIN_DRAG_TRACK_POINTS,
    TIANAI_MIN_DRAG_DURATION_MS,
    toTianaiTimestamp,
    normalizeTianaiTrackList,
    addTianaiVerticalVariance,
    buildQualifiedTianaiDragTrackList,
    normalizeTianaiRotateProgress,
    buildTianaiRotatePercentage,
    buildTianaiTrackPayload,
    buildTianaiWordClickPayload
  };
});
