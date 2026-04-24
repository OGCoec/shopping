package com.example.ShoppingSystem.service.user.auth.register.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册邮箱验证码发送消息。
 * 消息体中保留验证码文本、有效期、重试次数和最近一次错误信息，
 * 方便消费者在失败时继续重试或转入死信队列排查。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterEmailCodeMessage {

    private String messageId;
    private String email;
    private String code;
    private long expireMinutes;
    private int retryCount;
    private long createdAtEpochMilli;
    private String lastError;

    /**
     * 基于当前消息生成下一次重试使用的新消息对象。
     * 这里仅增加重试次数并更新最近一次错误信息，其余业务字段保持不变。
     *
     * @param errorMessage 最近一次发信失败的错误摘要
     * @return 适合重新投递到重试队列的新消息对象
     */
    public RegisterEmailCodeMessage nextRetry(String errorMessage) {
        return RegisterEmailCodeMessage.builder()
                .messageId(messageId)
                .email(email)
                .code(code)
                .expireMinutes(expireMinutes)
                .retryCount(retryCount + 1)
                .createdAtEpochMilli(createdAtEpochMilli)
                .lastError(errorMessage)
                .build();
    }

    /**
     * 生成用于死信队列的失败消息对象。
     * 当邮件发送已经达到最大重试次数时，保留最新错误信息，供后续排查使用。
     *
     * @param errorMessage 最终失败的错误摘要
     * @return 适合投递到死信队列的消息对象
     */
    public RegisterEmailCodeMessage markFailed(String errorMessage) {
        return RegisterEmailCodeMessage.builder()
                .messageId(messageId)
                .email(email)
                .code(code)
                .expireMinutes(expireMinutes)
                .retryCount(retryCount)
                .createdAtEpochMilli(createdAtEpochMilli)
                .lastError(errorMessage)
                .build();
    }
}
