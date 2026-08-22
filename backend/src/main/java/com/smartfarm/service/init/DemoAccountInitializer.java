package com.smartfarm.service.init;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.UserRepository;
import com.smartfarm.service.service.DemoAccountGuard;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 데모 계정 idempotent 시드 (이슈 #49, contract §4.5) — 포트폴리오 방문자 체험용.
 *
 * <ul>
 *   <li><b>데모 유저</b>: 판정 키는 이메일이 아니라 <b>is_demo=true</b>다(리뷰 P1-A — 이메일 판정은
 *       is_demo=false 선점 계정을 데모로 오인해 데모 농장 OWNER로 승격시키는 결함). 있으면 그 유저를
 *       쓰고, 없으면 email {@link DemoAccountGuard#DEMO_EMAIL}·is_demo=true로 생성한다.
 *       비밀번호는 랜덤 UUID를 인코더로 해시해 저장 — 평문은 어디에도 저장·로그하지 않으며,
 *       아무도 모르는 값이므로 비밀번호 로그인 경로(login A002)로는 사실상 진입 불가.
 *       진입은 {@code POST /api/auth/demo-login} 단일 경로다.</li>
 *   <li><b>이메일 유니크 충돌 시</b>(ux_users_email_active): 재조회해 그 행이 is_demo=true면
 *       다중 인스턴스 동시 기동의 승자 시드에 수렴(농장 시드만 재보장)하고, is_demo=false면
 *       예약 이메일 선점이므로 <b>명시 에러 로그와 함께 기동 실패(fail-fast)</b> — 침묵 채택하면
 *       is_demo=true 유저가 영영 안 생겨 demo-login이 영구 C002가 된다. 선점 가입 자체는
 *       {@code AuthService#signup}의 예약 이메일 차단(A001)이 봉쇄하고, 이 분기는 그 이전에
 *       만들어진 잔존 행에 대한 최후 방어다.</li>
 *   <li><b>데모 농장</b>: 데모 유저가 살아있는 OWNER 농장을 하나도 갖고 있지 않을 때만
 *       1개 생성(OWNER 멤버십 동반). 데모 계정은 농장 생성이 A007로 차단되므로
 *       {@code FarmService#createFarm}을 타지 않고 repository로 직접 구성한다.</li>
 * </ul>
 *
 * <p><b>왜 SmartLifecycle(phase 0)인가</b> — contract §4.5는 "데모 유저 존재는 시드가 보장"을
 * 전제로 demo-login 미존재를 C002(서버 결함)로 규정한다. ApplicationRunner는 웹 서버가 요청을
 * 받기 시작한 뒤 실행되어 기동 직후 demo-login이 C002를 받는 창이 생기므로, 기존
 * {@link PrescriptionRecoveryInitializer}와 동일하게 <b>싱글턴 초기화 완료 후 + 트래픽 수신 전</b>
 * 인 SmartLifecycle phase 0을 쓴다. 트랜잭션은 self-invocation({@code start() → seed()})에서도
 * 확실히 적용되도록 @Transactional 대신 {@link TransactionTemplate}로 명시한다(유저+농장 원자화).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoAccountInitializer implements SmartLifecycle {

    static final String DEMO_NICKNAME = "데모 계정";
    static final String DEMO_FARM_NAME = "데모 토마토 농장";

    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final FarmMemberRepository farmMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    private volatile boolean running;

    /** idempotent — 유저·농장 각각 존재 검사 후 없는 것만 생성한다(재기동·부분 실패 후 재실행 안전). */
    public void seed() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                User demoUser = userRepository.findFirstByIsDemoTrueOrderByIdAsc()
                        .orElseGet(this::createDemoUser);
                ensureDemoFarm(demoUser.getId());
            });
        } catch (DataIntegrityViolationException e) {
            resolveEmailConflict(e);
        }
    }

    /**
     * 예약 이메일 유니크 충돌 처리 — is_demo=true 행이면 동시 기동 승자에 수렴, 아니면 선점이므로
     * fail-fast(기동 실패). 충돌 트랜잭션은 이미 롤백됐으므로 새 트랜잭션에서 재조회한다.
     */
    private void resolveEmailConflict(DataIntegrityViolationException cause) {
        User existing = userRepository.findByEmail(DemoAccountGuard.DEMO_EMAIL).orElse(null);
        if (existing == null || !existing.isDemo()) {
            log.error("데모 계정 시드 실패 — 예약 이메일 {} 이 is_demo=false 계정에 선점됨(또는 판정 불가). "
                            + "이대로 기동하면 demo-login이 영구 C002가 되므로 기동을 중단한다(리뷰 P1-A). "
                            + "선점 행을 정리(이메일 변경/삭제)한 뒤 재기동할 것.",
                    DemoAccountGuard.DEMO_EMAIL);
            throw new IllegalStateException("데모 계정 시드 실패: 예약 이메일 선점 — 기동 중단", cause);
        }
        // 다중 인스턴스 동시 기동 — 승자가 만든 데모 유저에 수렴, 농장 시드만 재보장
        log.info("데모 계정 시드 — 동시 기동 충돌 감지, 기존 데모 유저(userId={})에 수렴", existing.getId());
        transactionTemplate.executeWithoutResult(status -> ensureDemoFarm(existing.getId()));
    }

    private User createDemoUser() {
        // saveAndFlush — 이메일 유니크 충돌을 이 트랜잭션 안에서 즉시 표면화(커밋 시점 지연 금지)
        User demoUser = userRepository.saveAndFlush(User.builder()
                .email(DemoAccountGuard.DEMO_EMAIL)
                // 랜덤 UUID 해시 — 평문 미저장·미로그(아무도 모르는 값 = 비밀번호 로그인 불가)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .nickname(DEMO_NICKNAME)
                .isDemo(true)
                .build());
        log.info("데모 계정 시드 — 데모 유저 생성 userId={}", demoUser.getId());
        return demoUser;
    }

    private void ensureDemoFarm(Long demoUserId) {
        if (farmMemberRepository.existsLiveFarmMembershipByUserIdAndRole(demoUserId, FarmRole.OWNER)) {
            return;
        }
        Farm farm = farmRepository.save(Farm.builder()
                .name(DEMO_FARM_NAME)
                .cropType(CropType.TOMATO)
                .build());
        farmMemberRepository.save(FarmMember.builder()
                .farmId(farm.getId())
                .userId(demoUserId)
                .role(FarmRole.OWNER)
                .build());
        log.info("데모 계정 시드 — 데모 농장 생성 farmId={}", farm.getId());
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

    /** phase 0 — Boot 웹서버 lifecycle(phase ≫ 0)보다 먼저 = demo-login 트래픽 수신 전 시드 보장. */
    @Override
    public int getPhase() {
        return 0;
    }
}
