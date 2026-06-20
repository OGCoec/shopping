package com.example.ShoppingSystem.filter.preauth.domain;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.loginflow.LoginFlowCookieFactory;
import com.example.ShoppingSystem.redisdata.LoginRedisKeys;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.registerflow.RegisterFlowCookieFactory;
import com.example.ShoppingSystem.service.user.auth.login.LoginFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengePolicy;
import com.example.ShoppingSystem.service.user.auth.login.LoginChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.register.ChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowSession;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Locale;

public interface PreAuthRiskStateSyncService {
    public void syncAfterBindingSaved(PreAuthBinding previous,
                                      PreAuthBinding current,
                                      HttpServletRequest request);

    public void forceClearDerivedState(PreAuthBinding current, HttpServletRequest request);
}
