package com.smartwms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（jjwt 0.12.x，HS256）。
 * 载荷口径（《系统安全架构设计》第 2 节）：
 * access —— sub=userId、username、clientType、iat、exp、jti；
 * refresh —— sub=userId、type=refresh、iat、exp、jti。
 * 密钥经环境变量注入，长度 ≥ 32 字节（启动时校验）。
 */
@Component
public class JwtUtil {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_CLIENT_TYPE = "clientType";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLIENT_WEB = "WEB";

    private final SecretKey key;
    private final long accessExpireSeconds;
    private final long refreshExpireSeconds;

    public JwtUtil(@Value("${smartwms.jwt.secret}") String secret,
                   @Value("${smartwms.jwt.access-token-expire-seconds}") long accessExpireSeconds,
                   @Value("${smartwms.jwt.refresh-token-expire-seconds}") long refreshExpireSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 密钥长度必须 ≥ 32 字节（环境变量 JWT_SECRET）");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpireSeconds = accessExpireSeconds;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    /** 签发访问令牌（2 小时）。 */
    public String generateAccessToken(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_CLIENT_TYPE, CLIENT_WEB)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpireSeconds * 1000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 签发刷新令牌（7 天）。 */
    public String generateRefreshToken(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpireSeconds * 1000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析访问令牌：签名/过期非法抛 JwtException；refresh 令牌冒用亦视为非法。 */
    public LoginUser parseAccessToken(String token) {
        Claims claims = parseAndVerify(token);
        if (TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("refresh token 不能作为 access token 使用");
        }
        return new LoginUser(Long.valueOf(claims.getSubject()), claims.get(CLAIM_USERNAME, String.class));
    }

    /** 解析刷新令牌：必须为 type=refresh，返回用户 ID；非法/过期抛 JwtException。 */
    public Long parseRefreshToken(String token) {
        Claims claims = parseAndVerify(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("非法令牌类型");
        }
        return Long.valueOf(claims.getSubject());
    }

    /** access token 有效期（秒），用于登录/刷新响应的 expiresIn。 */
    public long getAccessExpireSeconds() {
        return accessExpireSeconds;
    }

    private Claims parseAndVerify(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
