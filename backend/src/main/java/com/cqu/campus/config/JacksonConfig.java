package com.cqu.campus.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 时间序列化配置：输出 ISO 8601，输入兼容 ISO 与 "yyyy-MM-dd HH:mm:ss"
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return new Jackson2ObjectMapperBuilderCustomizer() {
            @Override
            public void customize(Jackson2ObjectMapperBuilder builder) {
                builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(ISO));
                builder.deserializerByType(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
                builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }
        };
    }

    /**
     * 兼容多种时间格式的反序列化器
     */
    static class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        private static final DateTimeFormatter SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
                throws IOException, JsonProcessingException {
            String text = parser.getText();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            String value = text.trim();
            try {
                return LocalDateTime.parse(value, ISO);
            } catch (DateTimeParseException ignored) {
                // 继续尝试其他格式
            }
            try {
                return LocalDateTime.parse(value, SPACE);
            } catch (DateTimeParseException ignored) {
                // 继续尝试其他格式
            }
            try {
                return LocalDateTime.parse(value, MINUTE);
            } catch (DateTimeParseException ignored) {
                // 继续尝试其他格式
            }
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                // 继续尝试仅日期
            }
            try {
                return java.time.LocalDate.parse(value, DATE_ONLY).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 全部格式失败
            }
            throw new IOException("时间格式不正确: " + value);
        }
    }
}
