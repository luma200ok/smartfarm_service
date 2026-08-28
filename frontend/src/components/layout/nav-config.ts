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
      // 알람 현황(이슈 #136)만 활성화 — 나머지 3항목은 후속 이슈로 비활성 유지(이슈 #133 IA 매핑).
      { kind: "farm", label: "알람 현황", restPrefix: "/alarms" },
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
export function getLeafHref(
  item: NavLeaf,
  farmId: string | null,
): string | null {
  if (item.kind === "disabled") return null;
  if (item.kind === "static") return item.path;
  return farmId ? `/farms/${farmId}${item.restPrefix}` : null;
}

/** 현재 pathname이 이 리프를 가리키는가(좌측 내비 활성 표시용). */
export function isLeafActive(
  item: NavLeaf,
  pathname: string,
  farmId: string | null,
): boolean {
  const href = getLeafHref(item, farmId);
  if (!href) return false;
  if (item.kind === "farm" && !item.exact) {
    return pathname === href || pathname.startsWith(`${href}/`);
  }
  return pathname === href;
}

/** 현재 pathname이 속한 대분류 key. 어느 그룹에도 속하지 않으면 null(예: /farms, /invitations). */
export function computeActiveGroupKey(
  pathname: string,
  farmId: string | null,
): string | null {
  for (const group of NAV_GROUPS) {
    if (group.items.some((item) => isLeafActive(item, pathname, farmId))) {
      return group.key;
    }
  }
  return null;
}

/** 상단 탭 클릭 시 이동할 그룹의 대표 화면(첫 활성 리프). 전부 비활성이면 null(예: 알람). */
export function resolveGroupHref(
  group: NavGroup,
  farmId: string | null,
): string | null {
  for (const item of group.items) {
    const href = getLeafHref(item, farmId);
    if (href) return href;
  }
  return null;
}

// 모바일 셸 상단 바(이슈 #147) — "뒤로가기 + 화면명" 화면명을 NAV_GROUPS 라벨에서 그대로 뽑는다.
// 라벨을 여기서 새로 하드코딩하면 nav-config 라벨이 바뀔 때 모바일만 stale해지므로
// (재사용 원칙) 리프 href 매칭으로 찾는다. 매칭되는 리프가 없는 경로(농장 목록·초대코드 등)는
// 소수라 특별 케이스로 보강한다.
const SPECIAL_TITLES: { test: (pathname: string) => boolean; label: string }[] =
  [
    { test: (p) => p === "/dashboard", label: "대시보드" },
    { test: (p) => p === "/farms", label: "농장 목록" },
    { test: (p) => p === "/invitations", label: "초대코드 입력" },
  ];

export function resolveMobileTitle(
  pathname: string,
  farmId: string | null,
): string | null {
  for (const special of SPECIAL_TITLES) {
    if (special.test(pathname)) return special.label;
  }
  // isLeafActive를 재사용한다(직접 prefix 비교하면 exact:true를 무시해 "/farms/{id}"(농장별
  // 현황, exact)가 "/farms/{id}/control" 같은 하위 경로에도 잘못 매치되는 버그가 있었다).
  // 여러 리프가 매치될 수 있어(예: exact 리프 vs 하위 경로 리프) href가 가장 긴, 즉 가장
  // 구체적인 것을 고른다.
  let best: { label: string; href: string } | null = null;
  for (const group of NAV_GROUPS) {
    for (const item of group.items) {
      if (!isLeafActive(item, pathname, farmId)) continue;
      const href = getLeafHref(item, farmId);
      if (!href) continue;
      if (!best || href.length > best.href.length) {
        best = { label: item.label, href };
      }
    }
  }
  return best?.label ?? null;
}
