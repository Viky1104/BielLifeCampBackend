package com.biel.lifecamp.starter.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * RSA 密钥读取与测试密钥生成工具。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public final class RsaKeys {
    private static final Pattern PEM_BEGIN_PATTERN = Pattern.compile("-----BEGIN [A-Z ]+-----");
    private static final Pattern PEM_END_PATTERN = Pattern.compile("-----END [A-Z ]+-----");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    private RsaKeys() {
    }

    /**
     * 从 Spring 资源位置读取 X.509 格式的 RSA 公钥。
     *
     * @param loader 资源加载器
     * @param location 密钥资源位置
     * @return RSA 公钥
     */
    public static RSAPublicKey readPublicKey(ResourceLoader loader, String location) {
        return (RSAPublicKey) read(loader, location, true);
    }

    /**
     * 从 Spring 资源位置读取 PKCS#8 格式的 RSA 私钥。
     *
     * @param loader 资源加载器
     * @param location 密钥资源位置
     * @return RSA 私钥
     */
    public static RSAPrivateKey readPrivateKey(ResourceLoader loader, String location) {
        return (RSAPrivateKey) read(loader, location, false);
    }

    /**
     * 生成仅用于测试或临时场景的 RSA 密钥对。
     *
     * @return 3072 位 RSA 密钥对
     */
    public static KeyPair ephemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to generate RSA key pair", ex);
        }
    }

    private static java.security.Key read(ResourceLoader loader, String location, boolean publicKey) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalStateException("RSA key location is required");
        }
        try {
            Resource resource = loader.getResource(location);
            String pem = resource.getContentAsString(StandardCharsets.UTF_8);
            String withoutBeginMarker = PEM_BEGIN_PATTERN.matcher(pem).replaceAll("");
            String withoutEndMarker = PEM_END_PATTERN.matcher(withoutBeginMarker).replaceAll("");
            String base64 = WHITESPACE_PATTERN.matcher(withoutEndMarker).replaceAll("");
            byte[] bytes = Base64.getDecoder().decode(base64);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return publicKey ? factory.generatePublic(new X509EncodedKeySpec(bytes))
                    : factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to load RSA key from " + location, ex);
        }
    }
}
