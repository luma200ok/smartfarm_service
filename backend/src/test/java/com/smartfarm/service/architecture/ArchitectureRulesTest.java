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
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * BE 하드닝(#9) 아키텍처 가드 — 두 규칙 모두 기존 코드가 GREEN인지 확인하는 회귀 방지 테스트다.
 * 위반이 새로 발견되면(즉 여기 없는 새 케이스가 걸리면) 이 테스트가 즉시 RED가 되어 리뷰에서 잡힌다.
 */
class ArchitectureRulesTest {

    /**
     * 규칙 ①: {farmId} PathVariable을 다루는 컨트롤러가 의존하는 서비스는 FarmAccessGuard에
     * 의존해야 한다(cross-tenant IDOR 차단).
     *
     * <p><b>배경(2026-08-23, #56·#54 반복 재발로 수동 목록 폐기)</b>: 이 규칙은 원래 "farmId를
     * 다루는 컨트롤러 → 서비스"를 사람이 손으로 나열한 화이트리스트였다. #56(FarmLogService·
     * ForecastService 누락)에 이어 #54(ChatService 누락)까지 두 번째 P2가 발생해, 목록 자체를
     * 없애고 리플렉션으로 <b>자동 도출</b>한다: (1) controller 패키지의 클래스·메서드 매핑
     * 애노테이션에서(@RequestMapping 및 그 메타 애노테이션인 @GetMapping·@PostMapping 등 — 향후
     * 추가되는 커스텀 매핑 애노테이션도 @RequestMapping을 메타로 달면 자동 인식) 클래스 경로+메서드
     * 경로를 합쳐 "{farmId}"가 포함된 컨트롤러를 찾고, (2) 그 컨트롤러의 생성자 주입 필드(Lombok
     * @RequiredArgsConstructor가 만드는 private final 필드) 중 service 패키지 타입을 그 컨트롤러가
     * 의존하는 farm-scoped 서비스로 간주한다({@link #deriveFarmScopedServices()}). 새 컨트롤러·
     * 서비스가 추가돼도 사람이 이 목록을 갱신할 필요가 없다.
     */
    @Test
    @DisplayName("규칙①: farmId를 다루는 서비스는 FarmAccessGuard에 의존해야 한다(자동 도출)")
    void farmScopedServicesMustDependOnFarmAccessGuard() {
        Set<String> farmScopedServices = deriveFarmScopedServices();

        // 도출이 실제로 동작하는지 자체 증명 — 최소한 기존 9개 + ChatService(#54)가 전부 포함돼야
        // 한다. 이 어서션이 없으면 리플렉션 로직에 버그가 생겨(예: 패키지 경로 오타) 집합이 텅 비어도
        // 아래 ArchRule은 "대상 0건이라 항상 PASS"로 조용히 통과해버린다 — 도출 실패의 은폐 방지가
        // 목적이라 containsAll(하한 증명)이지 exhaustive 목록이 아니다: 이후 새 서비스가 추가돼도
        // 이 어서션은 그대로 통과하며 갱신이 필요 없다.
        assertThat(farmScopedServices).containsAll(Set.of(
                "com.smartfarm.service.service.DiagnosisService",
                "com.smartfarm.service.service.PrescriptionService",
                "com.smartfarm.service.service.FarmService",
                "com.smartfarm.service.service.FarmMemberService",
                "com.smartfarm.service.service.InvitationService",
                "com.smartfarm.service.service.EnvironmentService",
                "com.smartfarm.service.service.EnvThresholdService",
                "com.smartfarm.service.service.FarmLogService",
                "com.smartfarm.service.service.ForecastService",
                "com.smartfarm.service.service.ChatService"
        ));

        // FarmAccessGuard 자신은 자기 자신에 의존할 수 없어 화이트리스트로 제외한다(현재 어떤
        // farmId 컨트롤러도 FarmAccessGuard를 직접 주입받지 않아 도출 대상에 걸리지 않지만, 향후
        // 그런 컨트롤러가 생기더라도 이 규칙이 오탐하지 않도록 명시해 둔다 — 가드를 쓰지 않는 다른
        // 정당한 예외는 현재 없음).
        Set<String> guardExempt = Set.of("com.smartfarm.service.service.FarmAccessGuard");
        Set<String> targets = new HashSet<>(farmScopedServices);
        targets.removeAll(guardExempt);

        JavaClasses classes = new ClassFileImporter().importPackages("com.smartfarm.service.service");

        ArchRule rule = classes()
                .that(new DescribedPredicate<JavaClass>(
                        "{farmId} PathVariable을 다루는 컨트롤러가 의존하는 서비스(리플렉션 자동 도출)") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return targets.contains(javaClass.getFullName());
                    }
                })
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.smartfarm.service.service.FarmAccessGuard");

        rule.check(classes);
    }

    /**
     * controller 패키지를 리플렉션으로 스캔해 {farmId} 경로를 다루는 컨트롤러가 생성자 주입받는
     * service 패키지 필드 타입을 도출한다.
     */
    private static Set<String> deriveFarmScopedServices() {
        JavaClasses controllerClasses = new ClassFileImporter().importPackages("com.smartfarm.service.controller");
        Set<String> result = new LinkedHashSet<>();

        for (JavaClass javaClass : controllerClasses) {
            if (javaClass.isInterface() || javaClass.isEnum()) {
                continue;
            }
            Class<?> controllerClass;
            try {
                controllerClass = javaClass.reflect();
            } catch (RuntimeException e) {
                continue; // 리플렉션 불가 클래스(합성 등)는 컨트롤러가 아니므로 건너뜀
            }
            if (!isFarmScopedController(controllerClass)) {
                continue;
            }
            for (Field field : controllerClass.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (fieldType.getPackageName().equals("com.smartfarm.service.service")) {
                    result.add(fieldType.getName());
                }
            }
        }
        return result;
    }

    /** 클래스 경로(있으면)와 메서드 경로를 조합해 "{farmId}"가 포함된 매핑이 하나라도 있으면 true. */
    private static boolean isFarmScopedController(Class<?> controllerClass) {
        List<String> classPrefixes = mappingPaths(controllerClass.getAnnotations());
        if (classPrefixes.isEmpty()) {
            classPrefixes = List.of("");
        }
        for (Method method : controllerClass.getDeclaredMethods()) {
            List<String> methodPaths = mappingPaths(method.getAnnotations());
            if (methodPaths.isEmpty()) {
                continue; // 매핑 애노테이션이 없는 메서드 — 엔드포인트가 아니므로 건너뜀
            }
            for (String classPrefix : classPrefixes) {
                for (String methodPath : methodPaths) {
                    if ((classPrefix + methodPath).contains("{farmId}")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 주어진 애노테이션 배열에서 Spring 매핑 애노테이션(@RequestMapping과 그 메타 애노테이션인
     * @GetMapping·@PostMapping·@PutMapping·@PatchMapping·@DeleteMapping)의 경로 값을 전부
     * 추출한다. 경로 값이 없는 매핑(예: {@code @GetMapping} 인자 없이)은 빈 문자열("")로 취급해
     * "경로 미지정 = 클래스/기본 경로 그대로"를 표현한다. 매핑 애노테이션 자체가 없으면 빈 리스트를
     * 반환해 "엔드포인트 아님"과 구분한다.
     */
    private static List<String> mappingPaths(Annotation[] annotations) {
        List<String> paths = new ArrayList<>();
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> type = annotation.annotationType();
            boolean isMapping = type.equals(RequestMapping.class) || type.isAnnotationPresent(RequestMapping.class);
            if (!isMapping) {
                continue;
            }
            String[] values = invokeValueAttribute(annotation);
            if (values.length == 0) {
                paths.add("");
            } else {
                paths.addAll(List.of(values));
            }
        }
        return paths;
    }

    private static String[] invokeValueAttribute(Annotation annotation) {
        try {
            Method valueMethod = annotation.annotationType().getMethod("value");
            Object result = valueMethod.invoke(annotation);
            return (String[]) result;
        } catch (ReflectiveOperationException e) {
            return new String[0];
        }
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
