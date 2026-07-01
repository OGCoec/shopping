package com.example.ShoppingSystem.ai.service.impl.AiChatService;

import com.example.ShoppingSystem.ai.config.AiProperties;
import com.example.ShoppingSystem.ai.dto.AiChatMessage;
import com.example.ShoppingSystem.ai.dto.AiChatStreamRequest;
import com.example.ShoppingSystem.ai.dto.AiModelResponse;
import com.example.ShoppingSystem.ai.dto.AiModelsResponse;
import com.example.ShoppingSystem.ai.dto.AiToolIntent;
import com.example.ShoppingSystem.ai.dto.AiToolIntentType;
import com.example.ShoppingSystem.ai.dto.AiToolResult;
import com.example.ShoppingSystem.ai.service.AiChatService;
import com.example.ShoppingSystem.ai.service.AiGatewayClientService;
import com.example.ShoppingSystem.ai.service.AiGatewayException;
import com.example.ShoppingSystem.ai.service.AiToolQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final int MAX_MESSAGE_COUNT = 120;
    private static final int MAX_MESSAGE_CHARS = 20000;
    private static final int MAX_SUMMARY_CHARS = 12000;
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("(?:商品|product|spu|SPU|id|ID)[^0-9]{0,8}([1-9][0-9]{0,18})");
    private static final Pattern BASE62_ID_PATTERN = Pattern.compile("\\b[0-9A-Za-z]{8,22}\\b");

    private final AiProperties properties;
    private final AiGatewayClientService gatewayClientService;
    private final AiToolQueryService toolQueryService;
    private final ObjectMapper objectMapper;
    private final ExecutorService aiSseExecutor;

    public AiChatServiceImpl(AiProperties properties,
                             AiGatewayClientService gatewayClientService,
                             AiToolQueryService toolQueryService,
                             ObjectMapper objectMapper,
                             ExecutorService aiSseExecutor) {
        this.properties = properties;
        this.gatewayClientService = gatewayClientService;
        this.toolQueryService = toolQueryService;
        this.objectMapper = objectMapper;
        this.aiSseExecutor = aiSseExecutor;
    }

    @Override
    public AiModelsResponse models() {
        List<AiModelResponse> models = properties.enabledModels().stream()
                .map(model -> new AiModelResponse(
                        model.getModelKey(),
                        model.getProviderModelName(),
                        model.getDisplayName(),
                        model.isEnabled(),
                        model.getModelKey().equals(properties.getDefaultModel()),
                        model.getContextTokens(),
                        model.getOutputReserveTokens(),
                        model.getTemperature()))
                .toList();
        return new AiModelsResponse(
                properties.getDefaultModel(),
                properties.getContextHardLimitTokens(),
                properties.getCompressionTriggerTokens(),
                models);
    }

    @Override
    public SseEmitter stream(Long userId, AiChatStreamRequest request) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AI_LOGIN_REQUIRED");
        }
        AiProperties.Model model = properties.findEnabledModel(request == null ? null : request.modelKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI_MODEL_INVALID"));
        List<AiChatMessage> messages = sanitizeMessages(request == null ? null : request.messages());
        if (messages.isEmpty() || latestUserMessage(messages).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI_MESSAGE_REQUIRED");
        }
        String summary = truncate(normalizeText(request.summary()), MAX_SUMMARY_CHARS);
        long estimatedTokens = estimateTokens(summary, messages);
        if (estimatedTokens > Math.max(1, properties.getContextHardLimitTokens())) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "AI_CONTEXT_TOO_LARGE");
        }
        SseEmitter emitter = new SseEmitter((long) Math.max(30000, properties.getStreamTimeoutMillis()));
        aiSseExecutor.submit(() -> runStream(userId, model, messages, summary, emitter));
        return emitter;
    }

    private void runStream(Long userId,
                           AiProperties.Model model,
                           List<AiChatMessage> messages,
                           String summary,
                           SseEmitter emitter) {
        try {
            AiToolIntent intent = classifyIntent(model, messages, summary);
            AiToolResult toolResult = toolQueryService.execute(userId, intent);
            sendEvent(emitter, "tool", Map.of(
                    "intent", toolResult.intent().name(),
                    "status", toolResult.status()));
            if (shouldAnswerDirectly(toolResult)) {
                sendEvent(emitter, "message", toolResult.answer());
                sendEvent(emitter, "done", Map.of("ok", true));
                emitter.complete();
                return;
            }
            List<AiChatMessage> finalMessages = finalAnswerMessages(messages, summary, toolResult);
            gatewayClientService.stream(
                    model,
                    finalMessages,
                    model.getTemperature(),
                    chunk -> sendEvent(emitter, "message", chunk));
            sendEvent(emitter, "done", Map.of("ok", true));
            emitter.complete();
        } catch (AiGatewayException e) {
            log.warn("[AI Chat] gateway stream failed", e);
            sendErrorAndComplete(emitter, "AI_GATEWAY_UNAVAILABLE", "AI 服务暂时不可用，请稍后再试。");
        } catch (ResponseStatusException e) {
            log.warn("[AI Chat] business query failed, status={}, reason={}", e.getStatusCode(), e.getReason());
            sendErrorAndComplete(emitter, normalizeText(e.getReason()).isBlank() ? "AI_QUERY_FAILED" : e.getReason(),
                    "查询失败，请确认问题中的商品或优惠券信息。");
        } catch (Exception e) {
            log.warn("[AI Chat] stream failed", e);
            sendErrorAndComplete(emitter, "AI_CHAT_FAILED", "AI 对话失败，请稍后再试。");
        }
    }

    private AiToolIntent classifyIntent(AiProperties.Model model, List<AiChatMessage> messages, String summary) {
        String latestUserMessage = latestUserMessage(messages);
        List<AiChatMessage> classifierMessages = List.of(
                new AiChatMessage("system", classifierPrompt()),
                new AiChatMessage("user", "对话摘要：" + summary + "\n\n用户最新问题：" + latestUserMessage)
        );
        try {
            String response = gatewayClientService.complete(model, classifierMessages, 0.05, 600);
            AiToolIntent intent = parseIntent(response);
            if (intent != null) {
                return intent;
            }
        } catch (AiGatewayException e) {
            log.warn("[AI Chat] intent classification gateway failed, fallback to local rules", e);
        }
        return fallbackIntent(latestUserMessage);
    }

    private String classifierPrompt() {
        return """
                你是购物系统 AI 助手的意图识别器。只输出一个 JSON 对象，不要输出解释。
                intent 只能是：
                LIST_CATEGORIES, SEARCH_PRODUCTS, FIND_PRODUCT_EXACT, GET_PRODUCT_DETAIL,
                LIST_HOT_SKUS, GET_HOT_SKU_DETAIL, SEARCH_COUPONS, GET_COUPON_DETAIL,
                GET_COUPON_STOCK, CLARIFY, UNSUPPORTED。
                字段固定为：
                {"intent":"","query":"","productId":null,"skuId":"","couponTemplateId":"","categoryId":null,"page":1,"pageSize":10}
                规则：
                - 领券、下单、退款、修改库存、管理端操作 => UNSUPPORTED。
                - 问商品分类 => LIST_CATEGORIES。
                - 问普通商品列表或商品有哪些 => SEARCH_PRODUCTS。
                - 明确要求按商品名称精确查询 => FIND_PRODUCT_EXACT，并把商品名称放 query。
                - 明确商品 ID 查详情 => GET_PRODUCT_DETAIL。
                - 问热点商品有哪些 => LIST_HOT_SKUS。
                - 明确 SKU ID 查热点详情/剩余 => GET_HOT_SKU_DETAIL。
                - 问优惠券有哪些 => SEARCH_COUPONS。
                - 明确优惠券 ID 查详情/规则/时间 => GET_COUPON_DETAIL。
                - 问优惠券剩余数量 => GET_COUPON_STOCK；如果没有明确优惠券 ID，把名称放 query。
                - 参数不够明确 => CLARIFY。
                """;
    }

    private AiToolIntent parseIntent(String rawResponse) {
        String json = extractJson(rawResponse);
        if (json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            AiToolIntentType intentType = AiToolIntentType.valueOf(node.path("intent").asText("").trim());
            return new AiToolIntent(
                    intentType,
                    node.path("query").asText(""),
                    node.path("productId").isNumber() ? node.path("productId").longValue() : null,
                    node.path("skuId").asText(""),
                    node.path("couponTemplateId").asText(""),
                    node.path("categoryId").isNumber() ? node.path("categoryId").longValue() : null,
                    node.path("page").isNumber() ? node.path("page").intValue() : 1,
                    node.path("pageSize").isNumber() ? node.path("pageSize").intValue() : 10);
        } catch (Exception e) {
            return null;
        }
    }

    private AiToolIntent fallbackIntent(String message) {
        String text = normalizeText(message);
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "下单", "购买", "领券", "领取", "退款", "删除", "修改", "新增", "创建", "启用", "禁用", "管理端", "admin")) {
            return intent(AiToolIntentType.UNSUPPORTED, "", null, "", "", null);
        }
        if (text.contains("优惠券") || text.contains("券")) {
            String couponId = extractBase62Id(text);
            String query = cleanupQuery(text, List.of("优惠券", "券", "详细", "详情", "规则", "使用规则", "开始时间", "结束时间", "起始时间", "剩余", "数量", "还有多少"));
            if (containsAny(text, "剩余", "数量", "还有多少", "库存")) {
                return intent(AiToolIntentType.GET_COUPON_STOCK, query, null, "", couponId, null);
            }
            if (!couponId.isBlank() && containsAny(text, "详细", "详情", "规则", "时间", "起始", "开始", "结束")) {
                return intent(AiToolIntentType.GET_COUPON_DETAIL, query, null, "", couponId, null);
            }
            return intent(AiToolIntentType.SEARCH_COUPONS, query, null, "", "", null);
        }
        if (text.contains("热点") || text.contains("热卖") || text.contains("热销")) {
            String skuId = extractBase62Id(text);
            if (!skuId.isBlank() && containsAny(text, "SKU", "sku", "剩余", "详情", "详细")) {
                return intent(AiToolIntentType.GET_HOT_SKU_DETAIL, "", null, skuId, "", null);
            }
            return intent(AiToolIntentType.LIST_HOT_SKUS, "", null, "", "", null);
        }
        if (text.contains("分类")) {
            String query = cleanupQuery(text, List.of("商品", "分类", "有哪些", "有什么", "查询", "搜索"));
            return intent(AiToolIntentType.LIST_CATEGORIES, query, null, "", "", null);
        }
        if (text.contains("商品")) {
            Long productId = extractProductId(text);
            if (productId != null && containsAny(text, "ID", "id", "详情", "详细")) {
                return intent(AiToolIntentType.GET_PRODUCT_DETAIL, "", productId, "", "", null);
            }
            String query = cleanupQuery(text, List.of("商品", "有哪些", "有什么", "查询", "搜索", "精确", "名称", "名字", "叫"));
            if (containsAny(text, "精确", "全名", "名称", "名字", "叫")) {
                return intent(AiToolIntentType.FIND_PRODUCT_EXACT, query, null, "", "", null);
            }
            return intent(AiToolIntentType.SEARCH_PRODUCTS, query, null, "", "", null);
        }
        return intent(AiToolIntentType.CLARIFY, "", null, "", "", null);
    }

    private AiToolIntent intent(AiToolIntentType type,
                                String query,
                                Long productId,
                                String skuId,
                                String couponTemplateId,
                                Long categoryId) {
        return new AiToolIntent(type, query, productId, skuId, couponTemplateId, categoryId, 1, 10);
    }

    private List<AiChatMessage> finalAnswerMessages(List<AiChatMessage> messages,
                                                    String summary,
                                                    AiToolResult toolResult) throws Exception {
        List<AiChatMessage> finalMessages = new ArrayList<>();
        finalMessages.add(new AiChatMessage("system", finalAnswerSystemPrompt()));
        if (!summary.isBlank()) {
            finalMessages.add(new AiChatMessage("system", "此前对话摘要：\n" + summary));
        }
        List<AiChatMessage> recentMessages = messages.size() <= 60
                ? messages
                : messages.subList(messages.size() - 60, messages.size());
        finalMessages.addAll(recentMessages);
        finalMessages.add(new AiChatMessage("system", "后端只读工具查询结果 JSON：\n"
                + objectMapper.writeValueAsString(toolResult)));
        finalMessages.add(new AiChatMessage("system", "请基于工具查询结果回答用户最新问题。"));
        return finalMessages;
    }

    private String finalAnswerSystemPrompt() {
        return """
                你是购物系统的用户侧 AI 助手，只能回答商品、商品分类、热点商品、优惠券相关问题。
                必须遵守：
                - 只能使用后端工具查询结果中的事实，不要编造库存、时间、价格、优惠券规则。
                - 如果工具结果要求用户二次确认，直接让用户选择具体商品或优惠券。
                - 如果工具结果为空，明确说明没有找到。
                - 不要承诺领券、下单、退款、修改库存或管理端操作。
                - 回答要简洁，优先列出 ID、名称、时间、剩余数量和使用规则。
                """;
    }

    private boolean shouldAnswerDirectly(AiToolResult toolResult) {
        String status = normalizeText(toolResult.status());
        return "clarify".equals(status)
                || "unsupported".equals(status)
                || "not_found".equals(status);
    }

    private List<AiChatMessage> sanitizeMessages(List<AiChatMessage> rawMessages) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return List.of();
        }
        List<AiChatMessage> messages = new ArrayList<>();
        for (AiChatMessage rawMessage : rawMessages) {
            if (messages.size() >= MAX_MESSAGE_COUNT) {
                break;
            }
            String content = truncate(normalizeText(rawMessage == null ? null : rawMessage.content()), MAX_MESSAGE_CHARS);
            if (!content.isBlank()) {
                messages.add(new AiChatMessage(normalizeRole(rawMessage.role()), content));
            }
        }
        return List.copyOf(messages);
    }

    private String latestUserMessage(List<AiChatMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index -= 1) {
            AiChatMessage message = messages.get(index);
            if ("user".equals(message.role())) {
                return normalizeText(message.content());
            }
        }
        return "";
    }

    private long estimateTokens(String summary, List<AiChatMessage> messages) {
        long total = estimateTextTokens(summary);
        for (AiChatMessage message : messages) {
            total += 4L + estimateTextTokens(message.content());
        }
        return total;
    }

    private long estimateTextTokens(String text) {
        long ascii = 0;
        long nonAscii = 0;
        String value = normalizeText(text);
        for (int index = 0; index < value.length(); index += 1) {
            char ch = value.charAt(index);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch <= 127) {
                ascii += 1;
            } else {
                nonAscii += 1;
            }
        }
        return Math.max(1, (ascii + 3) / 4 + nonAscii);
    }

    private String extractJson(String rawResponse) {
        String value = normalizeText(rawResponse);
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    private Long extractProductId(String text) {
        Matcher matcher = PRODUCT_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractBase62Id(String text) {
        Matcher matcher = BASE62_ID_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private String cleanupQuery(String text, List<String> removals) {
        String query = normalizeText(text)
                .replace("“", "")
                .replace("”", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("？", "")
                .replace("?", "")
                .replace("。", "")
                .replace(",", "")
                .replace("，", "");
        for (String removal : removals) {
            query = query.replace(removal, "");
        }
        query = query.replaceAll("\\s+", " ").trim();
        return query.length() <= 128 ? query : query.substring(0, 128);
    }

    private boolean containsAny(String value, String... targets) {
        for (String target : targets) {
            if (value.contains(target)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeRole(String rawRole) {
        String role = normalizeText(rawRole).toLowerCase(Locale.ROOT);
        return switch (role) {
            case "assistant" -> "assistant";
            default -> "user";
        };
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String truncate(String value, int maxChars) {
        String text = normalizeText(value);
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new AiGatewayException("AI_SSE_SEND_FAILED", e);
        }
    }

    private void sendErrorAndComplete(SseEmitter emitter, String error, String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", error);
            payload.put("message", message);
            emitter.send(SseEmitter.event().name("error").data(payload));
        } catch (Exception ignored) {
            // The client may have already closed the stream.
        } finally {
            emitter.complete();
        }
    }
}
