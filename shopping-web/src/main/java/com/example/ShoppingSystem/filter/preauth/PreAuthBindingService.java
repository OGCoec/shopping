package com.example.ShoppingSystem.filter.preauth;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthBindingFactory;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthRiskService;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBootstrapOutcome;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthRiskProfile;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthSnapshot;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationError;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationOutcome;
import com.example.ShoppingSystem.filter.preauth.store.PreAuthBindingRepository;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthCookieFactory;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthHashingService;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * preauth 模块的流程编排服务。
 * <p>
 * 这个类是整个预登录绑定机制的主入口，负责把下列子能力串起来：
 * 1) 请求上下文解析；
 * 2) 指纹 / UA hash；
 * 3) 绑定对象的 Redis 读写；
 * 4) 风险等级计算；
 * 5) 对外返回给 controller / filter 的统一结果对象。
 * <p>
 * 它本身不直接处理 Redis 字段细节，也不直接处理 Cookie 拼装细节，
 * 而是专注于“流程应该怎么走”。
 */
@Service
public class PreAuthBindingService {

    private static final Logger log = LoggerFactory.getLogger(PreAuthBindingService.class);

    /** preauth 模块的配置集合。 */
    private final PreAuthProperties properties;
    /** 请求解析器，用于读取 token、指纹、UA、真实 IP 等信息。 */
    private final PreAuthRequestResolver requestResolver;
    /** Cookie 构建器，用于统一生成 token / WAF 相关 Cookie。 */
    private final PreAuthCookieFactory cookieFactory;
    /** 哈希服务，用于对指纹、UA、IP 进行 SHA-256 处理。 */
    private final PreAuthHashingService hashingService;
    /** 绑定对象仓库，负责和 Redis Hash 交互。 */
    private final PreAuthBindingRepository bindingRepository;
    /** 风险服务，负责把 IP 风险分转换为风险等级。 */
    private final PreAuthRiskService riskService;
    /** 绑定对象工厂，负责创建和刷新绑定对象。 */
    private final PreAuthBindingFactory bindingFactory;

    /**
     * 注入 preauth 流程所需的全部依赖。
     */
    public PreAuthBindingService(PreAuthProperties properties,
                                 PreAuthRequestResolver requestResolver,
                                 PreAuthCookieFactory cookieFactory,
                                 PreAuthHashingService hashingService,
                                 PreAuthBindingRepository bindingRepository,
                                 PreAuthRiskService riskService,
                                 PreAuthBindingFactory bindingFactory) {
        this.properties = properties;
        this.requestResolver = requestResolver;
        this.cookieFactory = cookieFactory;
        this.hashingService = hashingService;
        this.bindingRepository = bindingRepository;
        this.riskService = riskService;
        this.bindingFactory = bindingFactory;
    }

    /**
     * bootstrap 入口：初始化或续期预登录绑定。
     * <p>
     * 主要分三种路径：
     * 1) 功能关闭：仅返回风险快照，不写 Redis；
     * 2) 旧 token 仍然可复用：续期并刷新绑定；
     * 3) 旧 token 不可复用：创建一个全新的绑定。
     */
    public PreAuthBootstrapOutcome bootstrap(String incomingToken,
                                             String rawFingerprint,
                                             HttpServletRequest request) {
        // 先把当前请求环境标准化，得到本次请求对应的指纹 hash、UA hash 和真实 IP。
        String normalizedFingerprint = requestResolver.normalizeFingerprint(rawFingerprint, request);
        String fpHash = hashingService.sha256(normalizedFingerprint);
        String uaHash = hashingService.sha256(requestResolver.resolveUserAgent(request));
        String ip = requestResolver.resolveClientIp(request);

        if (!properties.isEnabled()) {
            // 功能关闭时不建立绑定关系，只给前端返回一个按当前 IP 计算出来的风险快照。
            PreAuthRiskProfile fallbackRisk = riskService.resolveRiskProfile(ip);
            long expiresAt = System.currentTimeMillis() + bindingFactory.bindingTtl().toMillis();
            return PreAuthBootstrapOutcome.allowed(new PreAuthSnapshot(
                    "",
                    fallbackRisk.riskLevel(),
                    riskService.isChallengeRequired(fallbackRisk.riskLevel()),
                    riskService.isBlockedRisk(fallbackRisk.riskLevel()),
                    expiresAt
            ));
        }

        if (StrUtil.isNotBlank(incomingToken)) {
            // 如果请求已经带了 token，优先尝试复用旧绑定。
            PreAuthBinding existing = bindingRepository.load(incomingToken.trim());
            if (existing != null && fpHash.equals(existing.fpHash()) && uaHash.equals(existing.uaHash())) {
                // 指纹和 UA 都一致时，说明还是同一个浏览器环境；接下来只需要判断 IP 是否漂移。
                boolean ipChanged = !StrUtil.equals(existing.currentIp(), ip);
                if (ipChanged) {
                    logPreAuthDecision(
                            "bootstrap_ip_changed_waf_required",
                            incomingToken,
                            request,
                            existing.currentIp(),
                            ip,
                            true,
                            true
                    );
                    // 当前设计不再保留“WAF 已验证豁免票据”。
                    // 只要绑定里的 currentIp 和当前真实 IP 不一致，就固定要求重新经过 WAF 回调。
                    return PreAuthBootstrapOutcome.blocked(PreAuthValidationError.IP_CHANGED_WAF_REQUIRED);
                }

                // 复用旧绑定时刷新上下文：续期、更新 lastSeen。
                PreAuthBinding refreshed = bindingFactory.refreshExistingBinding(existing, ip);
                bindingRepository.save(refreshed);
                return PreAuthBootstrapOutcome.allowed(toSnapshot(refreshed));
            }
        }

        // 走到这里说明旧上下文不可复用，需要重新创建绑定。
        PreAuthBinding created = bindingFactory.createNewBinding(IdUtil.nanoId(48), fpHash, uaHash, ip);
        bindingRepository.save(created);
        return PreAuthBootstrapOutcome.allowed(toSnapshot(created));
    }

    /**
     * 受保护请求的主校验入口。
     * <p>
     * 校验顺序：
     * 1) token 是否存在；
     * 2) Redis 中是否仍有绑定；
     * 3) 指纹是否匹配；
     * 4) UA 是否匹配；
     * 5) IP 是否变化；只要变化，就要求先走一次 WAF 回调把 binding 刷到新 IP。
     */
    public PreAuthValidationOutcome validateAndTouch(String token,
                                                     String rawFingerprint,
                                                     HttpServletRequest request) {
        if (StrUtil.isBlank(token) || !properties.isEnabled()) {
            // token 缺失或功能未启用时，不允许把请求当作有效 preauth 请求继续处理。
            return PreAuthValidationOutcome.invalid(PreAuthValidationError.MISSING_TOKEN);
        }

        // 先读取当前 token 对应的绑定对象。
        PreAuthBinding existing = bindingRepository.load(token.trim());
        if (existing == null) {
            logPreAuthDecision(
                    "validate_expired",
                    token,
                    request,
                    null,
                    requestResolver.resolveClientIp(request),
                    null,
                    null
            );
            // Redis 中没有绑定，说明 token 已过期或已被清理。
            return PreAuthValidationOutcome.invalid(PreAuthValidationError.EXPIRED);
        }

        // 重新计算本次请求的指纹 hash，并与绑定中的值比较。
        String normalizedFingerprint = requestResolver.normalizeFingerprint(rawFingerprint, request);
        String fpHash = hashingService.sha256(normalizedFingerprint);
        if (!fpHash.equals(existing.fpHash())) {
            logPreAuthDecision(
                    "validate_fingerprint_mismatch",
                    token,
                    request,
                    existing.currentIp(),
                    requestResolver.resolveClientIp(request),
                    false,
                    null
            );
            // 指纹不匹配通常说明 token 被换到了别的浏览器环境中，直接删掉旧绑定。
            bindingRepository.delete(existing.token());
            return PreAuthValidationOutcome.invalid(PreAuthValidationError.FINGERPRINT_MISMATCH);
        }

        // 再对比 UA，进一步确认是不是同一浏览器环境。
        String uaHash = hashingService.sha256(requestResolver.resolveUserAgent(request));
        if (!uaHash.equals(existing.uaHash())) {
            logPreAuthDecision(
                    "validate_user_agent_mismatch",
                    token,
                    request,
                    existing.currentIp(),
                    requestResolver.resolveClientIp(request),
                    true,
                    false
            );
            // UA 不匹配时同样清掉旧绑定，避免脏状态残留。
            bindingRepository.delete(existing.token());
            return PreAuthValidationOutcome.invalid(PreAuthValidationError.USER_AGENT_MISMATCH);
        }

        // 最后检查真实 IP 是否和绑定中的 currentIp 一致。
        String currentIp = requestResolver.resolveClientIp(request);
        boolean ipChanged = !StrUtil.equals(existing.currentIp(), currentIp);
        if (ipChanged) {
            logPreAuthDecision(
                    "validate_ip_changed_waf_required",
                    token,
                    request,
                    existing.currentIp(),
                    currentIp,
                    true,
                    true
            );
            // 当前设计不再缓存“WAF 已验证”短期豁免状态。
            // 因此只要 currentIp 发生变化，就固定要求重新进入 WAF 回调。
            return PreAuthValidationOutcome.invalid(PreAuthValidationError.IP_CHANGED_WAF_REQUIRED);
        }

        // 校验通过后刷新绑定并续期，确保活跃上下文保持最新。
        PreAuthBinding updated = bindingFactory.refreshExistingBinding(existing, currentIp);
        bindingRepository.save(updated);
        return PreAuthValidationOutcome.valid(updated);
    }

    /**
     * 返回 preauth 是否启用。
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 对外暴露 token 解析入口，供 controller / filter 复用。
     */
    public String resolveIncomingToken(HttpServletRequest request) {
        return requestResolver.resolveIncomingToken(request);
    }

    public String resolveClientIp(HttpServletRequest request) {
        return requestResolver.resolveClientIp(request);
    }

    /**
     * 对外暴露正常 token Cookie 构建入口。
     */
    public ResponseCookie buildTokenCookie(String token, HttpServletRequest request) {
        return cookieFactory.buildTokenCookie(token, request);
    }

    /**
     * 对外暴露“清理 token”的过期 Cookie 构建入口。
     */
    public ResponseCookie buildExpiredTokenCookie(HttpServletRequest request) {
        return cookieFactory.buildExpiredTokenCookie(request);
    }

    /**
     * 对外暴露“需要先走 WAF”的提示 Cookie 构建入口。
     */
    public ResponseCookie buildWafRequiredCookie(HttpServletRequest request) {
        return cookieFactory.buildWafRequiredCookie(request);
    }

    /**
     * 对外暴露“清理 WAF_REQUIRED”的 Cookie 构建入口。
     */
    public ResponseCookie buildClearWafRequiredCookie(HttpServletRequest request) {
        return cookieFactory.buildClearWafRequiredCookie(request);
    }

    /**
     * WAF 通过后的统一处理入口。
     * <p>
     * 只有真正经过 WAF 回调后，才允许把绑定对象同步到当前真实 IP。
     * 这样可以保证：每次 currentIp 被改坏后，后续请求都会再次先走 WAF。
     */
    public void refreshBindingForCurrentIpAfterWaf(String token, HttpServletRequest request) {
        if (StrUtil.isBlank(token) || request == null) {
            return;
        }
        String normalizedToken = token.trim();
        String currentIp = requestResolver.resolveClientIp(request);

        // 如果绑定对象仍然存在，就立刻同步 currentIp / 风险分 / 风险等级。
        PreAuthBinding existing = bindingRepository.load(normalizedToken);
        if (existing == null) {
            return;
        }
        PreAuthBinding refreshed = bindingFactory.refreshExistingBinding(existing, currentIp);
        bindingRepository.save(refreshed);
    }

    /**
     * 对外暴露“是否直接阻断”的风险判断。
     */
    public boolean isBlockedRisk(String riskLevel) {
        return riskService.isBlockedRisk(riskLevel);
    }

    /**
     * 对外暴露“是否需要额外挑战”的风险判断。
     */
    public boolean isChallengeRequired(String riskLevel) {
        return riskService.isChallengeRequired(riskLevel);
    }

    /**
     * 把内部完整绑定对象压缩为给前端使用的轻量快照。
     */
    private PreAuthSnapshot toSnapshot(PreAuthBinding binding) {
        return new PreAuthSnapshot(
                binding.token(),
                binding.riskLevel(),
                riskService.isChallengeRequired(binding.riskLevel()),
                riskService.isBlockedRisk(binding.riskLevel()),
                binding.expiresAtEpochMillis()
        );
    }

    private void logPreAuthDecision(String stage,
                                    String token,
                                    HttpServletRequest request,
                                    String bindingIp,
                                    String currentIp,
                                    Boolean fpMatches,
                                    Boolean uaMatches) {
        log.warn("PreAuth decision: stage={}, tokenId={}, uri={}, bindingIp={}, currentIp={}, fpMatches={}, uaMatches={}, xForwardedFor={}, xRealIp={}, remoteAddr={}",
                stage,
                shortToken(token),
                requestUri(request),
                StrUtil.blankToDefault(bindingIp, "none"),
                StrUtil.blankToDefault(currentIp, "none"),
                fpMatches,
                uaMatches,
                header(request, "X-Forwarded-For"),
                header(request, "X-Real-IP"),
                remoteAddr(request));
    }

    private String requestUri(HttpServletRequest request) {
        return request == null ? "unknown" : StrUtil.blankToDefault(request.getRequestURI(), "unknown");
    }

    private String remoteAddr(HttpServletRequest request) {
        return request == null ? "unknown" : StrUtil.blankToDefault(request.getRemoteAddr(), "unknown");
    }

    private String header(HttpServletRequest request, String name) {
        if (request == null) {
            return "none";
        }
        String value = request.getHeader(name);
        if (StrUtil.isBlank(value)) {
            return "none";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 180) + "...";
    }

    private String shortToken(String token) {
        if (StrUtil.isBlank(token)) {
            return "none";
        }
        String normalized = token.trim();
        int length = normalized.length();
        String tail = normalized.substring(Math.max(0, length - 8));
        return "len=" + length + ",tail=" + tail;
    }
}
