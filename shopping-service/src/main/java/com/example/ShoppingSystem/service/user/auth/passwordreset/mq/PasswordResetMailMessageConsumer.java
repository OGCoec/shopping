package com.example.ShoppingSystem.service.user.auth.passwordreset.mq;

import com.example.ShoppingSystem.config.PasswordResetMailRabbitProperties;
import com.example.ShoppingSystem.service.mail.ShoppingMailSender;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetMailMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class PasswordResetMailMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailMessageConsumer.class);

    private final ShoppingMailSender shoppingMailSender;
    private final PasswordResetMailMessagePublisher publisher;
    private final PasswordResetMailRabbitProperties properties;

    public PasswordResetMailMessageConsumer(ShoppingMailSender shoppingMailSender,
                                            PasswordResetMailMessagePublisher publisher,
                                            PasswordResetMailRabbitProperties properties) {
        this.shoppingMailSender = shoppingMailSender;
        this.publisher = publisher;
        this.properties = properties;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.password-reset-mail.queue:password.reset.mail.queue}",
            containerFactory = "passwordResetMailRabbitListenerContainerFactory"
    )
    public void consume(PasswordResetMailMessage message) {
        try {
            if (message == null || message.getType() == null) {
                throw new IllegalArgumentException("Password reset mail message type is required.");
            }
            switch (message.getType()) {
                case RESET_CODE -> sendResetCode(message);
                case RESET_LINK -> sendResetLink(message);
                case RESET_CODE_AND_LINK -> sendResetCodeAndLink(message);
            }
            log.info("Password reset mail sent, messageId={}, type={}, email={}, retryCount={}",
                    message.getMessageId(), message.getType(), message.getEmail(), message.getRetryCount());
        } catch (Exception e) {
            log.warn("Password reset mail failed, messageId={}, type={}, email={}, retryCount={}, error={}",
                    message == null ? null : message.getMessageId(),
                    message == null ? null : message.getType(),
                    message == null ? null : message.getEmail(),
                    message == null ? null : message.getRetryCount(),
                    e.getMessage(),
                    e);
            handleFailure(message, e);
        }
    }

    private void sendResetCode(PasswordResetMailMessage message) {
        shoppingMailSender.sendText(
                message.getEmail(),
                "Password Reset Verification Code",
                String.format(
                        "Your password reset verification code is %s. It expires in %d minutes.",
                        message.getCode(),
                        message.getCodeExpireMinutes()));
    }

    private void sendResetLink(PasswordResetMailMessage message) {
        shoppingMailSender.sendHtmlAlternative(
                message.getEmail(),
                "Password Reset Link",
                String.format(
                        "Please open this email in an HTML-capable email client and click the reset password button. "
                                + "This link expires in %d minutes.",
                        message.getLinkExpireMinutes()),
                buildResetLinkHtml(message.getResetUrl(), message.getLinkExpireMinutes()));
    }

    private void sendResetCodeAndLink(PasswordResetMailMessage message) {
        shoppingMailSender.sendHtmlAlternative(
                message.getEmail(),
                "Password Reset Verification",
                String.format(
                        "Your password reset verification code is %s. It expires in %d minutes.%n%n"
                                + "You can also open this email in an HTML-capable email client and click the reset password button. "
                                + "This link expires in %d minutes.",
                        message.getCode(),
                        message.getCodeExpireMinutes(),
                        message.getLinkExpireMinutes()),
                buildResetCodeAndLinkHtml(
                        message.getCode(),
                        message.getResetUrl(),
                        message.getCodeExpireMinutes(),
                        message.getLinkExpireMinutes()));
    }

    private void handleFailure(PasswordResetMailMessage message, Exception exception) {
        if (message == null) {
            return;
        }
        String errorMessage = exception.getMessage();
        if (message.getRetryCount() < properties.getMaxRetryCount()) {
            long delayMilli = resolveRetryDelayMilli(message.getRetryCount());
            publisher.publishRetry(message.nextRetry(errorMessage), delayMilli);
            return;
        }
        publisher.publishDeadLetter(message.markFailed(errorMessage));
    }

    private long resolveRetryDelayMilli(int currentRetryCount) {
        return switch (currentRetryCount) {
            case 0 -> 30_000L;
            case 1 -> 120_000L;
            default -> 300_000L;
        };
    }

    private String buildResetLinkHtml(String resetUrl, long expireMinutes) {
        return String.format(
                """
                        <!doctype html>
                        <html>
                        <body style="margin:0;padding:24px;background:#f6f7fb;font-family:Arial,Helvetica,sans-serif;color:#202124;">
                          <div style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;padding:28px;">
                            <h1 style="margin:0 0 16px;font-size:22px;line-height:1.3;color:#111827;">Reset your password</h1>
                            <p style="margin:0 0 20px;font-size:15px;line-height:1.6;">We received a request to reset your Shopping System password.</p>
                            <p style="margin:0 0 22px;">
                              <a href="%s" style="display:inline-block;padding:12px 18px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:6px;font-size:15px;font-weight:700;">重置密码</a>
                            </p>
                            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#4b5563;">This link expires in %s minutes.</p>
                            <p style="margin:0;font-size:13px;line-height:1.6;color:#6b7280;">If you did not request a password reset, you can ignore this email.</p>
                          </div>
                        </body>
                        </html>
                        """,
                html(resetUrl),
                html(String.valueOf(expireMinutes)));
    }

    private String buildResetCodeAndLinkHtml(String code,
                                             String resetUrl,
                                             long codeExpireMinutes,
                                             long linkExpireMinutes) {
        return String.format(
                """
                        <!doctype html>
                        <html>
                        <body style="margin:0;padding:24px;background:#f6f7fb;font-family:Arial,Helvetica,sans-serif;color:#202124;">
                          <div style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;padding:28px;">
                            <h1 style="margin:0 0 16px;font-size:22px;line-height:1.3;color:#111827;">Reset your password</h1>
                            <p style="margin:0 0 12px;font-size:15px;line-height:1.6;">Your password reset verification code is:</p>
                            <div style="display:inline-block;margin:0 0 18px;padding:10px 14px;background:#f3f4f6;border-radius:6px;font-size:24px;font-weight:700;letter-spacing:4px;color:#111827;">%s</div>
                            <p style="margin:0 0 20px;font-size:14px;line-height:1.6;color:#4b5563;">The code expires in %s minutes. You can also reset your password with the button below.</p>
                            <p style="margin:0 0 22px;">
                              <a href="%s" style="display:inline-block;padding:12px 18px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:6px;font-size:15px;font-weight:700;">重置密码</a>
                            </p>
                            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#4b5563;">This link expires in %s minutes.</p>
                            <p style="margin:0;font-size:13px;line-height:1.6;color:#6b7280;">If you did not request a password reset, you can ignore this email.</p>
                          </div>
                        </body>
                        </html>
                        """,
                html(code),
                html(String.valueOf(codeExpireMinutes)),
                html(resetUrl),
                html(String.valueOf(linkExpireMinutes)));
    }

    private String html(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
