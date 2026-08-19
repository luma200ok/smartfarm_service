package com.smartfarm.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.service.dto.AiDiagnosisResponse;
import com.smartfarm.service.dto.PrescriptionResult;
import com.smartfarm.service.entity.Diagnosis;
import com.smartfarm.service.entity.DiagnosisStatus;
import com.smartfarm.service.entity.Prescription;
import com.smartfarm.service.entity.PrescriptionStatus;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.DiagnosisRepository;
import com.smartfarm.service.repository.PrescriptionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 처방 상태 전이 전용 서비스 — 워커({@link PrescriptionJobWorker})가 ai-server 호출 전후로 부르는
 * <b>짧은 쓰기 트랜잭션</b>만 모아둔다. ai-server 호출(최대 120s)은 이 클래스 밖(워커, 논트랜잭션)에서
 * 일어나므로 LLM 응답을 기다리는 동안 DB 커넥션을 점유하지 않는다(외부 호출은 트랜잭션 밖 — 프로젝트 룰,
 * DiagnosisService NOT_SUPPORTED 선례). 워커가 아닌 별도 빈으로 분리한 이유는 self-invocation이
 * 프록시를 우회해 @Transactional이 안 걸리는 함정을 피하기 위함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrescriptionTransitionService {

    private final PrescriptionRepository prescriptionRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final ObjectMapper objectMapper;

    /** 워커가 ai-server 호출에 필요한 스냅샷 — 전이 트랜잭션 안에서 한 번에 읽어 전달한다. */
    public record PrescriptionJob(Long id, String question, String diagnosisJson) {
    }

    /**
     * PENDING → PROCESSING 전이 + job 스냅샷 반환. PENDING이 아니면(이미 처리됨/중복 제출/삭제됨)
     * empty를 반환해 워커가 조용히 스킵하게 한다 — 재기동 복구의 재큐잉과 정상 접수 제출이 같은 id로
     * 겹쳐도 이 가드 덕에 정확히 한 번만 처리된다(멱등 픽업).
     */
    @Transactional
    public Optional<PrescriptionJob> markProcessing(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId).orElse(null);
        if (prescription == null || prescription.getStatus() != PrescriptionStatus.PENDING) {
            return Optional.empty();
        }
        prescription.startProcessing();
        return Optional.of(new PrescriptionJob(
                prescription.getId(),
                prescription.getQuestion(),
                buildDiagnosisJson(prescription.getDiagnosisId())));
    }

    /** PROCESSING → COMPLETED. result는 계약 4필드로 정규화된 JSON을 저장한다(JSONB). */
    @Transactional
    public void markCompleted(Long prescriptionId, PrescriptionResult result) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId).orElse(null);
        if (prescription == null || prescription.getStatus() != PrescriptionStatus.PROCESSING) {
            log.warn("완료 전이 스킵 — 처방이 없거나 PROCESSING이 아님: id={}", prescriptionId);
            return;
        }
        prescription.complete(toJson(result));
    }

    /** PENDING/PROCESSING → FAILED. 종료 상태면 스킵(결과 덮어쓰기 차단). */
    @Transactional
    public void markFailed(Long prescriptionId, ErrorCode errorCode) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId).orElse(null);
        if (prescription == null || prescription.getStatus().isTerminal()) {
            log.warn("실패 전이 스킵 — 처방이 없거나 이미 종료 상태: id={}", prescriptionId);
            return;
        }
        prescription.fail(errorCode.getCode());
    }

    /** 재기동 복구 — 이전 프로세스의 PROCESSING 잔존 건 일괄 FAILED(P002). 근거는 repository 주석 참고. */
    @Transactional
    public int failAllProcessing() {
        return prescriptionRepository.failAllProcessing(
                PrescriptionStatus.PROCESSING, PrescriptionStatus.FAILED,
                ErrorCode.P002.getCode(), LocalDateTime.now());
    }

    /**
     * 저장된 진단을 ai-server {@code diagnosis} 폼 필드용 JSON 문자열로 직렬화(contract §3 —
     * "진단 재사용 시 저장된 진단 JSON을 diagnosis 필드로 전달"). ai-server 진단 응답과 같은
     * snake_case 스키마({@link AiDiagnosisResponse})로 되돌려 보낸다.
     *
     * <p>cam_png_base64는 제외(null) — Grad-CAM 오버레이 base64는 수백 KB로 LLM 프롬프트에
     * 의미가 없고 요청만 비대해진다. 진단이 조회되지 않으면(이론상 FK로 불가) null로 진행 —
     * 질문만으로도 처방은 가능하므로 job을 실패시키지 않는다.
     */
    private String buildDiagnosisJson(Long diagnosisId) {
        if (diagnosisId == null) {
            return null;
        }
        Diagnosis diagnosis = diagnosisRepository.findById(diagnosisId).orElse(null);
        if (diagnosis == null) {
            log.warn("처방 참조 진단이 조회되지 않음 — 질문만으로 진행: diagnosisId={}", diagnosisId);
            return null;
        }
        return toJson(new AiDiagnosisResponse(
                diagnosis.getStatus() == DiagnosisStatus.OOD_BLOCKED,
                diagnosis.getReason(),
                diagnosis.getLabel(),
                diagnosis.getLabelKr(),
                diagnosis.getProb(),
                diagnosis.getPart(),
                null));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // 레코드 직렬화 실패는 코딩 오류 수준 — job은 워커의 catch에서 P002로 수렴한다
            throw new CustomException(ErrorCode.P002);
        }
    }
}
