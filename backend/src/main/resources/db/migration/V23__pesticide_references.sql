-- V23: pesticide_references / pesticide_alerts — 농약 참조정보(이슈 #128, contract 신설 예정).
-- 실제 농촌진흥청 오픈API 키·스펙이 없어 자체 DB에 참조정보를 시드하고 조회는 provider 인터페이스
-- 뒤에 둔다(1차 구현=로컬 DB, 나중에 실 API 구현체로 교체). 기존 마이그레이션(V1~V22)은 수정하지
-- 않는다(시행된 마이그레이션 수정 금지 컨벤션).
--
-- ⚠️ 스키마만 정의한다 — 시드 데이터(참조 행)는 이 마이그레이션에 INSERT하지 않는다. 이 데이터는
-- "나중에 실 API 데이터로 교체될 스텁"이라 Flyway(불변 이력)보다 재기동마다 재평가되는 idempotent
-- Java initializer(PesticideReferenceSeeder, init/DemoAccountInitializer·PrescriptionRecoveryInitializer
-- 선례와 동일한 SmartLifecycle phase 0 패턴)가 더 적합하다고 판단했다 — 근거는 PesticideReferenceSeeder
-- 클래스 주석 참고.

CREATE TABLE pesticide_references (
    id                        BIGSERIAL    PRIMARY KEY,
    crop_type                 VARCHAR(20)  NOT NULL,
    pest_name                 VARCHAR(50)  NOT NULL,
    registered_product_count  INTEGER      NOT NULL,
    pre_harvest_interval_days INTEGER,
    note                      VARCHAR(200),
    -- 출처 표기 — 반드시 "내부 샘플 데이터"임이 드러나야 한다(실제 연동 아님). 값은
    -- PesticideReferenceSeeder가 채운다(고정 문구, DB에 임의로 다른 값이 들어가면 안 됨).
    source                    VARCHAR(200) NOT NULL,
    updated_at                TIMESTAMP    NOT NULL,

    -- 같은 작물의 같은 병해충 행은 유일해야 한다(재시드 idempotency 판단 근거이자 동시 기동 방어선).
    CONSTRAINT ux_pesticide_references_crop_pest UNIQUE (crop_type, pest_name)
);

-- 목록 조회(GET, cropType 필수 파라미터)의 주 경로.
CREATE INDEX ix_pesticide_references_crop_type ON pesticide_references (crop_type);

CREATE TABLE pesticide_alerts (
    id          BIGSERIAL    PRIMARY KEY,
    crop_type   VARCHAR(20)  NOT NULL,
    message     VARCHAR(200) NOT NULL,
    severity    VARCHAR(20)  NOT NULL,
    valid_from  TIMESTAMP    NOT NULL,
    valid_until TIMESTAMP    NOT NULL,
    -- 출처 표기(리뷰 P2 — pesticide_references와 동일 원칙) — 경보 문구가 실제 관측 기반 공식
    -- 경보처럼 읽혀서(예: "총채벌레 발생 밀도가 증가하고 있습니다") 참조정보와 동일한 오인 위험이
    -- 있다. 값은 PesticideReferenceSeeder가 채운다(고정 문구, "농촌진흥청 연동" 암시 금지).
    source      VARCHAR(500) NOT NULL,

    CONSTRAINT ck_pesticide_alerts_valid_period CHECK (valid_from < valid_until),
    -- 동시 기동 시 같은 경보 문구 중복 삽입 방지(참조 테이블과 동일 원칙 — 시간창은 재시드마다
    -- 달라질 수 있어 유니크에 포함하지 않는다, message 자체가 사실상 식별자 역할).
    CONSTRAINT ux_pesticide_alerts_crop_message UNIQUE (crop_type, message)
);

-- 유효 경보 조회(GET /alerts, cropType + 유효기간 필터)의 주 경로.
CREATE INDEX ix_pesticide_alerts_crop_type_valid_until ON pesticide_alerts (crop_type, valid_until);
