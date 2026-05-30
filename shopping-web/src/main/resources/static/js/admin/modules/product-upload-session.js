(function (root) {
  const productApiDefault = root.AdminProductApi;
  const imageUtils = root.AdminProductImageUtils;
  const formUi = root.AdminProductFormUi;
  const {
    normalizeImageOrder,
    imagePayload,
    syncMainImageFromDisplayImages
  } = imageUtils;

  function create(options = {}) {
    const productApi = options.productApi || productApiDefault;
    const pickImageFile = options.pickImageFile || formUi.pickImageFile;
    const pickImageFiles = options.pickImageFiles || formUi.pickImageFiles;
    const tempImages = new Map();

    function setStatus(message, type) {
      if (typeof options.setStatus === "function") {
        options.setStatus(message, type);
      }
    }

    function renderProgress(view) {
      if (typeof options.renderProgress === "function") {
        options.renderProgress(view);
      }
    }

  function createUploadProgressTracker(files) {
    const records = files.map((file) => ({
      loaded: 0,
      total: Math.max(0, Number(file?.size) || 0),
      status: "uploading"
    }));
    const totalBytes = records.reduce((sum, record) => sum + record.total, 0);
    let lastLoaded = 0;
    let lastAt = performance.now();
    let speedBytesPerSecond = 0;

    function loadedBytes() {
      return records.reduce((sum, record) => sum + Math.max(0, Math.min(record.loaded, record.total || record.loaded)), 0);
    }

    function completedCount() {
      return records.filter((record) => record.status === "success").length;
    }

    function failedCount() {
      return records.filter((record) => record.status === "failed").length;
    }

    function currentOrdinal() {
      const index = records.findIndex((record) => record.status !== "success" && record.status !== "failed");
      return index === -1 ? records.length : index + 1;
    }

    function refresh(forcedTitle = "") {
      const now = performance.now();
      const loaded = loadedBytes();
      const elapsed = now - lastAt;
      if (elapsed >= 250) {
        speedBytesPerSecond = Math.max(0, (loaded - lastLoaded) / (elapsed / 1000));
        lastLoaded = loaded;
        lastAt = now;
      }
      const percent = totalBytes > 0
        ? (loaded / totalBytes) * 100
        : records.length > 0
          ? ((completedCount() + failedCount()) / records.length) * 100
          : 0;
      const hasUploading = records.some((record) => record.status === "uploading");
      const hasProcessing = records.some((record) => record.status === "processing");
      const title = forcedTitle
        || (hasUploading ? `正在上传 ${currentOrdinal()} / ${records.length}`
          : hasProcessing ? "正在写入 OSS..."
            : `已上传 ${completedCount()} 张图片`);
      renderProgress({
        title,
        loadedBytes: loaded,
        totalBytes,
        percent,
        speedBytesPerSecond,
        speedLabel: hasProcessing ? "等待 OSS 返回" : "",
        phase: hasProcessing ? "processing" : hasUploading ? "uploading" : "done"
      });
    }

    return {
      start() {
        lastLoaded = 0;
        lastAt = performance.now();
        speedBytesPerSecond = 0;
        refresh();
      },
      progress(index, event) {
        const record = records[index];
        if (!record) {
          return;
        }
        record.status = "uploading";
        if (event?.lengthComputable && Number(event.total) > 0) {
          record.total = Number(event.total);
        }
        record.loaded = Math.max(0, Number(event?.loaded) || 0);
        refresh();
      },
      uploadDone(index) {
        const record = records[index];
        if (!record || record.status === "success" || record.status === "failed") {
          return;
        }
        record.loaded = record.total || record.loaded;
        record.status = "processing";
        refresh("正在写入 OSS...");
      },
      success(index) {
        const record = records[index];
        if (!record) {
          return;
        }
        record.loaded = record.total || record.loaded;
        record.status = "success";
        refresh();
      },
      failure(index) {
        const record = records[index];
        if (!record) {
          return;
        }
        record.status = "failed";
        refresh();
      },
      finish(successCount, failCount) {
        const title = failCount > 0
          ? `已上传 ${successCount} 张图片，${failCount} 张失败或不是图片`
          : `已上传 ${successCount} 张图片`;
        renderProgress({
          title,
          loadedBytes: loadedBytes(),
          totalBytes,
          percent: records.length ? 100 : 0,
          speedBytesPerSecond: 0,
          speedLabel: "完成",
          phase: failCount > 0 ? "error" : "done"
        });
      }
    };
  }

  async function uploadImage() {
    const file = await pickImageFile();
    if (!file) {
      return null;
    }
    if (!file.type || !file.type.startsWith("image/")) {
      setStatus("只能上传图片文件。", "error");
      return null;
    }
    const tracker = createUploadProgressTracker([file]);
    tracker.start();
    try {
      setStatus("正在预上传图片。");
      const response = await productApi.preuploadImage(file, {
        onUploadProgress: (event) => tracker.progress(0, event),
        onUploadDone: () => tracker.uploadDone(0)
      });
      const uploaded = response.data || null;
      if (uploaded?.uploadSessionId && uploaded?.tempUrl) {
        tempImages.set(uploaded.tempUrl, uploaded);
      }
      tracker.success(0);
      tracker.finish(uploaded?.uploadSessionId && uploaded?.tempUrl ? 1 : 0, uploaded?.uploadSessionId && uploaded?.tempUrl ? 0 : 1);
      setStatus("图片已预上传。", "ok");
      return uploaded;
    } catch (error) {
      tracker.failure(0);
      tracker.finish(0, 1);
      setStatus(error.message || "图片预上传失败。", "error");
      return null;
    }
  }

  async function uploadImages(items, rerender) {
    const files = await pickImageFiles();
    if (!files.length) {
      return;
    }
    const images = files.filter((file) => file?.type?.startsWith("image/"));
    if (!images.length) {
      setStatus("请选择图片文件。", "error");
      return;
    }
    const tracker = createUploadProgressTracker(images);
    tracker.start();
    setStatus(`正在预上传 ${images.length} 张图片。`);
    const results = await Promise.allSettled(images.map((file, index) => productApi.preuploadImage(file, {
      onUploadProgress: (event) => tracker.progress(index, event),
      onUploadDone: () => tracker.uploadDone(index)
    }).then((response) => {
      tracker.success(index);
      return response;
    }).catch((error) => {
      tracker.failure(index);
      throw error;
    })));
    let successCount = 0;
    results.forEach((result) => {
      const uploaded = result.status === "fulfilled" ? result.value?.data : null;
      if (!uploaded?.uploadSessionId || !uploaded?.tempUrl) {
        return;
      }
      tempImages.set(uploaded.tempUrl, uploaded);
      items.push({ url: uploaded.tempUrl, sort: items.length + 1 });
      successCount += 1;
    });
    normalizeImageOrder(items);
    rerender();
    const failedCount = files.length - successCount;
    tracker.finish(successCount, failedCount);
    if (successCount === images.length && failedCount === 0) {
      setStatus(`已预上传 ${successCount} 张图片。`, "ok");
    } else {
      setStatus(`已预上传 ${successCount} 张图片，${failedCount} 张失败或不是图片。`, "error");
    }
  }

  async function uploadMainIntoDisplay(draft, rerender) {
    if (!draft) {
      return;
    }
    const uploaded = await uploadImage();
    if (!uploaded?.tempUrl) {
      return;
    }
    draft.imageUrls = Array.isArray(draft.imageUrls) ? draft.imageUrls : [];
    draft.imageUrls.unshift({ url: uploaded.tempUrl, sort: 1 });
    syncMainImageFromDisplayImages(draft);
    rerender();
  }

  async function cancelTempByUrl(url) {
    const key = String(url || "");
    const tempImage = tempImages.get(key);
    if (!tempImage) {
      return;
    }
    tempImages.delete(key);
    try {
      await productApi.cancelPreupload(tempImage);
    } catch (_) {
    }
  }

  async function cancelSkuTempImages(sku) {
    const urls = imagePayload(sku?.skuImageUrls || []);
    await Promise.all(urls.map((url) => cancelTempByUrl(url)));
  }

  async function cleanupTempImages() {
    const images = Array.from(tempImages.values());
    tempImages.clear();
    await Promise.all(images.map((tempImage) => productApi.cancelPreupload(tempImage).catch(() => null)));
  }

    function imageUploadSessions() {
      return Array.from(tempImages.values());
    }

    function clearCommitted() {
      tempImages.clear();
    }

    function clearCommittedByUrls(urls) {
      (Array.isArray(urls) ? urls : []).forEach((url) => {
        tempImages.delete(String(url || ""));
      });
    }

    return {
      uploadImage,
      uploadImages,
      uploadMainIntoDisplay,
      cancelByUrl: cancelTempByUrl,
      cancelSkuImages: cancelSkuTempImages,
      cleanup: cleanupTempImages,
      imageUploadSessions,
      clearCommitted,
      clearCommittedByUrls
    };
  }

  root.AdminProductUploadSession = { create };
})(window);
