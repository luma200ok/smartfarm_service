package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartfarm.service.dto.AcceptInvitationRequest;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.Invitation;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.InvitationRepository;
import com.smartfarm.service.repository.UserRepository;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * unique 제약 catch 경로 결정적 단위 테스트 — 리포지토리 목으로 DataIntegrityViolation을 강제해
 * 제약명에 따라 F005 변환/재throw가 갈리는 것을 검증한다.
 */
class InvitationServiceUnitTest {

    private static final String RAW_CODE = "unit-test-raw-code";

    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final FarmRepository farmRepository = mock(FarmRepository.class);
    private final FarmMemberRepository farmMemberRepository = mock(FarmMemberRepository.class);
    private final FarmAccessGuard farmAccessGuard = mock(FarmAccessGuard.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    // 목 기본 동작 = no-op → 데모 가드 통과 스텁(데모 차단 경로는 통합 테스트에서 검증)
    private final DemoAccountGuard demoAccountGuard = mock(DemoAccountGuard.class);

    private final InvitationService invitationService = new InvitationService(
            invitationRepository, farmRepository, farmMemberRepository, farmAccessGuard, userRepository,
            demoAccountGuard);

    private void stubAcceptUntilSave(DataIntegrityViolationException saveFailure) {
        // 진입부 유저 생존 검사(탈퇴 봉쇄 ①) 통과 스텁
        when(userRepository.findById(any())).thenReturn(Optional.of(
                User.builder().email("unit@example.com").password("x").nickname("단위유저").build()));
        Invitation invitation = Invitation.builder()
                .farmId(1L)
                .codeHash(TokenHasher.sha256(RAW_CODE))
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        Farm farm = Farm.builder().name("목 농장").cropType(CropType.TOMATO).build();
        when(invitationRepository.findByCodeHash(TokenHasher.sha256(RAW_CODE)))
                .thenReturn(Optional.of(invitation));
        when(farmRepository.findById(1L)).thenReturn(Optional.of(farm));
        when(farmMemberRepository.existsByFarmIdAndUserId(any(), any())).thenReturn(false);
        when(farmMemberRepository.saveAndFlush(any())).thenThrow(saveFailure);
    }

    @Test
    @DisplayName("unique(farm_id, user_id) 제약 위반이면 F005로 변환한다")
    void memberUniqueViolationBecomesF005() {
        stubAcceptUntilSave(new DataIntegrityViolationException("dup",
                new ConstraintViolationException("dup", new SQLException("23505"),
                        InvitationService.MEMBER_UNIQUE_CONSTRAINT)));

        assertThatThrownBy(() -> invitationService.acceptInvitation(10L, new AcceptInvitationRequest(RAW_CODE)))
                .isInstanceOf(CustomException.class)
                .extracting(t -> ((CustomException) t).getErrorCode())
                .isEqualTo(ErrorCode.F005);
    }

    @Test
    @DisplayName("다른 제약(FK 등) 위반이면 F005로 오분류하지 않고 재throw한다")
    void otherConstraintViolationIsRethrown() {
        DataIntegrityViolationException fkViolation = new DataIntegrityViolationException("fk",
                new ConstraintViolationException("fk", new SQLException("23503"),
                        "farm_members_user_id_fkey"));
        stubAcceptUntilSave(fkViolation);

        assertThatThrownBy(() -> invitationService.acceptInvitation(10L, new AcceptInvitationRequest(RAW_CODE)))
                .isSameAs(fkViolation);
    }
}
