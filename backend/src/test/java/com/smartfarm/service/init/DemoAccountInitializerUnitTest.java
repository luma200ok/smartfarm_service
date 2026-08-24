package com.smartfarm.service.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.UserRepository;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시드 이메일 유니크 충돌 분기(#49 리뷰 P1-A) 결정적 단위 테스트 — 통합 환경에서는 기동 시드가
 * 항상 선행돼 "선점/동시 기동 충돌"을 재현할 수 없어, 목으로 충돌을 강제해 두 분기를 검증한다.
 * <ul>
 *   <li>충돌 후 재조회 행이 is_demo=false(예약 이메일 선점) → 기동 실패(fail-fast)</li>
 *   <li>충돌 후 재조회 행이 is_demo=true(다중 인스턴스 동시 기동) → 승자에 수렴, 농장 시드만 재보장</li>
 * </ul>
 */
class DemoAccountInitializerUnitTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FarmRepository farmRepository = mock(FarmRepository.class);
    private final FarmMemberRepository farmMemberRepository = mock(FarmMemberRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private final DemoAccountInitializer initializer = new DemoAccountInitializer(
            userRepository, farmRepository, farmMemberRepository, passwordEncoder, transactionTemplate);

    @BeforeEach
    void passThroughTransactionTemplate() {
        // 목 TransactionTemplate은 콜백을 그대로 실행(트랜잭션 경계 자체는 통합 테스트가 검증)
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(passwordEncoder.encode(any())).thenReturn("encoded-hash");
    }

    private void stubUniqueViolationOnCreate() {
        // is_demo=true 유저 부재 → 생성 시도 → 이메일 유니크(ux_users_email_active) 충돌
        when(userRepository.findFirstByIsDemoTrueOrderByIdAsc()).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("ux_users_email_active"));
    }

    @Test
    @DisplayName("충돌 후 재조회 행이 is_demo=false면(예약 이메일 선점) 기동 실패로 fail-fast한다")
    void preemptedEmailFailsFast() {
        stubUniqueViolationOnCreate();
        User preempted = User.builder()
                .email("demo@smartfarm.local").password("x").nickname("선점유저").isDemo(false).build();
        when(userRepository.findByEmail("demo@smartfarm.local")).thenReturn(Optional.of(preempted));

        assertThatThrownBy(initializer::seed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("예약 이메일 선점")
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
        verify(farmRepository, never()).save(any());
        verify(farmMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("충돌 후 재조회 행이 is_demo=false 케이스와 달리 판정 불가(행 부재)여도 fail-fast한다")
    void unresolvableConflictFailsFast() {
        stubUniqueViolationOnCreate();
        when(userRepository.findByEmail("demo@smartfarm.local")).thenReturn(Optional.empty());

        assertThatThrownBy(initializer::seed).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("충돌 후 재조회 행이 is_demo=true면(동시 기동) 승자에 수렴하고 농장 시드만 재보장한다")
    void concurrentBootConvergesToWinner() {
        stubUniqueViolationOnCreate();
        User winner = User.builder()
                .email("demo@smartfarm.local").password("x").nickname("데모 계정").isDemo(true).build();
        when(userRepository.findByEmail("demo@smartfarm.local")).thenReturn(Optional.of(winner));
        // 승자가 농장 시드까지 이미 끝낸 상태 → 추가 생성 없음
        when(farmMemberRepository.existsLiveFarmMembershipByUserIdAndRole(any(), any()))
                .thenReturn(true);

        initializer.seed(); // 예외 없이 수렴

        verify(farmMemberRepository).existsLiveFarmMembershipByUserIdAndRole(
                winner.getId(), FarmRole.ADMIN);
        verify(farmRepository, never()).save(any());
        verify(farmMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("is_demo=true 유저가 이미 있으면 이메일과 무관하게 그 유저를 쓴다(판정 키=is_demo)")
    void existingDemoUserIsUsedWithoutCreation() {
        User existingDemo = User.builder()
                .email("demo@smartfarm.local").password("x").nickname("데모 계정").isDemo(true).build();
        when(userRepository.findFirstByIsDemoTrueOrderByIdAsc()).thenReturn(Optional.of(existingDemo));
        when(farmMemberRepository.existsLiveFarmMembershipByUserIdAndRole(any(), any()))
                .thenReturn(true);

        initializer.seed();

        verify(userRepository, never()).saveAndFlush(any());
        assertThat(existingDemo.isDemo()).isTrue();
    }
}
