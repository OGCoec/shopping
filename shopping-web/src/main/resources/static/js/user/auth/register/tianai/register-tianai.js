(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory(
      require("./register-tianai-track.js"),
      require("../../shared/preauth-client.js")
    );
    return;
  }
  root.ShoppingRegisterTianai = factory(root.ShoppingRegisterTianaiTrack, root.ShoppingPreAuthClient);
})(typeof globalThis !== "undefined" ? globalThis : this, function (registerTianaiTrackApi, preAuthClientApi) {
  if (!registerTianaiTrackApi) {
    throw new Error("register tianai track dependencies failed to load");
  }

  const preAuthFetch = preAuthClientApi && typeof preAuthClientApi.fetchWithPreAuth === "function"
    ? preAuthClientApi.fetchWithPreAuth
    : fetch;

  const {
    normalizeTianaiRotateProgress,
    buildTianaiRotatePercentage,
    buildTianaiTrackPayload,
    buildTianaiWordClickPayload
  } = registerTianaiTrackApi;

  function getConcatRenderData(payload) {
    const data = payload && payload.data ? payload.data : {};
    const topHeight = Number.isFinite(data.topHeight) ? data.topHeight : 0;
    const bottomHeight = Number.isFinite(data.bottomHeight) ? data.bottomHeight : 0;
    const fallbackTotalHeight = payload && payload.backgroundImageHeight ? payload.backgroundImageHeight : 1;
    const totalHeight = Math.max(topHeight + bottomHeight, fallbackTotalHeight, 1);
    const normalizedTopHeight = topHeight > 0 ? topHeight : Math.max(totalHeight - (data.randomY || 0), 1);
    const normalizedBottomHeight = bottomHeight > 0 ? bottomHeight : Math.max(totalHeight - normalizedTopHeight, 1);

    return {
      useLayerImages: Boolean(data.topImage && data.bottomImage),
      topImage: data.topImage || payload.backgroundImage || "",
      bottomImage: data.bottomImage || payload.backgroundImage || "",
      topHeight: normalizedTopHeight,
      bottomHeight: normalizedBottomHeight,
      movingLayer: data.movingLayer === "BOTTOM" ? "BOTTOM" : "TOP"
    };
  }

  function buildConcatLayerMarkup(renderData, options = {}) {
    const totalHeight = Math.max(renderData.topHeight + renderData.bottomHeight, 1);
    const topPercent = (renderData.topHeight / totalHeight) * 100;
    const bottomPercent = 100 - topPercent;
    const topId = options.topId || "ti-concat-top";
    const bottomId = options.bottomId || "ti-concat-bottom";

    return `
      <div id="${topId}" style="position:absolute; top:0; left:0; width:100%; height:${topPercent}%; background-image:url(${renderData.topImage}); background-size:100% 100%; background-repeat:repeat-x; background-position:0px 0px;"></div>
      <div id="${bottomId}" style="position:absolute; top:${topPercent}%; left:0; width:100%; height:${bottomPercent}%; background-image:url(${renderData.bottomImage}); background-size:100% 100%; background-repeat:repeat-x; background-position:0px 0px;"></div>
    `;
  }

  function resetCaptchaImageVisibility(bgImgNode, tpl, hideBackground) {
    const bgStyle = bgImgNode ? (bgImgNode.style || bgImgNode) : null;
    const tplStyle = tpl ? (tpl.style || tpl) : null;

    if (bgStyle) {
      bgStyle.display = "block";
      bgStyle.visibility = hideBackground ? "hidden" : "visible";
    }
    if (tplStyle) {
      tplStyle.display = hideBackground ? "none" : "block";
    }
  }

  function createRegisterTianai(options) {
    const {
      idPrefix,
      captchaPathMap,
      getElementDisplaySize,
      triggerCaptchaFailureAnimation,
      handleCaptchaDeliveryFailure,
      requestRegisterEmailCodeDelivery,
      openRegisterOtpAfterEmailSent,
      getPendingRegisterPayload
    } = options || {};
    const domIdPrefix = (typeof idPrefix === "string" && idPrefix.trim()) || "register";
    const urlMap = captchaPathMap || {
      SLIDER: "/shopping/user/register/tianai/slider",
      ROTATE: "/shopping/user/register/tianai/rotate",
      CONCAT: "/shopping/user/register/tianai/concat",
      WORD_IMAGE_CLICK: "/shopping/user/register/tianai/word-click"
    };

    let currentTianaiCaptchaId = "";
    let currentTianaiSubType = "";
    let tianaiTrackData = null;
    let tianaiActive = false;
    let trackList = [];
    let startTime = 0;
    let clickCoords = [];
    let tianaiRequestSeq = 0;
    const WORD_CLICK_REQUIRED_POINTS = 4;
    let wordClickAutoSubmitting = false;

    function getDomId(suffix) {
      return `${domIdPrefix}-${suffix}`;
    }

    async function resolveDeliveryResult(deliveryResult) {
      if (deliveryResult && typeof deliveryResult.json === "function") {
        const payload = await deliveryResult.json();
        return {
          ok: Boolean(deliveryResult.ok),
          payload: payload || {}
        };
      }
      const payload = deliveryResult && typeof deliveryResult === "object" ? deliveryResult : {};
      return {
        ok: Boolean(payload.success),
        payload
      };
    }

    function getConcatContainerId() {
      return getDomId("tianai-concat-container");
    }

    function getConcatTopId() {
      return getDomId("tianai-concat-top");
    }

    function getConcatBottomId() {
      return getDomId("tianai-concat-bottom");
    }

    function normalizeTianaiSubType(subType, fallback = "SLIDER") {
      const normalized = (subType || "").trim().toUpperCase();
      if (
        normalized === "SLIDER"
        || normalized === "ROTATE"
        || normalized === "CONCAT"
        || normalized === "WORD_IMAGE_CLICK"
      ) {
        return normalized;
      }
      return fallback;
    }

    function updateTianaiActionButtons(showWordClickActions) {
      const actionsNode = document.getElementById(getDomId("tianai-actions"));
      const cancelNode = document.getElementById(getDomId("tianai-cancel"));
      const refreshNode = document.getElementById(getDomId("tianai-refresh"));
      const confirmNode = document.getElementById(getDomId("tianai-confirm"));
      const displayValue = showWordClickActions ? "flex" : "none";
      if (actionsNode) actionsNode.style.display = displayValue;
      if (cancelNode) cancelNode.style.display = showWordClickActions ? "block" : "none";
      if (refreshNode) refreshNode.style.display = showWordClickActions ? "block" : "none";
      if (confirmNode) confirmNode.style.display = showWordClickActions ? "block" : "none";
    }

    function showTianaiError(message) {
      const errorNode = document.getElementById(getDomId("tianai-error-msg"));
      if (!errorNode) return;
      errorNode.textContent = message;
      errorNode.style.display = "block";
    }

    function clearTianaiError() {
      const errorNode = document.getElementById(getDomId("tianai-error-msg"));
      if (!errorNode) return;
      errorNode.textContent = "";
      errorNode.style.display = "none";
    }

    function closeTianaiModal() {
      const modal = document.getElementById(getDomId("tianai-modal"));
      if (modal) modal.style.display = "none";
      clearTianaiError();
      tianaiActive = false;
    }

    function openTianaiModal() {
      const modal = document.getElementById(getDomId("tianai-modal"));
      if (modal) modal.style.display = "flex";
      tianaiActive = true;
    }

    function resetTianaiUI() {
      clearTianaiError();
      document.getElementById(getDomId("tianai-slider-area")).style.display = "none";
      document.getElementById(getDomId("tianai-click-area")).style.display = "none";
      updateTianaiActionButtons(false);
      wordClickAutoSubmitting = false;

      const tpl = document.getElementById(getDomId("tianai-slider-tpl"));
      const btn = document.getElementById(getDomId("tianai-track-btn"));
      const prog = document.getElementById(getDomId("tianai-track-progress"));
      if (tpl) tpl.style.left = "0px";
      if (btn) btn.style.left = "0px";
      if (prog) prog.style.width = "20px";
      if (tpl && currentTianaiSubType === "ROTATE") {
        tpl.style.transform = "rotate(0deg)";
        tpl.style.left = `calc(50% - ${tpl.width / 2}px)`;
      } else if (tpl) {
        tpl.style.transform = "none";
      }

      const markers = document.getElementById(getDomId("tianai-click-markers"));
      if (markers) markers.innerHTML = "";

      const concatContainer = document.getElementById(getConcatContainerId());
      if (concatContainer) {
        concatContainer.style.display = "none";
        concatContainer.innerHTML = "";
        delete concatContainer.dataset.movingLayer;
      }
    }

    async function loadTianaiCaptcha(subType) {
      currentTianaiSubType = normalizeTianaiSubType(subType);
      resetTianaiUI();
      const requestSeq = ++tianaiRequestSeq;

      const pendingRegisterPayload = getPendingRegisterPayload?.() || null;
      const params = new URLSearchParams();
      if (currentTianaiCaptchaId) {
        params.set("captchaId", currentTianaiCaptchaId);
      }
      if (pendingRegisterPayload?.email) {
        params.set("email", pendingRegisterPayload.email);
      }
      if (pendingRegisterPayload?.deviceFingerprint) {
        params.set("deviceFingerprint", pendingRegisterPayload.deviceFingerprint);
      }
      const baseUrl = urlMap[currentTianaiSubType] || urlMap.SLIDER;
      const url = params.toString() ? `${baseUrl}?${params.toString()}` : baseUrl;

      try {
        const response = await preAuthFetch(url);
        if (!response.ok) throw new Error("Load failed");
        const payload = await response.json();
        if (requestSeq !== tianaiRequestSeq) {
          return;
        }
        currentTianaiCaptchaId = payload.captchaId || "";

        if (currentTianaiSubType === "WORD_IMAGE_CLICK") {
          document.getElementById(getDomId("tianai-click-area")).style.display = "block";
          updateTianaiActionButtons(false);
          document.getElementById(getDomId("tianai-click-bg")).src = payload.backgroundImage || "";
          const clickTpl = document.getElementById(getDomId("tianai-click-tpl"));
          if (clickTpl) clickTpl.src = payload.templateImage || "";
          setupClickTracking();
          return;
        }

        document.getElementById(getDomId("tianai-slider-area")).style.display = "block";
        document.getElementById(getDomId("tianai-slider-bg")).src = payload.backgroundImage || "";

        const tpl = document.getElementById(getDomId("tianai-slider-tpl"));
        tpl.src = payload.templateImage || "";

        const bgImgNode = document.getElementById(getDomId("tianai-slider-bg"));
        let concatContainer = document.getElementById(getConcatContainerId());
        if (currentTianaiSubType === "CONCAT") {
          const renderData = getConcatRenderData(payload);
          resetCaptchaImageVisibility(bgImgNode, tpl, true);

          if (!concatContainer) {
            concatContainer = document.createElement("div");
            concatContainer.id = getConcatContainerId();
            concatContainer.style.position = "absolute";
            concatContainer.style.top = "0";
            concatContainer.style.left = "0";
            concatContainer.style.width = "100%";
            concatContainer.style.height = "100%";
            bgImgNode.parentNode.appendChild(concatContainer);
          }
          concatContainer.style.display = "block";
          concatContainer.dataset.movingLayer = renderData.movingLayer;
          concatContainer.innerHTML = buildConcatLayerMarkup(renderData, {
            topId: getConcatTopId(),
            bottomId: getConcatBottomId()
          });
        } else {
          resetCaptchaImageVisibility(bgImgNode, tpl, false);
          if (concatContainer) concatContainer.style.display = "none";

          if (currentTianaiSubType === "ROTATE") {
            tpl.style.borderRadius = "50%";
            tpl.onload = () => {
              tpl.style.left = `calc(50% - ${tpl.width / 2}px)`;
            };
          } else {
            tpl.style.borderRadius = "0";
            tpl.onload = () => {
              tpl.style.left = "0px";
            };
          }
        }

        setupSliderTracking();
      } catch (error) {
        if (requestSeq === tianaiRequestSeq) {
        showTianaiError("验证码加载失败，请稍后重试");
        }
      }
    }

    function setupSliderTracking() {
      const btn = document.getElementById(getDomId("tianai-track-btn"));
      const prog = document.getElementById(getDomId("tianai-track-progress"));
      const tpl = document.getElementById(getDomId("tianai-slider-tpl"));
      const bg = document.getElementById(getDomId("tianai-slider-bg"));

      let isDragging = false;
      let startX = 0;

      const newBtn = btn.cloneNode(true);
      btn.parentNode.replaceChild(newBtn, btn);

      newBtn.addEventListener("mousedown", (event) => {
        isDragging = true;
        startX = event.clientX;
        startTime = Date.now();
        trackList = [{ x: 0, y: 0, type: "DOWN", t: 0 }];
      });

      document.addEventListener("mousemove", (event) => {
        if (!isDragging || !tianaiActive) return;

        let diffX = event.clientX - startX;
        const maxOffset = newBtn.parentNode.offsetWidth - newBtn.offsetWidth;
        if (diffX < 0) diffX = 0;
        if (diffX > maxOffset) diffX = maxOffset;

        newBtn.style.left = `${diffX}px`;
        prog.style.width = `${diffX + 20}px`;

        trackList.push({ x: diffX, y: 0, type: "MOVE", t: Date.now() - startTime });

        if (currentTianaiSubType === "ROTATE") {
          const angle = normalizeTianaiRotateProgress(diffX, maxOffset) * 360;
          tpl.style.transform = `rotate(${angle}deg)`;
          return;
        }

        if (currentTianaiSubType === "CONCAT") {
          const concatContainer = document.getElementById(getConcatContainerId());
          const movingLayer = concatContainer && concatContainer.dataset.movingLayer === "BOTTOM"
            ? "BOTTOM"
            : "TOP";
          const movingSlice = document.getElementById(movingLayer === "BOTTOM" ? getConcatBottomId() : getConcatTopId());
          const fixedSlice = document.getElementById(movingLayer === "BOTTOM" ? getConcatTopId() : getConcatBottomId());
          if (fixedSlice) fixedSlice.style.backgroundPosition = "0px 0px";
          if (movingSlice) movingSlice.style.backgroundPosition = `${diffX}px 0px`;
          return;
        }

        tpl.style.left = `${diffX}px`;
      });

      document.addEventListener("mouseup", () => {
        if (!isDragging || !tianaiActive) return;
        isDragging = false;

        const diffX = parseInt(newBtn.style.left, 10);
        const maxOffset = newBtn.parentNode.offsetWidth - newBtn.offsetWidth;
        trackList.push({ x: diffX, y: 0, type: "UP", t: Date.now() - startTime });

        if (currentTianaiSubType === "ROTATE") {
          tianaiTrackData = buildTianaiRotatePercentage(diffX, maxOffset);
          continueRegisterWithTianai();
          return;
        }

        const bgDisplaySize = getElementDisplaySize(bg, 278, 200);
        const tplDisplaySize = currentTianaiSubType === "CONCAT"
          ? { width: 0, height: 0 }
          : getElementDisplaySize(tpl, 50, 50);

        tianaiTrackData = JSON.stringify(buildTianaiTrackPayload({
          bgImageWidth: bgDisplaySize.width,
          bgImageHeight: bgDisplaySize.height,
          templateImageWidth: tplDisplaySize.width,
          templateImageHeight: tplDisplaySize.height,
          startTime,
          stopTime: Date.now(),
          trackList,
          left: diffX,
          top: 0
        }));

        continueRegisterWithTianai();
      });
    }

    function setupClickTracking() {
      const box = document.getElementById(getDomId("tianai-click-box"));

      clickCoords = [];
      trackList = [];
      startTime = Date.now();
      wordClickAutoSubmitting = false;

      const newBox = box.cloneNode(true);
      box.parentNode.replaceChild(newBox, box);

      newBox.addEventListener("click", (event) => {
        if (!tianaiActive || wordClickAutoSubmitting) return;
        if (clickCoords.length >= WORD_CLICK_REQUIRED_POINTS) return;

        const rect = newBox.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const y = event.clientY - rect.top;

        trackList.push({
          x: Math.round(x),
          y: Math.round(y),
          type: "CLICK",
          t: Math.max(Date.now() - startTime, clickCoords.length + 1)
        });

        const marker = document.createElement("div");
        marker.style.position = "absolute";
        marker.style.left = `${x - 10}px`;
        marker.style.top = `${y - 10}px`;
        marker.style.width = "20px";
        marker.style.height = "20px";
        marker.style.background = "#3b82f6";
        marker.style.color = "white";
        marker.style.borderRadius = "50%";
        marker.style.textAlign = "center";
        marker.style.lineHeight = "20px";
        marker.style.fontSize = "12px";
        marker.innerText = clickCoords.length + 1;
        document.getElementById(getDomId("tianai-click-markers")).appendChild(marker);

        clickCoords.push({ x, y });
        if (clickCoords.length === WORD_CLICK_REQUIRED_POINTS) {
          wordClickAutoSubmitting = true;
          setTimeout(() => {
            continueRegisterWithTianai()
              .catch(() => {
                showTianaiError("验证失败，请重试");
                triggerCaptchaFailureAnimation?.();
              })
              .finally(() => {
                if (tianaiActive && currentTianaiSubType === "WORD_IMAGE_CLICK") {
                  wordClickAutoSubmitting = false;
                }
              });
          }, 120);
        }
      });
    }

    async function continueRegisterWithTianai() {
      if (!currentTianaiCaptchaId) {
        showTianaiError("验证码上下文已失效，请重新加载");
        return;
      }

      if (currentTianaiSubType === "WORD_IMAGE_CLICK") {
        if (clickCoords.length < WORD_CLICK_REQUIRED_POINTS) {
          showTianaiError(`请依次点击 ${WORD_CLICK_REQUIRED_POINTS} 个文字后自动验证`);
          return;
        }
        const bg = document.getElementById(getDomId("tianai-click-bg"));
        const bgDisplaySize = getElementDisplaySize(bg, 278, 200);
        tianaiTrackData = JSON.stringify(buildTianaiWordClickPayload({
          bgImageWidth: bgDisplaySize.width,
          bgImageHeight: bgDisplaySize.height,
          startTime,
          stopTime: Date.now(),
          clickCoords
        }));
      }

      const deliveryResult = await requestRegisterEmailCodeDelivery(currentTianaiCaptchaId, tianaiTrackData, false);
      const { ok, payload } = await resolveDeliveryResult(deliveryResult);
      if (!ok || !payload.success) {
        const pendingRegisterPayload = getPendingRegisterPayload?.() || null;
        if (pendingRegisterPayload) {
          pendingRegisterPayload.challengeType = payload.challengeType || pendingRegisterPayload.challengeType || "";
          pendingRegisterPayload.challengeSubType = payload.challengeSubType || pendingRegisterPayload.challengeSubType || "";
        }
        if (typeof handleCaptchaDeliveryFailure === "function") {
          const handled = await handleCaptchaDeliveryFailure(payload, {
            defaultMessage: "Security challenge failed. Please retry.",
            closeModal: closeTianaiModal,
            showCaptchaError: showTianaiError
          });
          if (handled) {
            wordClickAutoSubmitting = false;
            return;
          }
        }
        const canRetryCurrentTianai = !payload.challengeType || payload.challengeType === "TIANAI_CAPTCHA";
        const errorMessage = payload.message === "当前风险等级需要先通过验证码验证"
          ? "验证码不存在或者已过期"
          : (payload.message || "验证失败，请重试");
        showTianaiError(errorMessage);
        triggerCaptchaFailureAnimation?.();
        wordClickAutoSubmitting = false;
        if (!canRetryCurrentTianai) {
          return;
        }
        const retrySubType = normalizeTianaiSubType(
          payload.challengeSubType || currentTianaiSubType,
          currentTianaiSubType || "SLIDER"
        );
        setTimeout(() => {
          loadTianaiCaptcha(retrySubType);
        }, 1000);
        return;
      }

      closeTianaiModal();
      const pendingRegisterPayload = getPendingRegisterPayload?.() || null;
      if (pendingRegisterPayload) {
        pendingRegisterPayload.riskLevel = payload.riskLevel || pendingRegisterPayload.riskLevel || "";
        pendingRegisterPayload.requirePhoneBinding = Boolean(payload.requirePhoneBinding);
      }
      openRegisterOtpAfterEmailSent(payload);
    }

    return {
      showTianaiError,
      clearTianaiError,
      openTianaiModal,
      closeTianaiModal,
      loadTianaiCaptcha,
      continueRegisterWithTianai,
      getCurrentTianaiSubType() {
        return currentTianaiSubType;
      }
    };
  }

  return {
    getConcatRenderData,
    buildConcatLayerMarkup,
    resetCaptchaImageVisibility,
    createRegisterTianai
  };
});
