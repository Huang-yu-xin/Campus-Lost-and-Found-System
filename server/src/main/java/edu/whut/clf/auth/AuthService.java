package edu.whut.clf.auth;

import edu.whut.clf.auth.dto.AuthDtos.*;
import edu.whut.clf.auth.model.AdminCredential;
import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.security.JwtService;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AuthService {

    private static final String PROVIDER_WECHAT = "WECHAT";
    private static final String PROVIDER_MOCK = "MOCK";

    // D5(P2-5)：管理员登录内存防爆破——同 username 失败 5 次锁 15 分钟
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MILLIS = 15 * 60 * 1000L;

    private final AppProperties props;
    private final JwtService jwtService;
    private final WechatClient wechatClient;
    private final AdminCredentialMapper adminCredentialMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuthProvisioningService provisioningService;

    private final ConcurrentHashMap<String, LoginAttempt> adminAttempts = new ConcurrentHashMap<>();

    public AuthService(AppProperties props, JwtService jwtService, WechatClient wechatClient,
                       AdminCredentialMapper adminCredentialMapper,
                       UserMapper userMapper, PasswordEncoder passwordEncoder, SessionService sessionService,
                       AuthProvisioningService provisioningService) {
        this.props = props;
        this.jwtService = jwtService;
        this.wechatClient = wechatClient;
        this.adminCredentialMapper = adminCredentialMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.provisioningService = provisioningService;
    }

    // D3(P2-3)：不加 @Transactional——微信 code2session 外呼在事务外执行，不占 DB 连接；
    // 用户开通的原子性由 provisioningService 的独立事务保证。
    public LoginResponse wechatLogin(WechatLoginRequest req) {
        String openid = wechatClient.code2session(req.code());
        User user = provisioningService.findOrCreateUser(PROVIDER_WECHAT, openid, defaultNickname(req.nickname(), "微信用户"));
        return issue(user);
    }

    public LoginResponse mockLogin(MockLoginRequest req) {
        if (!props.getMockLogin().isEnabled()) {
            throw BusinessException.of(ErrorCode.MOCK_LOGIN_DISABLED);
        }
        User user = provisioningService.findOrCreateUser(PROVIDER_MOCK, req.testUser(),
                defaultNickname(req.nickname(), "测试-" + req.testUser()));
        return issue(user);
    }

    public LoginResponse adminLogin(AdminLoginRequest req) {
        String key = req.username() == null ? "" : req.username();
        ensureNotLocked(key);
        AdminCredential cred = adminCredentialMapper.findByUsername(req.username());
        if (cred == null || !passwordEncoder.matches(req.password(), cred.getPasswordHash())) {
            recordFailure(key);
            throw BusinessException.of(ErrorCode.ADMIN_LOGIN_FAILED);
        }
        adminAttempts.remove(key); // 成功清零
        User user = userMapper.findById(cred.getAdminUserId());
        return issueWithSession(user.getId(), Principal.ROLE_ADMIN, user.getNickname());
    }

    /** 若该用户名处于锁定窗口则直接拒绝（429）。 */
    private void ensureNotLocked(String key) {
        LoginAttempt a = adminAttempts.get(key);
        if (a != null && a.lockedUntil > System.currentTimeMillis()) {
            throw BusinessException.of(ErrorCode.RATE_LIMITED);
        }
    }

    /** 记一次失败；达阈值则开启 15 分钟锁定窗口。 */
    private void recordFailure(String key) {
        LoginAttempt a = adminAttempts.computeIfAbsent(key, k -> new LoginAttempt());
        long now = System.currentTimeMillis();
        if (a.lockedUntil > now) {
            return;
        }
        int fails = a.failures.incrementAndGet();
        if (fails >= MAX_FAILURES) {
            a.lockedUntil = now + LOCK_MILLIS;
            a.failures.set(0);
        }
    }

    private static final class LoginAttempt {
        final AtomicInteger failures = new AtomicInteger(0);
        volatile long lockedUntil = 0L;
    }

    /** 退出：撤销当前 token 对应会话（FR-AUTH-04）。 */
    public void logout(String rawToken) {
        sessionService.revoke(rawToken);
    }

    /**
     * 续期：校验当前 token + 会话活跃 → 签发新 token + 新会话 → 撤销旧会话。
     * 由控制器传入已解析的 Principal 与原始 token。
     */
    @Transactional
    public LoginResponse refresh(Principal principal, String rawToken) {
        if (principal == null || !sessionService.isActive(rawToken)) {
            throw BusinessException.of(ErrorCode.UNAUTHENTICATED);
        }
        User user = userMapper.findById(principal.userId());
        String nickname = user != null ? user.getNickname() : null;
        LoginResponse resp = issueWithSession(principal.userId(), principal.role(), nickname);
        sessionService.revoke(rawToken);
        return resp;
    }

    public CampusCapabilitiesResponse campusCapabilities() {
        String provider = props.getCampusIdentity().getProvider();
        boolean enabled = !"disabled".equalsIgnoreCase(provider);
        return new CampusCapabilitiesResponse(provider, enabled);
    }

    private LoginResponse issue(User user) {
        return issueWithSession(user.getId(), Principal.ROLE_USER, user.getNickname());
    }

    /** 签发 token 并落地会话记录（三种登录与续期共用）。 */
    private LoginResponse issueWithSession(Long userId, String role, String nickname) {
        String token = jwtService.issueAccessToken(userId, role);
        sessionService.create(userId, token);
        return new LoginResponse(token, userId, role, nickname);
    }

    private String defaultNickname(String provided, String fallback) {
        return provided != null && !provided.isBlank() ? provided : fallback;
    }
}
