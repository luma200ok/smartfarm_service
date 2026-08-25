package com.smartfarm.service.init;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideAlert;
import com.smartfarm.service.entity.PesticideAlertSeverity;
import com.smartfarm.service.entity.PesticideReference;
import com.smartfarm.service.repository.PesticideAlertRepository;
import com.smartfarm.service.repository.PesticideReferenceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 농약 참조정보 idempotent 시드(이슈 #128) — {@code init/DemoAccountInitializer}·
 * {@code init/PrescriptionRecoveryInitializer}와 동일한 SmartLifecycle(phase 0) 패턴.
 *
 * <h2>왜 Flyway 정적 INSERT가 아니라 Java initializer인가(handoff가 명시한 판단 지점)</h2>
 * <ul>
 *   <li><b>이 데이터는 스텁이다</b> — 이슈 #128 범위 결정(2026-08-25)대로 실 농진청 API 키를 확보하면
 *       {@code LocalPesticideReferenceProvider}를 다른 구현체로 스위치하는 것이 목표다. Flyway
 *       마이그레이션은 "시행된 마이그레이션 수정 금지" 컨벤션상 <b>영구 이력</b>으로 남는데, 스텁
 *       데이터의 오탈자·표본 보강마다 새 마이그레이션을 계속 쌓는 것은 이 데이터의 임시성과 맞지
 *       않는다. Java initializer는 이 클래스 자체를 그냥 고치면 되고, 실 API로 전환한 뒤에는
 *       {@code @Component}만 제거하면 끝난다(마이그레이션 히스토리에 흔적을 남길 필요가 없다).</li>
 *   <li><b>경보는 "이번 주 발생 주의" 성격이라 유효기간이 시드 시점 기준 상대값이어야 자연스럽다</b>
 *       — 이 클래스는 기동 시각(LocalDateTime.now())을 기준으로 유효기간을 계산해 심는다. Flyway로
 *       고정 절대시각을 박으면 배포가 늦어질수록 경보가 처음부터 만료된 채로 심긴다.</li>
 *   <li>이 레포의 Flyway INSERT 선례(V16·V20)는 전부 <b>기존 행 재구성/이관</b>이지 신규 참조
 *       테이블 시드가 아니다(handoff 실측) — 참조 데이터 시드에 대응하는 선례는 Java initializer뿐
 *       (DemoAccountInitializer)이라 그 관례를 그대로 따른다.</li>
 * </ul>
 *
 * <h2>idempotency</h2>
 * <p>재기동마다 호출되지만 각 테이블이 <b>비어 있을 때만</b> 심는다({@link #referenceRepository}·
 * {@link #alertRepository}의 {@code count()}). DB 유니크 제약({@code ux_pesticide_references_crop_pest}·
 * {@code ux_pesticide_alerts_crop_message}, V23)이 다중 인스턴스 동시 기동 경합의 최후 방어선이다 —
 * 두 인스턴스가 동시에 count()==0을 관찰해 둘 다 삽입을 시도해도, 나중에 커밋을 시도하는 쪽만
 * {@link DataIntegrityViolationException}으로 실패하고 조용히 스킵한다(DemoAccountInitializer의
 * 충돌 처리보다 단순한 이유: 이 데이터는 "승자"를 가릴 필요 없는 고정 상수 목록이라 어느 인스턴스가
 * 먼저 심었든 결과가 동일하다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PesticideReferenceSeeder implements SmartLifecycle {

    /**
     * 출처 표기 — <b>절대 "농촌진흥청 연동"을 암시하지 않는다.</b> 프리뷰 mock 문구
     * ({@code PESTICIDE_SOURCE: "농촌진흥청 농약안전정보시스템 연동 · 최종 갱신 08.20"})는 실제로는
     * 거짓이므로 그대로 쓰지 않는다(handoff 핵심 요구사항 — 사용자가 이 수치를 실제 안전사용기준으로
     * 믿고 살포하면 작물 피해·잔류농약 문제로 이어질 수 있다).
     */
    static final String SAMPLE_DATA_SOURCE = "내부 시드 샘플 데이터입니다. 농촌진흥청과 실시간 연동되지 "
            + "않으며 실제 등록 농약 정보와 다를 수 있습니다. 살포 전 반드시 농약안전정보시스템에서 "
            + "정식 정보를 확인하세요.";

    /**
     * 경보용 출처 표기(리뷰 P2) — 참조정보와 동일 원칙이되 문구는 경보 성격에 맞춘다. 경보 문구
     * 자체가 "총채벌레 발생 밀도가 증가하고 있습니다"처럼 실제 관측 기반 공식 경보로 읽히기 쉬워,
     * "내부 샘플이며 실제 예찰 정보가 아니다"를 명시한다. <b>절대 "농촌진흥청 연동"을 암시하지
     * 않는다</b>(§클래스 주석·{@link #SAMPLE_DATA_SOURCE} 동일 요구사항).
     */
    static final String SAMPLE_ALERT_SOURCE = "내부 시드 샘플 경보입니다. 실제 농촌진흥청 예찰·발생정보와 "
            + "연동되지 않으며 실제 발생 상황과 다를 수 있습니다. 방제 판단 전 반드시 농약안전정보시스템·"
            + "지역 농업기술센터의 정식 예찰 정보를 확인하세요.";

    private final PesticideReferenceRepository referenceRepository;
    private final PesticideAlertRepository alertRepository;

    private volatile boolean running;

    /** idempotent — 각 테이블이 비어 있을 때만 심는다(재기동·부분 실패 후 재실행 안전). */
    public void seed() {
        seedReferences();
        seedAlerts();
    }

    private void seedReferences() {
        if (referenceRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PesticideReference> samples = List.of(
                reference(CropType.TOMATO, "담배가루이", 12, 3,
                        "바이러스 매개 우려 — 발견 즉시 방제 권장(예시 수치)", now),
                reference(CropType.TOMATO, "총채벌레", 9, 5,
                        "꽃·과실에 피해가 집중된다(예시 수치)", now),
                reference(CropType.TOMATO, "진딧물", 15, 3,
                        "개체수 급증 시 응애와 동시 방제를 검토한다(예시 수치)", now),
                reference(CropType.TOMATO, "잿빛곰팡이병", 7, 7,
                        "다습·저온기에 확산이 빠르다(예시 수치)", now),
                reference(CropType.TOMATO, "흰가루병", 10, 5,
                        "환기 관리로 예방하는 것이 우선이다(예시 수치)", now),
                reference(CropType.TOMATO, "역병", 6, 7,
                        "강우 직후에는 방제 효과가 떨어진다(예시 수치)", now)
        );
        insertIgnoringConflict(() -> referenceRepository.saveAll(samples),
                "농약 참조정보 시드 — {}건", samples.size());
    }

    private void seedAlerts() {
        if (alertRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PesticideAlert> samples = List.of(
                alert(CropType.TOMATO,
                        "최근 고온다습 조건으로 총채벌레 발생 밀도가 증가하고 있습니다 — 예찰 강화를 "
                                + "권장합니다.",
                        PesticideAlertSeverity.WARNING, now.minusDays(2), now.plusDays(5)),
                alert(CropType.TOMATO,
                        "환기 관리가 소홀한 재배동에서 흰가루병 발생 사례가 보고되고 있습니다.",
                        PesticideAlertSeverity.INFO, now.minusDays(1), now.plusDays(6))
        );
        insertIgnoringConflict(() -> alertRepository.saveAll(samples),
                "병해충 발생주의 경보 시드 — {}건", samples.size());
    }

    /**
     * 삽입 도중 유니크 제약 위반(다중 인스턴스 동시 기동 — 다른 인스턴스가 먼저 커밋)이 나면 이번
     * 트랜잭션의 삽입만 조용히 포기한다. 이 시드는 고정 상수 목록이라 "누가 먼저 심었는가"는
     * 결과에 영향이 없다(DemoAccountInitializer처럼 승자 유저를 가릴 필요가 없음).
     */
    private void insertIgnoringConflict(Runnable insert, String logMessage, int count) {
        try {
            insert.run();
            log.info(logMessage, count);
        } catch (DataIntegrityViolationException e) {
            log.info("농약 참조정보 시드 — 다른 인스턴스가 먼저 시드를 완료해 건너뜀");
        }
    }

    private static PesticideReference reference(CropType cropType, String pestName, int registeredProductCount,
                                                  Integer preHarvestIntervalDays, String note,
                                                  LocalDateTime updatedAt) {
        return PesticideReference.builder()
                .cropType(cropType)
                .pestName(pestName)
                .registeredProductCount(registeredProductCount)
                .preHarvestIntervalDays(preHarvestIntervalDays)
                .note(note)
                .source(SAMPLE_DATA_SOURCE)
                .updatedAt(updatedAt)
                .build();
    }

    private static PesticideAlert alert(CropType cropType, String message, PesticideAlertSeverity severity,
                                         LocalDateTime validFrom, LocalDateTime validUntil) {
        return PesticideAlert.builder()
                .cropType(cropType)
                .message(message)
                .severity(severity)
                .validFrom(validFrom)
                .validUntil(validUntil)
                .source(SAMPLE_ALERT_SOURCE)
                .build();
    }

    @Override
    public void start() {
        seed();
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** phase 0 — Boot 웹서버 lifecycle(phase ≫ 0)보다 먼저 = 트래픽 수신 전 시드 보장. */
    @Override
    public int getPhase() {
        return 0;
    }
}
