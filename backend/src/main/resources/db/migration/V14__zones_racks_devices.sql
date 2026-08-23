-- V14: zones, racks, rack_levels, devices — 랙·층 구조 + 장비/센서 레지스트리(contract §4.10, 이슈 #89)
-- + farms.planted_on — 프리뷰 농장 카드 "정식 N일" 표기용(사이클 1은 컬럼만 추가, API 노출은 후속)

ALTER TABLE farms ADD COLUMN planted_on DATE;

CREATE TABLE zones (
    id            BIGSERIAL PRIMARY KEY,
    farm_id       BIGINT      NOT NULL REFERENCES farms (id),
    name          VARCHAR(50) NOT NULL,
    display_order INTEGER     NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP,
    deleted_at    TIMESTAMP
);

CREATE INDEX ix_zones_farm_id ON zones (farm_id);

CREATE TABLE racks (
    id            BIGSERIAL PRIMARY KEY,
    zone_id       BIGINT      NOT NULL REFERENCES zones (id),
    -- farmId 비정규화 — 테넌트 스코프 쿼리용(zoneId 경유 join 없이 findByIdAndFarmId 가능, 멀티테넌트 룰)
    farm_id       BIGINT      NOT NULL REFERENCES farms (id),
    code          VARCHAR(30) NOT NULL,
    level_count   INTEGER     NOT NULL,
    display_order INTEGER     NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP,
    deleted_at    TIMESTAMP
);

CREATE INDEX ix_racks_zone_id ON racks (zone_id);
CREATE INDEX ix_racks_farm_id ON racks (farm_id);
-- 존 내 랙 코드 unique — 활성 행 대상(soft delete 이후 코드 재사용 허용, V1 ux_users_email_active와 동일 패턴)
CREATE UNIQUE INDEX ux_racks_zone_id_code_active ON racks (zone_id, code) WHERE deleted_at IS NULL;

CREATE TABLE rack_levels (
    id         BIGSERIAL PRIMARY KEY,
    rack_id    BIGINT      NOT NULL REFERENCES racks (id),
    -- farmId 비정규화 — 테넌트 스코프 쿼리용
    farm_id    BIGINT      NOT NULL REFERENCES farms (id),
    level_no   INTEGER     NOT NULL,
    label      VARCHAR(50),
    created_at TIMESTAMP   NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX ix_rack_levels_rack_id ON rack_levels (rack_id);
CREATE INDEX ix_rack_levels_farm_id ON rack_levels (farm_id);
-- 랙 내 층 번호 unique — 활성 행 대상
CREATE UNIQUE INDEX ux_rack_levels_rack_id_level_no_active ON rack_levels (rack_id, level_no) WHERE deleted_at IS NULL;

CREATE TABLE devices (
    id                  BIGSERIAL PRIMARY KEY,
    farm_id             BIGINT      NOT NULL REFERENCES farms (id),
    -- 위치 FK 3종 — 전부 nullable(게이트웨이는 존 단위, 센서는 층 단위 등). 최소 하나는 필수(C001,
    -- 애플리케이션에서 강제 — CHECK 제약으로 하면 partial update 유연성이 떨어져 서비스 계층에 둔다).
    zone_id             BIGINT      REFERENCES zones (id),
    rack_id             BIGINT      REFERENCES racks (id),
    rack_level_id       BIGINT      REFERENCES rack_levels (id),
    name                VARCHAR(50) NOT NULL,
    kind                VARCHAR(20) NOT NULL,
    model               VARCHAR(50),
    serial              VARCHAR(50),
    status              VARCHAR(20) NOT NULL,
    last_seen_at        TIMESTAMP,
    calibration_due_at  TIMESTAMP,
    installed_on        DATE,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

CREATE INDEX ix_devices_farm_id ON devices (farm_id);
CREATE INDEX ix_devices_zone_id ON devices (zone_id);
CREATE INDEX ix_devices_rack_id ON devices (rack_id);
CREATE INDEX ix_devices_rack_level_id ON devices (rack_level_id);
-- 농장 스코프 시리얼 unique — 활성 행 대상. NULL은 Postgres unique index에서 서로 중복으로 취급되지
-- 않으므로(NULL <> NULL) 별도 "AND serial IS NOT NULL" 없이도 null 허용이 자연히 성립한다.
CREATE UNIQUE INDEX ux_devices_farm_id_serial_active ON devices (farm_id, serial) WHERE deleted_at IS NULL;
