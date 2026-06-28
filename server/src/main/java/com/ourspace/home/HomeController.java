package com.ourspace.home;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public HomeController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> home(HttpServletRequest request) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var couple = jdbc.queryForMap("select anniversary_start_date from couples where id = ?", coupleId);
        Object start = couple.get("anniversary_start_date");
        long togetherDays = start == null ? 0 : ChronoUnit.DAYS.between(LocalDate.parse(start.toString()), LocalDate.now()) + 1;
        var partnerStatus = jdbc.queryForList("""
                select s.*, u.nickname from statuses s join users u on u.id = s.created_by
                where s.couple_id = ? and s.created_by <> ? and s.deleted_at is null
                order by s.created_at desc limit 1
                """, coupleId, userId);
        var anniversaries = jdbc.queryForList("""
                select *, datediff(event_date, curdate()) as days_left
                from anniversaries
                where couple_id = ? and deleted_at is null
                order by abs(datediff(event_date, curdate())) asc limit 3
                """, coupleId);
        var modules = jdbc.queryForList("""
                select module_key from couple_modules
                where couple_id = ? and enabled = true
                """, coupleId);
        var payload = new HashMap<String, Object>();
        payload.put("togetherDays", togetherDays);
        payload.put("partnerStatus", partnerStatus.isEmpty() ? null : partnerStatus.get(0));
        payload.put("anniversaries", anniversaries);
        payload.put("enabledModules", modules);
        return ApiResponse.ok(payload);
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }
}
