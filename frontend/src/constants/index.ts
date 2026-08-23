import type {
  DeviceKind,
  DeviceStatus,
  ErrorCode,
  EnvironmentHistoryRange,
  FarmLogType,
  NutrientStage,
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

// 장비 상태 라벨 (contract §4.10)
export const DEVICE_STATUS_LABELS: Record<DeviceStatus, string> = {
  NORMAL: "정상",
  WARNING: "주의",
  FAULT: "고장",
  OFFLINE: "통신두절",
};

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
  F003: "농장 관리자(OWNER) 권한이 필요합니다.",
  F004: "초대코드가 유효하지 않거나 만료되었습니다.",
  F005: "이미 해당 농장의 멤버입니다.",
  F006: "관리자는 농장 삭제로만 탈퇴할 수 있습니다.",
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
};
