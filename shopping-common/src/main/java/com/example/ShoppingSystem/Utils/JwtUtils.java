package com.example.ShoppingSystem.Utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * JWT 工具类：生成和解析令牌
 */
@Component
public class JwtUtils {

    private static final String SECRET_BASE64 = "orderfooddeliverysystemsupersecretkey2026DAMNitdamnIT"; // Base64 编码的密钥，原来是damn
    private static final SecretKey SECRET_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_BASE64));

    /**
     * 生成 JWT 令牌（简单版本，只有 subject）
     */
    @Async("jwtTaskExecutor")
    public CompletableFuture<String> generateToken(String subject, long expirationSeconds) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiry = new Date(nowMillis + expirationSeconds * 1000);

        String token = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(SECRET_KEY, Jwts.SIG.HS256)
                .compact();
        return CompletableFuture.completedFuture(token);
    }

    /**
     * 生成 JWT 令牌（携带自定义负载）
     *
     * @param claimsMap         自定义负载（例如 userId、role 等）
     * @param expirationSeconds 过期时间（秒）
     * @return 生成的 JWT 字符串
     */
    @Async("jwtTaskExecutor")
    public CompletableFuture<String> generateToken(Map<String, Object> claimsMap, long expirationSeconds) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiry = new Date(nowMillis + expirationSeconds * 1000);

        String token = Jwts.builder()
                .claims(claimsMap)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(SECRET_KEY, Jwts.SIG.HS256)
                .compact();
        return CompletableFuture.completedFuture(token);
    }

    /**
     * 解析 JWT 令牌并返回 Claims
     *
     * @param token JWT 字符串
     * @return Claims（包含 subject、过期时间等）
     */
    @Async("jwtTaskExecutor")
    public CompletableFuture<Claims> parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return CompletableFuture.completedFuture(claims);
    }
}

