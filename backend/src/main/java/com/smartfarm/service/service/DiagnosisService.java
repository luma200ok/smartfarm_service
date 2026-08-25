package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiDiagnosisResponse;
import com.smartfarm.service.dto.DiagnosisResponse;
import com.smartfarm.service.dto.DiagnosisSummaryResponse;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.entity.Diagnosis;
import com.smartfarm.service.entity.DiagnosisStatus;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.DiagnosisRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosisService {

    // 서버 진입 전 기본 캡(contract §3 handoff 예시 — 10MB)
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

    /** 저장 포맷 화이트리스트(reviewer P2) — SVG 등 활성 콘텐츠는 애초에 image/*에서 배제. */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    /** 매직바이트 검증에 필요한 최대 헤더 길이 — WEBP(RIFF....WEBP)가 가장 길어 12바이트. */
    private static final int SIGNATURE_HEADER_LENGTH = 12;

    private final DiagnosisRepository diagnosisRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final AiServerClient aiServerClient;
    private final ImageStorageService imageStorageService;

    /** GET .../image 응답 조립용 — 컨트롤러가 그대로 스트리밍 바디로 사용한다. */
    public record DiagnosisImage(Resource resource, String contentType) {
    }

    /**
     * 트랜잭션 밖에서 실행(reviewer P1) — ai-server 호출(최대 응답 타임아웃 30s)이 DB 트랜잭션 안에서
     * 일어나면 그 시간만큼 Hikari 커넥션을 물고 있게 되어, 기본 풀(10개) 기준 진단 몇 건만으로도
     * 풀이 고갈되고 무관한 엔드포인트까지 마비된다(외부 API 호출은 트랜잭션 밖 — 프로젝트 룰).
     * 클래스 기본값 {@code @Transactional(readOnly = true)}를 상속하면 이 메서드 전체가 여전히
     * 트랜잭션에 감싸이므로, {@code NOT_SUPPORTED}로 명시 오버라이드해 트랜잭션을 완전히 끊는다.
     *
     * <p>가드 조회(farmAccessGuard)와 최종 저장({@link DiagnosisRepository#save})은 각각
     * Spring Data JPA(SimpleJpaRepository)가 메서드 단위로 자체 {@code @Transactional}을 갖고
     * 있어(save()는 클래스 기본 readOnly=true를 오버라이드하는 자체 쓰기 트랜잭션 보유) 이 메서드가
     * 논트랜잭션이어도 각 DB 호출의 원자성은 보장된다. 별도 컴포넌트로 저장을 분리하는 대신 이
     * 리포지토리 자체 트랜잭션에 맡긴다(같은 빈 안에서 별도 @Transactional 메서드를 self-invocation
     * 으로 호출하면 프록시를 우회해 트랜잭션이 안 걸리는 함정을 피하기 위함).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DiagnosisResponse createDiagnosis(Long farmId, Long userId, MultipartFile file) {
        // OPERATOR 이상(이슈 #122 리뷰 P2-2) — VIEWER("조회 전용")가 농장 공유 자원을
        // 고갈시킬 수 있는 경로다. 데모 계정은 시드 농장의 ADMIN이라 계약 §4.5의
        // "조회·진단·처방은 데모 허용"에는 영향이 없다.
        // 여기서는 농장 스토리지에 원본 이미지를 남기고 LLM 호출 비용을 발생시킨다.
        farmAccessGuard.requireOperator(farmId, userId);
        String contentType = validateImage(file);

        AiDiagnosisResponse aiResponse = aiServerClient.diagnose(file);
        DiagnosisStatus status = aiResponse.oodBlocked() ? DiagnosisStatus.OOD_BLOCKED : DiagnosisStatus.OK;

        Diagnosis diagnosis = diagnosisRepository.save(Diagnosis.builder()
                .farmId(farmId)
                .createdBy(userId)
                .status(status)
                .label(aiResponse.label())
                .labelKr(aiResponse.labelKr())
                .prob(aiResponse.prob())
                .part(aiResponse.part())
                .reason(aiResponse.reason())
                .camPngBase64(aiResponse.camPngBase64())
                .build());

        attachImageIfStored(diagnosis, file, contentType);

        return DiagnosisResponse.from(diagnosis);
    }

    /**
     * 진단 생성 성공 후에만 실행 — image_path 컬럼용 id가 필요해 diagnosisRepository.save() 이후에만
     * 가능하다(IDENTITY 채번). 저장 실패는 {@link ImageStorageService#store}가 내부에서 흡수하므로
     * 여기서는 반환값 null 여부만 분기한다(진단 자체는 이미 성공 확정 — contract).
     */
    private void attachImageIfStored(Diagnosis diagnosis, MultipartFile file, String contentType) {
        String imagePath = imageStorageService.store(diagnosis.getFarmId(), diagnosis.getId(), file, contentType);
        if (imagePath != null) {
            diagnosis.attachImage(imagePath, contentType);
            diagnosisRepository.save(diagnosis);
        }
    }

    public PageResponse<DiagnosisSummaryResponse> findDiagnoses(Long farmId, Long userId, Pageable pageable) {
        farmAccessGuard.requireMember(farmId, userId);
        Page<DiagnosisSummaryResponse> page = diagnosisRepository.findSummariesByFarmId(farmId, pageable);
        return PageResponse.of(page);
    }

    public DiagnosisResponse findDiagnosis(Long farmId, Long userId, Long diagnosisId) {
        farmAccessGuard.requireMember(farmId, userId);
        Diagnosis diagnosis = diagnosisRepository.findByIdAndFarmId(diagnosisId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.D001));
        return DiagnosisResponse.from(diagnosis);
    }

    /** 가드 첫 줄 → farm 스코프 진단 조회(D001) → image_path 없음/파일 부재는 D004(contract). */
    public DiagnosisImage findDiagnosisImage(Long farmId, Long userId, Long diagnosisId) {
        farmAccessGuard.requireMember(farmId, userId);
        Diagnosis diagnosis = diagnosisRepository.findByIdAndFarmId(diagnosisId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.D001));
        Resource resource = imageStorageService.load(diagnosis.getImagePath())
                .orElseThrow(() -> new CustomException(ErrorCode.D004));
        // image_content_type 컬럼을 우선 사용(불일치 원천 제거 — reviewer P2). 컬럼이 비어있는
        // 과거 행에 한해서만 ImageStorageService의 확장자 기반 추정으로 폴백한다.
        String contentType = diagnosis.getImageContentType() != null
                ? diagnosis.getImageContentType()
                : imageStorageService.contentTypeOf(diagnosis.getImagePath());
        return new DiagnosisImage(resource, contentType);
    }

    /**
     * 빈 파일/용량 초과/화이트리스트 밖 content-type/매직바이트 불일치는 전부 D002로 통일
     * (handoff — 일관 선택). 화이트리스트를 통과한 실제 content-type을 반환해 저장·기록에 재사용한다.
     */
    private String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.D002);
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new CustomException(ErrorCode.D002);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new CustomException(ErrorCode.D002);
        }
        validateSignature(file, contentType);
        return contentType;
    }

    /**
     * 선언된 content-type과 실제 파일 헤더(매직바이트)가 일치하는지 확인한다(reviewer P2 —
     * content-type 헤더는 클라이언트가 임의로 지정 가능해 화이트리스트만으론 위조를 막지 못한다).
     * 헤더 12바이트만 읽는다(전체 파일을 다시 읽지 않음 — MultipartFile은 반복 읽기가 가능하다).
     */
    private void validateSignature(MultipartFile file, String contentType) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(SIGNATURE_HEADER_LENGTH);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.D002);
        }
        if (!matchesSignature(contentType, header)) {
            throw new CustomException(ErrorCode.D002);
        }
    }

    private boolean matchesSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47);
            case "image/gif" -> startsWith(header, 0x47, 0x49, 0x46);
            case "image/webp" -> header.length >= SIGNATURE_HEADER_LENGTH
                    && startsWith(header, 0x52, 0x49, 0x46, 0x46) // "RIFF"
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }

    private boolean startsWith(byte[] header, int... expectedUnsignedBytes) {
        if (header.length < expectedUnsignedBytes.length) {
            return false;
        }
        for (int i = 0; i < expectedUnsignedBytes.length; i++) {
            if ((header[i] & 0xFF) != expectedUnsignedBytes[i]) {
                return false;
            }
        }
        return true;
    }
}
