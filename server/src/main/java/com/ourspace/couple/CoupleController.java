package com.ourspace.couple;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.BusinessException;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/couples")
public class CoupleController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public CoupleController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
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
        var existingMembership = jdbc.queryForObject(
                "select count(*) from couple_members where user_id = ? and status = 'active' and left_at is null",
                Integer.class, userId);
        if (existingMembership != null && existingMembership > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "ALREADY_HAS_COUPLE_OR_INVITE");
        }
        String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long coupleId = createInvitingCouple(userId, hash(code));
        return ApiResponse.ok(Map.of("inviteCode", code, "coupleId", coupleId));
    }

    @PostMapping("/bind")
    public ApiResponse<Map<String, Object>> bind(HttpServletRequest request, @Valid @RequestBody BindRequest bindRequest) {
        long userId = currentUser(request);
        if (tenantService.activeCoupleId(userId) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "ALREADY_PAIRED");
        }
        var rows = jdbc.queryForList("""
                select id from couples
                where invite_code_hash = ? and status = 'inviting'
                  and invite_expires_at > now() and deleted_at is null
                limit 1
                """, Long.class, hash(bindRequest.inviteCode()));
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND");
        }
        long coupleId = rows.get(0);
        var memberCount = jdbc.queryForObject("select count(*) from couple_members where couple_id = ? and status = 'active'", Integer.class, coupleId);
        if (memberCount == null || memberCount >= 2) {
            throw new BusinessException(HttpStatus.CONFLICT, "COUPLE_FULL");
        }
        jdbc.update("insert into couple_members(couple_id, user_id, role) values(?, ?, 'member')", coupleId, userId);
        jdbc.update("update couples set status = 'active', paired_at = now(), invite_code_hash = null where id = ?", coupleId);
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

    private long createInvitingCouple(long userId, String codeHash) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into couples(invite_code_hash, invite_expires_at, status)
                    values(?, ?, 'inviting')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, codeHash);
            ps.setObject(2, LocalDateTime.now().plusHours(24));
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
            var digest = MessageDigest.getInstance("SHA-256").digest(code.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HASH_FAILED", e);
        }
    }

    public record BindRequest(@NotBlank String inviteCode) {
    }
}
