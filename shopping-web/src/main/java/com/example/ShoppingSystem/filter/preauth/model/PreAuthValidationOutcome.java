package com.example.ShoppingSystem.filter.preauth.model;

/**
 * 受保护请求在 preauth 校验阶段的结果包装。
 * <p>
 * 过滤器通过它判断：
 * 1) 当前请求是否允许进入业务链路；
 * 2) 如果不允许，应当返回什么类型的错误；
 * 3) 如果允许，刷新后的绑定对象是什么。
 */
public record PreAuthValidationOutcome(boolean valid,
                                       // 当前请求是否通过校验。
                                       PreAuthValidationError error,
                                       // 失败原因；成功时固定为 NONE。
                                       PreAuthBinding binding) {

    /**
     * 构造“校验通过”的标准结果。
     */
    public static PreAuthValidationOutcome valid(PreAuthBinding binding) {
        return new PreAuthValidationOutcome(true, PreAuthValidationError.NONE, binding);
    }

    /**
     * 构造“校验失败”的标准结果。
     */
    public static PreAuthValidationOutcome invalid(PreAuthValidationError error) {
        return new PreAuthValidationOutcome(false, error, null);
    }
}
