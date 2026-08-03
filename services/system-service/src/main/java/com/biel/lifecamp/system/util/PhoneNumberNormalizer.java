package com.biel.lifecamp.system.util;

import java.util.regex.Pattern;

/**
 * 将微信和 EHR 手机号统一为 E.164 格式。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-30
 */
public final class PhoneNumberNormalizer {
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[\\s()-]");
    private static final Pattern CHINA_MOBILE_PATTERN = Pattern.compile("1\\d{10}");
    private static final Pattern E164_PATTERN = Pattern.compile("\\+[1-9]\\d{7,14}");

    private PhoneNumberNormalizer() {
    }

    /**
     * 规范化手机号。
     *
     * @param value 原始手机号
     * @return E.164 手机号
     * @throws IllegalArgumentException 手机号为空或格式无效时抛出
     */
    public static String normalize(String value) {
        String compact = value == null
                ? "" : SEPARATOR_PATTERN.matcher(value).replaceAll("");
        if (CHINA_MOBILE_PATTERN.matcher(compact).matches()) {
            compact = "+86" + compact;
        }
        if (!E164_PATTERN.matcher(compact).matches()) {
            throw new IllegalArgumentException("Phone number is not valid E.164");
        }
        return compact;
    }
}
