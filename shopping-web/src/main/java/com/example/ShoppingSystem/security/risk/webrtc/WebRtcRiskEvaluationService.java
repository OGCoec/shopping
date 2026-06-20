package com.example.ShoppingSystem.security.risk.webrtc;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.store.PreAuthBindingRepository;
import com.example.ShoppingSystem.security.risk.AccountNetworkRiskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface WebRtcRiskEvaluationService {
    public void evaluateAndWriteBack(WebRtcRiskMessage message);
}
