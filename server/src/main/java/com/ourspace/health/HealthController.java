package com.ourspace.health;

import com.ourspace.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "ok",
                "service", "ourspace-server",
                "checkedAt", Instant.now().toString()));
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ready() {
        var databaseUp = databaseIsReady();
        var body = Map.<String, Object>of(
                "status", databaseUp ? "ok" : "degraded",
                "service", "ourspace-server",
                "database", databaseUp ? "up" : "down",
                "checkedAt", Instant.now().toString());
        if (!databaseUp) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiResponse<>(false, body, "DEPENDENCY_UNAVAILABLE"));
        }
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    private boolean databaseIsReady() {
        try {
            Integer result = jdbc.queryForObject("select 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception ignored) {
            return false;
        }
    }
}
