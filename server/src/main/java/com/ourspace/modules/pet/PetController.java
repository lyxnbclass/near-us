package com.ourspace.modules.pet;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/modules/pet")
public class PetController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public PetController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping("/profiles")
    public ApiResponse<List<Map<String, Object>>> profiles(HttpServletRequest request) {
        long coupleId = enabledCoupleId(request);
        return ApiResponse.ok(jdbc.queryForList("""
                select * from pet_profiles
                where couple_id = ? and deleted_at is null
                order by created_at desc
                """, coupleId));
    }

    @PostMapping("/profiles")
    public ApiResponse<Map<String, Object>> createProfile(HttpServletRequest request, @RequestBody PetProfileCreate body) {
        long userId = currentUser(request);
        long coupleId = enabledCoupleId(request);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into pet_profiles(couple_id, name, breed, birthday, avatar_file_id, created_by)
                    values(?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setString(2, body.name());
            ps.setString(3, body.breed());
            ps.setObject(4, body.birthday());
            ps.setObject(5, body.avatarFileId());
            ps.setLong(6, userId);
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    @GetMapping("/events")
    public ApiResponse<List<Map<String, Object>>> events(HttpServletRequest request, @RequestParam(required = false) Long petId) {
        long coupleId = enabledCoupleId(request);
        if (petId == null) {
            return ApiResponse.ok(jdbc.queryForList("""
                    select e.*, p.name as pet_name, f.object_key, f.mime_type from pet_events e
                    join pet_profiles p on p.id = e.pet_id
                    left join files f on f.id = e.file_id and f.couple_id = e.couple_id and f.deleted_at is null
                    where e.couple_id = ? and e.deleted_at is null
                    order by e.created_at desc limit 100
                    """, coupleId));
        }
        return ApiResponse.ok(jdbc.queryForList("""
                select e.*, p.name as pet_name, f.object_key, f.mime_type from pet_events e
                join pet_profiles p on p.id = e.pet_id
                left join files f on f.id = e.file_id and f.couple_id = e.couple_id and f.deleted_at is null
                where e.couple_id = ? and e.pet_id = ? and e.deleted_at is null
                order by e.created_at desc limit 100
                """, coupleId, petId));
    }

    @PostMapping("/events")
    public ApiResponse<Map<String, Object>> createEvent(HttpServletRequest request, @RequestBody PetEventCreate body) {
        long userId = currentUser(request);
        long coupleId = enabledCoupleId(request);
        var petCount = jdbc.queryForObject(
                "select count(*) from pet_profiles where id = ? and couple_id = ? and deleted_at is null",
                Integer.class, body.petId(), coupleId);
        if (petCount == null || petCount == 0) {
            return ApiResponse.fail("PET_NOT_FOUND");
        }
        if (body.fileId() != null) {
            var fileCount = jdbc.queryForObject(
                    "select count(*) from files where id = ? and couple_id = ? and deleted_at is null",
                    Integer.class, body.fileId(), coupleId);
            if (fileCount == null || fileCount == 0) {
                return ApiResponse.fail("FILE_NOT_FOUND");
            }
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into pet_events(couple_id, pet_id, event_type, content, file_id, created_by)
                    values(?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, body.petId());
            ps.setString(3, body.eventType());
            ps.setString(4, body.content());
            ps.setObject(5, body.fileId());
            ps.setLong(6, userId);
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    private long enabledCoupleId(HttpServletRequest request) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        tenantService.requireModuleEnabled(coupleId, "pet");
        return coupleId;
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    public record PetProfileCreate(@NotBlank String name, String breed, LocalDate birthday, Long avatarFileId) {
    }

    public record PetEventCreate(long petId, @NotBlank String eventType, String content, Long fileId) {
    }
}
