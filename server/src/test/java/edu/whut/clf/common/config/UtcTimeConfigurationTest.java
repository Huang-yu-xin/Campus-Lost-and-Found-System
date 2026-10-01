package edu.whut.clf.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class UtcTimeConfigurationTest {
    @Test void offsetsNormalizeToUtcAndResponsesIncludeZone() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new UtcTimeConfiguration().utcLocalDateTime().customize(builder);
        var mapper = builder.build();
        var expected = LocalDateTime.of(2026, 9, 27, 4, 0);
        assertEquals(expected, mapper.readValue("\"2026-09-27T12:00:00+08:00\"", LocalDateTime.class));
        assertEquals("\"2026-09-27T04:00:00Z\"", mapper.writeValueAsString(expected));
        assertEquals(expected, UtcTimeConfiguration.parse("2026-09-27T04:00:00Z"));
    }
}
