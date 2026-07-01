package com.example.ShoppingSystem.ai.service.impl.AiContextCompressionService;

import com.example.ShoppingSystem.ai.config.AiProperties;
import com.example.ShoppingSystem.ai.dto.AiChatMessage;
import com.example.ShoppingSystem.ai.dto.AiCompressionRequest;
import com.example.ShoppingSystem.ai.dto.AiCompressionResponse;
import com.example.ShoppingSystem.ai.service.AiContextCompressionService;
import com.example.ShoppingSystem.ai.service.AiGatewayClientService;
import com.example.ShoppingSystem.ai.service.AiGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiContextCompressionServiceImpl implements AiContextCompressionService {

    private static final int MAX_COMPRESSION_INPUT_CHARS = 80000;

    private final AiProperties properties;
    private final AiGatewayClientService gatewayClientService;

    public AiContextCompressionServiceImpl(AiProperties properties,
                                           AiGatewayClientService gatewayClientService) {
        this.properties = properties;
        this.gatewayClientService = gatewayClientService;
    }

    @Override
    public AiCompressionResponse compress(AiCompressionRequest request) {
        List<AiChatMessage> messages = sanitizeMessages(request == null ? null : request.messages());
        int retainCount = Math.max(1, properties.getRecentMessageRetainCount());
        if (messages.size() <= retainCount) {
            return new AiCompressionResponse(normalizeText(request == null ? null : request.summary()), messages);
        }
        int splitIndex = Math.max(0, messages.size() - retainCount);
        List<AiChatMessage> olderMessages = messages.subList(0, splitIndex);
        List<AiChatMessage> retainedMessages = List.copyOf(messages.subList(splitIndex, messages.size()));
        AiProperties.Model model = properties.findEnabledModel(request == null ? null : request.modelKey())
                .or(() -> properties.findEnabledModel(properties.getDefaultModel()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI_MODEL_UNAVAILABLE"));
        String prompt = compressionPrompt(normalizeText(request == null ? null : request.summary()), olderMessages);
        try {
            String summary = gatewayClientService.complete(
                    model,
                    List.of(
                            new AiChatMessage("system", "你负责压缩购物系统 AI 对话上下文，只输出摘要文本，不要输出 Markdown 标题。"),
                            new AiChatMessage("user", prompt)
                    ),
                    0.10,
                    1600);
            String normalizedSummary = normalizeText(summary);
            if (normalizedSummary.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI_COMPRESSION_EMPTY");
            }
            return new AiCompressionResponse(normalizedSummary, retainedMessages);
        } catch (AiGatewayException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI_GATEWAY_UNAVAILABLE", e);
        }
    }

    private String compressionPrompt(String existingSummary, List<AiChatMessage> olderMessages) {
        StringBuilder builder = new StringBuilder();
        builder.append("请把下面旧对话压缩成短期上下文摘要。必须保留：商品名、商品 ID、SKU ID、优惠券 ID、用户已经确认过的二次选择、仍未解决的问题。");
        builder.append("不要保留寒暄，不要编造系统没有提供的数据。\n\n");
        if (!existingSummary.isBlank()) {
            builder.append("已有摘要：\n").append(existingSummary).append("\n\n");
        }
        builder.append("旧消息：\n");
        for (AiChatMessage message : olderMessages) {
            if (builder.length() >= MAX_COMPRESSION_INPUT_CHARS) {
                builder.append("\n[旧消息过长，后续内容已截断]");
                break;
            }
            builder.append(normalizeRole(message.role()))
                    .append(": ")
                    .append(normalizeText(message.content()))
                    .append("\n");
        }
        return builder.toString();
    }

    private List<AiChatMessage> sanitizeMessages(List<AiChatMessage> rawMessages) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return List.of();
        }
        List<AiChatMessage> messages = new ArrayList<>();
        for (AiChatMessage rawMessage : rawMessages) {
            String content = normalizeText(rawMessage == null ? null : rawMessage.content());
            if (!content.isEmpty()) {
                messages.add(new AiChatMessage(normalizeRole(rawMessage.role()), content));
            }
        }
        return List.copyOf(messages);
    }

    private String normalizeRole(String rawRole) {
        String role = normalizeText(rawRole).toLowerCase();
        return switch (role) {
            case "user", "assistant" -> role;
            default -> "user";
        };
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
