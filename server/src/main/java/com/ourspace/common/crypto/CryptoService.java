package com.ourspace.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {
    private final SecureRandom random = new SecureRandom();
    private final byte[] key;

    public CryptoService(@Value("${app.crypto-key-base64:}") String configuredKey) {
        if (configuredKey != null && !configuredKey.isBlank()) {
            this.key = Base64.getDecoder().decode(configuredKey);
        } else {
            this.key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        }
    }

    public EncryptedText encrypt(String plain) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new EncryptedText(
                    Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8))),
                    Base64.getEncoder().encodeToString(nonce));
        } catch (Exception e) {
            throw new IllegalStateException("ENCRYPT_FAILED", e);
        }
    }

    public String decrypt(String cipherText, String nonceText) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            var nonce = Base64.getDecoder().decode(nonceText);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("DECRYPT_FAILED", e);
        }
    }

    public record EncryptedText(String cipher, String nonce) {
    }
}
