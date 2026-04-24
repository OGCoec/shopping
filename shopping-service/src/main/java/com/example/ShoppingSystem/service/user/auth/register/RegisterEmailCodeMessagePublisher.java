package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.service.user.auth.register.mq.RegisterEmailCodeMessage;

/**
 * 注册邮箱验证码消息发布器接口。
 * 负责把“发送注册邮箱验证码”从同步方法调用改成消息投递，
 * 让注册预检服务只关注业务判断，不直接依赖具体的 RabbitMQ 细节。
 */
public interface RegisterEmailCodeMessagePublisher {

    /**
     * 发布注册邮箱验证码发送消息。
     *
     * @param email         收件人邮箱
     * @param code          验证码文本
     * @param expireMinutes 验证码有效期（分钟）
     */
    void publishRegisterEmailCode(String email, String code, long expireMinutes);

    /**
     * 发布注册邮箱验证码重试消息。
     *
     * @param message    需要重新投递的邮件消息
     * @param delayMilli 本次重试延迟毫秒数
     */
    void publishRetry(RegisterEmailCodeMessage message, long delayMilli);

    /**
     * 把最终发送失败的邮件消息投递到死信队列。
     *
     * @param message 已经到达最大重试次数的邮件消息
     */
    void publishDeadLetter(RegisterEmailCodeMessage message);
}
