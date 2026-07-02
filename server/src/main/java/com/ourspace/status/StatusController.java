package com.ourspace.status;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.BusinessException;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statuses")
public class StatusController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public StatusController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        return ApiResponse.ok(jdbc.queryForList("""
                select s.*, u.nickname, f.object_key, f.mime_type,
                       group_concat(sr.reaction_key order by sr.created_at separator ',') as reactions
                from statuses s
                join users u on u.id = s.created_by
                left join files f on f.id = s.file_id and f.couple_id = s.couple_id and f.deleted_at is null
                left join status_reactions sr on sr.status_id = s.id and sr.couple_id = s.couple_id
                where s.couple_id = ? and s.deleted_at is null
                group by s.id, u.nickname, f.object_key, f.mime_type
                order by s.created_at desc limit 50
                """, coupleId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody StatusCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        if (body.fileId() != null) {
            var fileCount = jdbc.queryForObject("""
                    select count(*) from files
                    where id = ? and couple_id = ? and deleted_at is null
                    """, Integer.class, body.fileId(), coupleId);
            if (fileCount == null || fileCount == 0) {
                return ApiResponse.fail("FILE_NOT_FOUND");
            }
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into statuses(couple_id, created_by, content, mood_tag, file_id)
                    values(?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, userId);
            ps.setString(3, body.content());
            ps.setString(4, body.moodTag());
            ps.setObject(5, body.fileId());
            return ps;
        }, keyHolder);
        notifyPartner(coupleId, userId, "status.created", "新的此刻", body.content());
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    @PostMapping("/{id}/reactions")
    public ApiResponse<Map<String, Object>> react(HttpServletRequest request, @PathVariable long id, @RequestBody StatusReactionCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var count = jdbc.queryForObject("""
                select count(*) from statuses
                where id = ? and couple_id = ? and deleted_at is null
                """, Integer.class, id, coupleId);
        if (count == null || count == 0) {
            return ApiResponse.fail("STATUS_NOT_FOUND");
        }
        var existing = jdbc.queryForObject("""
                select count(*) from status_reactions
                where couple_id = ? and status_id = ? and created_by = ? and reaction_key = ?
                """, Integer.class, coupleId, id, userId, body.reactionKey());
        if (existing == null || existing == 0) {
            jdbc.update("""
                    insert into status_reactions(couple_id, status_id, created_by, reaction_key)
                    values(?, ?, ?, ?)
                    """, coupleId, id, userId, body.reactionKey());
            notifyPartner(coupleId, userId, "status.reacted", "TA 回应了你的此刻", body.reactionKey());
        }
        return ApiResponse.ok(Map.of("id", id, "reactionKey", body.reactionKey()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(HttpServletRequest request, @PathVariable long id) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var rows = jdbc.queryForList("""
                select created_by from statuses
                where id = ? and couple_id = ? and deleted_at is null
                """, id, coupleId);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "STATUS_NOT_FOUND");
        }
        if (((Number) rows.get(0).get("created_by")).longValue() != userId) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "STATUS_NOT_OWNER");
        }
        jdbc.update("""
                update statuses set deleted_at = now()
                where id = ? and couple_id = ? and created_by = ? and deleted_at is null
                """, id, coupleId, userId);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }

    private void notifyPartner(long coupleId, long userId, String type, String title, String body) {
        var partners = jdbc.queryForList("""
                select user_id from couple_members
                where couple_id = ? and user_id <> ? and status = 'active'
                """, Long.class, coupleId, userId);
        for (Long partnerId : partners) {
            jdbc.update("""
                    insert into notifications(couple_id, recipient_user_id, actor_user_id, type, title, body)
                    values(?, ?, ?, ?, ?, ?)
                    """, coupleId, partnerId, userId, type, title, body);
        }
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    public record StatusCreate(String content, String moodTag, Long fileId) {
    }

    public record StatusReactionCreate(String reactionKey) {
    }
}
