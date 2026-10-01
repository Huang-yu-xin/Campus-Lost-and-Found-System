package edu.whut.clf.common.config;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
import java.io.IOException;
import java.time.*;

/** 领域时间与DB会话均为UTC；旧的无偏移请求按UTC解释。 */
@Configuration
public class UtcTimeConfiguration {
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) return null;
        try { return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
        catch (java.time.format.DateTimeParseException ex) { return LocalDateTime.parse(text); }
    }
    @Bean
    Jackson2ObjectMapperBuilderCustomizer utcLocalDateTime() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                @Override public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    gen.writeString(java.time.format.DateTimeFormatter.ISO_INSTANT.format(value.toInstant(ZoneOffset.UTC)));
                }
            });
            builder.deserializerByType(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                @Override public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                    return parse(parser.getText());
                }
            });
        };
    }
}
