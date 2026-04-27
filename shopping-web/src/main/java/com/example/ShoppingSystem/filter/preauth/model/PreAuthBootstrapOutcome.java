package com.example.ShoppingSystem.filter.preauth.model;

/**
 * 预登录初始化 / 续期流程的结果包装。
 * <p>
 * 控制器只需要知道三件事：
 * 1) 这次是否允许继续；
 * 2) 如果不允许，失败原因是什么；
 * 3) 如果允许，返回给前端的快照内容是什么。
 */
public record PreAuthBootstrapOutcome(boolean allowed,
                                      // 是否允许当前 bootstrap 请求继续完成。
                                      PreAuthValidationError error,
                                      // 失败原因；成功时固定为 NONE。
                                      PreAuthSnapshot snapshot) {

    /**
     * 构造“允许继续”的标准结果。
     */
    public static PreAuthBootstrapOutcome allowed(PreAuthSnapshot snapshot) {
        return new PreAuthBootstrapOutcome(true, PreAuthValidationError.NONE, snapshot);
    }

    /**
     * 构造“被阻断”的标准结果。
     */
    public static PreAuthBootstrapOutcome blocked(PreAuthValidationError error) {
        return new PreAuthBootstrapOutcome(false, error, null);
    }
}
