package edu.whut.clf.common.security;

import edu.whut.clf.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 签发/校验访问令牌（HS256）。密钥来自配置，不硬编码。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMinutes;

    public JwtService(AppProperties props) {
        byte[] secret = props.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥至少 256bit；不足时用 sha 派生保证长度
        this.key = Keys.hmacShaKeyFor(padTo32(secret));
        this.accessTtlMinutes = props.getAuth().getAccessTokenTtlMinutes();
    }

    public String issueAccessToken(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** 解析并校验令牌；失败返回 null（调用方转 401）。 */
    public Principal parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            return new Principal(userId, role);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] padTo32(byte[] input) {
        if (input.length == 0) {
            // 空密钥不可用（生产由 StartupSecurityValidator 给出更明确的拒绝信息）
            throw new IllegalStateException("JWT 密钥不能为空：请配置 JWT_SECRET");
        }
        if (input.length >= 32) {
            return input;
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            out[i] = input[i % input.length];
        }
        return out;
    }
}
