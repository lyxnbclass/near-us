package com.ourspace.couple;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.BusinessException;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/couples")
public class CoupleController {
    private static final int DEFAULT_BIND_FAILURE_LIMIT = 5;
    private static final int DEFAULT_BIND_FAILURE_WINDOW_MINUTES = 15;

    private final JdbcTemplate jdbc;
    private final TenantService tenantService;
    private final boolean trustForwardedHeaders;
    private final int inviteBindFailureLimit;
    private final int inviteBindFailureWindowMinutes;

    public CoupleController(JdbcTemplate jdbc,
                            TenantService tenantService,
                            @Value("${app.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
                            @Value("${app.invite-bind-failure-limit:5}") String inviteBindFailureLimit,
                            @Value("${app.invite-bind-failure-window-minutes:15}") String inviteBindFailureWindowMinutes) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.inviteBindFailureLimit = parsePositiveOrDefault(inviteBindFailureLimit, DEFAULT_BIND_FAILURE_LIMIT);
        this.inviteBindFailureWindowMinutes = parsePositiveOrDefault(
                inviteBindFailureWindowMinutes,
                DEFAULT_BIND_FAILURE_WINDOW_MINUTES);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        long userId = currentUser(request);
        var memberships = jdbc.queryForList("""
                select cm.couple_id from couple_members cm
                join couples c on c.id = cm.couple_id
                where cm.user_id = ? and cm.status = 'active' and cm.left_at is null
                  and c.status in ('active', 'unbind_pending') and c.deleted_at is null
                order by cm.joined_at desc
                limit 1
                """, Long.class, userId);
        if (memberships.isEmpty()) {
            return ApiResponse.ok(Map.of("paired", false));
        }
        long coupleId = memberships.get(0);
        var couple = jdbc.queryForMap("select id, paired_at, anniversary_start_date, status from couples where id = ?", coupleId);
        var members = jdbc.queryForList("""
                select u.id, u.nickname, u.avatar_url
                from couple_members cm join users u on u.id = cm.user_id
                where cm.couple_id = ? and cm.status = 'active'
                order by cm.joined_at asc
                """, coupleId);
        return ApiResponse.ok(Map.of("paired", true, "couple", couple, "members", members));
    }

    @PostMapping("/invite")
    public ApiResponse<Map<String, Object>> invite(HttpServletRequest request) {
        long userId = currentUser(request);
        String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        var existingMemberships = jdbc.queryForList("""
                select c.id, c.status
                from couple_members cm
                join couples c on c.id = cm.couple_id
                where cm.user_id = ? and cm.status = 'active' and cm.left_at is null
                  and c.deleted_at is null
                order by cm.joined_at desc
                limit 1
                """, userId);
        if (!existingMemberships.isEmpty()) {
            var membership = existingMemberships.get(0);
            String status = String.valueOf(membership.get("status"));
            long coupleId = ((Number) membership.get("id")).longValue();
            if (!"inviting".equals(status)) {
                throw new BusinessException(HttpStatus.CONFLICT, "ALREADY_HAS_COUPLE_OR_INVITE");
            }
            jdbc.update("""
                    update couples
                    set invite_code_hash = ?, invite_expires_at = ?
                    where id = ? and status = 'inviting' and deleted_at is null
                    """, hash(code), expiresAt, coupleId);
            return ApiResponse.ok(Map.of("inviteCode", code, "expiresAt", expiresAt, "coupleId", coupleId));
        }
        long coupleId = createInvitingCouple(userId, hash(code), expiresAt);
        return ApiResponse.ok(Map.of("inviteCode", code, "expiresAt", expiresAt, "coupleId", coupleId));
    }

    @PostMapping("/bind")
    public ApiResponse<Map<String, Object>> bind(HttpServletRequest request, @Valid @RequestBody BindRequest bindRequest) {
        long userId = currentUser(request);
        if (tenantService.activeCoupleId(userId) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "ALREADY_PAIRED");
        }
        String normalizedCode = normalizeCode(bindRequest.inviteCode());
        String codeHash = hash(normalizedCode);
        String ipHash = requestIpHash(request);
        cleanupOldBindAttempts();
        ensureBindAttemptAllowed(userId, ipHash);
        var rows = jdbc.queryForList("""
                select id, invite_expires_at from couples
                where invite_code_hash = ? and status = 'inviting'
                  and deleted_at is null
                limit 1
                """, codeHash);
        if (rows.isEmpty()) {
            recordBindAttempt(userId, ipHash, codeHash, false, "INVITE_NOT_FOUND");
            throw new BusinessException(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND");
        }
        var invite = rows.get(0);
        long coupleId = ((Number) invite.get("id")).longValue();
        var inviteExpiresAt = asLocalDateTime(invite.get("invite_expires_at"));
        if (inviteExpiresAt == null || !inviteExpiresAt.isAfter(LocalDateTime.now())) {
            recordBindAttempt(userId, ipHash, codeHash, false, "INVITE_EXPIRED");
            throw new BusinessException(HttpStatus.GONE, "INVITE_EXPIRED");
        }
        var memberCount = jdbc.queryForObject("select count(*) from couple_members where couple_id = ? and status = 'active'", Integer.class, coupleId);
        if (memberCount == null || memberCount >= 2) {
            recordBindAttempt(userId, ipHash, codeHash, false, "COUPLE_FULL");
            throw new BusinessException(HttpStatus.CONFLICT, "COUPLE_FULL");
        }
        var selfInvite = jdbc.queryForObject("""
                select count(*) from couple_members
                where couple_id = ? and user_id = ? and status = 'active' and left_at is null
                """, Integer.class, coupleId, userId);
        if (selfInvite != null && selfInvite > 0) {
            recordBindAttempt(userId, ipHash, codeHash, false, "CANNOT_BIND_OWN_INVITE");
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_BIND_OWN_INVITE");
        }
        jdbc.update("insert into couple_members(couple_id, user_id, role) values(?, ?, 'member')", coupleId, userId);
        jdbc.update("""
                update couples
                set status = 'active', paired_at = now(), invite_code_hash = null, invite_expires_at = null
                where id = ?
                """, coupleId);
        recordBindAttempt(userId, ipHash, codeHash, true, null);
        return ApiResponse.ok(Map.of("coupleId", coupleId));
    }

    @PostMapping("/unbind-request")
    public ApiResponse<Map<String, Object>> unbindRequest(HttpServletRequest request) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        jdbc.update("update couples set status = 'unbind_pending', unbind_requested_by = ?, unbind_requested_at = now() where id = ?",
                userId, coupleId);
        return ApiResponse.ok(Map.of("coupleId", coupleId, "status", "unbind_pending"));
    }

    @PostMapping("/unbind-cancel")
    public ApiResponse<Map<String, Object>> unbindCancel(HttpServletRequest request) {
        long userId = currentUser(request);
        long coupleId = requireJoinedCouple(userId);
        var rows = jdbc.queryForList("""
                select unbind_requested_by from couples
                where id = ? and status = 'unbind_pending' and deleted_at is null
                """, coupleId);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "UNBIND_NOT_PENDING");
        }
        var requestedBy = rows.get(0).get("unbind_requested_by");
        if (requestedBy != null && ((Number) requestedBy).longValue() != userId) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ONLY_REQUESTER_CAN_CANCEL");
        }
        jdbc.update("""
                update couples
                set status = 'active', unbind_requested_by = null, unbind_requested_at = null
                where id = ?
                """, coupleId);
        return ApiResponse.ok(Map.of("coupleId", coupleId, "status", "active"));
    }

    @PostMapping("/unbind-confirm")
    public ApiResponse<Map<String, Object>> unbindConfirm(HttpServletRequest request) {
        long userId = currentUser(request);
        long coupleId = requireJoinedCouple(userId);
        var updated = jdbc.update("""
                update couples
                set status = 'unbound', deleted_at = now()
                where id = ? and status = 'unbind_pending' and deleted_at is null
                """, coupleId);
        if (updated == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "UNBIND_NOT_PENDING");
        }
        jdbc.update("""
                update couple_members
                set status = 'left', left_at = now()
                where couple_id = ? and status = 'active'
                """, coupleId);
        return ApiResponse.ok(Map.of("coupleId", coupleId, "status", "unbound"));
    }

    private long createInvitingCouple(long userId, String codeHash, LocalDateTime expiresAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into couples(invite_code_hash, invite_expires_at, status)
                    values(?, ?, 'inviting')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, codeHash);
            ps.setObject(2, expiresAt);
            return ps;
        }, keyHolder);
        long coupleId = keyHolder.getKey().longValue();
        jdbc.update("insert into couple_members(couple_id, user_id, role) values(?, ?, 'owner')", coupleId, userId);
        return coupleId;
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    private long requireJoinedCouple(long userId) {
        var rows = jdbc.queryForList("""
                select cm.couple_id from couple_members cm
                join couples c on c.id = cm.couple_id
                where cm.user_id = ? and cm.status = 'active' and cm.left_at is null
                  and c.status in ('active', 'unbind_pending') and c.deleted_at is null
                order by cm.joined_at desc
                limit 1
                """, Long.class, userId);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PAIR_REQUIRED");
        }
        return rows.get(0);
    }

    private String hash(String code) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(normalizeCode(code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HASH_FAILED", e);
        }
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private int parsePositiveOrDefault(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void ensureBindAttemptAllowed(long userId, String ipHash) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(inviteBindFailureWindowMinutes);
        Integer userFailures = jdbc.queryForObject("""
                select count(*) from invite_bind_attempts
                where user_id = ? and success = false and attempted_at >= ?
                """, Integer.class, userId, since);
        Integer ipFailures = ipHash == null ? 0 : jdbc.queryForObject("""
                select count(*) from invite_bind_attempts
                where ip_hash = ? and success = false and attempted_at >= ?
                """, Integer.class, ipHash, since);
        if ((userFailures != null && userFailures >= inviteBindFailureLimit)
                || (ipFailures != null && ipFailures >= inviteBindFailureLimit)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "INVITE_ATTEMPT_LIMITED");
        }
    }

    private void recordBindAttempt(long userId, String ipHash, String codeHash, boolean success, String failureCode) {
        jdbc.update("""
                insert into invite_bind_attempts(user_id, ip_hash, invite_code_hash, success, failure_code)
                values(?, ?, ?, ?, ?)
                """, userId, ipHash, codeHash, success, failureCode);
    }

    private void cleanupOldBindAttempts() {
        jdbc.update("delete from invite_bind_attempts where attempted_at < ?", LocalDateTime.now().minusDays(1));
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private String requestIpHash(HttpServletRequest request) {
        String forwardedFor = trustForwardedHeaders ? request.getHeader("X-Forwarded-For") : null;
        String ip = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        return ip == null || ip.isBlank() ? null : hash(ip);
    }

    public record BindRequest(@NotBlank String inviteCode) {
    }
}
