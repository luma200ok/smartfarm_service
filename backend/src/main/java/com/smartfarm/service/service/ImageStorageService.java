package com.smartfarm.service.service;

import com.smartfarm.service.config.ImageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
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
 * <p>확장자는 원본 파일명이 아니라, 호출자(DiagnosisService)가 화이트리스트+매직바이트로 이미 검증한
 * content-type에서 유도한다 — 원본 파일명을 그대로 쓰면 사용자 입력 문자열이 파일 경로 일부가 되어
 * path traversal 통로가 되므로 원천 차단(handoff).
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

    /** DiagnosisService.ALLOWED_CONTENT_TYPES와 동일 화이트리스트 — 이 밖 타입은 호출자가 이미 D002로 차단한다. */
    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp"
    );
    private static final String FALLBACK_EXTENSION = "bin";

    private final Path baseDir;

    public ImageStorageService(ImageProperties imageProperties) {
        this.baseDir = Path.of(imageProperties.storageDir()).toAbsolutePath().normalize();
    }

    /**
     * {@code {farmId}/{diagnosisId}.{ext}}로 저장하고 DB에 넣을 상대경로를 반환한다.
     * 저장 실패(디렉터리 생성 불가·권한 없음 등) 시 null을 반환한다 — 호출자는 그 경우 diagnosis에
     * image_path를 채우지 않는다(contract: 저장 실패가 진단 생성을 실패시키지 않음).
     *
     * @param contentType 호출자가 화이트리스트·매직바이트로 이미 검증한 content-type(DiagnosisService)
     */
    public String store(Long farmId, Long diagnosisId, MultipartFile file, String contentType) {
        String extension = CONTENT_TYPE_TO_EXTENSION.getOrDefault(contentType, FALLBACK_EXTENSION);
        String relativePath = farmId + "/" + diagnosisId + "." + extension;
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

    /**
     * 레거시 안전망 전용 — 정상 경로는 diagnoses.image_content_type 컬럼값을 그대로 쓴다
     * (DiagnosisService#findDiagnosisImage). 컬럼이 비어 있는 과거 행에 한해서만 확장자로 추정한다.
     */
    public String contentTypeOf(String imagePath) {
        int dotIndex = imagePath.lastIndexOf('.');
        String extension = dotIndex >= 0 ? imagePath.substring(dotIndex + 1).toLowerCase(Locale.ROOT) : "";
        return CONTENT_TYPE_TO_EXTENSION.entrySet().stream()
                .filter(entry -> entry.getValue().equals(extension))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private Path resolveWithinBase(String relativePath) {
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("경로가 base 디렉터리를 벗어남: " + relativePath);
        }
        return resolved;
    }
}
