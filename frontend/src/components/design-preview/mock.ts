// 디자인 시안 프리뷰(이슈 #83)용 목업 데이터.
// 출처 = claude.ai/design `design_handoff_smartfarm_dfx/screens.html`.
// 핸드오프 README 명시대로 화면 안의 **모든 수치·농장명·사용자명은 예시 데이터**이며
// 실제 API 스펙에 맞춰 교체해야 한다. 현 백엔드에 대응 도메인이 없는 영역
// (랙·층 도면, 장비 제어, 알람 규칙, 장비 레지스트리)이라 이 파일이 유일한 소스다.

// Severity·CellState 정의는 types/index.ts로 옮겼다(이슈 #99 리뷰 — 운영 화면이 프리뷰
// 모듈에 의존하는 걸 막기 위해 방향을 뒤집음). 이 파일은 원래 이름으로 재수출만 한다.
import type { PreviewCellState, PreviewSeverity } from "@/types";

export type Severity = PreviewSeverity;
export type CellState = PreviewCellState;

/* ── 대분류 / 서브 내비 (핸드오프 Global Structure) ─────────────────────── */

export interface NavSection {
  key: string;
  label: string;
  /** `/design-preview` 하위 대표 화면 경로 */
  href: string;
  /** 좌측 서브 내비 항목. 대표 화면(첫 항목) 외에는 디자인 미정 */
  items: string[];
}

export const NAV_SECTIONS: NavSection[] = [
  {
    key: "dashboard",
    label: "대시보드",
    href: "/design-preview/home",
    items: ["통합 대시보드", "농장별 현황", "랙 · 층 도면 뷰", "위젯 편집"],
  },
  {
    key: "control",
    label: "제어",
    href: "/design-preview/control",
    items: ["복합환경제어", "양액 제어", "에너지 · 난방", "광 · 재배 레시피", "스케줄 · 자동화 규칙"],
  },
  {
    key: "data",
    label: "데이터",
    href: "/design-preview/data",
    items: ["그래프 분석", "농장 · 층 비교", "리포트", "에너지 사용 분석", "CSV 내보내기"],
  },
  {
    key: "alarm",
    label: "알람",
    href: "/design-preview/alarms",
    items: ["알람 현황", "알람 이력", "임계값 · 규칙", "수신 채널"],
  },
  {
    key: "services",
    label: "부가 서비스",
    href: "/design-preview/services",
    items: ["AI 챗봇", "날씨 예보", "농약 정보"],
  },
  {
    key: "admin",
    label: "관리",
    href: "/design-preview/admin",
    items: ["장비 · 센서 관리", "농장 · 랙 구성", "사용자 · 권한", "계정 · 보안", "시스템 로그"],
  },
];

export const ACTIVE_FARM = "군산 제1식물공장";
export const ALL_FARMS_LABEL = "전체 농장 4곳";
export const NOW_LABEL = "2026. 08. 23 (일) 14:38";
export const UNREAD_ALARMS = 3;

/* ── 01 통합 대시보드 (홈) ─────────────────────────────────────────────── */

export const BRIEFING_PILLS = [
  { label: "조치 필요 2곳", alert: true },
  { label: "수확 예정 3랙", alert: false },
  { label: "보정 기한 임박 6대", alert: false },
];

export const FARM_SORT_KEYS = ["상태순", "이름순"];

export interface FarmCard {
  name: string;
  meta: string;
  badge: string;
  badgeTone: Severity;
  metrics: { label: string; value: string; tone: "normal" | "critical" | "warning" }[];
  /** 7일 추이 미니 막대 — height는 %, tone은 막대 색 */
  bars: { height: number; tone: "past" | "recent" | "latest" | "critical" | "warning" | "warning-mid" }[];
  summary: string;
  summaryTone: Severity;
}

export const FARM_CARDS: FarmCard[] = [
  {
    name: "군산 제1식물공장",
    meta: "12랙 60층 · 로메인 · 정식 18일",
    badge: "경보 1",
    badgeTone: "critical",
    metrics: [
      { label: "온도", value: "23.8°", tone: "normal" },
      { label: "습도", value: "68%", tone: "normal" },
      { label: "EC", value: "2.6", tone: "critical" },
    ],
    bars: [
      { height: 52, tone: "past" },
      { height: 64, tone: "past" },
      { height: 58, tone: "past" },
      { height: 71, tone: "recent" },
      { height: 66, tone: "recent" },
      { height: 80, tone: "latest" },
      { height: 100, tone: "critical" },
    ],
    summary: "B3랙 4층 급액 EC 3.1 초과 · 32분 경과",
    summaryTone: "critical",
  },
  {
    name: "익산 스마트팜",
    meta: "10랙 50층 · 딸기묘 · 정식 9일",
    badge: "주의 2",
    badgeTone: "warning",
    metrics: [
      { label: "온도", value: "25.4°", tone: "normal" },
      { label: "습도", value: "81%", tone: "warning" },
      { label: "EC", value: "1.9", tone: "normal" },
    ],
    bars: [
      { height: 44, tone: "past" },
      { height: 50, tone: "past" },
      { height: 62, tone: "past" },
      { height: 74, tone: "warning-mid" },
      { height: 86, tone: "warning" },
      { height: 78, tone: "warning-mid" },
      { height: 84, tone: "warning" },
    ],
    summary: "야간 습도 3일 연속 상한 근접 · 제습 스케줄 검토",
    summaryTone: "warning",
  },
  {
    name: "군산 제2식물공장",
    meta: "8랙 40층 · 바질 · 정식 24일",
    badge: "정상",
    badgeTone: "done",
    metrics: [
      { label: "온도", value: "22.1°", tone: "normal" },
      { label: "습도", value: "65%", tone: "normal" },
      { label: "EC", value: "2.2", tone: "normal" },
    ],
    bars: [
      { height: 58, tone: "recent" },
      { height: 62, tone: "recent" },
      { height: 60, tone: "recent" },
      { height: 64, tone: "recent" },
      { height: 61, tone: "recent" },
      { height: 66, tone: "latest" },
      { height: 63, tone: "latest" },
    ],
    summary: "C동 2랙 08.25 수확 예정 · 준비 필요",
    summaryTone: "done",
  },
  {
    name: "정읍 R&D 센터",
    meta: "4랙 20층 · 시험재배 6구",
    badge: "정상",
    badgeTone: "done",
    metrics: [
      { label: "온도", value: "21.9°", tone: "normal" },
      { label: "습도", value: "62%", tone: "normal" },
      { label: "EC", value: "2.0", tone: "normal" },
    ],
    bars: [
      { height: 48, tone: "recent" },
      { height: 52, tone: "recent" },
      { height: 50, tone: "recent" },
      { height: 54, tone: "recent" },
      { height: 56, tone: "recent" },
      { height: 53, tone: "latest" },
      { height: 55, tone: "latest" },
    ],
    summary: "시험구 3 광량 조건 변경 2일차 · 이상 없음",
    summaryTone: "done",
  },
];

/* 랙 배치도 — 12랙 × 5층 (위=5층) */
export const RACK_COLUMNS = ["A1", "A2", "A3", "A4", "A5", "A6", "A7", "B1", "B2", "B3", "B4", "B5"];
export const RACK_ROWS = ["5층", "4층", "3층", "2층", "1층"];

export const RACK_CELLS: CellState[][] = [
  ["ok", "ok", "ok", "ok-soft", "ok", "ok", "idle", "ok", "ok", "ok-soft", "ok", "ok"],
  ["ok", "ok", "warning", "ok", "ok", "ok", "idle", "ok-soft", "ok", "critical", "ok", "ok"],
  ["ok", "ok-soft", "ok", "ok", "ok", "ok", "ok", "ok", "ok", "ok", "warning", "ok"],
  ["ok", "ok", "ok", "ok", "ok-soft", "ok", "ok", "ok", "ok", "ok", "ok", "ok-soft"],
  ["ok", "ok", "ok", "ok", "ok", "ok", "ok", "ok", "ok-soft", "ok", "ok", "ok"],
];

/** 시안에서 검은 아웃라인으로 선택 표시된 셀 = B3랙 4층 */
export const RACK_SELECTED = { row: 1, col: 9 };
export const RACK_SELECTED_SUMMARY = "B3랙 4층 선택됨 · EC 3.1";

export const RACK_LEGEND: { state: CellState; label: string }[] = [
  { state: "ok", label: "정상" },
  { state: "warning", label: "주의" },
  { state: "critical", label: "경보" },
  { state: "idle", label: "미가동" },
];

/* 농장 간 온도·습도 그래프 (viewBox 0 0 400 140) */
export const CROSS_FARM_SERIES = [
  {
    key: "gunsan1",
    label: "군산1",
    tone: "green" as const,
    points: "0,100 33,104 66,96 100,72 133,52 166,42 200,38 233,42 266,50 300,62 333,78 366,90 400,98",
  },
  {
    key: "iksan",
    label: "익산",
    tone: "amber" as const,
    points: "0,70 33,66 66,58 100,40 133,26 166,18 200,14 233,18 266,26 300,38 333,52 366,62 400,68",
  },
  {
    key: "gunsan2",
    label: "군산2",
    tone: "neutral" as const,
    width: 1.6,
    dashed: true,
    points: "0,116 33,118 66,112 100,98 133,84 166,78 200,74 233,78 266,84 300,94 333,104 366,112 400,116",
  },
];

export const CROSS_FARM_NOTE =
  "익산이 다른 두 곳보다 주야간 모두 2~3° 높게 유지되고 있습니다. 냉방 설정 비교가 필요합니다.";

export const TODO_ITEMS = [
  { title: "B3랙 혼합 밸브 점검", meta: "군산1 · 즉시", tone: "critical" as const },
  { title: "배기팬 통신 복구", meta: "군산1 B동 · 즉시", tone: "critical" as const },
  { title: "야간 제습 스케줄 조정", meta: "익산 · 오늘", tone: "warning" as const },
  { title: "CO₂ 센서 보정 6대", meta: "08.30까지", tone: "plain" as const },
  { title: "C동 2랙 수확 준비", meta: "군산2 · 08.25", tone: "plain" as const },
  { title: "원수 탱크 보충 확인", meta: "익산 · 오늘", tone: "plain" as const },
];

export const HOME_SHORTCUTS = ["AI 챗봇", "날씨 예보", "일일 리포트", "농약 정보"];
export const HOME_WEATHER_NOTE = ["군산 흐림 27° · 내일 강수 60%", "야간 제습 부하 증가 예상"];

/* ── 02 복합환경제어 ──────────────────────────────────────────────────── */

export const CONTROL_ZONES = ["A동", "B동", "전체"];

export interface Setpoint {
  key: string;
  label: string;
  status: string;
  statusTone: "ok" | "muted";
  value: number;
  step: number;
  target: string;
  fill: number;
  marker: number;
}

/** 전부 JSON 직렬화 가능한 순수 데이터 — 실 API 응답으로 그대로 맞바꿀 수 있다. */
export const SETPOINTS: Setpoint[] = [
  { key: "temp", label: "온도", status: "정상", statusTone: "ok", value: 23.8, step: 0.1, target: "목표 24.0", fill: 62, marker: 66 },
  { key: "humidity", label: "습도", status: "정상", statusTone: "ok", value: 68, step: 1, target: "목표 65~75", fill: 54, marker: 58 },
  { key: "co2", label: "CO₂", status: "시비 중", statusTone: "ok", value: 1020, step: 10, target: "목표 1,000", fill: 71, marker: 69 },
  { key: "ppfd", label: "광량 PPFD", status: "점등", statusTone: "muted", value: 218, step: 1, target: "목표 220", fill: 58, marker: 60 },
];

// 표시 포맷은 데이터가 아니라 표시 로직이므로 SETPOINTS 밖에 둔다.
// (데이터에 함수를 박아두면 API 응답으로 교체할 때 그대로 못 쓴다)
const SETPOINT_FORMATTERS: Record<string, (v: number) => string> = {
  temp: (v) => `${v.toFixed(1)}°`,
  humidity: (v) => `${Math.round(v)}%`,
  co2: (v) => Math.round(v).toLocaleString("ko-KR"),
  ppfd: (v) => `${Math.round(v)}`,
};

export function formatSetpoint(key: string, value: number): string {
  return (SETPOINT_FORMATTERS[key] ?? ((v: number) => String(v)))(value);
}

export interface DeviceToggle {
  key: string;
  label: string;
  onLabel: string;
  offLabel: string;
  on: boolean;
  /** 통신 두절 — 조작 불가 */
  offline?: boolean;
}

export const DEVICE_TOGGLES: DeviceToggle[] = [
  { key: "fan-a", label: "순환팬 A동", onLabel: "가동 · 60%", offLabel: "대기", on: true },
  { key: "dehumidifier", label: "제습기", onLabel: "가동", offLabel: "대기", on: false },
  { key: "co2-valve", label: "CO₂ 공급 밸브", onLabel: "개방", offLabel: "차단", on: true },
  { key: "cooler", label: "냉방기", onLabel: "가동", offLabel: "대기", on: false },
  { key: "humidifier", label: "가습기", onLabel: "가동", offLabel: "대기", on: false },
  { key: "exhaust-b", label: "배기팬 B동", onLabel: "가동", offLabel: "통신 없음", on: false, offline: true },
  { key: "fan-b", label: "순환팬 B동", onLabel: "가동 · 45%", offLabel: "대기", on: true },
  { key: "led-a", label: "LED 조광 A동", onLabel: "점등 · 88%", offLabel: "소등", on: true },
  { key: "pump-1", label: "급액 펌프 1호", onLabel: "가동", offLabel: "대기", on: true },
  { key: "pump-2", label: "급액 펌프 2호", onLabel: "가동", offLabel: "대기", on: false },
  { key: "drain-pump", label: "배액 회수 펌프", onLabel: "가동", offLabel: "대기", on: true },
  { key: "uv", label: "UV 살균 램프", onLabel: "가동", offLabel: "대기", on: false },
  { key: "damper", label: "외기 도입 댐퍼", onLabel: "열림", offLabel: "닫힘", on: false },
];

export const PENDING_CHANGES = [
  { id: "p1", title: "주간 목표온도 24.0 → 23.5", detail: "A동 전체 · 즉시 적용" },
  { id: "p2", title: "CO₂ 상한 1,000 → 1,100", detail: "A동 전체 · 다음 주기부터" },
  { id: "p3", title: "야간 습도 상한 78 → 75%", detail: "A동 전체 · 오늘 22시부터" },
  { id: "p4", title: "순환팬 최소 출력 40 → 50%", detail: "A동 1~3층 · 즉시 적용" },
];

export const CONTROL_APPLY_HISTORY = [
  { time: "14:02", text: "주간 목표온도 24.0 · 김재현", dim: false },
  { time: "11:20", text: "CO₂ 시비 종료 16시로 · 이수민", dim: false },
  { time: "09:12", text: "급액 스케줄 2건 수정 · 이수민", dim: false },
  { time: "08.22", text: "야간 제습 규칙 신규 등록 · 김재현", dim: true },
];

export const CONTROL_LAST_CHANGE = { time: "14:02 김주임", what: "주간 목표온도 24.0" };

/* ── 03 그래프 분석 ───────────────────────────────────────────────────── */

export const DATA_RANGES = ["24시간", "7일", "30일", "기간 지정"];
/** 핸드오프 Interactions: 항목 칩은 다중 선택, 최대 4개 */
export const DATA_METRIC_LIMIT = 4;

export interface Metric {
  key: string;
  label: string;
  tone: "green" | "blue" | "amber" | "neutral";
  series?: string;
  width?: number;
  dashed?: boolean;
}

export const DATA_METRICS: Metric[] = [
  {
    key: "temp",
    label: "온도",
    tone: "green",
    series:
      "0,140 25,120 50,96 75,88 100,104 125,132 150,142 175,118 200,92 225,84 250,100 275,130 300,140 325,116 350,90 375,82 400,98 425,128 450,138 475,114 500,88 525,80 550,96 575,126 600,136 625,112 650,86 675,80 700,94",
  },
  {
    key: "humidity",
    label: "습도",
    tone: "blue",
    series:
      "0,60 25,72 50,92 75,102 100,88 125,66 150,56 175,74 200,96 225,106 250,90 275,68 300,58 325,76 350,98 375,108 400,92 425,70 450,60 475,78 500,100 525,110 550,94 575,72 600,62 625,80 650,102 675,112 700,96",
  },
  {
    key: "co2",
    label: "CO₂",
    tone: "amber",
    width: 1.6,
    dashed: true,
    series:
      "0,170 25,158 50,150 75,146 100,152 125,166 150,172 175,156 200,148 225,142 250,150 275,164 300,170 325,154 350,146 375,140 400,148 425,162 450,168 475,152 500,144 525,138 550,146 575,160 600,166 625,150 650,142 675,138 700,146",
  },
  { key: "ec", label: "EC", tone: "neutral" },
  { key: "ph", label: "pH", tone: "neutral" },
  { key: "ppfd", label: "PPFD", tone: "neutral" },
  { key: "power", label: "전력", tone: "neutral" },
];

export const DATA_DEFAULT_METRICS = ["temp", "humidity", "co2"];
export const DATA_X_LABELS = ["08.17", "08.18", "08.19", "08.20", "08.21", "08.22", "08.23"];
/** 좁은 폭에서는 축 라벨 개수를 줄인다(핸드오프 Responsive 원칙) */
export const DATA_X_LABELS_NARROW = ["08.17", "08.19", "08.21", "08.23"];
export const DATA_CHART_TITLE = "08.17 – 08.23 추이";
export const DATA_CHART_SUB = "10분 평균 · 결측 0.2%";
export const DATA_ANNOTATION = "08.21 EC 경보 구간";

export interface FloorRow {
  floor: string;
  temp: string;
  humidity: string;
  ec: string;
  ppfd: string;
  deviation: string;
  tone: "ok" | "warning" | "critical";
}

export const FLOOR_COMPARISON: FloorRow[] = [
  { floor: "5층", temp: "24.4", humidity: "66", ec: "2.3", ppfd: "226", deviation: "+0.4", tone: "ok" },
  { floor: "4층", temp: "24.1", humidity: "67", ec: "2.4", ppfd: "221", deviation: "+0.1", tone: "ok" },
  { floor: "3층", temp: "23.6", humidity: "71", ec: "2.9", ppfd: "214", deviation: "+0.7", tone: "critical" },
  { floor: "2층", temp: "23.2", humidity: "73", ec: "2.4", ppfd: "209", deviation: "−0.8", tone: "warning" },
];

export const REPORTS = [
  { title: "일일 운영 리포트 08.22", format: "PDF" },
  { title: "주간 생육 · 급배액 33주", format: "PDF" },
  { title: "월간 에너지 사용 7월", format: "XLSX" },
];

export const SAVED_ANALYSES = ["주간 EC 편차 점검", "층별 광량 비교"];

/* ── 04 알람 현황 ─────────────────────────────────────────────────────── */

export interface Alarm {
  id: string;
  severity: Severity;
  severityLabel: string;
  title: string;
  location: string;
  time: string;
  status: string;
  detail: { rule: string; current: string; occurred: string; cause: string };
  history: { time: string; text: string; dim?: boolean }[];
  /** 상세 하단 참고 블록 */
  related?: string;
}

export const ALARMS: Alarm[] = [
  {
    id: "a1",
    severity: "critical",
    severityLabel: "경보",
    title: "급액 EC 상한 초과 3.1 dS/m",
    location: "군산1 · B3랙 4층",
    time: "14:06",
    status: "미확인",
    detail: {
      rule: "급액 EC > 2.8 dS/m · 5분 지속",
      current: "3.1 dS/m · 목표 2.2",
      occurred: "14:06 · 32분 경과",
      cause: "추정 원인 — 원수 혼합 밸브 응답 지연. 동일 랙에서 지난 7일간 2회 발생.",
    },
    history: [
      { time: "14:06", text: "알람 발생 · SMS 2명 발송" },
      { time: "14:09", text: "자동 대응 — 급액 일시 중지" },
      { time: "14:22", text: "이 대리 확인 · 현장 이동" },
      { time: "14:26", text: "원수 EC 재측정 1.1 dS/m 기록" },
      { time: "14:31", text: "혼합 밸브 개도 수동 40% 조정" },
      { time: "14:34", text: "급액 재개 · EC 2.9 하강 확인" },
      { time: "14:38", text: "경보 유지 중 · 목표 도달 대기", dim: true },
    ],
    related: "동일 랙 최근 발생 — 08.19 EC 초과(42분), 08.16 EC 초과(28분). 두 건 모두 밸브 조정으로 해소됨.",
  },
  {
    id: "a2",
    severity: "critical",
    severityLabel: "경보",
    title: "배기팬 통신 두절 · 응답 없음",
    location: "군산1 · B동",
    time: "13:41",
    status: "미확인",
    detail: {
      rule: "게이트웨이 응답 없음 · 3분 지속",
      current: "마지막 수신 13:41 · 42분 전",
      occurred: "13:41 · 57분 경과",
      cause: "추정 원인 — DH-FC8 제어기 전원 또는 RS-485 배선. 동일 장비 첫 발생.",
    },
    history: [
      { time: "13:41", text: "통신 두절 감지 · 자동 재시도 3회" },
      { time: "13:45", text: "알람 승격 · SMS 2명 발송" },
    ],
  },
  {
    id: "a3",
    severity: "critical",
    severityLabel: "경보",
    title: "야간 습도 90% 30분 지속",
    location: "익산 · A동 2층",
    time: "02:18",
    status: "미확인",
    detail: {
      rule: "습도 > 88% · 30분 지속",
      current: "90% · 목표 65~75",
      occurred: "02:18 · 야간 무인 시간대",
      cause: "추정 원인 — 야간 제습 스케줄 미실행. 결로 위험 구간.",
    },
    history: [{ time: "02:18", text: "알람 발생 · 야간 수신 채널 발송" }],
  },
  {
    id: "a4",
    severity: "warning",
    severityLabel: "주의",
    title: "pH 하한 근접 5.6",
    location: "군산1 · A2랙",
    time: "11:52",
    status: "확인됨",
    detail: {
      rule: "급액 pH < 5.7 · 10분 지속",
      current: "5.6 · 목표 5.8~6.2",
      occurred: "11:52 · 3시간 경과",
      cause: "추정 원인 — 산 주입 펌프 과다 토출. 원액 잔량 확인 필요.",
    },
    history: [
      { time: "11:52", text: "알람 발생" },
      { time: "12:05", text: "김재현 확인 · 경과 관찰" },
    ],
  },
  {
    id: "a5",
    severity: "warning",
    severityLabel: "주의",
    title: "냉방기 소비전력 평소 대비 +34%",
    location: "군산2 · 기계실",
    time: "10:20",
    status: "확인됨",
    detail: {
      rule: "일 소비전력 · 최근 14일 평균 대비 +30%",
      current: "+34% · 누적 182kWh",
      occurred: "10:20 · 5시간 경과",
      cause: "추정 원인 — 응축기 오염 또는 외기 온도 상승. 필터 점검 권장.",
    },
    history: [
      { time: "10:20", text: "알람 발생" },
      { time: "10:40", text: "이수민 확인 · 정비 일정 등록" },
    ],
  },
  {
    id: "a6",
    severity: "warning",
    severityLabel: "주의",
    title: "배액률 12% · 목표 20~25%",
    location: "군산1 · A동",
    time: "09:05",
    status: "확인됨",
    detail: {
      rule: "일 배액률 < 15%",
      current: "12% · 목표 20~25",
      occurred: "09:05 · 6시간 경과",
      cause: "추정 원인 — 급액량 부족 또는 배액 라인 막힘.",
    },
    history: [
      { time: "09:05", text: "알람 발생" },
      { time: "09:30", text: "김재현 확인 · 급액량 상향 예약" },
    ],
  },
  {
    id: "a7",
    severity: "done",
    severityLabel: "완료",
    title: "CO₂ 센서 보정 필요",
    location: "정읍 · R1랙",
    time: "08.22",
    status: "조치완료",
    detail: {
      rule: "보정 주기 초과 · 180일",
      current: "보정 완료 08.22",
      occurred: "08.22 · 종료",
      cause: "정기 보정 알림. 현장 보정 후 자동 해제.",
    },
    history: [
      { time: "08.22", text: "알람 발생" },
      { time: "08.22", text: "박한결 보정 완료 · 알람 해제" },
    ],
  },
  {
    id: "a8",
    severity: "done",
    severityLabel: "완료",
    title: "LED 점등 스케줄 미실행",
    location: "군산2 · C동",
    time: "08.22",
    status: "조치완료",
    detail: {
      rule: "스케줄 실행 실패 · 재시도 2회",
      current: "수동 점등 후 정상화",
      occurred: "08.22 · 종료",
      cause: "추정 원인 — 조광 제어기 일시 무응답. 재기동 후 정상.",
    },
    history: [
      { time: "08.22", text: "스케줄 실패 감지" },
      { time: "08.22", text: "이수민 수동 점등 · 알람 해제" },
    ],
  },
  {
    id: "a9",
    severity: "warning",
    severityLabel: "주의",
    title: "랙 간 온도 편차 2.4°C",
    location: "군산1 · A동",
    time: "08:36",
    status: "확인됨",
    detail: {
      rule: "동일 동 랙 간 편차 > 2.0°C · 30분 지속",
      current: "2.4°C · 최대 A1랙, 최소 A7랙",
      occurred: "08:36 · 6시간 경과",
      cause: "추정 원인 — A7랙 미가동으로 인한 기류 불균형. 순환팬 출력 재배분 검토.",
    },
    history: [
      { time: "08:36", text: "알람 발생" },
      { time: "08:50", text: "이수민 확인 · 순환팬 출력 조정 예약" },
    ],
  },
  {
    id: "a10",
    severity: "warning",
    severityLabel: "주의",
    title: "원수 탱크 수위 22% · 보충 필요",
    location: "익산 · 양액기실",
    time: "07:14",
    status: "확인됨",
    detail: {
      rule: "원수 탱크 수위 < 25%",
      current: "22% · 잔여 약 1.5일분",
      occurred: "07:14 · 7시간 경과",
      cause: "추정 원인 — 정상 소모. 보충 일정 등록 필요.",
    },
    history: [
      { time: "07:14", text: "알람 발생" },
      { time: "07:40", text: "박한결 확인 · 보충 요청 접수" },
    ],
  },
  {
    id: "a11",
    severity: "done",
    severityLabel: "완료",
    title: "양액 혼합기 재기동",
    location: "군산1 · 양액기실",
    time: "08.21",
    status: "조치완료",
    detail: {
      rule: "혼합기 응답 없음 · 2분 지속",
      current: "재기동 후 정상",
      occurred: "08.21 · 종료",
      cause: "추정 원인 — 인버터 일시 트립. 재기동으로 해소.",
    },
    history: [
      { time: "08.21", text: "알람 발생 · 자동 재기동 시도" },
      { time: "08.21", text: "정상 복귀 확인 · 알람 해제" },
    ],
  },
  {
    id: "a12",
    severity: "done",
    severityLabel: "완료",
    title: "게이트웨이 재접속 · 5분 결측",
    location: "군산2 · 기계실",
    time: "08.21",
    status: "조치완료",
    detail: {
      rule: "게이트웨이 응답 없음 · 3분 지속",
      current: "재접속 완료 · 결측 5분",
      occurred: "08.21 · 종료",
      cause: "추정 원인 — 상위 네트워크 순단. 결측 구간은 보간하지 않음.",
    },
    history: [
      { time: "08.21", text: "통신 두절 감지" },
      { time: "08.21", text: "자동 재접속 성공 · 알람 해제" },
    ],
  },
  {
    id: "a13",
    severity: "warning",
    severityLabel: "주의",
    title: "CO₂ 목표 미달 780ppm · 20분",
    location: "정읍 · R2랙",
    time: "08.21",
    status: "확인됨",
    detail: {
      rule: "CO₂ < 850ppm · 15분 지속",
      current: "780ppm · 목표 1,000",
      occurred: "08.21 · 종료 대기",
      cause: "추정 원인 — 시비 밸브 개도 부족 또는 외기 도입 과다.",
    },
    history: [
      { time: "08.21", text: "알람 발생" },
      { time: "08.21", text: "정다은 확인 · 밸브 개도 상향" },
    ],
  },
  {
    id: "a14",
    severity: "done",
    severityLabel: "완료",
    title: "배액 EC 센서 세척",
    location: "익산 · A동",
    time: "08.20",
    status: "조치완료",
    detail: {
      rule: "센서 드리프트 감지 · 기준값 대비 ±0.3",
      current: "세척 후 재보정 완료",
      occurred: "08.20 · 종료",
      cause: "정기 점검 항목. 세척 후 정상 범위 복귀.",
    },
    history: [
      { time: "08.20", text: "드리프트 감지" },
      { time: "08.20", text: "한지우 세척·재보정 · 알람 해제" },
    ],
  },
];

export const ALARM_FILTERS = [
  { key: "all" as const, label: "전체 12", tone: "neutral" as const },
  { key: "critical" as const, label: "경보 3", tone: "critical" as const },
  { key: "warning" as const, label: "주의 5", tone: "warning" as const },
  { key: "done" as const, label: "완료 4", tone: "neutral" as const },
];

export const ALARM_FOOTER = "최근 7일 14건 전체 표시";
export const ALARM_STATS = [
  { label: "경보", value: "7", tone: "critical" as const },
  { label: "주의", value: "14", tone: "warning" as const },
  { label: "평균 처리", value: "21분", tone: "neutral" as const },
];

/* ── 05 부가 서비스 ───────────────────────────────────────────────────── */

export interface ChatTurn {
  role: "user" | "assistant";
  text: string;
  chart?: { title: string; target: string; series: string };
  tail?: string;
}

export const CHAT_OPENING: ChatTurn[] = [
  { role: "user", text: "B3랙 EC가 왜 계속 올라가?" },
  {
    role: "assistant",
    text: "지난 7일 B3랙 급액 EC는 목표 2.2 대비 평균 +0.35 높았고, 오늘 14:06에 3.1까지 올랐습니다. 같은 시간대 원수 혼합 밸브 개도가 3회 연속 지연 응답했습니다.",
    chart: {
      title: "B3랙 EC · 7일",
      target: "목표 2.2",
      series: "0,36 43,34 86,38 129,32 172,28 215,22 258,14 300,8",
    },
    tail: "확인할 점 세 가지를 제안합니다. 1) 혼합 밸브 구동부 점검, 2) 원수 EC 재측정, 3) 배액 EC와의 차이 확인.",
  },
  { role: "user", text: "배액 EC랑 차이는 얼마야?" },
  {
    role: "assistant",
    text: "오늘 B3랙의 급액 EC 평균은 2.7, 배액 EC 평균은 3.4로 차이가 0.7입니다. 지난 7일 평균 차이 0.35의 두 배입니다. 배액률도 같은 기간 20~25% 목표 대비 12%로 낮습니다.",
    tail: "급액량이 부족해 배지 내 염류가 쌓이는 흐름으로 보입니다. 급액 1회당 공급량을 늘려 배액률을 먼저 회복시키는 편을 권합니다.",
  },
];

export const CHAT_SUGGESTIONS: { label: string; reply: ChatTurn }[] = [
  {
    label: "배액 EC 보여줘",
    reply: {
      role: "assistant",
      text: "B3랙 배액 EC는 오늘 평균 3.6 dS/m으로 급액 대비 +0.5입니다. 배액률이 12%까지 떨어져 염류가 배지에 남고 있습니다. 급액량을 20% 올려 배액률을 20~25%로 회복시키는 편이 먼저입니다.",
    },
  },
  {
    label: "밸브 점검 이력",
    reply: {
      role: "assistant",
      text: "원수 혼합 밸브(DH-NX #2)는 최근 90일간 점검 기록이 없습니다. 지연 응답은 08.17·08.21·08.23 세 차례 기록됐고 모두 2회차 급액 시간대였습니다. 구동부 점검을 정비 일정에 등록할까요?",
    },
  },
  {
    label: "담당자에게 공유",
    reply: {
      role: "assistant",
      text: "이수민(제어 가능)에게 이 대화 요약과 B3랙 EC 7일 그래프를 보냈습니다. 알람 #a1 처리 이력에도 공유 기록이 남습니다.",
    },
  },
];

export const CHAT_PLACEHOLDER = "농장 상태나 데이터에 대해 물어보세요";
export const CHAT_SIDE_NOTE = "챗봇은 이 농장의 실시간 센서값과 최근 30일 이력을 참조해 답합니다.";

export const WEATHER = {
  place: "군산시 미원동",
  temp: "27°",
  summary: ["흐림 · 체감 30°", "습도 84% · 남서풍 3m/s"],
  hourly: [
    { time: "15시", temp: "27°", night: false },
    { time: "18시", temp: "25°", night: false },
    { time: "21시", temp: "23°", night: true },
    { time: "00시", temp: "22°", night: true },
    { time: "03시", temp: "21°", night: false },
  ],
  note: "내일 강수 60% · 야간 외기 습도 상승. 제습 부하 증가가 예상됩니다.",
};

export const PESTICIDES = [
  { name: "상추 노균병", detail: "등록 약제 12종 · 수확 전 7일" },
  { name: "상추 잿빛곰팡이병", detail: "등록 약제 8종 · 수확 전 3일" },
  { name: "총채벌레", detail: "등록 약제 15종 · 천적 병행 가능" },
  { name: "상추 균핵병", detail: "등록 약제 6종 · 수확 전 5일" },
  { name: "뿌리썩음병", detail: "등록 약제 4종 · 관주 처리" },
  { name: "작은뿌리파리", detail: "등록 약제 9종 · 수확 전 3일" },
  { name: "진딧물", detail: "등록 약제 18종 · 수확 전 2일" },
  { name: "시들음병", detail: "등록 약제 5종 · 토양 소독 권장" },
];

export const PESTICIDE_ALERTS = [
  {
    text: "고온 다습 지속 — 잿빛곰팡이병 발생 조건. 야간 습도 85% 이상 구간 점검 권장",
    tone: "warning" as const,
  },
  { text: "군산1 A동 08.19 총채벌레 예방 처리 · 다음 처리 08.26", tone: "plain" as const },
];

export const PESTICIDE_SOURCE = "농촌진흥청 농약안전정보시스템 연동 · 최종 갱신 08.20";

/* ── 06 장비 · 센서 관리 ──────────────────────────────────────────────── */

export type DeviceKind = "센서" | "제어기" | "통신 장치";

export interface DeviceRow {
  name: string;
  count: string;
  kind: DeviceKind;
  location: string;
  lastSeen: string;
  lastSeenAlert?: boolean;
  calibration: string;
  calibrationWarn?: boolean;
  status: string;
  statusTone: "ok" | "warning" | "critical";
}

export const DEVICES: DeviceRow[] = [
  { name: "온습도 센서 DH-T200", count: "24 EA", kind: "센서", location: "A동 전 랙", lastSeen: "8초 전", calibration: "09.14", status: "정상", statusTone: "ok" },
  { name: "CO₂ 센서 DH-C50", count: "12 EA", kind: "센서", location: "A동 · B동", lastSeen: "11초 전", calibration: "08.30", calibrationWarn: true, status: "보정 필요", statusTone: "warning" },
  { name: "EC/pH 복합 센서 DH-EP", count: "8 EA", kind: "센서", location: "양액기실", lastSeen: "6초 전", calibration: "09.02", status: "정상", statusTone: "ok" },
  { name: "배기팬 제어기 DH-FC8", count: "1 EA", kind: "제어기", location: "B동", lastSeen: "42분 전", lastSeenAlert: true, calibration: "10.01", status: "통신 없음", statusTone: "critical" },
  { name: "LED 조광 제어기 DH-LD", count: "60 EA", kind: "제어기", location: "전 랙", lastSeen: "9초 전", calibration: "—", status: "정상", statusTone: "ok" },
  { name: "양액 혼합기 DH-NX", count: "2 EA", kind: "제어기", location: "양액기실", lastSeen: "7초 전", calibration: "09.20", status: "정상", statusTone: "ok" },
  { name: "게이트웨이 DH-GW2", count: "3 EA", kind: "통신 장치", location: "기계실", lastSeen: "4초 전", calibration: "—", status: "정상", statusTone: "ok" },
  { name: "유량 센서 DH-FL20", count: "10 EA", kind: "센서", location: "급배액 배관", lastSeen: "10초 전", calibration: "09.01", calibrationWarn: true, status: "보정 필요", statusTone: "warning" },
  { name: "수위 센서 DH-WL", count: "6 EA", kind: "센서", location: "원수 · 배액 탱크", lastSeen: "12초 전", calibration: "10.11", status: "정상", statusTone: "ok" },
  { name: "순환팬 제어기 DH-FC4", count: "16 EA", kind: "제어기", location: "A동 · B동", lastSeen: "9초 전", calibration: "—", status: "정상", statusTone: "ok" },
  { name: "전력 계측기 DH-PM", count: "4 EA", kind: "센서", location: "배전반", lastSeen: "15초 전", calibration: "11.30", status: "정상", statusTone: "ok" },
  { name: "CCTV 브릿지 DH-CV", count: "2 EA", kind: "통신 장치", location: "A동 통로", lastSeen: "2시간 전", lastSeenAlert: true, calibration: "—", status: "통신 없음", statusTone: "critical" },
  { name: "난방기 제어기 DH-HT", count: "2 EA", kind: "제어기", location: "기계실", lastSeen: "6초 전", calibration: "12.02", status: "정상", statusTone: "ok" },
];

export const DEVICE_KINDS: ("전체" | DeviceKind)[] = ["전체", "센서", "제어기", "통신 장치"];

export const DEVICE_KPIS = [
  { label: "등록 장비", value: "148", tone: "neutral" as const },
  { label: "정상 통신", value: "145", tone: "ok" as const },
  { label: "통신 이상", value: "2", tone: "critical" as const },
  { label: "보정 기한 임박", value: "6", tone: "warning" as const },
];

export const DEVICE_TOTAL_LABEL = "13개 그룹 · 148대";

export interface MemberRow {
  initial: string;
  name: string;
  scope: string;
  role: string;
  tone: "admin" | "member" | "pending";
}

export const MEMBERS: MemberRow[] = [
  { initial: "김", name: "김재현", scope: "전체 농장 4곳", role: "관리자", tone: "admin" },
  { initial: "이", name: "이수민", scope: "군산1 · 군산2", role: "제어 가능", tone: "member" },
  { initial: "박", name: "박한결", scope: "익산", role: "조회 전용", tone: "member" },
  { initial: "정", name: "정다은", scope: "정읍 R&D", role: "제어 가능", tone: "member" },
  { initial: "한", name: "한지우", scope: "군산2", role: "조회 전용", tone: "member" },
  { initial: "최", name: "최민서", scope: "초대 대기 · 08.21", role: "대기", tone: "pending" },
];

export const SYSTEM_LOG = [
  { time: "14:02", text: "김재현 · 목표온도 24.0 변경", dim: false },
  { time: "13:41", text: "DH-FC8 통신 두절 감지", dim: false },
  { time: "09:12", text: "이수민 · 스케줄 2건 수정", dim: false },
  { time: "08:36", text: "DH-FL20 보정 기한 알림 생성", dim: false },
  { time: "07:02", text: "일일 리포트 자동 생성 · 4농장", dim: false },
  { time: "06:00", text: "LED 점등 스케줄 정상 실행", dim: false },
  { time: "08.22", text: "최민서 계정 초대 발송 · 김재현", dim: true },
  { time: "08.22", text: "펌웨어 2.4.1 배포 · 게이트웨이 3대", dim: true },
];

/* ── 모바일 (M1~M4) ──────────────────────────────────────────────────── */

export const MOBILE_TABS = [
  { key: "home", label: "홈", href: "/design-preview/mobile/home" },
  { key: "control", label: "제어", href: "/design-preview/mobile/control" },
  { key: "alarm", label: "알람", href: "/design-preview/mobile/alarms" },
  { key: "more", label: "더보기", href: "/design-preview/mobile/more" },
];

export const M_HOME_BANNER = {
  title: "B-3랙 4층 EC 3.1 초과",
  detail: "14:06부터 지속 · 밸브 점검 필요",
};

export const M_HOME_METRICS = [
  { label: "온도", value: "23.8°", alert: false },
  { label: "습도", value: "68%", alert: false },
  { label: "CO₂", value: "1,020", alert: false },
  { label: "PPFD", value: "218", alert: false },
  { label: "EC", value: "2.6", alert: true },
  { label: "pH", value: "5.9", alert: false },
];

export const M_HOME_SCHEDULE = [
  { time: "14:00", label: "양액 2회차", status: "EC 이상", alert: true },
  { time: "16:00", label: "CO₂ 시비 종료", status: "대기", alert: false },
];

export const M_CONTROL_SECONDARY = [
  { label: "습도", status: "정상", statusTone: "ok" as const, value: "68%", target: "목표 65~75", alert: false },
  { label: "CO₂", status: "시비", statusTone: "ok" as const, value: "1,020", target: "목표 1,000", alert: false },
  { label: "PPFD", status: "점등", statusTone: "muted" as const, value: "218", target: "목표 220", alert: false },
  { label: "급액 EC", status: "초과", statusTone: "alert" as const, value: "2.6", target: "목표 2.2", alert: true },
];

export const M_CONTROL_DEVICES = [
  { key: "fan-a", label: "순환팬 A동", state: "60%", tone: "ok" as const, on: true },
  { key: "dehumidifier", label: "제습기", state: "대기", tone: "muted" as const, on: false },
  { key: "exhaust-b", label: "배기팬 B동", state: "통신 없음", tone: "alert" as const, on: false, offline: true },
];

export interface MobileAlarmCard {
  severity: Severity;
  severityLabel: string;
  time: string;
  title: string;
  meta: string;
  /** 미확인 최상위 알람에만 액션 버튼 노출 */
  primary?: boolean;
}

export const M_ALARMS: MobileAlarmCard[] = [
  {
    severity: "critical",
    severityLabel: "경보",
    time: "14:06 · 32분",
    title: "급액 EC 상한 초과 3.1 dS/m",
    meta: "군산1 · B3랙 4층 · 원수 혼합 밸브 점검 필요",
    primary: true,
  },
  { severity: "critical", severityLabel: "경보", time: "13:41", title: "배기팬 통신 두절", meta: "군산1 · B동" },
  { severity: "warning", severityLabel: "주의", time: "11:52", title: "pH 하한 근접 5.6", meta: "군산1 · A2랙 · 확인됨" },
  { severity: "warning", severityLabel: "주의", time: "10:20", title: "냉방기 소비전력 +34%", meta: "군산2 · 기계실 · 확인됨" },
  { severity: "done", severityLabel: "완료", time: "08.22", title: "CO₂ 센서 보정 필요", meta: "정읍 · R1랙 · 조치완료" },
];

export const M_ALARM_FILTERS = ["전체 12", "경보 3", "주의 5", "완료 4"];

export interface MoreItem {
  label: string;
  /** 우측 배지/값 */
  badge?: { text: string; tone: "green" | "red" | "plain" };
  highlight?: boolean;
}

export const M_MORE_GROUPS: { title: string; items: MoreItem[] }[] = [
  {
    title: "데이터",
    items: [
      { label: "그래프 분석" },
      { label: "농장 · 층 비교" },
      { label: "리포트", badge: { text: "신규 1", tone: "green" } },
    ],
  },
  {
    title: "부가 서비스",
    items: [
      { label: "AI 챗봇", highlight: true },
      { label: "날씨 예보", badge: { text: "27° 흐림", tone: "plain" } },
      { label: "농약 정보" },
    ],
  },
  {
    title: "관리",
    items: [
      { label: "장비 · 센서", badge: { text: "이상 2", tone: "red" } },
      { label: "농장 · 랙 구성" },
      { label: "사용자 · 권한" },
      { label: "계정 · 보안" },
    ],
  },
];

export const M_MORE_USER = { name: "김재현", meta: "관리자 · 전체 농장 4곳" };
export const M_MORE_FOOTER = ["앱 버전 2.4.1 · 통신 정상", "최종 수신 8초 전"];

/* ── 프리뷰 인덱스 ────────────────────────────────────────────────────── */

export interface Artboard {
  id: string;
  href: string;
  title: string;
  caption: string;
  viewport: string;
}

export const ARTBOARDS: { group: string; boards: Artboard[] }[] = [
  {
    group: "PC · 1920 × 1080",
    boards: [
      { id: "01", href: "/design-preview/home", title: "통합 대시보드 (홈)", caption: "4개 농장 비교 → 선택 농장의 랙 상태", viewport: "1920" },
      { id: "02", href: "/design-preview/control", title: "복합환경제어", caption: "목표값 조정 · 장비 조작 · 적용 대기 큐", viewport: "1920" },
      { id: "03", href: "/design-preview/data", title: "그래프 분석", caption: "기간·항목 선택, 층별 비교, 리포트", viewport: "1920" },
      { id: "04", href: "/design-preview/alarms", title: "알람 현황", caption: "등급 필터 · 원인과 처리 이력", viewport: "1920" },
      { id: "05", href: "/design-preview/services", title: "AI 챗봇 · 날씨 · 농약", caption: "농장 데이터 참조 챗봇 + 외부 연동 패널", viewport: "1920" },
      { id: "06", href: "/design-preview/admin", title: "장비 · 센서 관리", caption: "통신·보정 상태, 사용자 권한, 시스템 로그", viewport: "1920" },
    ],
  },
  {
    group: "모바일 · 390 × 844",
    boards: [
      { id: "M1", href: "/design-preview/mobile/home", title: "홈", caption: "농장 전환 + 지표 + 도면 축약", viewport: "390" },
      { id: "M2", href: "/design-preview/mobile/control", title: "제어", caption: "온도 강조 + 하단 고정 적용 바", viewport: "390" },
      { id: "M3", href: "/design-preview/mobile/alarms", title: "알람", caption: "카드 스택 · 최상위만 액션 노출", viewport: "390" },
      { id: "M4", href: "/design-preview/mobile/more", title: "더보기", caption: "데이터·부가 서비스·관리 리스트 그룹", viewport: "390" },
    ],
  },
];

export const RESPONSIVE_TIERS = [
  {
    name: "기준",
    range: "1920 이상",
    primary: true,
    lines: ["좌측 서브 내비 240px 고정", "우측 상세 패널 400~440px 고정", "가운데 영역이 남는 폭을 흡수", "지표 카드 6열 · 농장 카드 4열", "콘텐츠 패딩 26 / 30px"],
  },
  {
    name: "노트북",
    range: "1440 – 1919",
    primary: false,
    lines: ["내비 240 → 200px", "상세 패널 400 → 340px", "지표 카드 6열 유지", "농장 카드 4열 유지", "패딩 20 / 24px"],
  },
  {
    name: "태블릿",
    range: "768 – 1439",
    primary: false,
    lines: ["서브 내비는 상단 가로 메뉴로", "상세 패널은 아래로 쌓임", "지표 카드 3열 × 2행", "농장 카드 2열", "그래프는 세로로 쌓임"],
  },
  {
    name: "모바일",
    range: "767 이하",
    primary: false,
    lines: ["전용 레이아웃(축소 아님)", "하단 4탭 · 상단 농장 전환 바", "단일 컬럼 · 지표 3열 × 2행", "목록 → 상세는 화면 이동", "터치 타깃 최소 44px"],
  },
];

export const RESPONSIVE_PRINCIPLES = [
  { title: "고정 폭은 두 곳만", body: "서브 내비와 상세 패널. 그 외는 모두 fr과 minmax로 늘어난다." },
  { title: "테이블은 열을 줄인다", body: "폭이 좁아지면 글자를 줄이지 말고 우선순위 낮은 열(발생 시각, 보정 예정)을 숨긴다." },
  { title: "높이는 고정하지 않는다", body: "1080은 설계 기준일 뿐, 카드 높이는 내용에 맞춰 늘어나고 화면은 세로 스크롤한다." },
];

export const NOT_DESIGNED = [
  "대시보드 — 농장별 현황, 랙·층 도면 전체 뷰, 위젯 편집",
  "제어 — 양액 제어, 에너지·난방, 광·재배 레시피, 스케줄·자동화 규칙",
  "데이터 — 농장·층 비교 전용, 리포트 전용, 에너지 사용 분석, CSV 내보내기 설정",
  "알람 — 알람 이력, 임계값·규칙, 수신 채널",
  "관리 — 농장·랙 구성, 사용자·권한 전용, 계정·보안, 시스템 로그 전용",
  "모바일 — 데이터 그래프, AI 챗봇, 장비 관리 상세, 설정 화면들",
];
