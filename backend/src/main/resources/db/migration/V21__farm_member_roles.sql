-- V21: farm_members.role 4단계 이관 — OWNER/MEMBER 2단계 → ADMIN/OPERATOR/VIEWER/PENDING (이슈 #122)
--
-- 배경: FarmRole이 OWNER/MEMBER 2단계뿐이라 프리뷰의 4단계(관리자/제어가능/조회전용/대기)를 표현할 수
-- 없었다. role은 @Enumerated(STRING)이라 DB에 enum 상수명이 그대로 문자열로 저장된다 — 따라서 enum
-- 상수를 바꾸는 것만으로는 기존 행이 매핑 불가(IllegalArgumentException)가 되므로 이관 DML이 필수다.
-- 기존 마이그레이션(V1~V20)은 수정하지 않는다(시행된 마이그레이션 수정 금지 컨벤션).

-- ① OWNER → ADMIN. 구 OWNER의 권한(구조 CRUD·초대 발급·멤버 관리·농장 삭제)을 ADMIN이 전량 승계한다.
UPDATE farm_members SET role = 'ADMIN' WHERE role = 'OWNER';

-- ② MEMBER → OPERATOR.
-- ⚠️ VIEWER로 내리면 기능 회귀다. 현행 MEMBER는 제어를 할 수 있었다 — ControlService의
-- changeMode·enqueueChange·cancelChange·cancelAllChanges·apply가 전부 requireMember만 통과하면
-- 되는 경로였기 때문이다(2026-08-25 실측). VIEWER는 "조회만"이므로 이관 대상이 아니다.
UPDATE farm_members SET role = 'OPERATOR' WHERE role = 'MEMBER';

-- ③ 이관 누락 방어선 — 위 두 UPDATE 이후 남은 구 값이 있으면 배포를 실패시킨다.
-- 조용히 통과시키면 애플리케이션이 그 행을 읽는 순간 enum 매핑 실패로 500이 나고, 그 사용자는
-- 농장 접근이 영구 불가가 된다(가드가 멤버십 조회 단계에서 터진다).
DO $$
DECLARE stale_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO stale_count
    FROM farm_members
    WHERE role NOT IN ('ADMIN', 'OPERATOR', 'VIEWER', 'PENDING');
    IF stale_count > 0 THEN
        RAISE EXCEPTION 'V21 역할 이관 누락 — 알 수 없는 role 값이 %건 남아 있습니다', stale_count;
    END IF;
END $$;

-- ④ 값 도메인 고정 — 애플리케이션 밖(수기 SQL·데이터 복구 등)에서 알 수 없는 역할이 들어오는 것을
-- 막는다. 역할이 추가되면 이 제약도 후속 마이그레이션으로 함께 갱신해야 한다.
ALTER TABLE farm_members
    ADD CONSTRAINT ck_farm_members_role
        CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER', 'PENDING'));
