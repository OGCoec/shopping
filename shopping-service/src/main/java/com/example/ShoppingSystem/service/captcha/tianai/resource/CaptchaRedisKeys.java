package com.example.ShoppingSystem.service.captcha.tianai.resource;

public final class CaptchaRedisKeys {

    private CaptchaRedisKeys() {
    }

    public static final String RESOURCE_PREFIX = "captcha:config:resource:";
    public static final String RESOURCE_DEFAULT_PREFIX = RESOURCE_PREFIX + "default:";
    public static final String RESOURCE_DEFAULT_WILDCARD = RESOURCE_DEFAULT_PREFIX + "*";
    public static final String TEMPLATE_PREFIX = "captcha:config:template:";
    public static final String TEMPLATE_DEFAULT_PREFIX = TEMPLATE_PREFIX + "default:";
    public static final String TEMPLATE_DEFAULT_WILDCARD = TEMPLATE_DEFAULT_PREFIX + "*";

    public static final String RESOURCE_DEFAULT_INIT_LOCK = "lock:captcha:config:resource:default:init";

    public static String resourceDefaultKey(String type) {
        return RESOURCE_DEFAULT_PREFIX + type;
    }

    public static String resourceDefaultTempKey(String type, String suffix) {
        return RESOURCE_DEFAULT_PREFIX + type + ":tmp:" + suffix;
    }

    public static String templateDefaultKey(String type) {
        return TEMPLATE_DEFAULT_PREFIX + type;
    }

    public static String templateDefaultTempKey(String type, String suffix) {
        return TEMPLATE_DEFAULT_PREFIX + type + ":tmp:" + suffix;
    }
}
