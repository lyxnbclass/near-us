package com.ourspace.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class JwtService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final byte[] secret;

    public JwtService(@Value("${app.jwt-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(long userId) {
        try {
            var header = Map.of("alg", "HS256", "typ", "JWT");
            var payload = Map.of("sub", userId, "exp", Instant.now().plusSeconds(60L * 60L * 24L * 30L).getEpochSecond());
            var unsigned = b64(mapper.writeValueAsBytes(header)) + "." + b64(mapper.writeValueAsBytes(payload));
            return unsigned + "." + sign(unsigned);
        } catch (Exception e) {
            throw new IllegalStateException("JWT_ISSUE_FAILED", e);
        }
    }

    public Long verify(String token) {
        try {
            var parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            var unsigned = parts[0] + "." + parts[1];
            if (!sign(unsigned).equals(parts[2])) {
                return null;
            }
            @SuppressWarnings("unchecked")
            var payload = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            var exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                return null;
            }
            return ((Number) payload.get("sub")).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String text) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return b64(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
