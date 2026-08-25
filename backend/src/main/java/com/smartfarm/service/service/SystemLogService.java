package com.smartfarm.service.service;

import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.dto.SystemLogResponse;
import com.smartfarm.service.entity.SystemLog;
import com.smartfarm.service.entity.SystemLogCategory;
import com.smartfarm.service.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시스템 로그 기록·조회(V24, 이슈 #129-A). {@link #record}는 4곳(제어 모드 변경·멤버 초대 발급·알람
 * 이벤트 생성·장비 등록/수정)에서 호출되는 <b>부가 작업</b>이다.
 *
 * <p>⚠️ <b>기록 실패가 원 작업을 깨뜨리면 안 된다</b>(#116 리뷰에서 확립된 원칙 — 그 사이클에서는
 * 부가 작업의 예외가 스케줄러 틱 전체를 날린 사례가 있었다). 세 겹으로 격리한다:
 * <ol>
 *   <li>실제 저장은 별도 빈 {@link SystemLogWriter#write}가 {@code REQUIRES_NEW}로 별도 물리
 *       트랜잭션(별도 커넥션)을 연다 — Postgres는 트랜잭션 중 하나의 SQL이 실패하면 그 트랜잭션
 *       <b>전체</b>가 "aborted" 상태가 되어(커밋 전까지 이후 모든 SQL이 예외를 던진다) 이후 명령을
 *       거부한다. 호출측과 같은 트랜잭션을 썼다면 이 메서드 내부에서 예외를 잡아도 이미 호출측
 *       트랜잭션의 커넥션이 오염된 뒤라 그 이후의 원 작업 쓰기가 전부 실패한다.</li>
 *   <li>{@link #record}는 <b>트랜잭션 경계 밖</b>에서 그 호출을 감싸 예외를 전부 흡수한다. ⚠️ 이
 *       메서드 자체에 {@code @Transactional}을 달면 안 된다 — 실제 회귀로 발견된 문제: 저장이
 *       실패하면 Hibernate/JPA가 그 트랜잭션을 내부적으로 rollback-only로 표시하는데, 이 플래그는
 *       Spring AOP의 예외 전파 감지와 무관하게 걸려서 메서드 안에서 Java 예외를 잡아도 지워지지
 *       않는다. {@code record}에 {@code @Transactional}이 있었다면 메서드가 정상 반환해도 그
 *       트랜잭션 인터셉터가 커밋을 시도하다 rollback-only를 발견해 {@code UnexpectedRollbackException}을
 *       메서드 밖으로 던져버린다({@code AlarmEventServiceIntegrationTest} 5건이 이 문제로 깨졌던
 *       실제 회귀 — {@link SystemLogWriter} 클래스 주석에 상세 근거).</li>
 *   <li>호출측 메서드 실행이 어떤 경우에도 중단되지 않는다 — 원 작업(제어 모드 변경 등)의 트랜잭션
 *       경계 안에서 {@code record}를 호출해도, {@code record} 자신이 트랜잭션을 열지 않으므로
 *       원 작업 트랜잭션에 rollback-only가 전염될 경로가 없다.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SystemLogWriter systemLogWriter;
    private final SystemLogRepository systemLogRepository;
    private final FarmAccessGuard farmAccessGuard;

    /**
     * 로그 기록 — 실패해도 예외를 던지지 않는다(위 클래스 주석). {@code actorId}는 시스템 자동
     * 이벤트(알람 이벤트 생성)에서 null을 넘긴다.
     *
     * <p>⚠️ 이 메서드에는 절대 {@code @Transactional}을 달지 않는다 — 위 클래스 주석 참고.
     */
    public void record(Long farmId, SystemLogCategory category, String message, Long actorId) {
        try {
            systemLogWriter.write(farmId, category, message, actorId);
        } catch (RuntimeException e) {
            log.warn("시스템 로그 기록 실패(원 작업에는 영향 없음): farmId={}, category={}", farmId, category, e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemLogResponse> list(Long farmId, Long userId, SystemLogCategory category,
                                                 Pageable pageable) {
        farmAccessGuard.requireMember(farmId, userId);
        Page<SystemLog> page = category != null
                ? systemLogRepository.findByFarmIdAndCategoryOrderByOccurredAtDescIdDesc(farmId, category, pageable)
                : systemLogRepository.findByFarmIdOrderByOccurredAtDescIdDesc(farmId, pageable);
        return PageResponse.of(page.map(SystemLogResponse::from));
    }
}
