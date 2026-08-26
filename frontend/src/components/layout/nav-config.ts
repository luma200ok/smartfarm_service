// 운영 셸 IA(이슈 #133, 1단계) — 시안 핸드오프 `Global Structure` 절의 6대분류 아코디언을
// 그대로 반영한다. `/design-preview`의 NAV_SECTIONS(components/design-preview/mock.ts)는
// 목업 전용이라 재사용하지 않는다 — 여기는 실 운영 라우트(§ 이슈 #133 IA 매핑 표)만 담는다.
//
// 라우팅 구조는 미리 잡아두고 화면만 나중에 채우는 방식(핸드오프 README) — 아직 없는 화면은
// kind:"disabled"로 자리만 잡는다(2단계 이후 실제 라우트가 생기면 kind를 바꿔 채운다).

export type NavLeaf =
  | { kind: "static"; label: string; path: string }
  | { kind: "farm"; label: string; restPrefix: string; exact?: boolean }
  | { kind: "disabled"; label: string };

export interface NavGroup {
  no: string;
  key: string;
  label: string;
  items: NavLeaf[];
}

export const NAV_GROUPS: NavGroup[] = [
  {
    no: "01",
    key: "dashboard",
    label: "대시보드",
    items: [
      { kind: "static", label: "통합 대시보드", path: "/dashboard" },
      { kind: "farm", label: "농장별 현황", restPrefix: "", exact: true },
      { kind: "disabled", label: "랙 · 층 도면 뷰" },
      { kind: "disabled", label: "위젯 편집" },
    ],
  },
  {
    no: "02",
    key: "control",
    label: "제어",
    items: [
      { kind: "farm", label: "복합환경제어", restPrefix: "/control" },
      { kind: "farm", label: "양액 제어", restPrefix: "/nutrient" },
      { kind: "disabled", label: "에너지 · 난방" },
      { kind: "disabled", label: "광 · 재배 레시피" },
      { kind: "disabled", label: "스케줄 · 자동화 규칙" },
    ],
  },
  {
    no: "03",
    key: "data",
    label: "데이터",
    items: [
      { kind: "farm", label: "그래프 분석", restPrefix: "/data" },
      { kind: "farm", label: "작업일지", restPrefix: "/logs" },
      { kind: "disabled", label: "농장 · 층 비교" },
      { kind: "disabled", label: "리포트" },
      { kind: "disabled", label: "에너지 사용 분석" },
      { kind: "disabled", label: "CSV 내보내기" },
    ],
  },
  {
    no: "04",
    key: "alarm",
    label: "알람",
    items: [
      // 2단계에서 생성(이슈 #133 IA 매핑) — 전부 비활성.
      { kind: "disabled", label: "알람 현황" },
      { kind: "disabled", label: "알람 이력" },
      { kind: "disabled", label: "임계값 · 규칙" },
      { kind: "disabled", label: "수신 채널" },
    ],
  },
  {
    no: "05",
    key: "services",
    label: "부가 서비스",
    items: [
      { kind: "farm", label: "AI 챗봇", restPrefix: "/chat" },
      { kind: "farm", label: "AI 진단", restPrefix: "/diagnoses" },
      { kind: "farm", label: "AI 처방", restPrefix: "/prescriptions" },
      { kind: "disabled", label: "날씨 예보" },
      { kind: "disabled", label: "농약 정보" },
    ],
  },
  {
    no: "06",
    key: "admin",
    label: "관리",
    items: [
      { kind: "farm", label: "장비 · 센서 관리", restPrefix: "/devices" },
      { kind: "farm", label: "사용자 · 권한", restPrefix: "/members" },
      { kind: "disabled", label: "농장 · 랙 구성" },
      { kind: "disabled", label: "계정 · 보안" },
      { kind: "disabled", label: "시스템 로그" },
    ],
  },
];

/** 경로에서 farmId를 추출한다. `/farms`(목록)는 매치하지 않는다. */
export function extractFarmIdFromPath(pathname: string): string | null {
  const match = pathname.match(/^\/farms\/([^/]+)(?:\/|$)/);
  return match ? match[1] : null;
}

/** 리프의 실제 이동 대상. farm-scoped 리프는 farmId가 없으면 null(= 비활성 취급). */
export function getLeafHref(item: NavLeaf, farmId: string | null): string | null {
  if (item.kind === "disabled") return null;
  if (item.kind === "static") return item.path;
  return farmId ? `/farms/${farmId}${item.restPrefix}` : null;
}

/** 현재 pathname이 이 리프를 가리키는가(좌측 내비 활성 표시용). */
export function isLeafActive(item: NavLeaf, pathname: string, farmId: string | null): boolean {
  const href = getLeafHref(item, farmId);
  if (!href) return false;
  if (item.kind === "farm" && !item.exact) {
    return pathname === href || pathname.startsWith(`${href}/`);
  }
  return pathname === href;
}

/** 현재 pathname이 속한 대분류 key. 어느 그룹에도 속하지 않으면 null(예: /farms, /invitations). */
export function computeActiveGroupKey(pathname: string, farmId: string | null): string | null {
  for (const group of NAV_GROUPS) {
    if (group.items.some((item) => isLeafActive(item, pathname, farmId))) {
      return group.key;
    }
  }
  return null;
}

/** 상단 탭 클릭 시 이동할 그룹의 대표 화면(첫 활성 리프). 전부 비활성이면 null(예: 알람). */
export function resolveGroupHref(group: NavGroup, farmId: string | null): string | null {
  for (const item of group.items) {
    const href = getLeafHref(item, farmId);
    if (href) return href;
  }
  return null;
}
