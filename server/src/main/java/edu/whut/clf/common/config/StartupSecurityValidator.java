package edu.whut.clf.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 启动期安全校验（B7 + B8）。当激活 profile 不属于 {dev, ci, test}（即视为生产/正式环境）时：
 * - 强制禁用测试登录：app.mock-login.enabled=true → 拒绝启动；
 * - 拒绝默认/过短 JWT 密钥（< 32 字节 或 等于开发默认值）→ 拒绝启动。
 * 开发环境（dev/ci/test）保留短密钥兼容（JwtService 内部补长），便于本地运行。
 * 用 @PostConstruct 在上下文初始化阶段校验，确保不安全配置下服务根本不对外提供。
 */
@Component
public class StartupSecurityValidator {

    private static final Set<String> DEV_PROFILES = Set.of("dev", "ci", "test");
    private static final String DEFAULT_SECRET = "dev-only-secret-change-me";

    private final AppProperties props;
    private final Environment env;

    public StartupSecurityValidator(AppProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    /** 上下文初始化时校验；也供单元测试直接调用。 */
    @PostConstruct
    public void validate() {
        boolean isProdLike = true;
        for (String p : env.getActiveProfiles()) {
            if (DEV_PROFILES.contains(p)) {
                isProdLike = false;
                break;
            }
        }
        if (env.getActiveProfiles().length == 0) {
            // 无激活 profile 时按 application.yml 默认 dev 处理（不视为生产）
            isProdLike = false;
        }
        if (!isProdLike) {
            return;
        }
        if (props.getMockLogin().isEnabled()) {
            throw new IllegalStateException(
                    "生产环境禁止启用测试登录：请设置 app.mock-login.enabled=false（或不要在生产设 MOCK_LOGIN_ENABLED=true）");
        }
        String secret = props.getAuth().getJwtSecret();
        if (secret == null || DEFAULT_SECRET.equals(secret)
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "生产环境 JWT 密钥不安全：请通过 JWT_SECRET 配置至少 32 字节（建议 43+ 字符）的随机密钥");
        }
    }
}
