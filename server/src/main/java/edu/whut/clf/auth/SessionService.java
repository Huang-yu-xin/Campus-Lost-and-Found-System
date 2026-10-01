package edu.whut.clf.auth;

import edu.whut.clf.auth.model.Session;
import edu.whut.clf.common.config.AppProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * 服务端会话：登录签发时创建、退出/续期时撤销。使 JWT 可被服务端主动失效（FR-AUTH-04）。
 * sessions 表仅存 token 的 SHA-256 摘要。
 */
@Service
public class SessionService {

    private final SessionMapper sessionMapper;
    private final long accessTtlMinutes;

    public SessionService(SessionMapper sessionMapper, AppProperties props) {
        this.sessionMapper = sessionMapper;
        this.accessTtlMinutes = props.getAuth().getAccessTokenTtlMinutes();
    }

    public void create(Long userId, String rawToken) {
        Session s = new Session();
        s.setUserId(userId);
        s.setTokenHash(hash(rawToken));
        s.setExpiresAt(LocalDateTime.now().plusMinutes(accessTtlMinutes));
        sessionMapper.insert(s);
    }

    public void revoke(String rawToken) {
        if (rawToken == null) {
            return;
        }
        sessionMapper.revokeByHash(hash(rawToken), LocalDateTime.now());
    }

    public boolean isActive(String rawToken) {
        if (rawToken == null) {
            return false;
        }
        return sessionMapper.countActive(hash(rawToken), LocalDateTime.now()) > 0;
    }

    /** D14/P2-13：清理创建超过 30 天且已失效（已撤销或已过期）的会话行。返回删除行数。 */
    public int purgeStaleSessions() {
        LocalDateTime now = LocalDateTime.now();
        return sessionMapper.deleteStale(now.minusDays(30), now);
    }

    static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
