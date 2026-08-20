package com.smartfarm.service.service;

import com.smartfarm.service.config.ImageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 진단 이미지 원본 파일 저장·조회(contract §3 Phase 3, 이슈 #20).
 *
 * <p>확장자는 원본 파일명이 아니라 이미 검증된 content-type(image/*)에서 유도한다 — 원본 파일명을
 * 그대로 쓰면 사용자 입력 문자열이 파일 경로 일부가 되어 path traversal 통로가 되므로 원천 차단(handoff).
 *
 * <p>저장은 best-effort 부가 기능이다(contract 명시: 저장 실패가 진단 생성 자체를 실패시키면 안 됨) —
 * {@link #store}는 어떤 예외든 내부에서 흡수해 WARN 로그만 남기고 null을 반환한다.
 *
 * <p>조회 시에는 DB에 저장된 image_path를(과거 데이터·직접 DB 조작 등으로 오염됐을 가능성까지 대비해)
 * base 디렉터리 기준으로 정규화(normalize)한 뒤 base 디렉터리를 벗어나면 무조건 거부한다.
 */
@Slf4j
@Component
public class ImageStorageService {

    private static final String IMAGE_CONTENT_TYPE_PREFIX = "image/";
    private static final String FALLBACK_EXTENSION = "bin";
    private static final int MAX_EXTENSION_LENGTH = 10;

    private final Path baseDir;

    public ImageStorageService(ImageProperties imageProperties) {
        this.baseDir = Path.of(imageProperties.storageDir()).toAbsolutePath().normalize();
    }

    /**
     * {@code {farmId}/{diagnosisId}.{ext}}로 저장하고 DB에 넣을 상대경로를 반환한다.
     * 저장 실패(디렉터리 생성 불가·권한 없음 등) 시 null을 반환한다 — 호출자는 그 경우 diagnosis에
     * image_path를 채우지 않는다(contract: 저장 실패가 진단 생성을 실패시키지 않음).
     */
    public String store(Long farmId, Long diagnosisId, MultipartFile file) {
        String relativePath = farmId + "/" + diagnosisId + "." + deriveExtension(file.getContentType());
        try {
            Path target = resolveWithinBase(relativePath);
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return relativePath;
        } catch (Exception e) {
            // 저장은 best-effort 부가 기능(contract) — 어떤 예외든 흡수해 진단 응답에 영향 없게 한다.
            log.warn("진단 이미지 저장 실패 farmId={}, diagnosisId={}", farmId, diagnosisId, e);
            return null;
        }
    }

    /** base 디렉터리 밖으로 못 나가게 정규화 검증 후, 파일이 실재할 때만 Resource를 반환한다. */
    public Optional<Resource> load(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return Optional.empty();
        }
        Path resolved;
        try {
            resolved = resolveWithinBase(imagePath);
        } catch (IllegalArgumentException e) {
            log.warn("이미지 경로가 base 디렉터리를 벗어남(path traversal 의심): {}", imagePath);
            return Optional.empty();
        }
        if (!Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(resolved));
    }

    /** 저장 시 부여한 확장자 규칙을 그대로 역산 — 디스크 재접근(probeContentType) 없이 결정한다. */
    public String contentTypeOf(String imagePath) {
        int dotIndex = imagePath.lastIndexOf('.');
        String extension = dotIndex >= 0 ? imagePath.substring(dotIndex + 1).toLowerCase(Locale.ROOT) : "";
        if (extension.isBlank() || FALLBACK_EXTENSION.equals(extension)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return IMAGE_CONTENT_TYPE_PREFIX + extension;
    }

    private Path resolveWithinBase(String relativePath) {
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("경로가 base 디렉터리를 벗어남: " + relativePath);
        }
        return resolved;
    }

    private String deriveExtension(String contentType) {
        if (contentType == null || !contentType.startsWith(IMAGE_CONTENT_TYPE_PREFIX)) {
            return FALLBACK_EXTENSION;
        }
        String subtype = contentType.substring(IMAGE_CONTENT_TYPE_PREFIX.length());
        String sanitized = subtype.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        if (sanitized.isBlank()) {
            return FALLBACK_EXTENSION;
        }
        return sanitized.length() > MAX_EXTENSION_LENGTH ? sanitized.substring(0, MAX_EXTENSION_LENGTH) : sanitized;
    }
}
