package com.ourspace.memory;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public MemoryController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> today(HttpServletRequest request) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var today = LocalDate.now();
        var month = today.getMonthValue();
        var day = today.getDayOfMonth();

        var albums = jdbc.queryForList("""
                select ai.id, ai.caption, ai.created_at, ai.taken_at, ai.scene_type,
                       f.object_key, f.mime_type, u.nickname as creator_name,
                       year(curdate()) - year(coalesce(ai.taken_at, ai.created_at)) as years_ago
                from album_items ai
                join files f on f.id = ai.file_id
                join users u on u.id = ai.created_by
                where ai.couple_id = ? and ai.deleted_at is null
                  and month(coalesce(ai.taken_at, ai.created_at)) = ?
                  and day(coalesce(ai.taken_at, ai.created_at)) = ?
                  and year(coalesce(ai.taken_at, ai.created_at)) < year(curdate())
                order by coalesce(ai.taken_at, ai.created_at) desc
                limit 20
                """, coupleId, month, day);

        var diaries = jdbc.queryForList("""
                select id, owner_user_id, title, visibility, created_at, updated_at,
                       year(curdate()) - year(created_at) as years_ago
                from diaries
                where couple_id = ? and deleted_at is null
                  and month(created_at) = ? and day(created_at) = ?
                  and year(created_at) < year(curdate())
                  and (visibility = 'shared' or owner_user_id = ?)
                order by created_at desc
                limit 20
                """, coupleId, month, day, userId);

        return ApiResponse.ok(Map.of(
                "date", today.toString(),
                "albums", albums,
                "diaries", diaries
        ));
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }
}
