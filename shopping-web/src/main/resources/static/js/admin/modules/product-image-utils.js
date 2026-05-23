(function (root) {
  function normalizeImageItems(raw) {
    const source = Array.isArray(raw) ? raw : [];
    const normalized = source.map((item, index) => {
      const next = imageItemFromRaw(item, index);
      return {
        ...next,
        __index: index,
        __hasSort: normalizeImageSort(next.sort) !== null
      };
    }).filter((item) => item.url);
    if (normalized.some((item) => item.__hasSort)) {
      normalized.sort((left, right) => {
        const leftSort = left.__hasSort ? left.sort : left.__index + 1;
        const rightSort = right.__hasSort ? right.sort : right.__index + 1;
        return leftSort - rightSort || left.__index - right.__index;
      });
    }
    return normalized.map((item, index) => {
      const { __index, __hasSort, ...clean } = item;
      return { ...clean, sort: index + 1 };
    });
  }

  function imageItemFromRaw(item, index) {
    if (typeof item === "string") {
      return { url: item.trim(), sort: index + 1 };
    }
    if (item && typeof item === "object") {
      const sort = normalizeImageSort(item.sort);
      return {
        ...item,
        url: String(item.url || "").trim(),
        sort: sort === null ? index + 1 : sort
      };
    }
    return { url: "", sort: index + 1 };
  }

  function normalizeImageSort(value) {
    const number = Number.parseInt(String(value ?? ""), 10);
    return Number.isFinite(number) && number > 0 ? number : null;
  }

  function normalizeImageOrder(items) {
    const next = orderedImageItems(items);
    if (Array.isArray(items)) {
      items.splice(0, items.length, ...next);
    }
    return next;
  }

  function orderedImageItems(items) {
    const source = Array.isArray(items) ? items : [];
    const next = [];
    source.forEach((item) => {
      const url = imageItemUrl(item).trim();
      if (!url) {
        return;
      }
      const normalized = item && typeof item === "object" ? { ...item, url } : { url };
      normalized.sort = next.length + 1;
      next.push(normalized);
    });
    return next;
  }

  function imagePayload(items) {
    return orderedImageItems(items).map(imageItemUrl).filter(Boolean);
  }

  function normalizeUniqueImageOrder(items) {
    const source = Array.isArray(items) ? items : [];
    const seen = new Set();
    const next = [];
    source.forEach((item) => {
      const url = imageItemUrl(item).trim();
      if (!url || seen.has(url)) {
        return;
      }
      seen.add(url);
      const normalized = item && typeof item === "object" ? { ...item, url } : { url };
      normalized.sort = next.length + 1;
      next.push(normalized);
    });
    if (Array.isArray(items)) {
      items.splice(0, items.length, ...next);
    }
    return next;
  }

  function buildDisplayImages(productOrDraft) {
    const items = [];
    const mainImageUrl = String(productOrDraft?.mainImageUrl || "").trim();
    if (mainImageUrl) {
      items.push({ url: mainImageUrl, sort: 1 });
    }
    normalizeImageItems(productOrDraft?.imageUrls).forEach((item) => {
      items.push({ ...item });
    });
    return normalizeUniqueImageOrder(items);
  }

  function syncMainImageFromDisplayImages(draft) {
    if (!draft || typeof draft !== "object") {
      return "";
    }
    if (Array.isArray(draft.imageUrls)) {
      normalizeUniqueImageOrder(draft.imageUrls);
    } else {
      draft.imageUrls = normalizeUniqueImageOrder(draft.imageUrls);
    }
    draft.mainImageUrl = imageItemUrl(draft.imageUrls[0]).trim();
    return draft.mainImageUrl;
  }

  function displayImagePayload(items) {
    return normalizeUniqueImageOrder(Array.isArray(items)
      ? items.map((item) => (item && typeof item === "object" ? { ...item } : item))
      : []).map(imageItemUrl).filter(Boolean);
  }

  function moveImageItem(items, fromIndex, toIndex) {
    if (!Array.isArray(items) || items.length < 2) {
      return;
    }
    const lastIndex = items.length - 1;
    const from = Math.min(Math.max(fromIndex, 0), lastIndex);
    const to = Math.min(Math.max(toIndex, 0), lastIndex);
    if (from === to) {
      return;
    }
    const [item] = items.splice(from, 1);
    items.splice(to, 0, item);
    normalizeImageOrder(items);
  }

  function imageUrlsFromNode(raw) {
    return normalizeImageItems(raw).map(imageItemUrl).filter(Boolean);
  }

  function imageItemUrl(item) {
    return typeof item === "string" ? item : String(item?.url || "");
  }

  function setImageItemUrl(item, url) {
    if (item && typeof item === "object") {
      item.url = url;
    }
  }

  function collectProductCarouselImages(product) {
    return buildDisplayImages(product).map(imageItemUrl).filter(Boolean);
  }

  function formatJson(value) {
    try {
      return JSON.stringify(value || {}, null, 2);
    } catch (_) {
      return "{}";
    }
  }

  function integerOrZero(value) {
    const number = Number.parseInt(String(value || "0"), 10);
    return Number.isFinite(number) && number >= 0 ? number : 0;
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function escapeAttribute(value) {
    return escapeHtml(value);
  }

  function normalizeSearchText(value) {
    return String(value ?? "").trim().toLowerCase();
  }

  function formatDate(raw) {
    if (!raw) {
      return "-";
    }
    const date = new Date(raw);
    if (Number.isNaN(date.getTime())) {
      return String(raw).replace("T", " ");
    }
    return date.toLocaleString("zh-CN", { hour12: false });
  }

  root.AdminProductImageUtils = {
    normalizeImageItems,
    imageItemFromRaw,
    normalizeImageSort,
    normalizeImageOrder,
    orderedImageItems,
    imagePayload,
    normalizeUniqueImageOrder,
    buildDisplayImages,
    syncMainImageFromDisplayImages,
    displayImagePayload,
    moveImageItem,
    imageUrlsFromNode,
    imageItemUrl,
    setImageItemUrl,
    collectProductCarouselImages,
    formatJson,
    integerOrZero,
    escapeHtml,
    escapeAttribute,
    normalizeSearchText,
    formatDate
  };
})(window);
