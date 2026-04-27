package com.example.ShoppingSystem.filter.preauth.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * preauth 相关的哈希服务。
 * <p>
 * 当前只提供 SHA-256，用于：
 * 1) 设备指纹 hash；
 * 2) User-Agent hash；
 * 3) WAF 标记 key 中的 IP 脱敏。
 */
@Component
public class PreAuthHashingService {

    /**
     * 计算给定字符串的 SHA-256 十六进制结果。
     * <p>
     * 如果出现极端异常，出于可用性考虑会回退为原始字符串，
     * 避免因为 hash 服务异常直接把请求链路打断。
     */
    public String sha256(String value) {
        try {
            // 用标准 SHA-256 算法把输入字符串转成固定长度摘要。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(StrUtil.blankToDefault(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte item : hashed) {
                // 每个字节转成 2 位十六进制，最终得到可读的字符串形式。
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ignored) {
            // 极端情况下不中断主流程，直接回退为原值。
            return StrUtil.blankToDefault(value, "");
        }
    }
}
