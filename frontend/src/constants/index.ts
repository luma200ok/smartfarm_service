import type {
  AlarmComparator,
  AlarmEventLogAction,
  AlarmEventStatus,
  AlarmSeverity,
  ControllableMetric,
  DeviceKind,
  DeviceStatus,
  ErrorCode,
  EnvironmentHistoryRange,
  FarmLogType,
  FarmRole,
  NutrientStage,
  OperationMode,
  ReadingCellState,
  SensorMetric,
  WeatherSky,
} from "@/types";

// localStorage 키 — 앱 prefix = farm (docs/api-contract.md §1)
export const STORAGE_KEYS = {
  accessToken: "farmAccessToken",
  refreshToken: "farmRefreshToken",
  theme: "farmTheme",
} as const;

// 간단한 형식 체크용(엄밀한 RFC 5322 검증 아님) — 최종 검증은 백엔드 Bean Validation.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Bean Validation과 동일한 클라이언트 검증 제약 (docs/api-contract.md §4)
export const VALIDATION = {
  email: { pattern: EMAIL_PATTERN },
  password: { minLength: 8 },
  nickname: { minLength: 2, maxLength: 20 },
  farmName: { minLength: 2, maxLength: 50 },
  prescriptionQuestion: { minLength: 1, maxLength: 500 },
  farmLogMemo: { maxLength: 1000 },
  chatQuestion: { minLength: 1, maxLength: 500 },
} as const;

export const DEFAULT_ERROR_MESSAGE = "요청 처리 중 오류가 발생했습니다.";

// 농장 역할 4단계 라벨(이슈 #122/#123, contract §2) — 전 화면 공용(FarmMembers·FarmOverview·
// FarmSummaryList·FarmStatusCard 등, 로컬 ROLE_LABELS 중복 정의 금지). 서열 판정은
// lib/roles.ts를 쓰고, 이 상수는 표시 전용이다.
export const ROLE_LABELS: Record<FarmRole, string> = {
  ADMIN: "관리자",
  OPERATOR: "제어 가능",
  VIEWER: "조회 전용",
  PENDING: "대기 중",
};

// 환경 대시보드 장치명 한글 라벨 (docs/_local/handoff/fs-22-dashboard-next.md FE 범위 6)
export const DEVICE_LABELS: Record<string, string> = {
  dehumidifier: "제습기",
  humidifier: "가습기",
  cooling_fan: "쿨링팬",
  heater: "히터",
};

// 환경 시계열 기간 탭 라벨 (docs/api-contract.md §4.6, 이슈 #53)
export const ENV_HISTORY_RANGE_LABELS: Record<EnvironmentHistoryRange, string> = {
  "24h": "24시간",
  "7d": "7일",
  "30d": "30일",
};

// 작업일지 type 라벨 (docs/api-contract.md §4.8, 이슈 #56·#57)
export const FARM_LOG_TYPE_LABELS: Record<FarmLogType, string> = {
  WATERING: "물주기",
  FERTILIZING: "시비",
  PRUNING: "정지/적심",
  HARVEST: "수확",
  PEST_CONTROL: "방제",
  ETC: "기타",
};

// 날씨예보 하늘상태 라벨 (docs/api-contract.md §4.8)
export const WEATHER_SKY_LABELS: Record<WeatherSky, string> = {
  SUNNY: "맑음",
  CLOUDY: "구름많음",
  OVERCAST: "흐림",
};

// 임계치 입력 범위(서버 Bean Validation과 동일 — docs/api-contract.md §4.6). 실제 검증은 서버 C001로 판정.
export const ENV_THRESHOLD_RANGE = {
  temp: { min: -50, max: 80 },
  humidity: { min: 0, max: 100 },
} as const;

// 양액 배합 생육단계 라벨 (contract §4.9, 이슈 #64·#65). 백엔드 NutrientPresetResponse에는
// 라벨 필드가 없어(GrowthStage enum 값만 옴) FE에서 한글 라벨을 정의한다.
export const NUTRIENT_STAGE_LABELS: Record<NutrientStage, string> = {
  SEEDLING: "육묘기",
  VEGETATIVE: "영양생장기",
  FRUITING: "착과/과실비대기",
  HARVEST: "수확기",
};

// 계산 결과 화면 상시 노출 고지 문구(요구사항 — 숨김/토글 금지, contract §4.9).
export const NUTRIENT_SAFETY_NOTICE = "참고용 — 적용 전 원수 분석·현장 확인이 필요합니다.";

// 프리셋 출처 표기(백엔드 NutrientPresets.java 클래스 주석 인용 — API 응답엔 출처 필드가 없어 FE 상수로 고정).
export const NUTRIENT_PRESET_SOURCE =
  "프리셋 출처: Kroggel & Kubota (2018), OSU Extension HYG-1437 (Table 3, 4단계 Jensen/UA-CEA 처방)";

// 장비 종류 라벨 (contract §4.10, 이슈 #89 — 프리뷰 "센서/제어기/통신 장치")
export const DEVICE_KIND_LABELS: Record<DeviceKind, string> = {
  SENSOR: "센서",
  CONTROLLER: "제어기",
  GATEWAY: "통신 장치",
};

// 장비 상태 라벨 (contract §4.10 — OFF는 사이클 3에서 추가, contract §4.12)
export const DEVICE_STATUS_LABELS: Record<DeviceStatus, string> = {
  NORMAL: "정상",
  WARNING: "주의",
  FAULT: "고장",
  OFFLINE: "통신두절",
  OFF: "정지",
};

// 운전 모드 라벨 (contract §4.12)
export const OPERATION_MODE_LABELS: Record<OperationMode, string> = {
  AUTO: "자동",
  MANUAL: "수동",
};

// 제어 가능한 지표 4종(contract §4.12 — 목표값 카드가 항상 이 순서·개수로 렌더된다).
export const CONTROLLABLE_METRICS: ControllableMetric[] = ["TEMPERATURE", "HUMIDITY", "CO2", "PPFD"];

// 센서 측정 지표 라벨 (contract §4.11, 이슈 #90) — unit은 서버 응답 필드를 그대로 쓴다.
export const SENSOR_METRIC_LABELS: Record<SensorMetric, string> = {
  TEMPERATURE: "온도",
  HUMIDITY: "습도",
  CO2: "CO2",
  EC: "EC",
  PH: "pH",
  PPFD: "PPFD",
  POWER: "전력",
};

// 랙 도면·층별 비교표 상태 라벨 (contract §4.11)
export const READING_CELL_STATE_LABELS: Record<ReadingCellState, string> = {
  OK: "정상",
  WARNING: "주의",
  CRITICAL: "경보",
  IDLE: "미가동",
};

// 시계열 항목(metrics) 다중 선택 상한 (contract §4.11 — 초과 시 C001)
export const READING_METRIC_LIMIT = 4;

// ErrorCode -> 사용자 노출 메시지 (docs/api-contract.md §5)
export const ERROR_MESSAGES: Record<ErrorCode, string> = {
  C001: "입력값을 확인해주세요.",
  C002: "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
  C003: "존재하지 않는 경로입니다.",
  C004: "허용되지 않는 요청입니다.",
  A001: "이미 가입된 이메일입니다.",
  A002: "이메일 또는 비밀번호가 일치하지 않습니다.",
  A003: "로그인이 만료되었습니다. 다시 로그인해주세요.",
  A004: "인증 정보가 유효하지 않습니다. 다시 로그인해주세요.",
  A005: "접근 권한이 없습니다.",
  A007: "데모 계정에서는 이 작업을 수행할 수 없습니다.",
  F001: "농장을 찾을 수 없습니다.",
  F002: "해당 농장의 멤버가 아닙니다.",
  F003: "농장 관리자 권한이 필요합니다.",
  F004: "초대코드가 유효하지 않거나 만료되었습니다.",
  F005: "이미 해당 농장의 멤버입니다.",
  F006: "농장의 마지막 관리자는 강등하거나 제거할 수 없습니다. 다른 멤버를 관리자로 지정한 뒤 다시 시도해주세요.",
  F007: "농장 제어 권한이 필요합니다.",
  // PENDING(가입 승인 대기) 전용 — F002(멤버 아님)와 달리 "권한이 없다"가 아니라 "승인을
  // 기다리는 중"임을 알려야 한다. 이 메시지가 초대 수락 직후 화면·모든 farm-scoped 탭의
  // 진입 차단 안내를 겸한다(resolveErrorMessage 경유로 별도 분기 없이 자동 노출).
  F008: "농장 가입 승인 대기 중입니다. 관리자가 역할을 부여하면 이용할 수 있어요. 잠시 후 다시 확인하거나 농장 관리자에게 문의해주세요.",
  F009: "농장 멤버를 찾을 수 없습니다.",
  R001: "존을 찾을 수 없습니다.",
  R002: "랙을 찾을 수 없습니다.",
  R003: "층을 찾을 수 없습니다.",
  R004: "하위에 장비가 있어 처리할 수 없습니다.",
  E001: "장비를 찾을 수 없습니다.",
  E002: "이미 등록된 시리얼 번호입니다.",
  D001: "진단 이력을 찾을 수 없습니다.",
  D002: "이미지 형식 또는 크기를 확인해주세요.",
  D003: "AI 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
  P001: "처방 이력을 찾을 수 없습니다.",
  P002: "처방 생성에 실패했습니다.",
  P003: "AI 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.",
  P004: "처방 대기 한도 초과 — 진행 중인 처방이 끝나면 다시 시도해 주세요.",
  L001: "작업일지를 찾을 수 없습니다.",
  L002: "작업일지를 수정/삭제할 권한이 없습니다.",
  W001: "예보를 불러올 수 없습니다.",
  CH001: "AI 응답 생성에 실패했습니다. 잠시 후 다시 시도해주세요.",
  CH002: "AI 상담이 혼잡합니다. 잠시 후 다시 시도해주세요.",
  N001: "양액 레시피를 찾을 수 없습니다.",
  N002: "양액 레시피를 수정/삭제할 권한이 없습니다.",
  // N003은 서버가 상세 사유를 message에 담아 보낸다 — 이 값은 resolveErrorMessage의 최후
  // 폴백일 뿐, 실제 노출은 resolveNutrientCalculationErrorMessage(errorMessage.ts)가
  // err.message를 그대로 사용해 이 문구를 대체한다(코드 쪽 임의 문구로 덮지 않는다).
  N003: "배합할 수 없는 조합입니다. 입력값을 확인해주세요.",
  CT001: "대상 대기 항목을 찾을 수 없습니다.",
  CT002: "통신 두절 상태인 장비는 조작할 수 없습니다.",
  CT003: "현재 운전 모드에서는 허용되지 않는 조작입니다.",
  CT004: "적용 대기 큐가 가득 찼습니다(존당 최대 50건). 먼저 적용하거나 취소해주세요.",
  // CT005는 응답 본문에 최신 pendingChanges를 함께 실어 보낸다 — 이 문구는 최후 폴백일 뿐,
  // 실제 화면은 isQueueConflict(errorMessage.ts)로 큐를 갱신하고 재확인 안내를 별도로 보여준다.
  CT005: "다른 사용자가 대기 큐를 변경했습니다. 최신 큐를 확인한 뒤 다시 적용해주세요.",
  AL001: "알람 이벤트를 찾을 수 없습니다.",
  AL002: "현재 상태에서는 처리할 수 없는 알람입니다.",
  ALR001: "알람 규칙을 찾을 수 없습니다.",
  ALR002: "농장당 알람 규칙 상한을 초과했습니다.",
  ALR003: "비교 조건과 임계값 구성이 올바르지 않습니다.",
  ALR004: "환경 임계치 설정에서 만들어진 규칙은 임계치 설정 API로만 변경할 수 있습니다.",
};

// 알람 등급 라벨 (이슈 #116/#136). "완료"는 severity가 아니라 상태(RESOLVED)에서 파생되는
// 화면 전용 표시라 여기 포함하지 않는다 — 행 렌더링에서 status로 별도 분기한다.
export const ALARM_SEVERITY_LABELS: Record<AlarmSeverity, string> = {
  CRITICAL: "경보",
  WARNING: "주의",
};

// 알람 상태 라벨 (이슈 #116/#136).
export const ALARM_STATUS_LABELS: Record<AlarmEventStatus, string> = {
  UNACKNOWLEDGED: "미확인",
  ACKNOWLEDGED: "확인됨",
  RESOLVED: "완료",
};

// 처리 이력(timeline) 액션 라벨 — actorId는 이름이 없어 시각·행위만 표기한다(이슈 #136 핸드오프).
export const ALARM_TIMELINE_ACTION_LABELS: Record<AlarmEventLogAction, string> = {
  CREATED: "알람 발생",
  ACKNOWLEDGED: "확인 처리",
  RESOLVED: "조치 완료",
  MEMO_ADDED: "메모 추가",
};

// 알람 규칙 비교 연산자 라벨 — 백엔드 AlarmComparator#label()과 문구를 그대로 맞춘다.
export const ALARM_COMPARATOR_LABELS: Record<AlarmComparator, string> = {
  GT: "상한 초과",
  LT: "하한 미만",
  OUTSIDE_RANGE: "범위 이탈",
  ABSENT: "무응답",
};
