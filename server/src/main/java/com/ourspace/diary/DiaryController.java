package com.ourspace.diary;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.BusinessException;
import com.ourspace.common.crypto.CryptoService;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diaries")
public class DiaryController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;
    private final CryptoService cryptoService;

    public DiaryController(JdbcTemplate jdbc, TenantService tenantService, CryptoService cryptoService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
        this.cryptoService = cryptoService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        return ApiResponse.ok(jdbc.queryForList("""
                select id, owner_user_id, title, visibility, cover_file_id, created_at, updated_at
                from diaries
                where couple_id = ? and deleted_at is null
                  and (visibility = 'shared' or owner_user_id = ?)
                order by created_at desc
                """, coupleId, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(HttpServletRequest request, @PathVariable long id) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var diary = requireVisibleDiary(id, coupleId, userId);
        diary.put("content", cryptoService.decrypt((String) diary.get("content_cipher"), (String) diary.get("content_nonce")));
        diary.remove("content_cipher");
        diary.remove("content_nonce");
        if ("shared".equals(diary.get("visibility"))) {
            diary.put("comments", comments(id, coupleId));
        }
        return ApiResponse.ok(diary);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody DiaryCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var encrypted = cryptoService.encrypt(body.content());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into diaries(couple_id, owner_user_id, title, visibility, content_cipher, content_nonce, cover_file_id)
                    values(?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, userId);
            ps.setString(3, body.title());
            ps.setString(4, body.visibility());
            ps.setString(5, encrypted.cipher());
            ps.setString(6, encrypted.nonce());
            ps.setObject(7, body.coverFileId());
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    @GetMapping("/{id}/comments")
    public ApiResponse<List<Map<String, Object>>> comments(HttpServletRequest request, @PathVariable long id) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var diary = requireVisibleDiary(id, coupleId, userId);
        if (!"shared".equals(diary.get("visibility"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DIARY_PRIVATE");
        }
        return ApiResponse.ok(comments(id, coupleId));
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<Map<String, Object>> comment(HttpServletRequest request,
                                                    @PathVariable long id,
                                                    @Valid @RequestBody DiaryCommentCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var diary = requireVisibleDiary(id, coupleId, userId);
        if (!"shared".equals(diary.get("visibility"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DIARY_PRIVATE");
        }
        var encrypted = cryptoService.encrypt(body.content());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into diary_comments(couple_id, diary_id, created_by, content_cipher, content_nonce)
                    values(?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, id);
            ps.setLong(3, userId);
            ps.setString(4, encrypted.cipher());
            ps.setString(5, encrypted.nonce());
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    private Map<String, Object> requireVisibleDiary(long id, long coupleId, long userId) {
        var rows = jdbc.queryForList("""
                select * from diaries
                where id = ? and couple_id = ? and deleted_at is null
                """, id, coupleId);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "DIARY_NOT_FOUND");
        }
        var diary = rows.get(0);
        if ("private".equals(diary.get("visibility")) && ((Number) diary.get("owner_user_id")).longValue() != userId) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DIARY_PRIVATE");
        }
        return diary;
    }

    private List<Map<String, Object>> comments(long diaryId, long coupleId) {
        var rows = jdbc.queryForList("""
                select dc.id, dc.created_by, dc.content_cipher, dc.content_nonce, dc.created_at, u.nickname
                from diary_comments dc
                join users u on u.id = dc.created_by
                where dc.diary_id = ? and dc.couple_id = ? and dc.deleted_at is null
                order by dc.created_at asc
                """, diaryId, coupleId);
        rows.forEach(row -> {
            row.put("content", cryptoService.decrypt((String) row.get("content_cipher"), (String) row.get("content_nonce")));
            row.remove("content_cipher");
            row.remove("content_nonce");
        });
        return rows;
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    public record DiaryCreate(@NotBlank String title, @NotBlank String visibility, @NotBlank String content, Long coverFileId) {
    }

    public record DiaryCommentCreate(@NotBlank String content) {
    }
}
