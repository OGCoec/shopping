package com.example.ShoppingSystem.filter.preauth.model;

/**
 * 预登录校验流程中可能出现的标准失败原因。
 * <p>
 * 使用枚举而不是字符串的好处是：调用方可以安全地 switch 分支，
 * 不需要担心拼写错误或错误码漂移。
 */
public enum PreAuthValidationError {
    /** 没有错误，通常只用于成功态占位。 */
    NONE,
    /** 请求中没有携带可识别的预登录 token。 */
    MISSING_TOKEN,
    /** token 不存在或已过期。 */
    EXPIRED,
    /** 设备指纹不匹配，说明不是同一个浏览器环境。 */
    FINGERPRINT_MISMATCH,
    /** User-Agent 不匹配，说明浏览器环境发生变化。 */
    USER_AGENT_MISMATCH,
    /** IP 已变化，但当前 token 在该 IP 上尚未完成 WAF 验证。 */
    IP_CHANGED_WAF_REQUIRED
}
