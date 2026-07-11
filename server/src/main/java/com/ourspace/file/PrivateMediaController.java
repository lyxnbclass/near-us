package com.ourspace.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@RestController
public class PrivateMediaController {
    private final Path mediaStorageRoot;
    private final MediaUrlSigner mediaUrlSigner;

    public PrivateMediaController(@Value("${app.media-storage-dir:var/media}") String mediaStorageDir,
                                  MediaUrlSigner mediaUrlSigner) {
        this.mediaStorageRoot = Path.of(mediaStorageDir).toAbsolutePath().normalize();
        this.mediaUrlSigner = mediaUrlSigner;
    }

    @GetMapping("/private-media/**")
    public ResponseEntity<Resource> read(HttpServletRequest request,
                                         @RequestParam(required = false) Long expires,
                                         @RequestParam(required = false) String sig) throws Exception {
        String objectKey = extractObjectKey(request);
        if (expires == null || expires < Instant.now().getEpochSecond()) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        if (!mediaUrlSigner.verify(objectKey, expires, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Path file = mediaStorageRoot.resolve(objectKey).normalize();
        if (!file.startsWith(mediaStorageRoot) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .body(new PathResource(file));
    }

    private String extractObjectKey(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefix = "/private-media/";
        String value = path != null && path.startsWith(prefix) ? path.substring(prefix.length()) : "";
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
