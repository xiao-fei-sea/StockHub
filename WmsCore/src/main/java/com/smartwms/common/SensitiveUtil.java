package com.smartwms.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感字段脱敏工具（《后端开发规范》第 10 节）：操作日志记录前对
 * password / token / phone / idCard 等字段脱敏。
 */
public final class SensitiveUtil {

    /** 匹配 JSON 片段中的敏感键值对："password":"xxx" → 已脱敏 */
    private static final Pattern JSON_SENSITIVE = Pattern.compile(
            "(\"(?:password|newPassword|oldPassword|accessToken|refreshToken|token|idCard)\"\\s*:\\s*\")([^\"]*)(\")");

    /** 手机号：138****1234 */
    private static final Pattern PHONE = Pattern.compile("(1\\d{2})\\d{4}(\\d{4})");

    private SensitiveUtil() {
    }

    /** 对 JSON 字符串做整体脱敏（密码/令牌全掩，手机号中四位掩）。 */
    public static String maskJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String masked = JSON_SENSITIVE.matcher(json).replaceAll("$1***$3");
        return PHONE.matcher(masked).replaceAll("$1****$2");
    }

    /** 单值脱敏：手机号中四位掩，其余原样返回。 */
    public static String maskValue(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = PHONE.matcher(value);
        return m.find() ? m.replaceAll("$1****$2") : value;
    }
}
