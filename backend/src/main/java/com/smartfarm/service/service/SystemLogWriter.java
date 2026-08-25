package com.smartfarm.service.service;

import com.smartfarm.service.entity.SystemLog;
import com.smartfarm.service.entity.SystemLogCategory;
import com.smartfarm.service.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SystemLogService#record}의 실제 저장을 맡는 별도 빈(이슈 #129-A) — {@link SystemLogService}와
 * 굳이 분리한 이유는 <b>같은 클래스 안의 self-invocation은 Spring AOP 프록시를 거치지 않아
 * {@code @Transactional}이 적용되지 않기 때문</b>이다(Spring 공지 사항). {@code record}가 이 빈의
 * {@link #write}를 <b>다른 빈을 통해</b> 호출해야만 아래 REQUIRES_NEW가 실제로 걸린다.
 *
 * <p>⚠️ <b>{@code record}가 REQUIRES_NEW 메서드 안에서 예외를 잡는 것만으로는 부족했다</b>(실제
 * 회귀로 발견 — {@code AlarmEventServiceIntegrationTest} 5건이 이 문제로 깨졌었다): {@code save()}가
 * 실패하면 Hibernate/JPA가 그 트랜잭션을 <b>내부적으로 rollback-only로 표시</b>한다 — 이건 Spring
 * AOP의 예외 전파 감지와 무관하게 걸리는 플래그라, 메서드 안에서 Java 예외를 잡아도 지워지지 않는다.
 * 그 결과 메서드가 정상 반환해도 트랜잭션 인터셉터가 커밋을 시도하다 rollback-only를 발견하고
 * {@code UnexpectedRollbackException}을 <b>그 자리에서</b> 던진다 — 이 예외는 메서드 본문이 아니라
 * 커밋 시점에 발생하므로 메서드 안의 try/catch로는 잡을 수 없다.
 *
 * <p>그래서 저장은 이 빈의 {@link #write}(트랜잭션 경계)가 전담하고, 예외 흡수는
 * {@link SystemLogService#record}(트랜잭션 경계 <b>밖</b>)가 전담한다 — 커밋 시점 실패든 save() 자체
 * 실패든 전부 {@code record}의 try/catch에서 잡힌다(그 호출은 더 이상 트랜잭션 프록시를 거치지
 * 않으므로 예외가 그대로 자바 예외로 올라온다).
 */
@Component
@RequiredArgsConstructor
class SystemLogWriter {

    private final SystemLogRepository systemLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(Long farmId, SystemLogCategory category, String message, Long actorId) {
        systemLogRepository.save(SystemLog.builder()
                .farmId(farmId)
                .category(category)
                .message(message)
                .actorId(actorId)
                .build());
    }
}
