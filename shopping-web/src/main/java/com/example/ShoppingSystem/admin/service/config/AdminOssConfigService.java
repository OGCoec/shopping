package com.example.ShoppingSystem.admin.service.config;

import com.example.ShoppingSystem.admin.dto.AdminOAuth2ConfigField;
import com.example.ShoppingSystem.admin.dto.AdminOssConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminOssProviderConfigResponse;
import org.springframework.core.env.ConfigurableEnvironment;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

public interface AdminOssConfigService {
    public AdminOssProviderConfigResponse aliyunConfig();

    public AdminOssProviderConfigResponse updateAliyunConfig(AdminOssConfigUpdateRequest request);
}
