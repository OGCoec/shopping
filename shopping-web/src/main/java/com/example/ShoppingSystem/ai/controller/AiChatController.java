package com.example.ShoppingSystem.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.ai.dto.AiChatStreamRequest;
import com.example.ShoppingSystem.ai.dto.AiCompressionRequest;
import com.example.ShoppingSystem.ai.dto.AiCompressionResponse;
import com.example.ShoppingSystem.ai.dto.AiModelsResponse;
import com.example.ShoppingSystem.ai.service.AiChatService;
import com.example.ShoppingSystem.ai.service.AiContextCompressionService;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "用户AI聊天", description = "用户AI聊天接口")
@RestController
@RequestMapping("/shopping/user/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiContextCompressionService aiContextCompressionService;

    public AiChatController(AiChatService aiChatService,
                            AiContextCompressionService aiContextCompressionService) {
        this.aiChatService = aiChatService;
        this.aiContextCompressionService = aiContextCompressionService;
    }

    @Operation(summary = "查询AI模型列表")
    @GetMapping("/models")
    public AiModelsResponse models() {
        requireUserId();
        return aiChatService.models();
    }

    @Operation(summary = "流式发送AI聊天消息")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AiChatStreamRequest request) {
        return aiChatService.stream(requireUserId(), request);
    }

    @Operation(summary = "压缩AI聊天上下文")
    @PostMapping("/chat/compress")
    public AiCompressionResponse compress(@RequestBody AiCompressionRequest request) {
        requireUserId();
        return aiContextCompressionService.compress(request);
    }

    private Long requireUserId() {
        AuthUserContext context = AuthUserContextHolder.get();
        if (context == null || context.userId() == null || context.userId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AI_LOGIN_REQUIRED");
        }
        return context.userId();
    }
}
