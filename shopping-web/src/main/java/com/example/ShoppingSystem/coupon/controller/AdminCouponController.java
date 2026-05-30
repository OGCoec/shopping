package com.example.ShoppingSystem.coupon.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
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

@RestController
@RequestMapping("/shopping/admin/api/coupons/templates")
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    public AdminCouponController(AdminCouponService adminCouponService) {
        this.adminCouponService = adminCouponService;
    }

    @GetMapping
    public AdminApiResponse<AdminCouponTemplatePageResponse> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) String status) {
        return AdminApiResponse.ok(adminCouponService.page(page, pageSize, name, status));
    }

    @GetMapping("/{id}")
    public AdminApiResponse<AdminCouponTemplateResponse> detail(@PathVariable String id) {
        return AdminApiResponse.ok(adminCouponService.detail(id));
    }

    @PostMapping
    public AdminApiResponse<AdminCouponTemplateResponse> create(@RequestBody AdminCouponTemplateRequest request) {
        return AdminApiResponse.ok(adminCouponService.create(request));
    }

    @PutMapping("/{id}")
    public AdminApiResponse<AdminCouponTemplateResponse> update(@PathVariable String id,
                                                                @RequestBody AdminCouponTemplateRequest request) {
        return AdminApiResponse.ok(adminCouponService.update(id, request));
    }

    @PatchMapping("/{id}/publish")
    public AdminApiResponse<AdminCouponTemplateResponse> publish(@PathVariable String id) {
        return AdminApiResponse.ok(adminCouponService.publish(id));
    }

    @PatchMapping("/{id}/disable")
    public AdminApiResponse<AdminCouponTemplateResponse> disable(@PathVariable String id) {
        return AdminApiResponse.ok(adminCouponService.disable(id));
    }

    @DeleteMapping("/{id}")
    public AdminApiResponse<Void> delete(@PathVariable String id) {
        adminCouponService.softDelete(id);
        return AdminApiResponse.ok(null);
    }
}
