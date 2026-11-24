package com.cqu.campus.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 时间解析工具：兼容 ISO 8601 与 "yyyy-MM-dd HH:mm:ss"，并允许仅日期
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateTimeUtils() {
    }

    public static LocalDateTime parse(String text) {
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
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // 继续尝试仅日期
        }
        try {
            return LocalDate.parse(value, DATE_ONLY).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // 全部格式失败
        }
        throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "时间格式不正确: " + value);
    }

    public static String format(LocalDateTime time) {
        return time == null ? "" : time.format(ISO);
    }
}
