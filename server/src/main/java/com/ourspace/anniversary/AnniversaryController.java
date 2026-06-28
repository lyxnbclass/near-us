package com.ourspace.anniversary;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.BusinessException;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/anniversaries")
public class AnniversaryController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public AnniversaryController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        return ApiResponse.ok(jdbc.queryForList("""
                select * from anniversaries
                where couple_id = ? and deleted_at is null
                order by event_date asc
                """, coupleId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody AnniversaryCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into anniversaries(couple_id, created_by, title, event_date, event_type, remind_time, card_theme)
                    values(?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, userId);
            ps.setString(3, body.title());
            ps.setObject(4, body.eventDate());
            ps.setString(5, body.eventType() == null ? "custom" : body.eventType());
            ps.setObject(6, body.remindTime());
            ps.setString(7, body.cardTheme() == null ? "warm" : body.cardTheme());
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(HttpServletRequest request,
                                                   @PathVariable long id,
                                                   @RequestBody AnniversaryCreate body) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        int updated = jdbc.update("""
                update anniversaries
                set title = ?, event_date = ?, event_type = ?, remind_time = ?, card_theme = ?
                where id = ? and couple_id = ? and deleted_at is null
                """,
                body.title(),
                body.eventDate(),
                body.eventType() == null ? "custom" : body.eventType(),
                body.remindTime(),
                body.cardTheme() == null ? "warm" : body.cardTheme(),
                id,
                coupleId);
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ANNIVERSARY_NOT_FOUND");
        }
        return ApiResponse.ok(Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(HttpServletRequest request, @PathVariable long id) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        int updated = jdbc.update("""
                update anniversaries
                set deleted_at = now()
                where id = ? and couple_id = ? and deleted_at is null
                """, id, coupleId);
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ANNIVERSARY_NOT_FOUND");
        }
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    public record AnniversaryCreate(
            @NotBlank String title,
            @NotNull LocalDate eventDate,
            String eventType,
            LocalTime remindTime,
            String cardTheme) {
    }
}
