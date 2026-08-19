package com.smartfarm.service.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;

/**
 * BE 하드닝(#9) 아키텍처 가드 — 두 규칙 모두 기존 코드가 GREEN인지 확인하는 회귀 방지 테스트다.
 * 위반이 새로 발견되면(즉 여기 없는 새 케이스가 걸리면) 이 테스트가 즉시 RED가 되어 리뷰에서 잡힌다.
 */
class ArchitectureRulesTest {

    /**
     * 규칙 ①: {farmId} PathVariable을 받는 컨트롤러가 호출하는 서비스는 FarmAccessGuard 의존을
     * 가져야 한다(cross-tenant IDOR 차단). ArchUnit이 "컨트롤러 메서드의 PathVariable 이름"까지
     * 정적 분석하긴 어려워, controller 패키지에서 {farmId}를 다루는 컨트롤러 5개
     * (DiagnosisController·InvitationController·FarmMemberController·FarmController·
     * PrescriptionController — grep '{farmId}' 확인)가 실제로 호출하는 서비스 5개를 명시 리스트로
     * 검증하는 근사 규칙을 쓴다(handoff 허용).
     */
    @Test
    @DisplayName("규칙①: farmId를 다루는 서비스는 FarmAccessGuard에 의존해야 한다")
    void farmScopedServicesMustDependOnFarmAccessGuard() {
        Set<String> farmScopedServices = Set.of(
                "com.smartfarm.service.service.DiagnosisService",
                "com.smartfarm.service.service.PrescriptionService",
                "com.smartfarm.service.service.FarmService",
                "com.smartfarm.service.service.FarmMemberService",
                "com.smartfarm.service.service.InvitationService"
        );

        JavaClasses classes = new ClassFileImporter().importPackages("com.smartfarm.service.service");

        ArchRule rule = classes()
                .that(new DescribedPredicate<JavaClass>(
                        "{farmId} PathVariable을 다루는 컨트롤러가 호출하는 서비스(명시 리스트)") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return farmScopedServices.contains(javaClass.getFullName());
                    }
                })
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.smartfarm.service.service.FarmAccessGuard");

        rule.check(classes);
    }

    /**
     * 규칙 ②: repository 패키지의 @Modifying 벌크 쿼리 메서드는 flushAutomatically=true여야 한다.
     * repository는 아키텍처상 service·scheduler(및 기동 1회용 init) 계층에서만 호출되므로, 이 계층
     * 경계 자체가 대상 범위다. ArchUnit은 어노테이션 "속성 값" 검사에 적합하지 않아(핸드오프 허용대로)
     * 리플렉션 기반 커스텀 검증을 쓴다.
     *
     * <p><b>알려진 예외</b>: {@code PrescriptionRepository#failAllProcessing} — flushAutomatically
     * 미설정(clearAutomatically만 있음) 발견. claimForProcessing·failStaleByStatusBefore와 같은
     * 벌크 UPDATE인데 패턴이 어긋난다. 재기동 복구(SmartLifecycle, 트래픽 수신 전 1회 실행)라
     * 즉시 위험은 낮아 보이나, 프로덕션 코드 변경은 도메인 판단이 필요해 이번 범위에서는 수정하지
     * 않고 이 목록으로만 고정한다(핸드오프: "위반 발견 시 수정 말고 보고" — main 보고 완료, 수정
     * 여부는 main 판단 대기). 이 예외가 남아있는 한 새 위반이 섞여 들어와도 이 테스트가 잡아낸다.
     */
    @Test
    @DisplayName("규칙②: @Modifying 벌크 쿼리 메서드는 flushAutomatically=true여야 한다(알려진 예외 제외)")
    void modifyingBulkQueryMethodsMustFlushAutomatically() {
        Set<String> knownExceptions = Set.of("PrescriptionRepository#failAllProcessing");

        JavaClasses classes = new ClassFileImporter().importPackages("com.smartfarm.service.repository");

        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.isInterface()) {
                continue;
            }
            Class<?> repositoryInterface = javaClass.reflect();
            for (Method method : repositoryInterface.getDeclaredMethods()) {
                Modifying modifying = method.getAnnotation(Modifying.class);
                if (modifying != null && !modifying.flushAutomatically()) {
                    violations.add(repositoryInterface.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(violations)
                .as("새 @Modifying 메서드는 flushAutomatically=true로 선언하거나, 알려진 예외라면"
                        + " 이 테스트의 knownExceptions에 사유와 함께 추가해야 한다")
                .containsExactlyInAnyOrderElementsOf(knownExceptions);
    }
}
