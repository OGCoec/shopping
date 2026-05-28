(function (root) {
  const imageUtils = root.AdminProductImageUtils;
  const {
    normalizeImageItems,
    imagePayload,
    buildDisplayImages,
    syncMainImageFromDisplayImages,
    displayImagePayload,
    formatJson,
    integerOrZero,
    decimalOrZero
  } = imageUtils;

  function buildDraft(product, allocateSkuClientKey) {
    return {
      id: String(product?.id || ""),
      categoryId: String(product?.categoryId || ""),
      name: product?.name || "",
      subtitle: product?.subtitle || "",
      brandName: product?.brandName || "",
      mainImageUrl: product?.mainImageUrl || "",
      status: product?.status || "ACTIVE",
      imageUrls: buildDisplayImages(product),
      detailImageUrls: normalizeImageItems(product?.detailImageUrls),
      attributesText: formatJson(product?.attributes || {}),
      description: product?.description || "",
      afterSale: product?.afterSale || "",
      skus: (Array.isArray(product?.skus) ? product.skus : []).map((sku) => ({
        clientKey: allocateSkuClientKey(sku),
        id: sku.id ? String(sku.id) : "",
        skuCode: sku.skuCode || "",
        skuName: sku.skuName || "",
        specJsonText: formatJson(sku.specJson || {}),
        skuImageUrls: normalizeImageItems(sku.skuImageUrls),
        priceYuan: String(sku.priceYuan ?? 0),
        originalPriceYuan: sku.originalPriceYuan == null ? "" : String(sku.originalPriceYuan),
        stockQuantity: String(sku.stockQuantity ?? 0),
        status: sku.status || "ACTIVE"
      }))
    };
  }

  function buildUpdatePayload(draft, imageUploadSessions) {
    syncMainImageFromDisplayImages(draft);
    let attributes;
    try {
      attributes = JSON.parse(draft.attributesText || "{}");
    } catch (_) {
      return { ok: false, message: "商品参数 JSON 格式无效。" };
    }
    const skus = [];
    for (const sku of draft.skus) {
      let specJson;
      try {
        specJson = JSON.parse(sku.specJsonText || "{}");
      } catch (_) {
        return { ok: false, message: "SKU 规格 JSON 格式无效。" };
      }
      skus.push({
        id: sku.id || null,
        skuCode: sku.skuCode.trim(),
        skuName: sku.skuName.trim(),
        specJson,
        skuImageUrls: imagePayload(sku.skuImageUrls || []),
        priceYuan: decimalOrZero(sku.priceYuan),
        originalPriceYuan: sku.originalPriceYuan === "" ? null : decimalOrZero(sku.originalPriceYuan),
        stockQuantity: integerOrZero(sku.stockQuantity),
        status: sku.status
      });
    }
    return {
      ok: true,
      payload: {
        categoryId: draft.categoryId,
        subtitle: draft.subtitle.trim(),
        brandName: draft.brandName.trim(),
        mainImageUrl: draft.mainImageUrl.trim(),
        status: draft.status,
        imageUrls: displayImagePayload(draft.imageUrls),
        detailImageUrls: imagePayload(draft.detailImageUrls),
        attributes,
        description: draft.description,
        afterSale: draft.afterSale,
        skus,
        imageUploadSessions: Array.from(imageUploadSessions || [])
      }
    };
  }

  root.AdminProductDetailModel = {
    buildDraft,
    buildUpdatePayload
  };
})(window);
