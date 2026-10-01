package edu.whut.clf.auth;

import edu.whut.clf.auth.model.AuthIdentity;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录身份开通：按 provider+subject 查找或创建用户与身份绑定。
 * D3(P2-3)：独立事务——让微信 code2session 外呼在事务之外执行（不占用 DB 连接），
 * 开通本身仍保持 identity+user 插入的原子性。
 */
@Service
public class AuthProvisioningService {

    private final AuthIdentityMapper identityMapper;
    private final UserMapper userMapper;

    public AuthProvisioningService(AuthIdentityMapper identityMapper, UserMapper userMapper) {
        this.identityMapper = identityMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public User findOrCreateUser(String provider, String subject, String nickname) {
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
}
