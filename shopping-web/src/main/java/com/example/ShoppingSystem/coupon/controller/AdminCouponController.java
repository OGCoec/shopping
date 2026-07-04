package com.example.ShoppingSystem.coupon.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.coupon.dto.AdminCouponClaimPageResponse;
import com.example.ShoppingSystem.coupon.dto.AdminCouponTemplatePageResponse;
import com.example.ShoppingSystem.coupon.dto.AdminCouponTemplateRequest;
import com.example.ShoppingSystem.coupon.dto.AdminCouponTemplateResponse;
import com.example.ShoppingSystem.coupon.service.AdminCouponService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台优惠券管理", description = "后台优惠券模板管理接口")
@RestController
@RequestMapping("/shopping/admin/api/coupons/templates")
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    public AdminCouponController(AdminCouponService adminCouponService) {
        this.adminCouponService = adminCouponService;
    }

    @Operation(summary = "分页查询优惠券模板")
    @GetMapping
    public AdminApiResponse<AdminCouponTemplatePageResponse> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "receiveStartAtFrom", required = false) String receiveStartAtFrom,
            @RequestParam(value = "receiveEndAtTo", required = false) String receiveEndAtTo) {
        return AdminApiResponse.ok(adminCouponService.page(
                page,
                pageSize,
                name,
                status,
                receiveStartAtFrom,
                receiveEndAtTo));
    }

    @Operation(summary = "查询优惠券模板详情")
    @GetMapping("/{id}")
    public AdminApiResponse<AdminCouponTemplateResponse> detail(@PathVariable String id) {
        return AdminApiResponse.ok(adminCouponService.detail(id));
    }

    @Operation(summary = "查询优惠券领取记录")
    @GetMapping("/{id}/claims")
    public AdminApiResponse<AdminCouponClaimPageResponse> claims(@PathVariable String id,
                                                                 @RequestParam(value = "page", required = false) Integer page,
                                                                 @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                                 @RequestParam(value = "status", required = false) String status,
                                                                 @RequestParam(value = "email", required = false) String email) {
        return AdminApiResponse.ok(adminCouponService.claimPage(id, page, pageSize, status, email));
    }

    @Operation(summary = "创建优惠券模板")
    @PostMapping
    public AdminApiResponse<AdminCouponTemplateResponse> create(@RequestBody AdminCouponTemplateRequest request) {
        return AdminApiResponse.ok(adminCouponService.create(request));
    }

    @Operation(summary = "更新优惠券模板")
    @PutMapping("/{id}")
    public AdminApiResponse<AdminCouponTemplateResponse> update(@PathVariable String id,
                                                                @RequestBody AdminCouponTemplateRequest request) {
        return AdminApiResponse.ok(adminCouponService.update(id, request));
    }

    @Operation(summary = "发布优惠券模板")
    @PatchMapping("/{id}/publish")
    public AdminApiResponse<AdminCouponTemplateResponse> publish(@PathVariable String id) {
        return AdminApiResponse.ok(adminCouponService.publish(id));
    }

    @Operation(summary = "禁用优惠券模板")
    @PatchMapping("/{id}/disable")
    public AdminApiResponse<AdminCouponTemplateResponse> disable(@PathVariable String id) {
        return AdminApiResponse.ok(adminCouponService.disable(id));
    }

    @Operation(summary = "删除优惠券模板")
    @DeleteMapping("/{id}")
    public AdminApiResponse<Void> delete(@PathVariable String id) {
        adminCouponService.softDelete(id);
        return AdminApiResponse.ok(null);
    }
}
