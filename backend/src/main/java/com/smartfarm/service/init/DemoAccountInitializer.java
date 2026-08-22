package com.smartfarm.service.init;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 데모 계정 idempotent 시드 (이슈 #49, contract §4.5) — 포트폴리오 방문자 체험용.
 *
 * <ul>
 *   <li><b>데모 유저</b>: email {@value #DEMO_EMAIL}, is_demo=true. 이미 존재하면 skip.
 *       비밀번호는 랜덤 UUID를 인코더로 해시해 저장 — 평문은 어디에도 저장·로그하지 않으며,
 *       아무도 모르는 값이므로 비밀번호 로그인 경로(login A002)로는 사실상 진입 불가.
 *       진입은 {@code POST /api/auth/demo-login} 단일 경로다.</li>
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

    /** 시드 식별값 — 시크릿 아님(비밀번호 자체가 존재하지 않는 계정, contract §4.5) */
    static final String DEMO_EMAIL = "demo@smartfarm.local";
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
        transactionTemplate.executeWithoutResult(status -> {
            User demoUser = userRepository.findByEmail(DEMO_EMAIL)
                    .orElseGet(this::createDemoUser);
            if (!farmMemberRepository.existsLiveFarmMembershipByUserIdAndRole(
                    demoUser.getId(), FarmRole.OWNER)) {
                createDemoFarm(demoUser.getId());
            }
        });
    }

    private User createDemoUser() {
        User demoUser = userRepository.save(User.builder()
                .email(DEMO_EMAIL)
                // 랜덤 UUID 해시 — 평문 미저장·미로그(아무도 모르는 값 = 비밀번호 로그인 불가)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .nickname(DEMO_NICKNAME)
                .isDemo(true)
                .build());
        log.info("데모 계정 시드 — 데모 유저 생성 userId={}", demoUser.getId());
        return demoUser;
    }

    private void createDemoFarm(Long demoUserId) {
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
