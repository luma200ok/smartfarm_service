package com.smartfarm.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.dto.PrescriptionRequest;
import com.smartfarm.service.dto.PrescriptionResponse;
import com.smartfarm.service.dto.PrescriptionResult;
import com.smartfarm.service.dto.PrescriptionSummaryResponse;
import com.smartfarm.service.entity.Prescription;
import com.smartfarm.service.entity.PrescriptionStatus;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.DiagnosisRepository;
import com.smartfarm.service.repository.PrescriptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrescriptionService {

    /** 농장당 진행 중(PENDING+PROCESSING) 접수 상한(contract §3, 2026-08-19 확정). */
    private static final int MAX_ACTIVE_PER_FARM = 3;
    private static final List<PrescriptionStatus> ACTIVE_STATUSES =
            List.of(PrescriptionStatus.PENDING, PrescriptionStatus.PROCESSING);

    private final PrescriptionRepository prescriptionRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final PrescriptionJobWorker prescriptionJobWorker;
    private final ObjectMapper objectMapper;

    /**
     * 접수(202) — PENDING 저장 후 단일 워커에 제출하고 즉시 반환한다(contract §3).
     *
     * <p>트랜잭션 밖 실행(NOT_SUPPORTED, DiagnosisService 선례와 같은 패턴·다른 근거) —
     * 여기서는 외부 호출이 아니라 <b>커밋-후-제출 순서 보장</b>이 목적이다. 이 메서드가 트랜잭션에
     * 감싸이면 {@code save()} 후 워커 제출 시점에 행이 아직 커밋 전이라, 워커의 markProcessing이
     * 미커밋 행을 못 읽고(READ COMMITTED) 조용히 스킵해 job이 유실될 수 있다(재기동 전까지 PENDING
     * 방치). NOT_SUPPORTED로 두면 {@code save()}가 리포지토리 자체 쓰기 트랜잭션으로 즉시 커밋되고,
     * 그 뒤에 제출되므로 워커는 항상 커밋된 행을 본다. 가드·진단 검증 등 각 DB 호출의 원자성은
     * Spring Data JPA의 메서드 단위 자체 트랜잭션이 보장한다(자세한 근거는 DiagnosisService 주석).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PrescriptionResponse createPrescription(Long farmId, Long userId, PrescriptionRequest request) {
        farmAccessGuard.requireMember(farmId, userId);

        // 요청 자체의 오류(D001)를 혼잡(P004/429)보다 먼저 판정 — 잘못된 diagnosisId 요청이
        // 429로 오인되면 클라이언트가 "잠시 후 재시도"를 무한 반복하게 된다(reviewer P3).
        if (request.diagnosisId() != null
                && !diagnosisRepository.existsByIdAndFarmId(request.diagnosisId(), farmId)) {
            // 진단 참조는 같은 farm 스코프만 허용 — 타 농장 진단 참조는 D001(cross-tenant IDOR 차단).
            // exists 쿼리 사용 — 엔티티 로드는 @Lob 필드 때문에 논트랜잭션 경로에서 불가(repository 주석)
            throw new CustomException(ErrorCode.D001);
        }

        // 접수 상한(contract §3): 농장당 진행 중(PENDING+PROCESSING) 3건 — 초과 접수는 P004(429).
        // count-저장 사이 TOCTOU로 동시 접수가 상한을 살짝 넘을 수 있으나(단일 인스턴스·개인 서비스)
        // 상한의 목적(폴링 적체·중복 클릭 완화)에는 충분하다 — 엄밀 상한이 필요해지면 유니크 제약/락으로 승격.
        long activeCount = prescriptionRepository.countByFarmIdAndStatusIn(
                farmId, ACTIVE_STATUSES);
        if (activeCount >= MAX_ACTIVE_PER_FARM) {
            throw new CustomException(ErrorCode.P004);
        }
        // 워커 큐 포화 시 저장 없이 P004(contract §3 "워커 큐는 유한(포화 시 저장 없이 P004)")
        if (!prescriptionJobWorker.hasCapacity()) {
            throw new CustomException(ErrorCode.P004);
        }

        Prescription saved = prescriptionRepository.save(Prescription.builder()
                .farmId(farmId)
                .createdBy(userId)
                .diagnosisId(request.diagnosisId())
                .question(request.question())
                .build());
        prescriptionJobWorker.submit(saved.getId());
        return PrescriptionResponse.of(saved, null);
    }

    public PrescriptionResponse findPrescription(Long farmId, Long userId, Long prescriptionId) {
        farmAccessGuard.requireMember(farmId, userId);
        Prescription prescription = prescriptionRepository.findByIdAndFarmId(prescriptionId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.P001));
        return PrescriptionResponse.of(prescription, parseResult(prescription));
    }

    public PageResponse<PrescriptionSummaryResponse> findPrescriptions(Long farmId, Long userId, Pageable pageable) {
        farmAccessGuard.requireMember(farmId, userId);
        Page<PrescriptionSummaryResponse> page = prescriptionRepository.findSummariesByFarmId(farmId, pageable);
        return PageResponse.of(page);
    }

    /** 저장 JSON은 워커가 계약 4필드로 정규화해 저장한 것 — 파싱 실패는 코딩 오류 수준(C002). */
    private PrescriptionResult parseResult(Prescription prescription) {
        if (prescription.getResult() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(prescription.getResult(), PrescriptionResult.class);
        } catch (JsonProcessingException e) {
            log.error("저장된 처방 result JSON 파싱 실패: id={}", prescription.getId(), e);
            throw new CustomException(ErrorCode.C002);
        }
    }
}
