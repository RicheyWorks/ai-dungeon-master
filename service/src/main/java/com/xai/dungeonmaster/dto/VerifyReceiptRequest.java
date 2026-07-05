package com.xai.dungeonmaster.dto;

/** Body for {@code POST /v2/entitlements/verify}: a purchase receipt to validate. */
public record VerifyReceiptRequest(String storefront, String productId, String receipt) {}
