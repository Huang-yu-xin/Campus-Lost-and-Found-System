package edu.whut.clf.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

/** B7/B8 启动安全校验单元测试（无需数据库/上下文）。 */
class StartupSecurityValidatorTest {

    private AppProperties props(boolean mockEnabled, String secret) {
        AppProperties p = new AppProperties();
        p.getMockLogin().setEnabled(mockEnabled);
        p.getAuth().setJwtSecret(secret);
        return p;
    }

    private final String strong = "a-very-long-random-secret-key-0123456789abcdef"; // ≥32 bytes

    @Test
    void prod_mockEnabled_refuses() {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        var v = new StartupSecurityValidator(props(true, strong), env);
        assertThrows(IllegalStateException.class, v::validate);
    }

    @Test
    void prod_defaultSecret_refuses() {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        var v = new StartupSecurityValidator(props(false, "dev-only-secret-change-me"), env);
        assertThrows(IllegalStateException.class, v::validate);
    }

    @Test
    void prod_shortSecret_refuses() {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        var v = new StartupSecurityValidator(props(false, "short"), env);
        assertThrows(IllegalStateException.class, v::validate);
    }

    @Test
    void prod_strongSecret_ok() {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        var v = new StartupSecurityValidator(props(false, strong), env);
        assertDoesNotThrow(v::validate);
    }

    @Test
    void dev_allowsMockAndShortSecret() {
        var env = new MockEnvironment();
        env.setActiveProfiles("dev");
        var v = new StartupSecurityValidator(props(true, "dev-only-secret-change-me"), env);
        assertDoesNotThrow(v::validate);
    }

    @Test
    void explicitProdCannotBeExemptedByDevOrTest() {
        for (String other : new String[]{"dev", "test", "ci"}) {
            var env = new MockEnvironment();
            env.setActiveProfiles("prod", other);
            assertThrows(IllegalStateException.class, () -> new StartupSecurityValidator(props(true, strong), env).validate());
            assertThrows(IllegalStateException.class, () -> new StartupSecurityValidator(props(false, "dev-only-secret-change-me"), env).validate());
            assertThrows(IllegalStateException.class, () -> new StartupSecurityValidator(props(false, strong), env).validate());
        }
    }
}
