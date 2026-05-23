(function (root) {
  function create(options) {
    const imageUtils = root.AdminProductImageUtils;
    const modulePath = options.modulePath;
    const setStatus = typeof options.setStatus === "function" ? options.setStatus : () => {};
    let carousel = null;
    let modulePromise = null;
    let prewarmId = null;
    let prewarmKey = "";
    let instanceToken = 0;

    function collectImages(product) {
      return imageUtils.collectProductCarouselImages(product);
    }

    function loadModule() {
      if (!modulePromise) {
        modulePromise = import(modulePath).catch((error) => {
          modulePromise = null;
          throw error;
        });
      }
      return modulePromise;
    }

    function destroy() {
      instanceToken += 1;
      if (!carousel) {
        return;
      }
      try {
        carousel.destroy();
      } catch (_) {
      }
      carousel = null;
    }

    function cancelPrewarm() {
      const token = prewarmId;
      prewarmId = null;
      prewarmKey = "";
      if (!token) {
        return;
      }
      if (token.type === "idle" && root.cancelIdleCallback) {
        root.cancelIdleCallback(token.id);
      } else {
        root.clearTimeout(token.id);
      }
    }

    async function open(product) {
      const images = collectImages(product);
      if (!images.length) {
        setStatus("当前商品没有可轮播图片。", "error");
        return;
      }
      destroy();
      const token = ++instanceToken;
      try {
        setStatus("正在打开图片轮播。");
        const module = await loadModule();
        if (token !== instanceToken) {
          return;
        }
        carousel = module.createProductImageRippleCarousel(document.body, {
          images,
          initialIndex: 0,
          intervalMs: 5000,
          transitionMs: 1400,
          title: product?.name || "商品图片轮播"
        });
        carousel.open();
        setStatus("");
      } catch (error) {
        setStatus(error.message || "图片轮播打开失败。", "error");
      }
    }

    async function mount(container, product) {
      const images = collectImages(product);
      if (!images.length) {
        setStatus("当前商品没有可轮播图片。", "error");
        return;
      }
      if (!container) {
        setStatus("图片轮播挂载容器不存在。", "error");
        return;
      }
      destroy();
      const token = ++instanceToken;
      try {
        setStatus("正在加载图片轮播。");
        const module = await loadModule();
        if (token !== instanceToken || !container.isConnected) {
          return;
        }
        carousel = module.createProductImageRippleCarousel(container, {
          images,
          initialIndex: 0,
          intervalMs: 5000,
          transitionMs: 1400,
          title: product?.name || "商品图片轮播",
          inline: true
        });
        carousel.mount();
        setStatus("");
      } catch (error) {
        setStatus(error.message || "图片轮播加载失败。", "error");
      }
    }

    function schedulePrewarm(product) {
      const images = collectImages(product);
      if (!images.length) {
        cancelPrewarm();
        return;
      }
      const key = `${product?.id || ""}:${images.join("\n")}`;
      if (prewarmKey === key) {
        return;
      }
      cancelPrewarm();
      prewarmKey = key;
      const run = () => {
        prewarmId = null;
        prewarm(images);
      };
      if (root.requestIdleCallback) {
        prewarmId = {
          type: "idle",
          id: root.requestIdleCallback(run, { timeout: 1200 })
        };
      } else {
        prewarmId = {
          type: "timeout",
          id: root.setTimeout(run, 180)
        };
      }
    }

    async function prewarm(images) {
      try {
        const module = await loadModule();
        if (typeof module.prewarmProductImageRippleCarousel === "function") {
          await module.prewarmProductImageRippleCarousel({
            images,
            initialIndex: 0,
            transitionMs: 1400
          });
        }
      } catch (_) {
      }
    }

    return {
      collectImages,
      open,
      mount,
      destroy,
      cancelPrewarm,
      schedulePrewarm
    };
  }

  root.AdminProductCarouselController = { create };
})(window);
