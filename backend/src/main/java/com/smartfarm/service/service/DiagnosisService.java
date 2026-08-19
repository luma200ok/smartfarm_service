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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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

    @Transactional
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
        return DiagnosisResponse.from(diagnosis);
    }

    public PageResponse<DiagnosisSummaryResponse> findDiagnoses(Long farmId, Long userId, Pageable pageable) {
        farmAccessGuard.requireMember(farmId, userId);
        Page<DiagnosisSummaryResponse> page = diagnosisRepository
                .findByFarmIdOrderByCreatedAtDescIdDesc(farmId, pageable)
                .map(DiagnosisSummaryResponse::from);
        return PageResponse.of(page);
    }

    public DiagnosisResponse findDiagnosis(Long farmId, Long userId, Long diagnosisId) {
        farmAccessGuard.requireMember(farmId, userId);
        Diagnosis diagnosis = diagnosisRepository.findByIdAndFarmId(diagnosisId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.D001));
        return DiagnosisResponse.from(diagnosis);
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
