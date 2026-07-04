package com.example.ShoppingSystem.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.order.dto.OrderApiResponse;
import com.example.ShoppingSystem.order.dto.PaymentRefundApplyRequest;
import com.example.ShoppingSystem.order.dto.PaymentRefundPageResponse;
import com.example.ShoppingSystem.order.dto.PaymentRefundResponse;
import com.example.ShoppingSystem.order.service.PaymentRefundService;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "用户退款", description = "用户订单退款申请接口")
@RestController
@RequestMapping("/shopping/user/api/orders")
public class OrderRefundController {

    private static final String AUTH_USER_ID_SESSION_ATTRIBUTE = "AUTH_USER_ID";

    private final PaymentRefundService paymentRefundService;

    public OrderRefundController(PaymentRefundService paymentRefundService) {
        this.paymentRefundService = paymentRefundService;
    }

    @Operation(summary = "申请订单退款")
    @PostMapping("/{orderNo}/refunds")
    public OrderApiResponse<PaymentRefundResponse> apply(@PathVariable String orderNo,
                                                         @RequestBody(required = false) PaymentRefundApplyRequest request,
                                                         Authentication authentication,
                                                         HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_REFUND_APPLY_OK", paymentRefundService.applyForUser(userId, orderNo, request));
    }

    @Operation(summary = "分页查询订单退款记录")
    @GetMapping("/{orderNo}/refunds")
    public OrderApiResponse<PaymentRefundPageResponse> page(@PathVariable String orderNo,
                                                            @RequestParam(value = "page", required = false) Integer page,
                                                            @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                            Authentication authentication,
                                                            HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_REFUND_PAGE_OK", paymentRefundService.pageForUserOrder(userId, orderNo, page, pageSize));
    }

    private Long requireCurrentUserId(Authentication authentication, HttpServletRequest request) {
        Long userId = currentUserId(authentication, request);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ORDER_AUTH_REQUIRED");
        }
        return userId;
    }

    private Long currentUserId(Authentication authentication, HttpServletRequest request) {
        AuthUserContext holderContext = AuthUserContextHolder.get();
        if (holderContext != null && holderContext.userId() != null) {
            return holderContext.userId();
        }

        Object requestUserId = request == null ? null : request.getAttribute("authUserId");
        Long parsedRequestUserId = parseUserId(requestUserId);
        if (parsedRequestUserId != null) {
            return parsedRequestUserId;
        }

        Long sessionUserId = resolveSessionUserId(request);
        if (sessionUserId != null) {
            return sessionUserId;
        }

        if (authentication != null && authentication.isAuthenticated()) {
            if (authentication.getPrincipal() instanceof AuthUserContext context && context.userId() != null) {
                return context.userId();
            }
            return parseUserId(authentication.getName());
        }
        return null;
    }

    private Long resolveSessionUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return parseUserId(session.getAttribute(AUTH_USER_ID_SESSION_ATTRIBUTE));
    }

    private Long parseUserId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
