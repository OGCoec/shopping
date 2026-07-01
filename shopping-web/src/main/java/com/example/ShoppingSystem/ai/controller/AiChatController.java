package com.example.ShoppingSystem.ai.controller;

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

    @GetMapping("/models")
    public AiModelsResponse models() {
        requireUserId();
        return aiChatService.models();
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AiChatStreamRequest request) {
        return aiChatService.stream(requireUserId(), request);
    }

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
