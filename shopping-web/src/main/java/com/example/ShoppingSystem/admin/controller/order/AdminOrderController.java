package com.example.ShoppingSystem.admin.controller.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderPageResponse;
import com.example.ShoppingSystem.order.service.AdminOrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台订单管理", description = "后台订单查询接口")
@RestController
@RequestMapping("/shopping/admin/api/orders")
public class AdminOrderController {

    private final AdminOrderQueryService adminOrderQueryService;

    public AdminOrderController(AdminOrderQueryService adminOrderQueryService) {
        this.adminOrderQueryService = adminOrderQueryService;
    }

    @Operation(summary = "分页查询后台订单")
    @GetMapping
    public AdminApiResponse<AdminOrderPageResponse> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "orderNo", required = false) String orderNo) {
        return AdminApiResponse.ok(adminOrderQueryService.page(page, pageSize, status, orderNo));
    }

    @Operation(summary = "查询后台订单详情")
    @GetMapping("/{orderNo}")
    public AdminApiResponse<AdminOrderDetailResponse> detail(@PathVariable String orderNo) {
        return AdminApiResponse.ok(adminOrderQueryService.detail(orderNo));
    }
}
