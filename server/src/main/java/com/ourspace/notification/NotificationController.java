package com.ourspace.notification;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final JdbcTemplate jdbc;

    public NotificationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        long userId = currentUser(request);
        return ApiResponse.ok(jdbc.queryForList("""
                select * from notifications
                where recipient_user_id = ?
                order by created_at desc limit 100
                """, userId));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> read(HttpServletRequest request, @PathVariable long id) {
        long userId = currentUser(request);
        jdbc.update("update notifications set read_at = now() where id = ? and recipient_user_id = ?", id, userId);
        return ApiResponse.ok(Map.of("id", id, "read", true));
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }
}
