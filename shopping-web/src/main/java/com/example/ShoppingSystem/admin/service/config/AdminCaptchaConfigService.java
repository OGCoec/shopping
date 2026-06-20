package com.example.ShoppingSystem.admin.service.config;

import com.example.ShoppingSystem.admin.config.AdminOAuth2WindowsEnvPostProcessor;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaConfigField;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaProviderConfigResponse;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

public interface AdminCaptchaConfigService {
    public AdminCaptchaProviderConfigResponse turnstileConfig();

    public AdminCaptchaProviderConfigResponse hcaptchaConfig();

    public AdminCaptchaProviderConfigResponse recaptchaConfig();

    public AdminCaptchaProviderConfigResponse updateConfig(String provider,
                                                           AdminCaptchaConfigUpdateRequest request);
}
