package com.ourspace.module;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {
    private static final List<String> SUPPORTED = List.of("pet");
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public ModuleController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        return ApiResponse.ok(jdbc.queryForList("""
                select module_key, enabled, enabled_at, disabled_at
                from couple_modules where couple_id = ?
                """, coupleId));
    }

    @PutMapping("/{moduleKey}")
    public ApiResponse<Map<String, Object>> set(HttpServletRequest request, @PathVariable String moduleKey, @RequestBody ModuleToggle toggle) {
        if (!SUPPORTED.contains(moduleKey)) {
            return ApiResponse.fail("MODULE_UNSUPPORTED");
        }
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        jdbc.update("""
                insert into couple_modules(couple_id, module_key, enabled, enabled_at, disabled_at)
                values(?, ?, ?, if(? = true, now(), null), if(? = false, now(), null))
                on duplicate key update enabled = values(enabled),
                  enabled_at = if(values(enabled) = true, now(), enabled_at),
                  disabled_at = if(values(enabled) = false, now(), disabled_at)
                """, coupleId, moduleKey, toggle.enabled(), toggle.enabled(), toggle.enabled());
        return ApiResponse.ok(Map.of("moduleKey", moduleKey, "enabled", toggle.enabled()));
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    public record ModuleToggle(boolean enabled) {
    }
}
