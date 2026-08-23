package com.smartfarm.service.service;

import com.smartfarm.service.dto.FarmLogRequest;
import com.smartfarm.service.dto.FarmLogResponse;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.entity.FarmLog;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작업일지 CRUD(contract §4.8, 이슈 #56). 데모 계정도 작성 가능 — 콘텐츠 생성은 진단·처방과 동일
 * 원칙이라 {@link DemoAccountGuard}를 의도적으로 적용하지 않는다(contract §4.8 명시). 수정·삭제는
 * 작성자 본인(삭제는 OWNER도 가능)만 가능해 데모 계정 간 상호 간섭도 자연히 격리된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmLogService {

    private final FarmLogRepository farmLogRepository;
    private final FarmAccessGuard farmAccessGuard;

    @Transactional
    public FarmLogResponse createLog(Long farmId, Long userId, FarmLogRequest request) {
        farmAccessGuard.requireMember(farmId, userId);

        FarmLog log = farmLogRepository.save(FarmLog.builder()
                .farmId(farmId)
                .author(userId)
                .logDate(request.logDate())
                .type(request.type())
                .memo(request.memo())
                .build());

        return FarmLogResponse.from(log);
    }

    public PageResponse<FarmLogResponse> findLogs(Long farmId, Long userId, Pageable pageable) {
        farmAccessGuard.requireMember(farmId, userId);
        Page<FarmLogResponse> page = farmLogRepository.findByFarmIdOrderByLogDateDescIdDesc(farmId, pageable)
                .map(FarmLogResponse::from);
        return PageResponse.of(page);
    }

    @Transactional
    public FarmLogResponse updateLog(Long farmId, Long userId, Long logId, FarmLogRequest request) {
        farmAccessGuard.requireMember(farmId, userId);
        FarmLog log = findLogOrThrow(farmId, logId);
        if (!log.getAuthor().equals(userId)) {
            throw new CustomException(ErrorCode.L002);
        }
        log.update(request.logDate(), request.type(), request.memo());
        return FarmLogResponse.from(log);
    }

    @Transactional
    public void deleteLog(Long farmId, Long userId, Long logId) {
        FarmAccessGuard.FarmAccess access = farmAccessGuard.requireMember(farmId, userId);
        FarmLog log = findLogOrThrow(farmId, logId);
        boolean isAuthor = log.getAuthor().equals(userId);
        boolean isOwner = access.membership().getRole() == FarmRole.OWNER;
        if (!isAuthor && !isOwner) {
            throw new CustomException(ErrorCode.L002);
        }
        farmLogRepository.delete(log);
    }

    private FarmLog findLogOrThrow(Long farmId, Long logId) {
        return farmLogRepository.findByIdAndFarmId(logId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.L001));
    }
}
