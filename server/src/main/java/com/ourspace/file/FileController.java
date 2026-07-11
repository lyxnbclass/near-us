package com.ourspace.file;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.BusinessException;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;
    private final MediaUrlSigner mediaUrlSigner;
    private final String mediaBaseUrl;
    private final long mediaUrlTtlMinutes;
    private final Path mediaStorageRoot;

    public FileController(JdbcTemplate jdbc,
                          TenantService tenantService,
                          MediaUrlSigner mediaUrlSigner,
                          @Value("${app.media-base-url:}") String mediaBaseUrl,
                          @Value("${app.media-url-ttl-minutes:15}") long mediaUrlTtlMinutes,
                          @Value("${app.media-storage-dir:var/media}") String mediaStorageDir) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
        this.mediaUrlSigner = mediaUrlSigner;
        this.mediaBaseUrl = trimTrailingSlash(mediaBaseUrl);
        this.mediaUrlTtlMinutes = mediaUrlTtlMinutes;
        this.mediaStorageRoot = Path.of(mediaStorageDir).toAbsolutePath().normalize();
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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(HttpServletRequest request,
                                                   @RequestPart("file") MultipartFile file,
                                                   @RequestParam(required = false) String originalName) throws Exception {
        if (file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FILE_EMPTY");
        }
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        String safeName = safeFileName(originalName == null || originalName.isBlank()
                ? file.getOriginalFilename()
                : originalName);
        String objectKey = coupleId + "/" + UUID.randomUUID() + "-" + safeName;
        Path target = mediaStorageRoot.resolve(objectKey).normalize();
        if (!target.startsWith(mediaStorageRoot)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FILE_INVALID_NAME");
        }
        Files.createDirectories(target.getParent());
        file.transferTo(target);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into files(couple_id, owner_user_id, object_key, original_name, mime_type, size_bytes)
                    values(?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, userId);
            ps.setString(3, objectKey);
            ps.setString(4, safeName);
            ps.setString(5, file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            ps.setLong(6, file.getSize());
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of(
                "id", keyHolder.getKey().longValue(),
                "objectKey", objectKey,
                "url", signedMediaUrl(objectKey)));
    }

    @GetMapping("/{id}/signed-url")
    public ApiResponse<Map<String, Object>> signedUrl(HttpServletRequest request, @PathVariable long id) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        var rows = jdbc.queryForList("select object_key from files where id = ? and couple_id = ? and deleted_at is null", id, coupleId);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
        }
        String objectKey = (String) rows.get(0).get("object_key");
        return ApiResponse.ok(Map.of(
                "url", signedMediaUrl(objectKey),
                "ttlSeconds", mediaUrlTtlMinutes * 60));
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    private String signedMediaUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return "";
        }
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }
        var encodedKey = encodePath(objectKey);
        var expires = Instant.now().plusSeconds(mediaUrlTtlMinutes * 60).getEpochSecond();
        var path = "/private-media/" + encodedKey
                + "?expires=" + expires
                + "&sig=" + mediaUrlSigner.sign(objectKey, expires);
        return mediaBaseUrl.isBlank() ? path : mediaBaseUrl + path;
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    private String safeFileName(String value) {
        String name = value == null || value.isBlank() ? "upload.bin" : value;
        return name.replaceAll("[\\\\/:*?\"<>|]+", "-");
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
