// 디자인 시안 프리뷰(이슈 #83)용 목업 데이터.
// 값은 전부 `스마트팜 플랫폼.dc.html` 시안에 적힌 예시값을 그대로 옮긴 것이며 실제 API와 무관하다.
// 현 백엔드에 대응 도메인이 없는 영역(랙·층 도면, 장비 제어, 알람 규칙, 장비 레지스트리)이라
// 이 파일이 유일한 데이터 소스다. 실 API 연결 시 이 모듈만 교체하면 된다.

export type Severity = "critical" | "warning" | "done";
export type CellState = "ok" | "ok-soft" | "warning" | "critical" | "idle";

/* ── 상단 글로벌 바 / 대분류 내비 (시안 1a) ─────────────────────────────── */

export interface NavSection {
  /** `/design-preview` 하위 경로. 미구현 화면은 null → 비활성 표시 */
  href: string | null;
  label: string;
  items: string[];
}

export const NAV_SECTIONS: NavSection[] = [
  {
    href: "/design-preview/home",
    label: "대시보드",
    items: ["통합 대시보드", "농장별 현황", "랙 · 층 도면 뷰", "위젯 편집"],
  },
  {
    href: "/design-preview/control",
    label: "제어",
    items: ["복합환경제어", "양액 제어", "에너지 · 난방", "광 · 재배 레시피", "스케줄 · 자동화 규칙"],
  },
  {
    href: "/design-preview/data",
    label: "데이터",
    items: ["그래프 분석", "농장 · 층 비교", "리포트 (일 · 주 · 월)", "에너지 사용 분석", "CSV 내보내기"],
  },
  {
    href: "/design-preview/alarms",
    label: "알람",
    items: ["알람 현황", "알람 이력", "임계값 · 규칙", "수신 채널"],
  },
  {
    href: "/design-preview/services",
    label: "부가 서비스",
    items: ["AI 챗봇", "날씨 예보", "농약 정보"],
  },
  {
    href: "/design-preview/admin",
    label: "관리",
    items: ["장비 · 센서 관리", "농장 · 랙 구성", "사용자 · 권한", "계정 · 보안", "시스템 로그"],
  },
];

/** 1a 카드에만 나오는 참고용 항목(미선택) */
export const IA_UNSELECTED_NOTE = "몰리에르 선도 · 습도 계산기 (미선택 · 참고)";

export const IA_PRINCIPLES = [
  { title: "3뎁스 금지", body: "대분류 → 페이지까지 2뎁스. 세부는 페이지 안 탭으로." },
  { title: "모바일은 4개만", body: "홈 · 제어 · 알람 · 더보기. 데이터/관리는 더보기 안으로." },
  { title: "농장 전환은 전역", body: "메뉴가 아니라 상단 바에서. 어느 화면에서든 농장만 바뀜." },
];

/* ── 농장 (시안 1b 좌측) ───────────────────────────────────────────────── */

export interface MockFarm {
  name: string;
  spec: string;
  status: Severity;
  temp: string;
  humidity: string;
  co2: string;
}

export const FARMS: MockFarm[] = [
  { name: "군산 제1식물공장", spec: "12랙 · 60층 · 로메인", status: "critical", temp: "23.8°C", humidity: "68%", co2: "1,020ppm" },
  { name: "군산 제2식물공장", spec: "8랙 · 40층 · 바질", status: "done", temp: "22.1°C", humidity: "65%", co2: "980ppm" },
  { name: "익산 스마트팜", spec: "10랙 · 50층 · 딸기묘", status: "warning", temp: "25.4°C", humidity: "81%", co2: "640ppm" },
  { name: "정읍 R&D 센터", spec: "4랙 · 20층 · 시험재배", status: "done", temp: "21.9°C", humidity: "62%", co2: "1,100ppm" },
];

export const ACTIVE_FARM = FARMS[0].name;

/* ── 홈 KPI (시안 1b 상단 6칸) ─────────────────────────────────────────── */

export interface Kpi {
  label: string;
  value: string;
  unit?: string;
  note: string;
  tone: "ok" | "muted" | "alert";
}

export const HOME_KPIS: Kpi[] = [
  { label: "온도", value: "23.8", unit: "°C", note: "목표 24.0 · 정상", tone: "ok" },
  { label: "습도", value: "68", unit: "%", note: "목표 65~75", tone: "ok" },
  { label: "CO₂", value: "1,020", unit: "ppm", note: "시비 중", tone: "ok" },
  { label: "광량 PPFD", value: "218", unit: "μmol", note: "점등 06:00~22:00", tone: "muted" },
  { label: "급액 EC", value: "2.6", unit: "dS/m", note: "목표 2.2 초과", tone: "alert" },
  { label: "급액 pH", value: "5.9", note: "목표 5.8~6.2", tone: "ok" },
];

/* ── 랙 배치도 (시안 1b 가운데) ────────────────────────────────────────── */

export const RACK_COLUMNS = ["A1", "A2", "A3", "A4", "A5", "A6", "A7", "B1", "B2", "B3", "B4", "B5"];
export const RACK_ROWS = ["5층", "4층", "3층", "2층", "1층"];

/** RACK_ROWS 순서(위=5층)대로 12칸씩. 시안의 셀 색을 그대로 상태로 환산했다. */
export const RACK_CELLS: CellState[][] = [
  ["ok", "ok", "ok", "ok-soft", "ok", "ok", "idle", "ok", "ok", "ok-soft", "ok", "ok"],
  ["ok", "ok", "warning", "ok", "ok", "ok", "idle", "ok-soft", "ok", "ok", "ok", "ok"],
  ["ok", "ok-soft", "critical", "ok", "ok", "ok", "ok", "ok", "ok", "ok", "warning", "ok"],
  ["ok", "ok", "ok", "ok", "ok-soft", "ok", "ok", "ok", "ok", "ok", "ok", "ok-soft"],
  ["ok", "ok", "ok", "ok", "ok", "ok", "ok", "ok", "ok-soft", "ok", "ok", "ok"],
];

/** 시안에서 검은 아웃라인으로 선택 표시된 셀 (3층 · A3 열) */
export const RACK_SELECTED = { row: 2, col: 2 };

export const RACK_LEGEND: { state: CellState; label: string }[] = [
  { state: "ok", label: "정상" },
  { state: "warning", label: "주의" },
  { state: "critical", label: "경보" },
  { state: "idle", label: "미가동" },
];

export const SELECTED_CELL_DETAIL = {
  title: "선택 · B3랙 3층",
  badge: "경보",
  metrics: [
    { label: "EC", value: "3.1", alert: true },
    { label: "pH", value: "5.7", alert: false },
    { label: "수온", value: "20.4", alert: false },
    { label: "정식 후", value: "18일", alert: false },
  ],
};

/* ── 오늘의 제어 · 스케줄 (시안 1b 우측 하단) ──────────────────────────── */

export interface ScheduleRow {
  time: string;
  label: string;
  status: string;
  tone: "ok" | "alert" | "muted";
}

export const SCHEDULE: ScheduleRow[] = [
  { time: "06:00", label: "LED 점등 · 전 랙", status: "완료", tone: "ok" },
  { time: "09:30", label: "양액 1회차 급액", status: "완료", tone: "ok" },
  { time: "14:00", label: "양액 2회차 급액", status: "EC 이상", tone: "alert" },
  { time: "16:00", label: "CO₂ 시비 종료", status: "대기", tone: "muted" },
  { time: "22:00", label: "LED 소등 · 야간 제습", status: "대기", tone: "muted" },
];

/* ── 배너 알람 (홈 상단) ──────────────────────────────────────────────── */

export const HOME_BANNER = {
  title: "B-3랙 4층 급액 EC 3.1 dS/m — 목표 2.2 초과",
  detail: "14:06부터 지속 · 원수 혼합 밸브 점검 필요",
  mobileTitle: "B-3랙 4층 EC 3.1 초과",
  mobileDetail: "14:06부터 지속 · 밸브 점검 필요",
};

/* ── 홈 그래프 (시안 1b 하단) ─────────────────────────────────────────── */

export const TEMP_SERIES = "0,72 30,74 60,70 90,52 120,38 150,30 180,26 210,28 240,34 270,42 300,55 330,64 360,70 400,71";
export const HUMIDITY_SERIES = "0,28 30,26 60,30 90,40 120,52 150,58 180,60 210,58 240,54 270,48 300,40 330,32 360,28 400,27";
export const EC_SERIES = "0,58 40,56 80,60 120,54 160,52 200,48 240,44 280,34 320,22 360,18 400,16";
export const EC_TARGET_SERIES = "0,50 40,52 80,50 120,53 160,50 200,49 240,51 280,50 320,52 360,49 400,50";

export const HOME_SHORTCUTS = ["AI 챗봇", "날씨 예보", "농약 정보", "일일 리포트"];
export const HOME_WEATHER_NOTE = ["군산 흐림 27°C · 습도 84%", "내일 강수 60% · 제습 부하 주의"];

/* ── 제어 (시안 2a) ───────────────────────────────────────────────────── */

export interface Setpoint {
  key: string;
  label: string;
  status: string;
  statusTone: "ok" | "muted";
  value: number;
  /** 표시 단위/포맷 */
  format: (v: number) => string;
  step: number;
  target: string;
  /** 게이지 채움 % / 목표 눈금 % */
  fill: number;
  marker: number;
}

export const SETPOINTS: Setpoint[] = [
  {
    key: "temp",
    label: "온도",
    status: "정상",
    statusTone: "ok",
    value: 23.8,
    format: (v) => `${v.toFixed(1)}°`,
    step: 0.1,
    target: "목표 24.0",
    fill: 62,
    marker: 66,
  },
  {
    key: "humidity",
    label: "습도",
    status: "정상",
    statusTone: "ok",
    value: 68,
    format: (v) => `${Math.round(v)}%`,
    step: 1,
    target: "목표 65~75",
    fill: 54,
    marker: 58,
  },
  {
    key: "co2",
    label: "CO₂",
    status: "시비 중",
    statusTone: "ok",
    value: 1020,
    format: (v) => Math.round(v).toLocaleString("ko-KR"),
    step: 10,
    target: "목표 1,000",
    fill: 71,
    marker: 69,
  },
  {
    key: "ppfd",
    label: "광량 PPFD",
    status: "점등",
    statusTone: "muted",
    value: 218,
    format: (v) => `${Math.round(v)}`,
    step: 1,
    target: "목표 220",
    fill: 58,
    marker: 60,
  },
];

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
];

export const PENDING_CHANGES = [
  { title: "주간 목표온도 24.0 → 23.5", detail: "A동 전체 · 즉시 적용" },
  { title: "CO₂ 상한 1,000 → 1,100", detail: "A동 전체 · 다음 주기부터" },
];

export const CONTROL_LAST_CHANGE = { time: "14:02 김주임", what: "주간 목표온도 24.0" };

/* ── 데이터 (시안 2b) ─────────────────────────────────────────────────── */

export const DATA_RANGES = ["24시간", "7일", "30일", "기간 지정"];

export interface Metric {
  key: string;
  label: string;
  /** 선택 시 칩 색 토큰 접미사 */
  tone: "green" | "blue" | "amber" | "neutral";
  /** 선택 가능한 시계열이 있는 항목만 그래프에 그려진다 */
  series?: string;
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

/* ── 알람 (시안 2c) ───────────────────────────────────────────────────── */

export interface Alarm {
  id: string;
  severity: Severity;
  severityLabel: string;
  title: string;
  location: string;
  time: string;
  status: string;
  detail: {
    rule: string;
    current: string;
    occurred: string;
    cause: string;
  };
  history: { time: string; text: string }[];
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
    ],
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
];

export const ALARM_TOTAL = 12;
export const ALARM_STATS = [
  { label: "경보", value: "7", tone: "critical" as const },
  { label: "주의", value: "14", tone: "warning" as const },
  { label: "평균 처리", value: "21분", tone: "neutral" as const },
];

/* ── 부가 서비스 (시안 2d) ────────────────────────────────────────────── */

export interface ChatTurn {
  role: "user" | "assistant";
  text: string;
  /** 어시스턴트 답변에 끼워 넣는 미니 차트 */
  chart?: { title: string; target: string; series: string };
  /** 차트 뒤에 이어지는 문단 */
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
];

/** 제안 칩을 누르면 이어붙는 목업 응답 */
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
];

export const PESTICIDE_SOURCE = "농촌진흥청 농약안전정보시스템 연동 · 최종 갱신 08.20";

/* ── 관리 (시안 2e) ───────────────────────────────────────────────────── */

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
];

export const DEVICE_KPIS = [
  { label: "등록 장비", value: "148", tone: "neutral" as const },
  { label: "정상 통신", value: "145", tone: "ok" as const },
  { label: "통신 이상", value: "2", tone: "critical" as const },
  { label: "보정 기한 임박", value: "6", tone: "warning" as const },
];

export const DEVICE_FOOTER = "7개 그룹 · 148대";

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
  { initial: "최", name: "최민서", scope: "초대 대기 · 08.21", role: "대기", tone: "pending" },
];

export const SYSTEM_LOG = [
  { time: "14:02", text: "김재현 · 목표온도 24.0 변경" },
  { time: "13:41", text: "DH-FC8 통신 두절 감지" },
  { time: "09:12", text: "이수민 · 스케줄 2건 수정" },
];

/* ── 아트보드 목록 (프리뷰 인덱스) ────────────────────────────────────── */

export interface Artboard {
  id: string;
  href: string;
  title: string;
  caption: string;
  viewport: string;
}

export const ARTBOARDS: { turn: string; note: string; boards: Artboard[] }[] = [
  {
    turn: "메뉴 구조 + 홈 화면",
    note: "식물공장은 층·랙 단위로 환경이 갈리므로, 온실용 평면도 대신 랙 단면도를 홈의 중심에 두었습니다. 매니저는 농장 여러 곳을 오가므로 좌측에 농장 목록을 상시 노출하고, 값 하나하나보다 어느 농장 어느 층이 목표를 벗어났는지를 먼저 보여줍니다.",
    boards: [
      { id: "1a", href: "/design-preview/ia", title: "메뉴 구조 (IA)", caption: "10개 항목을 6개 대분류로", viewport: "1200" },
      { id: "1b", href: "/design-preview/home", title: "홈 · PC", caption: "랙 도면형 — 농장 목록 → 도면 → 상세", viewport: "1440" },
      { id: "1c", href: "/design-preview/home-mobile", title: "홈 · 모바일", caption: "농장 전환 + 도면 축약 + 하단 4탭", viewport: "390" },
    ],
  },
  {
    turn: "5개 메인 화면",
    note: "1a 메뉴 구조의 02~06 대분류를 실제 화면으로 옮겼습니다. 상단 글로벌 바와 좌측 서브메뉴는 1b와 동일한 규칙을 쓰고, 각 화면은 상태 요약 → 조작/목록 → 상세 순서로 좌에서 우로 읽히게 배치했습니다.",
    boards: [
      { id: "2a", href: "/design-preview/control", title: "제어", caption: "목표값 조정과 장비 수동 조작", viewport: "1280" },
      { id: "2b", href: "/design-preview/data", title: "데이터", caption: "기간·항목 선택, 다중 그래프와 비교", viewport: "1280" },
      { id: "2c", href: "/design-preview/alarms", title: "알람", caption: "통합 목록, 심각도 필터, 처리 패널", viewport: "1280" },
      { id: "2d", href: "/design-preview/services", title: "부가 서비스", caption: "AI 챗봇 중심, 날씨·농약 패널", viewport: "1280" },
      { id: "2e", href: "/design-preview/admin", title: "관리", caption: "장비·센서 목록과 사용자 권한", viewport: "1280" },
    ],
  },
];

export const DESIGN_TAGS = ["PC 1440 + 모바일 390", "식물공장 · 다농장 매니저", "화이트 + 그린 포인트", "데이터는 모두 예시값"];
