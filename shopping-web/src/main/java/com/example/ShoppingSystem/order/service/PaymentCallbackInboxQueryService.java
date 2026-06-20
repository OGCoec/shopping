package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.PaymentCallbackInboxMapper;
import com.example.ShoppingSystem.order.dto.PaymentCallbackInboxPageResponse;
import com.example.ShoppingSystem.order.dto.PaymentCallbackInboxResponse;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;

public interface PaymentCallbackInboxQueryService {
    public PaymentCallbackInboxPageResponse page(Integer rawPage,
                                                 Integer rawPageSize,
                                                 String rawStatus,
                                                 String rawOrderNo,
                                                 String rawCallbackNo,
                                                 String rawExternalTradeNo,
                                                 String rawResultOutcome);

    public PaymentCallbackInboxResponse detail(String callbackNo);
}
