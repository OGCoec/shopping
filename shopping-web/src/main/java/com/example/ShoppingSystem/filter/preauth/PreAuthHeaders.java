package com.example.ShoppingSystem.filter.preauth;

/**
 * Pre-auth constants used by backend filters and frontend requests.
 */
public final class PreAuthHeaders {

    private PreAuthHeaders() {
    }

    /**
     * Legacy header token (kept for backward compatibility).
     */
    public static final String HEADER_PREAUTH_TOKEN = "X-Pre-Auth-Token";

    /**
     * Raw device fingerprint from browser.
     */
    public static final String HEADER_DEVICE_FINGERPRINT = "X-Device-Fingerprint";

    /**
     * HttpOnly cookie name for pre-auth token.
     */
    public static final String COOKIE_PREAUTH_TOKEN = "PREAUTH_TOKEN";
}
