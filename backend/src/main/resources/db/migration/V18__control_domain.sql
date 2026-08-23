-- V18: control_modes, control_setpoints, control_changes, control_apply_logs
--      제어 도메인 — 목표값·장비 조작·적용 대기 큐·비상정지(contract §4.12, 이슈 #100)
-- 기존 마이그레이션(V1~V17)은 수정하지 않는다(시행된 마이그레이션 수정 금지 컨벤션).
--
-- devices.status에 'OFF'가 추가되지만(DeviceStatus enum, §4.12 — 의도적으로 꺼진 상태) status는
-- VARCHAR(20)에 CHECK 제약이 없어 스키마 변경이 필요 없다(V14 참고).

-- 존 운전 모드 — 존당 1행. 동시에 "존 단위 직렬화"의 잠금 지점(SELECT ... FOR UPDATE 대상)이다
-- (contract §4.12 동시성 3). 미설정 존은 AUTO로 간주하므로 첫 제어 조작 시 지연 생성된다.
-- soft delete를 두지 않는다 — 존이 삭제되면 모든 제어 표면이 R001로 막혀 도달 불가하고, zone id는
-- 재사용되지 않는다. unique는 partial이 아닌 전체 unique여야 애플리케이션의
-- `INSERT ... ON CONFLICT (zone_id) DO NOTHING`(락 지점 지연 생성, 예외 없이 경합 흡수)이 성립한다.
CREATE TABLE control_modes (
    id         BIGSERIAL PRIMARY KEY,
    farm_id    BIGINT      NOT NULL REFERENCES farms (id),
    zone_id    BIGINT      NOT NULL REFERENCES zones (id),
    mode       VARCHAR(20) NOT NULL,
    updated_by BIGINT REFERENCES users (id),
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX ux_control_modes_zone_id ON control_modes (zone_id);
CREATE INDEX ix_control_modes_farm_id ON control_modes (farm_id);

-- 존×지표 목표값 — 존×지표당 1행(활성 행 대상 partial unique: 존과 함께 soft delete되므로
-- 삭제 후 재설정이 충돌하지 않아야 한다). metric은 제어 가능한 4종(TEMPERATURE·HUMIDITY·CO2·PPFD)만
-- 애플리케이션이 허용한다(SensorMetric#isControllable — CHECK로 굳히면 지표 확장 시 마이그레이션이 필요).
CREATE TABLE control_setpoints (
    id           BIGSERIAL PRIMARY KEY,
    farm_id      BIGINT           NOT NULL REFERENCES farms (id),
    zone_id      BIGINT           NOT NULL REFERENCES zones (id),
    metric       VARCHAR(20)      NOT NULL,
    target_value DOUBLE PRECISION NOT NULL,
    updated_by   BIGINT REFERENCES users (id),
    created_at   TIMESTAMP        NOT NULL,
    updated_at   TIMESTAMP,
    deleted_at   TIMESTAMP
);

CREATE UNIQUE INDEX ux_control_setpoints_zone_metric_active
    ON control_setpoints (zone_id, metric) WHERE deleted_at IS NULL;
CREATE INDEX ix_control_setpoints_farm_id ON control_setpoints (farm_id);

-- 적용 대기 큐 — 큐 = status='PENDING' 목록. 프리뷰의 로컬 큐를 서버 저장으로 승격(다중 탭·다중
-- 사용자 공유). from_value/to_value는 종류별 의미가 달라 문자열로 저장한다(SETPOINT=숫자 문자열,
-- DEVICE=DeviceStatus 이름).
CREATE TABLE control_changes (
    id         BIGSERIAL PRIMARY KEY,
    farm_id    BIGINT      NOT NULL REFERENCES farms (id),
    zone_id    BIGINT      NOT NULL REFERENCES zones (id),
    kind       VARCHAR(20) NOT NULL,
    metric     VARCHAR(20),
    device_id  BIGINT REFERENCES devices (id),
    from_value VARCHAR(50),
    to_value   VARCHAR(50) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_by BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMP   NOT NULL,
    applied_at TIMESTAMP,
    applied_by BIGINT REFERENCES users (id)
);

-- 큐 조회(존별 PENDING)·상한 판정(존당 50건)이 전부 (zone_id, status) 스캔이라 필수.
CREATE INDEX ix_control_changes_zone_id_status ON control_changes (zone_id, status);
-- 비상 정지(농장 전체 PENDING 일괄 폐기)용.
CREATE INDEX ix_control_changes_farm_id_status ON control_changes (farm_id, status);
-- 장비 soft delete 캐스케이드(그 장비를 참조하는 PENDING 폐기)용.
CREATE INDEX ix_control_changes_device_id ON control_changes (device_id);

-- 적용 이력(프리뷰 "최근 적용") — 감사 이력이라 구조 삭제와 무관하게 보존, 90일 purge 대상.
CREATE TABLE control_apply_logs (
    id         BIGSERIAL PRIMARY KEY,
    farm_id    BIGINT       NOT NULL REFERENCES farms (id),
    zone_id    BIGINT       NOT NULL REFERENCES zones (id),
    summary    VARCHAR(255) NOT NULL,
    item_count INTEGER      NOT NULL,
    applied_by BIGINT REFERENCES users (id),
    applied_at TIMESTAMP    NOT NULL
);

-- 존별 최근 이력 조회(ControlStateResponse의 최근 5건)용.
CREATE INDEX ix_control_apply_logs_zone_id_applied_at ON control_apply_logs (zone_id, applied_at DESC);
-- purge 전용 단독 인덱스 — 위 복합 인덱스는 선행 컬럼이 zone_id라 `WHERE applied_at < :cutoff`에
-- 쓸 수 없다(V9 idx_env_snapshots_captured_at·V17 idx_sensor_readings_measured_at와 동일 이유).
CREATE INDEX ix_control_apply_logs_applied_at ON control_apply_logs (applied_at);
