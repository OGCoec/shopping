package com.example.ShoppingSystem.admin.service.product;

import com.fasterxml.jackson.annotation.JsonProperty;

record CardSecretInventoryInsertItem(@JsonProperty("id_hex") String idHex,
                                     @JsonProperty("sku_id_hex") String skuIdHex,
                                     @JsonProperty("batch_no") String batchNo,
                                     @JsonProperty("secret_ciphertext") String secretCiphertext,
                                     @JsonProperty("secret_nonce") String secretNonce,
                                     @JsonProperty("secret_hash") String secretHash,
                                     @JsonProperty("secret_key_version") String secretKeyVersion,
                                     @JsonProperty("import_source") String importSource,
                                     @JsonProperty("created_by_admin_username") String createdByAdminUsername,
                                     @JsonProperty("created_by_admin_email") String createdByAdminEmail,
                                     @JsonProperty("created_by_admin_phone") String createdByAdminPhone) {
}
