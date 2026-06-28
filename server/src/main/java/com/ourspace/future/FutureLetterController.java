package com.ourspace.future;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.BusinessException;
import com.ourspace.common.crypto.CryptoService;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/future-letters")
public class FutureLetterController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;
    private final CryptoService cryptoService;

    public FutureLetterController(JdbcTemplate jdbc, TenantService tenantService, CryptoService cryptoService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
        this.cryptoService = cryptoService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        return ApiResponse.ok(jdbc.queryForList("""
                select fl.id, fl.sender_user_id, fl.recipient_user_id, fl.title, fl.open_at, fl.created_at,
                       fl.open_at <= now() as unlocked, u.nickname as sender_name
                from future_letters fl
                join users u on u.id = fl.sender_user_id
                where fl.couple_id = ? and fl.deleted_at is null
                  and (fl.sender_user_id = ? or fl.recipient_user_id is null or fl.recipient_user_id = ?)
                order by fl.open_at asc
                """, coupleId, userId, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(HttpServletRequest request, @PathVariable long id) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var rows = jdbc.queryForList("""
                select fl.*, u.nickname as sender_name
                from future_letters fl join users u on u.id = fl.sender_user_id
                where fl.id = ? and fl.couple_id = ? and fl.deleted_at is null
                  and (fl.sender_user_id = ? or fl.recipient_user_id is null or fl.recipient_user_id = ?)
                """, id, coupleId, userId, userId);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "FUTURE_LETTER_NOT_FOUND");
        }
        var letter = rows.get(0);
        var openAt = ((java.sql.Timestamp) letter.get("open_at")).toLocalDateTime();
        if (openAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FUTURE_LETTER_LOCKED");
        }
        letter.put("content", cryptoService.decrypt((String) letter.get("content_cipher"), (String) letter.get("content_nonce")));
        letter.remove("content_cipher");
        letter.remove("content_nonce");
        return ApiResponse.ok(letter);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody FutureLetterCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        Long recipientId = resolveRecipient(coupleId, userId, body.recipientMode());
        var encrypted = cryptoService.encrypt(body.content());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into future_letters(couple_id, sender_user_id, recipient_user_id, title, content_cipher, content_nonce, open_at)
                    values(?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, userId);
            ps.setObject(3, recipientId);
            ps.setString(4, body.title());
            ps.setString(5, encrypted.cipher());
            ps.setString(6, encrypted.nonce());
            ps.setObject(7, body.openAt());
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    private Long resolveRecipient(long coupleId, long userId, String recipientMode) {
        if ("both".equals(recipientMode)) {
            return null;
        }
        var rows = jdbc.queryForList("""
                select user_id from couple_members
                where couple_id = ? and user_id <> ? and status = 'active'
                limit 1
                """, Long.class, coupleId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    public record FutureLetterCreate(@NotBlank String title, @NotBlank String content, LocalDateTime openAt, String recipientMode) {
    }
}
