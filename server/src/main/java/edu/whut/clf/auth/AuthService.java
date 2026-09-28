package edu.whut.clf.auth;

import edu.whut.clf.auth.dto.AuthDtos.*;
import edu.whut.clf.auth.model.AdminCredential;
import edu.whut.clf.auth.model.AuthIdentity;
import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.security.JwtService;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String PROVIDER_WECHAT = "WECHAT";
    private static final String PROVIDER_MOCK = "MOCK";

    private final AppProperties props;
    private final JwtService jwtService;
    private final WechatClient wechatClient;
    private final AuthIdentityMapper identityMapper;
    private final AdminCredentialMapper adminCredentialMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppProperties props, JwtService jwtService, WechatClient wechatClient,
                       AuthIdentityMapper identityMapper, AdminCredentialMapper adminCredentialMapper,
                       UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.props = props;
        this.jwtService = jwtService;
        this.wechatClient = wechatClient;
        this.identityMapper = identityMapper;
        this.adminCredentialMapper = adminCredentialMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponse wechatLogin(WechatLoginRequest req) {
        String openid = wechatClient.code2session(req.code());
        User user = findOrCreateUser(PROVIDER_WECHAT, openid, defaultNickname(req.nickname(), "微信用户"));
        return issue(user);
    }

    @Transactional
    public LoginResponse mockLogin(MockLoginRequest req) {
        if (!props.getMockLogin().isEnabled()) {
            throw BusinessException.of(ErrorCode.MOCK_LOGIN_DISABLED);
        }
        User user = findOrCreateUser(PROVIDER_MOCK, req.testUser(), defaultNickname(req.nickname(), "测试-" + req.testUser()));
        return issue(user);
    }

    public LoginResponse adminLogin(AdminLoginRequest req) {
        AdminCredential cred = adminCredentialMapper.findByUsername(req.username());
        if (cred == null || !passwordEncoder.matches(req.password(), cred.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.ADMIN_LOGIN_FAILED);
        }
        User user = userMapper.findById(cred.getAdminUserId());
        String token = jwtService.issueAccessToken(user.getId(), Principal.ROLE_ADMIN);
        return new LoginResponse(token, user.getId(), Principal.ROLE_ADMIN, user.getNickname());
    }

    public CampusCapabilitiesResponse campusCapabilities() {
        String provider = props.getCampusIdentity().getProvider();
        boolean enabled = !"disabled".equalsIgnoreCase(provider);
        return new CampusCapabilitiesResponse(provider, enabled);
    }

    private LoginResponse issue(User user) {
        String token = jwtService.issueAccessToken(user.getId(), Principal.ROLE_USER);
        return new LoginResponse(token, user.getId(), Principal.ROLE_USER, user.getNickname());
    }

    private User findOrCreateUser(String provider, String subject, String nickname) {
        AuthIdentity identity = identityMapper.findByProviderSubject(provider, subject);
        if (identity != null) {
            return userMapper.findById(identity.getUserId());
        }
        User user = new User();
        user.setNickname(nickname);
        user.setStatus(UserStatus.ACTIVE.name());
        user.setCampusVerificationStatus("UNVERIFIED");
        userMapper.insert(user);

        AuthIdentity ai = new AuthIdentity();
        ai.setUserId(user.getId());
        ai.setProvider(provider);
        ai.setProviderSubject(subject);
        identityMapper.insert(ai);
        return user;
    }

    private String defaultNickname(String provided, String fallback) {
        return provided != null && !provided.isBlank() ? provided : fallback;
    }
}
