package com.example.ShoppingSystem.filter.preauth;

/**
 * 预登录绑定协议中使用到的常量定义。
 * <p>
 * 这个类的职责很单一：把前后端约定好的 Header / Cookie 名称集中放在一起，
 * 避免在过滤器、控制器和前端脚本中重复硬编码。
 */
public final class PreAuthHeaders {

    /**
     * 常量类不需要被实例化。
     */
    private PreAuthHeaders() {
    }

    /**
     * 旧版预登录 token Header。
     * <p>
     * 目前主链路已经迁移到 HttpOnly Cookie，但这个 Header 仍然保留，
     * 用于兼容旧调用方或者某些调试场景。
     */
    public static final String HEADER_PREAUTH_TOKEN = "X-Pre-Auth-Token";

    /**
     * 浏览器原始设备指纹 Header。
     * <p>
     * 前端会把 UA、语言、平台、分辨率、时区等信息拼成一个字符串，
     * 通过这个 Header 传给后端，后端再做 hash 存储与比对。
     */
    public static final String HEADER_DEVICE_FINGERPRINT = "X-Device-Fingerprint";

    /**
     * 预登录 token 的 Cookie 名称。
     * <p>
     * 实际名称同时也会出现在配置中；这里保留常量是为了给调用方一个稳定的默认值。
     */
    public static final String COOKIE_PREAUTH_TOKEN = "PREAUTH_TOKEN";
}
