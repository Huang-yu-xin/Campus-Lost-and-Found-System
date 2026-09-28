package edu.whut.clf.common.config;

import edu.whut.clf.auth.AdminCredentialMapper;
import edu.whut.clf.auth.model.AdminCredential;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 仅开发环境种子：若无管理员则创建一个 bootstrap 管理员。
 * 用户名/密码来自 env（ADMIN_BOOTSTRAP_USERNAME/PASSWORD），拒绝硬编码弱口令入库。
 * 生产 profile 不加载。
 */
@Component
@Profile("dev")
public class DevSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    private final AdminCredentialMapper adminCredentialMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment env;

    public DevSeeder(AdminCredentialMapper adminCredentialMapper, UserMapper userMapper,
                     PasswordEncoder passwordEncoder, Environment env) {
        this.adminCredentialMapper = adminCredentialMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.env = env;
    }

    @Override
    public void run(String... args) {
        String username = env.getProperty("ADMIN_BOOTSTRAP_USERNAME", "admin");
        String password = env.getProperty("ADMIN_BOOTSTRAP_PASSWORD");
        if (password == null || password.isBlank()) {
            log.warn("ADMIN_BOOTSTRAP_PASSWORD 未设置，跳过管理员种子（请在 deploy/.env 配置强口令）");
            return;
        }
        if (adminCredentialMapper.findByUsername(username) != null) {
            return;
        }
        User adminUser = new User();
        adminUser.setNickname("平台管理员");
        adminUser.setStatus(UserStatus.ACTIVE.name());
        adminUser.setCampusVerificationStatus("UNVERIFIED");
        userMapper.insert(adminUser);

        AdminCredential cred = new AdminCredential();
        cred.setAdminUserId(adminUser.getId());
        cred.setUsername(username);
        cred.setPasswordHash(passwordEncoder.encode(password));
        cred.setSecurityStatus("ACTIVE");
        adminCredentialMapper.insert(cred);
        log.info("已创建 bootstrap 管理员账号: {}", username);
    }
}
