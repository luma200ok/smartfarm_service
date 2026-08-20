package com.smartfarm.service.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
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
     * <p>구현 중 {@code PrescriptionRepository#failAllProcessing}에서 flushAutomatically 누락을
     * 발견했으나(clearAutomatically만 있었음), main 결정에 따라 즉시 수정해 패턴을 통일했다
     * (claimForProcessing·failStaleByStatusBefore와 동일). 예외 0건이 이 규칙의 정상 상태다.
     */
    @Test
    @DisplayName("규칙②: @Modifying 벌크 쿼리 메서드는 flushAutomatically=true여야 한다")
    void modifyingBulkQueryMethodsMustFlushAutomatically() {
        Set<String> knownExceptions = Set.of();

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

    /**
     * 규칙 ③ (BE #19 탈퇴): 회원 탈퇴는 {@code User#withdraw()}(soft delete + PII 즉시 익명화)
     * 단일 경로만 허용한다. {@code UserRepository}의 delete 계열을 직접 호출하면 @SQLDelete가
     * 익명화를 건너뛴 soft delete를 수행하므로 정적으로 차단한다(현재 호출자 0이 정상 상태 —
     * @SQLDelete 어노테이션 자체는 유지: 제거 시 미래 delete 호출이 hard delete로 더 위험).
     */
    @Test
    @DisplayName("규칙③: UserRepository의 delete 계열 호출 금지 — 탈퇴는 User.withdraw() 경유 강제")
    void userDeletionMustGoThroughEntityWithdraw() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.smartfarm.service");

        ArchRule rule = noClasses()
                .should().callMethodWhere(new DescribedPredicate<JavaMethodCall>(
                        "UserRepository의 delete 계열 메서드 호출") {
                    @Override
                    public boolean test(JavaMethodCall call) {
                        return call.getTargetOwner().getFullName()
                                .equals("com.smartfarm.service.repository.UserRepository")
                                && call.getName().startsWith("delete");
                    }
                });

        rule.check(classes);
    }
}
