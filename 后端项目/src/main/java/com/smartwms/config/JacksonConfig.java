package com.smartwms.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Jackson 日期序列化统一（《API接口统一规范》第 7 节）：
 * DATETIME 字段（createTime/auditTime 等）→ yyyy-MM-dd HH:mm:ss，
 * DATE 字段（planDate/expiryDate 等）→ yyyy-MM-dd。
 * 未自定义前 LocalDateTime 默认输出 ISO-8601（带 T），与接口文档不符。
 */
@Configuration
public class JacksonConfig {

    /** 日期时间格式，与接口规范第 7 节一致 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 纯日期格式，与接口规范第 7 节一致 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonTimeCustomizer() {
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(DATE_TIME_FORMATTER),
                        new LocalDateSerializer(DATE_FORMATTER));
    }
}
