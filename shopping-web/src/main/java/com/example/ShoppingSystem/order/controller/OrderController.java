package com.example.ShoppingSystem.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import com.example.ShoppingSystem.order.service.OrderPaymentService;
import com.example.ShoppingSystem.order.service.OrderPreviewService;
import com.example.ShoppingSystem.order.service.OrderQueryService;
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

@Tag(name = "用户订单", description = "用户订单创建、查询和支付接口")
@RestController
@RequestMapping("/shopping/user/api/orders")
public class OrderController {

    private static final String AUTH_USER_ID_SESSION_ATTRIBUTE = "AUTH_USER_ID";

    private final OrderPreviewService orderPreviewService;
    private final OrderCreateService orderCreateService;
    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;
    private final OrderPaymentService orderPaymentService;
    private final OrderCardSecretQueryService orderCardSecretQueryService;

    public OrderController(OrderPreviewService orderPreviewService,
                           OrderCreateService orderCreateService,
                           OrderQueryService orderQueryService,
                           OrderCancelService orderCancelService,
                           OrderPaymentService orderPaymentService,
                           OrderCardSecretQueryService orderCardSecretQueryService) {
        this.orderPreviewService = orderPreviewService;
        this.orderCreateService = orderCreateService;
        this.orderQueryService = orderQueryService;
        this.orderCancelService = orderCancelService;
        this.orderPaymentService = orderPaymentService;
        this.orderCardSecretQueryService = orderCardSecretQueryService;
    }

    @Operation(summary = "预览订单")
    @PostMapping("/preview")
    public OrderApiResponse<OrderPreviewResponse> preview(@RequestBody(required = false) OrderPreviewRequest request,
                                                          Authentication authentication,
                                                          HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_PREVIEW_OK", orderPreviewService.preview(userId, request));
    }

    @Operation(summary = "创建订单")
    @PostMapping
    public OrderApiResponse<OrderCreateResponse> create(@RequestBody(required = false) OrderCreateRequest request,
                                                        Authentication authentication,
                                                        HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_CREATE_OK", orderCreateService.create(userId, request));
    }

    @Operation(summary = "查询订单详情")
    @GetMapping("/{orderNo}")
    public OrderApiResponse<OrderDetailResponse> detail(@PathVariable String orderNo,
                                                        Authentication authentication,
                                                        HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_DETAIL_OK", orderQueryService.detail(userId, orderNo));
    }

    @Operation(summary = "查询订单卡密")
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

    @Operation(summary = "分页查询用户订单")
    @GetMapping
    public OrderApiResponse<OrderPageResponse> page(@RequestParam(value = "page", required = false) Integer page,
                                                    @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                    @RequestParam(value = "status", required = false) String status,
                                                    Authentication authentication,
                                                    HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return OrderApiResponse.ok("ORDER_PAGE_OK", orderQueryService.page(userId, page, pageSize, status));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/{orderNo}/cancel")
    public ResponseEntity<OrderApiResponse<OrderCancelResponse>> cancel(@PathVariable String orderNo,
                                                                        @RequestBody(required = false) OrderCancelRequest request,
                                                                        Authentication authentication,
                                                                        HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return ResponseEntity.ok(OrderApiResponse.ok("ORDER_CANCEL_OK", orderCancelService.cancel(userId, orderNo)));
    }

    @Operation(summary = "支付订单")
    @PostMapping("/{orderNo}/pay")
    public ResponseEntity<OrderApiResponse<OrderPaymentResponse>> pay(@PathVariable String orderNo,
                                                                      @RequestBody(required = false) OrderPaymentRequest request,
                                                                      Authentication authentication,
                                                                      HttpServletRequest servletRequest) {
        Long userId = requireCurrentUserId(authentication, servletRequest);
        return ResponseEntity.ok(OrderApiResponse.ok(
                "ORDER_PAY_OK",
                orderPaymentService.pay(userId, orderNo, request)
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

}
