package edu.whut.clf.common.security;

import edu.whut.clf.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    @Test void consecutiveIssuesForSameIdentityHaveUniqueTokens() {
        JwtService service = new JwtService(new AppProperties());
        var tokens = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            String token = service.issueAccessToken(1L, Principal.ROLE_USER);
            assertTrue(tokens.add(token), "同秒签发也必须唯一");
            assertEquals(1L, service.parse(token).userId());
        }
    }
}
