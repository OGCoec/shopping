package com.example.ShoppingSystem.order.controller;

import com.example.ShoppingSystem.order.dto.OrderApiResponse;
import com.example.ShoppingSystem.order.dto.OrderCancelRequest;
import com.example.ShoppingSystem.order.dto.OrderCancelResponse;
import com.example.ShoppingSystem.order.dto.OrderCardSecretResponse;
import com.example.ShoppingSystem.order.dto.OrderCreateRequest;
import com.example.ShoppingSystem.order.dto.OrderCreateResponse;
import com.example.ShoppingSystem.order.dto.OrderDetailResponse;
import com.example.ShoppingSystem.order.dto.OrderPageResponse;
import com.example.ShoppingSystem.order.dto.OrderPaymentRequest;
import com.example.ShoppingSystem.order.dto.OrderPaymentResponse;
import com.example.ShoppingSystem.order.dto.OrderPreviewRequest;
import com.example.ShoppingSystem.order.dto.OrderPreviewResponse;
import com.example.ShoppingSystem.order.service.OrderCancelService;
import com.example.ShoppingSystem.order.service.OrderCardSecretQueryService;
import com.example.ShoppingSystem.order.service.OrderCreateService;
import com.example.ShoppingSystem.order.service.OrderPaymentSuccessService;
import com.example.ShoppingSystem.order.service.OrderPreviewService;
import com.example.ShoppingSystem.order.service.OrderQueryService;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderStatus;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/shopping/user/api/orders")
public class OrderController {

    private static final String AUTH_USER_ID_SESSION_ATTRIBUTE = "AUTH_USER_ID";

    private final OrderPreviewService orderPreviewService;
    private final OrderCreateService orderCreateService;
    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;
    private final OrderPaymentSuccessService orderPaymentSuccessService;
    private final OrderCardSecretQueryService orderCardSecretQueryService;

    public OrderController(OrderPreviewService orderPreviewService,
                           OrderCreateService orderCreateService,
                           OrderQueryService orderQueryService,
                           OrderCancelService orderCancelService,
                           OrderPaymentSuccessService orderPaymentSuccessService,
                           OrderCardSecretQueryService orderCardSecretQueryService) {
        this.orderPreviewService = orderPreviewService;
        this.orderCreateService = orderCreateService;
        this.orderQueryService = orderQueryService;
        this.orderCancelService = orderCancelService;
        this.orderPaymentSuccessService = orderPaymentSuccessService;
        this.orderCardSecretQueryService = orderCardSecretQueryService;
    }

    @PostMapping("/preview")
    public OrderApiResponse<OrderPreviewResponse> preview(@RequestBody(required = false) OrderPreviewRequest request,
                                                          Authentication authentication,
                                                          HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_PREVIEW_OK", orderPreviewService.preview(userId, request));
    }

    @PostMapping
    public OrderApiResponse<OrderCreateResponse> create(@RequestBody(required = false) OrderCreateRequest request,
                                                        Authentication authentication,
                                                        HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_CREATE_OK", orderCreateService.create(userId, request));
    }

    @GetMapping("/{orderNo}")
    public OrderApiResponse<OrderDetailResponse> detail(@PathVariable String orderNo,
                                                        Authentication authentication,
                                                        HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_DETAIL_OK", orderQueryService.detail(userId, orderNo));
    }

    @GetMapping("/{orderNo}/card-secrets")
    public ResponseEntity<OrderApiResponse<OrderCardSecretResponse>> cardSecrets(@PathVariable String orderNo,
                                                                                 Authentication authentication,
                                                                                 HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        OrderCardSecretResponse response = orderCardSecretQueryService.getForUser(userId, orderNo);
        String code = OrderCardSecretQueryService.DELIVERY_STATUS_DELIVERED.equals(response.deliveryStatus())
                ? "ORDER_CARD_SECRET_LIST_OK"
                : "ORDER_CARD_SECRET_DELIVERY_PENDING";
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(OrderApiResponse.ok(code, response));
    }

    @GetMapping
    public OrderApiResponse<OrderPageResponse> page(@RequestParam(value = "page", required = false) Integer page,
                                                    @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                    @RequestParam(value = "status", required = false) String status,
                                                    Authentication authentication,
                                                    HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_PAGE_OK", orderQueryService.page(userId, page, pageSize, status));
    }

    @PostMapping("/{orderNo}/cancel")
    public ResponseEntity<OrderApiResponse<OrderCancelResponse>> cancel(@PathVariable String orderNo,
                                                                        @RequestBody(required = false) OrderCancelRequest request,
                                                                        Authentication authentication,
                                                                        HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return ResponseEntity.ok(OrderApiResponse.ok("ORDER_CANCEL_OK", orderCancelService.cancel(userId, orderNo)));
    }

    @PostMapping("/{orderNo}/pay")
    public ResponseEntity<OrderApiResponse<OrderPaymentResponse>> pay(@PathVariable String orderNo,
                                                                      @RequestBody(required = false) OrderPaymentRequest request,
                                                                      Authentication authentication,
                                                                      HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        OffsetDateTime paidAt = OffsetDateTime.now();
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        String externalTradeNo = externalTradeNo(request == null ? null : request.externalTradeNo(), normalizedOrderNo);
        boolean paid = orderPaymentSuccessService.markPendingPaidForUser(
                userId,
                normalizedOrderNo,
                paidAt,
                externalTradeNo
        );
        if (!paid) {
            throw new OrderServiceException("ORDER_PAY_UNAVAILABLE", "Only pending current-user orders can be paid.", HttpStatus.CONFLICT);
        }
        return ResponseEntity.ok(OrderApiResponse.ok(
                "ORDER_PAY_OK",
                new OrderPaymentResponse(normalizedOrderNo, OrderStatus.PAID, paidAt, externalTradeNo)
        ));
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

    private String normalizeOrderNo(String orderNo) {
        String value = orderNo == null ? "" : orderNo.trim();
        if (value.isEmpty() || value.length() > 64) {
            throw new OrderServiceException("ORDER_NO_INVALID", "Order number is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String externalTradeNo(String rawExternalTradeNo, String orderNo) {
        String value = rawExternalTradeNo == null ? "" : rawExternalTradeNo.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return "MOCKPAY-" + orderNo + "-" + UUID.randomUUID();
    }
}
