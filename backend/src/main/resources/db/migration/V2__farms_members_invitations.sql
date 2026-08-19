-- V2: farms, farm_members, invitations — Farm 멀티테넌시 (PostgreSQL 16)

CREATE TABLE farms (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL,
    crop_type  VARCHAR(20)  NOT NULL,
    location   VARCHAR(255),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE farm_members (
    id        BIGSERIAL PRIMARY KEY,
    farm_id   BIGINT      NOT NULL REFERENCES farms (id),
    user_id   BIGINT      NOT NULL REFERENCES users (id),
    role      VARCHAR(10) NOT NULL,
    joined_at TIMESTAMP   NOT NULL,
    -- 중복 합류 차단(같은 유저 동시 수락 race 포함)
    CONSTRAINT ux_farm_members_farm_user UNIQUE (farm_id, user_id)
);

CREATE INDEX ix_farm_members_user_id ON farm_members (user_id);

CREATE TABLE invitations (
    id         BIGSERIAL PRIMARY KEY,
    farm_id    BIGINT      NOT NULL REFERENCES farms (id),
    code       VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL
);

CREATE UNIQUE INDEX ux_invitations_code ON invitations (code);
CREATE INDEX ix_invitations_farm_id ON invitations (farm_id);
