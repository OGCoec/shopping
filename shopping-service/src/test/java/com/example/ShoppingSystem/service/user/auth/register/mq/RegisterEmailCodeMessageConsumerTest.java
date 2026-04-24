package com.example.ShoppingSystem.service.user.auth.register.mq;

import com.example.ShoppingSystem.config.RegisterEmailCodeRabbitProperties;
import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMailSender;
import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.register.mq.RegisterEmailCodeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegisterEmailCodeMessageConsumerTest {

    @Test
    void consumeRegisterEmailCodeSendsMailSynchronously() {
        RegisterEmailCodeMailSender mailSender = mock(RegisterEmailCodeMailSender.class);
        RegisterEmailCodeMessagePublisher publisher = mock(RegisterEmailCodeMessagePublisher.class);
        RegisterEmailCodeRabbitProperties properties = new RegisterEmailCodeRabbitProperties();
        RegisterEmailCodeMessageConsumer consumer = new RegisterEmailCodeMessageConsumer(mailSender, publisher, properties);

        RegisterEmailCodeMessage message = RegisterEmailCodeMessage.builder()
                .messageId("msg-1")
                .email("user@example.com")
                .code("123456")
                .expireMinutes(5)
                .retryCount(0)
                .createdAtEpochMilli(1L)
                .build();

        consumer.consumeRegisterEmailCode(message);

        verify(mailSender).sendRegisterEmailCode("user@example.com", "123456", 5L);
        verifyNoInteractions(publisher);
    }

    @Test
    void consumeRegisterEmailCodeSchedulesRetryWhenSendFailsBelowMaxRetryCount() {
        RegisterEmailCodeMailSender mailSender = mock(RegisterEmailCodeMailSender.class);
        RegisterEmailCodeMessagePublisher publisher = mock(RegisterEmailCodeMessagePublisher.class);
        RegisterEmailCodeRabbitProperties properties = new RegisterEmailCodeRabbitProperties();
        properties.setMaxRetryCount(2);
        RegisterEmailCodeMessageConsumer consumer = new RegisterEmailCodeMessageConsumer(mailSender, publisher, properties);
        doThrow(new IllegalStateException("smtp down"))
                .when(mailSender)
                .sendRegisterEmailCode("user@example.com", "123456", 5L);

        RegisterEmailCodeMessage message = RegisterEmailCodeMessage.builder()
                .messageId("msg-2")
                .email("user@example.com")
                .code("123456")
                .expireMinutes(5)
                .retryCount(0)
                .createdAtEpochMilli(2L)
                .build();

        consumer.consumeRegisterEmailCode(message);

        ArgumentCaptor<RegisterEmailCodeMessage> retryMessageCaptor = ArgumentCaptor.forClass(RegisterEmailCodeMessage.class);
        verify(publisher).publishRetry(retryMessageCaptor.capture(), eq(30_000L));
        verify(publisher, never()).publishDeadLetter(any(RegisterEmailCodeMessage.class));
        assertEquals(1, retryMessageCaptor.getValue().getRetryCount());
        assertEquals("smtp down", retryMessageCaptor.getValue().getLastError());
    }

    @Test
    void consumeRegisterEmailCodePublishesDeadLetterWhenMaxRetryReached() {
        RegisterEmailCodeMailSender mailSender = mock(RegisterEmailCodeMailSender.class);
        RegisterEmailCodeMessagePublisher publisher = mock(RegisterEmailCodeMessagePublisher.class);
        RegisterEmailCodeRabbitProperties properties = new RegisterEmailCodeRabbitProperties();
        properties.setMaxRetryCount(2);
        RegisterEmailCodeMessageConsumer consumer = new RegisterEmailCodeMessageConsumer(mailSender, publisher, properties);
        doThrow(new IllegalStateException("smtp down"))
                .when(mailSender)
                .sendRegisterEmailCode("user@example.com", "123456", 5L);

        RegisterEmailCodeMessage message = RegisterEmailCodeMessage.builder()
                .messageId("msg-3")
                .email("user@example.com")
                .code("123456")
                .expireMinutes(5)
                .retryCount(2)
                .createdAtEpochMilli(3L)
                .build();

        consumer.consumeRegisterEmailCode(message);

        ArgumentCaptor<RegisterEmailCodeMessage> deadLetterCaptor = ArgumentCaptor.forClass(RegisterEmailCodeMessage.class);
        verify(publisher).publishDeadLetter(deadLetterCaptor.capture());
        verify(publisher, never()).publishRetry(any(RegisterEmailCodeMessage.class), any(Long.class));
        assertEquals(2, deadLetterCaptor.getValue().getRetryCount());
        assertEquals("smtp down", deadLetterCaptor.getValue().getLastError());
    }

    @Test
    void resolveRetryDelayMilliUsesExpectedBackoff() {
        RegisterEmailCodeMailSender mailSender = mock(RegisterEmailCodeMailSender.class);
        RegisterEmailCodeMessagePublisher publisher = mock(RegisterEmailCodeMessagePublisher.class);
        RegisterEmailCodeRabbitProperties properties = new RegisterEmailCodeRabbitProperties();
        RegisterEmailCodeMessageConsumer consumer = new RegisterEmailCodeMessageConsumer(mailSender, publisher, properties);

        assertEquals(30_000L, consumer.resolveRetryDelayMilli(0));
        assertEquals(120_000L, consumer.resolveRetryDelayMilli(1));
        assertEquals(300_000L, consumer.resolveRetryDelayMilli(2));
    }
}
