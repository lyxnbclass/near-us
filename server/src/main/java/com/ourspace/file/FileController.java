package com.ourspace.file;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public FileController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @Valid @RequestBody FileCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into files(couple_id, owner_user_id, object_key, original_name, mime_type, size_bytes, width, height, checksum)
                    values(?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, userId);
            ps.setString(3, body.objectKey());
            ps.setString(4, body.originalName());
            ps.setString(5, body.mimeType());
            ps.setLong(6, body.sizeBytes());
            ps.setObject(7, body.width());
            ps.setObject(8, body.height());
            ps.setString(9, body.checksum());
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    @GetMapping("/{id}/signed-url")
    public ApiResponse<Map<String, Object>> signedUrl(HttpServletRequest request, @PathVariable long id) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        var rows = jdbc.queryForList("select object_key from files where id = ? and couple_id = ? and deleted_at is null", id, coupleId);
        if (rows.isEmpty()) {
            return ApiResponse.fail("FILE_NOT_FOUND");
        }
        String objectKey = (String) rows.get(0).get("object_key");
        return ApiResponse.ok(Map.of(
                "url", "/private-media/" + objectKey + "?expires=" + Instant.now().plusSeconds(900).getEpochSecond(),
                "ttlSeconds", 900));
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    public record FileCreate(
            @NotBlank String objectKey,
            String originalName,
            @NotBlank String mimeType,
            long sizeBytes,
            Integer width,
            Integer height,
            String checksum) {
    }
}
