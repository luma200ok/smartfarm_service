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
        farmAccessGuard.requireMember(farmId, userId);
        validateImage(file);

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

        attachImageIfStored(diagnosis, file);

        return DiagnosisResponse.from(diagnosis);
    }

    /**
     * 진단 생성 성공 후에만 실행 — image_path 컬럼용 id가 필요해 diagnosisRepository.save() 이후에만
     * 가능하다(IDENTITY 채번). 저장 실패는 {@link ImageStorageService#store}가 내부에서 흡수하므로
     * 여기서는 반환값 null 여부만 분기한다(진단 자체는 이미 성공 확정 — contract).
     */
    private void attachImageIfStored(Diagnosis diagnosis, MultipartFile file) {
        String imagePath = imageStorageService.store(diagnosis.getFarmId(), diagnosis.getId(), file);
        if (imagePath != null) {
            diagnosis.attachImage(imagePath);
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
        return new DiagnosisImage(resource, imageStorageService.contentTypeOf(diagnosis.getImagePath()));
    }

    /** 빈 파일/용량 초과/비이미지 content-type은 전부 D002로 통일(handoff — 일관 선택). */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.D002);
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new CustomException(ErrorCode.D002);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(ErrorCode.D002);
        }
    }
}
