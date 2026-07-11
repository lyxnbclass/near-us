package com.ourspace.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class MediaUrlSigner {
    private final byte[] secret;

    public MediaUrlSigner(@Value("${app.jwt-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String objectKey, long expires) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            var text = objectKey + ":" + expires;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("MEDIA_SIGN_FAILED", e);
        }
    }

    public boolean verify(String objectKey, long expires, String signature) {
        if (signature == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(objectKey, expires).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
