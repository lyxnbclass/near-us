package com.ourspace.privacy;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public PrivacyController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> exportMyData(HttpServletRequest request) {
        long userId = currentUser(request);
        Long coupleId = tenantService.activeCoupleId(userId);
        var payload = new HashMap<String, Object>();
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("user", jdbc.queryForMap("""
                select id, phone, openid, nickname, avatar_url, status, created_at
                from users where id = ?
                """, userId));

        if (coupleId != null) {
            payload.put("couple", jdbc.queryForMap("""
                    select id, paired_at, anniversary_start_date, status, created_at
                    from couples where id = ?
                    """, coupleId));
            payload.put("members", jdbc.queryForList("""
                    select u.id, u.nickname, cm.role, cm.joined_at, cm.status
                    from couple_members cm join users u on u.id = cm.user_id
                    where cm.couple_id = ?
                    """, coupleId));
            payload.put("albums", jdbc.queryForList("""
                    select id, caption, scene_type, scene_ref_id, taken_at, created_by, created_at
                    from album_items where couple_id = ? and deleted_at is null
                    """, coupleId));
            payload.put("statuses", jdbc.queryForList("""
                    select id, content, mood_tag, created_by, created_at
                    from statuses where couple_id = ? and deleted_at is null
                    """, coupleId));
            payload.put("diaries", jdbc.queryForList("""
                    select id, owner_user_id, title, visibility, cover_file_id, created_at, updated_at
                    from diaries
                    where couple_id = ? and deleted_at is null
                      and (visibility = 'shared' or owner_user_id = ?)
                    """, coupleId, userId));
            payload.put("anniversaries", jdbc.queryForList("""
                    select id, title, event_date, event_type, remind_time, card_theme, created_by, created_at
                    from anniversaries where couple_id = ? and deleted_at is null
                    """, coupleId));
            payload.put("affectionCards", jdbc.queryForList("""
                    select id, title, amount, message, created_by, created_at
                    from affection_cards where couple_id = ? and deleted_at is null
                    """, coupleId));
            payload.put("wishes", jdbc.queryForList("""
                    select id, title, note, completed, completed_by, completed_at, created_by, created_at
                    from wishes where couple_id = ? and deleted_at is null
                    """, coupleId));
            payload.put("futureLetters", jdbc.queryForList("""
                    select id, sender_user_id, recipient_user_id, title, open_at, created_at
                    from future_letters
                    where couple_id = ? and deleted_at is null
                      and (sender_user_id = ? or recipient_user_id is null or recipient_user_id = ?)
                    """, coupleId, userId, userId));
        }
        return ApiResponse.ok(payload);
    }

    @PostMapping("/deletion-request")
    public ApiResponse<Map<String, Object>> requestDeletion(HttpServletRequest request) {
        long userId = currentUser(request);
        Long coupleId = tenantService.activeCoupleId(userId);
        var scheduledAt = LocalDateTime.now().plusDays(7);
        jdbc.update("""
                insert into privacy_requests(user_id, couple_id, request_type, status, scheduled_delete_at, note)
                values(?, ?, 'account_deletion', 'pending', ?, 'User requested account deletion from privacy center')
                """, userId, coupleId, scheduledAt);
        jdbc.update("update users set status = 'deletion_pending' where id = ?", userId);
        return ApiResponse.ok(Map.of(
                "status", "pending",
                "scheduledDeleteAt", scheduledAt.toString()
        ));
    }

    @GetMapping("/requests")
    public ApiResponse<Object> requests(HttpServletRequest request) {
        long userId = currentUser(request);
        return ApiResponse.ok(jdbc.queryForList("""
                select id, request_type, status, requested_at, scheduled_delete_at, completed_at, note
                from privacy_requests where user_id = ?
                order by requested_at desc
                """, userId));
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }
}
