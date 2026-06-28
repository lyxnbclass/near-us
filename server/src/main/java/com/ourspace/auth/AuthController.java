package com.ourspace.auth;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final JwtService jwtService;

    public AuthController(JdbcTemplate jdbc, JwtService jwtService) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
    }

    @PostMapping("/mock-login")
    public ApiResponse<Map<String, Object>> mockLogin(@Valid @RequestBody MockLoginRequest request) {
        var rows = jdbc.queryForList("select id from users where phone = ? and deleted_at is null", Long.class, request.phone());
        long userId = rows.isEmpty() ? createUser(request) : rows.get(0);
        return ApiResponse.ok(Map.of("token", jwtService.issue(userId), "userId", userId));
    }

    private long createUser(MockLoginRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into users(phone, nickname) values(?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.phone());
            ps.setString(2, request.nickname());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public record MockLoginRequest(@NotBlank String phone, @NotBlank String nickname) {
    }
}
