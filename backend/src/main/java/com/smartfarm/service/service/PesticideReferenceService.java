package com.smartfarm.service.service;

import com.smartfarm.service.dto.PesticideAlertResponse;
import com.smartfarm.service.dto.PesticideReferenceResponse;
import com.smartfarm.service.entity.CropType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 농약 참조정보 조회(이슈 #128, {@code NutrientService#findPresets} 패턴 — 전역 조회, farm-scoped
 * 아님). 실제 조회는 {@link PesticideReferenceProvider}에 위임한다 — 이 클래스는 결과 상한(#91
 * 정책과 일관: 참조 데이터라 규모는 작지만 q 없이 전체 조회가 가능해 상한을 둔다) 정책만 쥔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PesticideReferenceService {

    /** 참조정보 결과 상한 — q 없이 전체 조회해도 무제한 로드를 막는다(#91 정책). */
    static final int MAX_REFERENCE_RESULTS = 50;

    /** 경보 결과 상한 — 유효기간 내 경보만 남긴 뒤에도 무제한 로드를 막는다(#91 정책). */
    static final int MAX_ALERT_RESULTS = 20;

    private final PesticideReferenceProvider pesticideReferenceProvider;

    public List<PesticideReferenceResponse> findReferences(CropType cropType, String query) {
        return pesticideReferenceProvider.findReferences(cropType, query, MAX_REFERENCE_RESULTS)
                .stream()
                .map(PesticideReferenceResponse::from)
                .toList();
    }

    public List<PesticideAlertResponse> findActiveAlerts(CropType cropType) {
        return pesticideReferenceProvider.findActiveAlerts(cropType, LocalDateTime.now(), MAX_ALERT_RESULTS)
                .stream()
                .map(PesticideAlertResponse::from)
                .toList();
    }
}
