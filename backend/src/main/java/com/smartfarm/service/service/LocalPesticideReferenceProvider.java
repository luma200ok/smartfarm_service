package com.smartfarm.service.service;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideAlert;
import com.smartfarm.service.entity.PesticideReference;
import com.smartfarm.service.repository.PesticideAlertRepository;
import com.smartfarm.service.repository.PesticideReferenceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PesticideReferenceProvider} 1차 구현 — 자체 시드 DB 조회(이슈 #128). 데이터는
 * {@code PesticideReferenceSeeder}가 기동 시 idempotent하게 채운다. 실 농진청 API 구현체가
 * 들어오면 이 클래스 대신 새 {@code @Component}(예: {@code RdaPesticideReferenceProvider})가
 * {@link PesticideReferenceProvider}를 구현해 대체한다 — 컨트롤러·서비스·DTO는 무변경.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocalPesticideReferenceProvider implements PesticideReferenceProvider {

    private final PesticideReferenceRepository pesticideReferenceRepository;
    private final PesticideAlertRepository pesticideAlertRepository;

    @Override
    public List<PesticideReferenceItem> findReferences(CropType cropType, String query, int limit) {
        String pestNameQuery = query == null ? "" : query.trim();
        return pesticideReferenceRepository
                .findByCropTypeAndPestNameContainingIgnoreCaseOrderByPestNameAsc(
                        cropType, pestNameQuery, PageRequest.of(0, limit))
                .stream()
                .map(LocalPesticideReferenceProvider::toItem)
                .toList();
    }

    @Override
    public List<PesticideAlertItem> findActiveAlerts(CropType cropType, LocalDateTime now, int limit) {
        return pesticideAlertRepository.findActive(cropType, now, PageRequest.of(0, limit))
                .stream()
                .map(LocalPesticideReferenceProvider::toItem)
                .toList();
    }

    private static PesticideReferenceItem toItem(PesticideReference reference) {
        return new PesticideReferenceItem(reference.getCropType(), reference.getPestName(),
                reference.getRegisteredProductCount(), reference.getPreHarvestIntervalDays(),
                reference.getNote(), reference.getSource(), reference.getUpdatedAt());
    }

    private static PesticideAlertItem toItem(PesticideAlert alert) {
        return new PesticideAlertItem(alert.getCropType(), alert.getMessage(), alert.getSeverity(),
                alert.getValidFrom(), alert.getValidUntil(), alert.getSource());
    }
}
