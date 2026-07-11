package com.ourspace.album;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.BusinessException;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public AlbumController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request,
                                                       @RequestParam(defaultValue = "couple_album") String sceneType,
                                                       @RequestParam(required = false) Long sceneRefId) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        if (sceneRefId == null) {
            return ApiResponse.ok(jdbc.queryForList("""
                    select ai.*, f.object_key, f.mime_type from album_items ai
                    join files f on f.id = ai.file_id
                    where ai.couple_id = ? and ai.scene_type = ? and ai.deleted_at is null
                    order by coalesce(ai.taken_at, ai.created_at) desc
                    """, coupleId, sceneType));
        }
        return ApiResponse.ok(jdbc.queryForList("""
                select ai.*, f.object_key, f.mime_type from album_items ai
                join files f on f.id = ai.file_id
                where ai.couple_id = ? and ai.scene_type = ? and ai.scene_ref_id = ? and ai.deleted_at is null
                order by coalesce(ai.taken_at, ai.created_at) desc
                """, coupleId, sceneType, sceneRefId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @Valid @RequestBody AlbumCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var fileCount = jdbc.queryForObject(
                "select count(*) from files where id = ? and couple_id = ? and deleted_at is null",
                Integer.class, body.fileId(), coupleId);
        if (fileCount == null || fileCount == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into album_items(couple_id, file_id, caption, scene_type, scene_ref_id, taken_at, created_by)
                    values(?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, body.fileId());
            ps.setString(3, body.caption());
            ps.setString(4, body.sceneType() == null ? "couple_album" : body.sceneType());
            ps.setObject(5, body.sceneRefId());
            ps.setObject(6, body.takenAt());
            ps.setLong(7, userId);
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(HttpServletRequest request,
                                                   @PathVariable long id,
                                                   @RequestBody AlbumUpdate body) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        requireAlbum(id, coupleId);
        jdbc.update("""
                update album_items set caption = ?, updated_at = now()
                where id = ? and couple_id = ? and deleted_at is null
                """, body.caption(), id, coupleId);
        return ApiResponse.ok(Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(HttpServletRequest request, @PathVariable long id) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        requireAlbum(id, coupleId);
        jdbc.update("""
                update album_items set deleted_at = now()
                where id = ? and couple_id = ? and deleted_at is null
                """, id, coupleId);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }

    private void requireAlbum(long id, long coupleId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from album_items
                where id = ? and couple_id = ? and deleted_at is null
                """, Integer.class, id, coupleId);
        if (count == null || count == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ALBUM_NOT_FOUND");
        }
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    public record AlbumCreate(@NotNull Long fileId, String caption, String sceneType, Long sceneRefId, LocalDateTime takenAt) {
    }

    public record AlbumUpdate(String caption) {
    }
}
