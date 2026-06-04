package com.example.ShoppingSystem.coupon.controller;

import com.example.ShoppingSystem.coupon.dto.CouponClaimResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponMineDetailResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponMinePageResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplateDetailResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplatePageResponse;
import com.example.ShoppingSystem.coupon.service.CouponClaimService;
import com.example.ShoppingSystem.coupon.service.UserCouponQueryService;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/shopping/user/api/coupons")
public class UserCouponController {

    private static final String AUTH_USER_ID_SESSION_ATTRIBUTE = "AUTH_USER_ID";

    private final CouponClaimService couponClaimService;
    private final UserCouponQueryService userCouponQueryService;

    public UserCouponController(CouponClaimService couponClaimService,
                                UserCouponQueryService userCouponQueryService) {
        this.couponClaimService = couponClaimService;
        this.userCouponQueryService = userCouponQueryService;
    }

    @GetMapping
    public UserCouponTemplatePageResponse page(@RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                               @RequestParam(value = "name", required = false) String name,
                                               Authentication authentication,
                                               HttpServletRequest request) {
        return userCouponQueryService.receivablePage(requireCurrentUserId(authentication, request), page, pageSize, name);
    }

    @GetMapping("/mine")
    public UserCouponMinePageResponse mine(@RequestParam(value = "page", required = false) Integer page,
                                           @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                           @RequestParam(value = "status", required = false) String status,
                                           Authentication authentication,
                                           HttpServletRequest request) {
        return userCouponQueryService.minePage(requireCurrentUserId(authentication, request), page, pageSize, status);
    }

    @GetMapping("/mine/{userCouponId}")
    public UserCouponMineDetailResponse mineDetail(@PathVariable String userCouponId,
                                                   Authentication authentication,
                                                   HttpServletRequest request) {
        return userCouponQueryService.mineDetail(requireCurrentUserId(authentication, request), userCouponId);
    }

    @GetMapping("/{couponTemplateId}")
    public UserCouponTemplateDetailResponse detail(@PathVariable String couponTemplateId,
                                                   Authentication authentication,
                                                   HttpServletRequest request) {
        return userCouponQueryService.receivableDetail(requireCurrentUserId(authentication, request), couponTemplateId);
    }

    @PostMapping("/{couponTemplateId}/claim")
    public ResponseEntity<CouponClaimResponse> claim(@PathVariable String couponTemplateId,
                                                     Authentication authentication,
                                                     HttpServletRequest request) {
        Long userId = currentUserId(authentication, request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CouponClaimResponse.fail("COUPON_AUTH_REQUIRED", "Current user is not authenticated."));
        }
        CouponClaimResponse response = couponClaimService.claim(couponTemplateId, userId);
        return ResponseEntity.status(status(response)).body(response);
    }

    private Long requireCurrentUserId(Authentication authentication, HttpServletRequest request) {
        Long userId = currentUserId(authentication, request);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "COUPON_AUTH_REQUIRED");
        }
        return userId;
    }

    private HttpStatus status(CouponClaimResponse response) {
        if (response.success()) {
            return HttpStatus.OK;
        }
        return switch (response.code()) {
            case "COUPON_CACHE_REBUILDING", "COUPON_CLAIM_MQ_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "COUPON_NOT_ACTIVE", "COUPON_ALREADY_CLAIMED", "COUPON_SOLD_OUT" -> HttpStatus.CONFLICT;
            case "COUPON_ID_INVALID" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
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
