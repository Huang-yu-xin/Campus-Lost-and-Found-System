package edu.whut.clf.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用级配置绑定（前缀 app.*）。默认值见 application.yml / deploy/env.example。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Auth auth = new Auth();
    private MockLogin mockLogin = new MockLogin();
    private CampusIdentity campusIdentity = new CampusIdentity();
    private Wechat wechat = new Wechat();
    private File file = new File();
    private Match match = new Match();

    @Data
    public static class Auth {
        private long accessTokenTtlMinutes = 120;
        private long refreshTokenTtlDays = 7;
        private String jwtSecret = "dev-only-secret-change-me";
    }

    @Data
    public static class MockLogin {
        /** 仅开发/测试启用；生产必须 false。 */
        private boolean enabled = false;
    }

    @Data
    public static class CampusIdentity {
        /** 本期正式默认 disabled；测试可用 mock。 */
        private String provider = "disabled";
    }

    @Data
    public static class Wechat {
        private String appid = "";
        private String appsecret = "";
    }

    @Data
    public static class File {
        private String storageRoot = "./storage";
        private int maxSizeMb = 5;
        private int maxCountPerPost = 6;
        private String allowedMime = "image/jpeg,image/png,image/webp";
    }

    /** 匹配权重与阈值（集中配置，见 D-05）。S = wCategory*C + wLocation*L + wTime*T + wKeyword*K。 */
    @Data
    public static class Match {
        private double wCategory = 0.40;
        private double wLocation = 0.25;
        private double wTime = 0.20;
        private double wKeyword = 0.15;
        /** 时间评分窗口（天）：间隔越大分越低。 */
        private int timeWindowDays = 30;
        /** 允许的拾取早于丢失的误差容忍（小时）。 */
        private int timeToleranceHours = 24;
        /** 候选最多返回条数。 */
        private int maxCandidates = 20;
        /** 入选最低分阈值。 */
        private double minScore = 0.10;
    }
}
