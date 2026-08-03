package com.biel.lifecamp.system.util;

import java.io.Console;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 在本机交互式生成管理员 BCrypt 密码哈希。
 *
 * <p>直接在 IDEA 中运行本类。工具只输出可写入数据库的哈希，不输出明文密码，
 * 也不连接数据库或远程配置中心。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public final class AdminPasswordHashTool {
    private AdminPasswordHashTool() {
    }

    /**
     * 从系统控制台读取两次密码并输出 DelegatingPasswordEncoder 格式哈希。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("A system console is required to read the password securely");
        }
        char[] password = console.readPassword("Admin password: ");
        char[] confirmation = console.readPassword("Confirm admin password: ");
        try {
            if (password == null || confirmation == null
                    || password.length < 12 || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("密码至少 12 位且两次输入必须一致");
            }
            PasswordEncoder encoder = new DelegatingPasswordEncoder(
                    "bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
            System.out.println(encoder.encode(CharBuffer.wrap(password)));
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }
}
