package com.smartfarm.service.service;

import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시뮬레이터 tick의 농장별 저장 전용(contract §4.11 사이클 2 리뷰 P2-4) — {@code SensorSimulatorService}
 * 안에서 농장 루프를 돌며 이 메서드를 self-invocation으로 호출하면 {@code @Transactional} 프록시를
 * 우회해 여전히 하나의 트랜잭션으로 묶인다({@code EnvSnapshotPurgeService} 선례와 동일한 함정). 별도
 * 빈으로 분리해 농장 하나당 트랜잭션 하나가 되게 한다 — 농장 N개 중 하나에서 예외가 나도 앞서 커밋된
 * 농장들의 데이터는 롤백되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SensorSimulatorPersistenceService {

    private final SensorReadingRepository sensorReadingRepository;

    @Transactional
    public void saveForFarm(List<SensorReading> readings) {
        if (!readings.isEmpty()) {
            sensorReadingRepository.saveAll(readings);
        }
    }
}
