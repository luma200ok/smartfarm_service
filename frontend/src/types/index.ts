// docs/api-contract.md §4(DTO 스키마) §5(ErrorCode) 기준 타입 정의.
// 백엔드가 아직 없으므로 contract 스펙만 보고 작성 — 실제 응답 필드가 달라지면 여기부터 갱신.

export type FarmRole = "OWNER" | "MEMBER";

// cropType enum: 1차 TOMATO만(ai-server 모델 전용), 확장 대비 string union 유지
export type CropType = "TOMATO";

export type DiagnosisStatus = "ok" | "ood_blocked";

export type PrescriptionStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

// 작업일지 type enum (contract §4.8, 이슈 #56·#57)
export type FarmLogType = "WATERING" | "FERTILIZING" | "PRUNING" | "HARVEST" | "PEST_CONTROL" | "ETC";

// 날씨예보 하늘상태 enum (contract §4.8)
export type WeatherSky = "SUNNY" | "CLOUDY" | "OVERCAST";

// 양액 배합 생육단계 enum (contract §4.9, 이슈 #64·#65) — 1차는 TOMATO 단일이라 작물별 분기 없음.
export type NutrientStage = "SEEDLING" | "VEGETATIVE" | "FRUITING" | "HARVEST";

export type ErrorCode =
  | "C001"
  | "C002"
  | "C003"
  | "C004"
  | "A001"
  | "A002"
  | "A003"
  | "A004"
  | "A005"
  | "A007"
  | "F001"
  | "F002"
  | "F003"
  | "F004"
  | "F005"
  | "F006"
  | "D001"
  | "D002"
  | "D003"
  | "P001"
  | "P002"
  | "P003"
  | "P004"
  | "L001"
  | "L002"
  | "W001"
  | "CH001"
  | "CH002"
  | "N001"
  | "N002"
  | "N003";

// GlobalExceptionHandler 공통 응답
export interface ApiErrorResponse {
  timestamp: string;
  code: ErrorCode;
  message: string;
}

// ── 인증 ──────────────────────────────────────────
export interface SignupRequest {
  email: string;
  password: string;
  nickname: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface UserResponse {
  id: number;
  email: string;
  nickname: string;
  createdAt: string;
}

// ── 농장(테넌트) ──────────────────────────────────
export interface FarmRequest {
  name: string;
  cropType: CropType;
  location?: string;
}

export interface FarmResponse {
  id: number;
  name: string;
  cropType: CropType;
  location?: string;
  myRole: FarmRole;
  memberCount: number;
  createdAt: string;
}

export interface FarmSummaryResponse {
  id: number;
  name: string;
  cropType: CropType;
  myRole: FarmRole;
}

// ── 초대 ──────────────────────────────────────────
export interface InvitationResponse {
  code: string;
  expiresAt: string;
}

export interface AcceptInvitationRequest {
  code: string;
}

// ── 멤버 ──────────────────────────────────────────
export interface MemberResponse {
  memberId: number;
  userId: number;
  nickname: string;
  role: FarmRole;
  joinedAt: string;
}

// ── 진단 ──────────────────────────────────────────
export interface DiagnosisResponse {
  id: number;
  status: DiagnosisStatus;
  label: string;
  labelKr: string;
  prob: number;
  part: string;
  reason?: string;
  imageUrl?: string;
  camPngBase64?: string;
  createdBy: number;
  createdAt: string;
}

// 목록 Summary 확정 필드 (contract §4) — 상세 전용 필드(reason·imageUrl·camPngBase64) 제외.
export interface DiagnosisSummaryResponse {
  id: number;
  status: DiagnosisStatus;
  label: string;
  labelKr: string;
  prob: number;
  part: string;
  createdBy: number;
  createdAt: string;
}

// ── 처방 ──────────────────────────────────────────
export interface PrescriptionResult {
  summary: string;
  actions: string[];
  caution: string;
  sources: string[];
}

export interface PrescriptionRequest {
  question: string;
  diagnosisId?: number;
}

export interface PrescriptionResponse {
  id: number;
  status: PrescriptionStatus;
  question: string;
  diagnosisId?: number;
  result?: PrescriptionResult;
  errorCode?: ErrorCode;
  createdBy: number;
  createdAt: string;
  completedAt?: string;
}

// 목록 Summary 확정 필드 (contract §4) — result 본문·diagnosisId·errorCode 제외.
export interface PrescriptionSummaryResponse {
  id: number;
  status: PrescriptionStatus;
  question: string;
  createdBy: number;
  createdAt: string;
  completedAt?: string;
}

// ── 환경 대시보드 ──────────────────────────────────
// contract §3·§4 Phase 3 확정 스키마 — ai-server가 상태 파일/KMA 조회 불가 시에도 항상 200 +
// 가용 필드만 채워 alerts에 사유를 남기므로, 값 필드는 전부 null 허용으로 취급한다.
export interface EnvironmentWeather {
  temp: number | null;
  humidity: number | null;
}

export interface EnvironmentIndoor {
  temp: number | null;
  humidity: number | null;
  controlled: boolean | null;
}

export interface EnvironmentDevice {
  name: string;
  on: boolean | null;
}

export interface EnvironmentTodayResponse {
  demo: boolean;
  updatedAt: string;
  outdoor: EnvironmentWeather | null;
  indoor: EnvironmentIndoor | null;
  devices: EnvironmentDevice[];
  alerts: string[];
}

// ── 환경 시계열·임계치 (contract §4.6, 이슈 #52·#53) ──────────────
export type EnvironmentHistoryRange = "24h" | "7d" | "30d";

// 다운샘플은 서버가 수행(24h=원본·7d=30분평균·30d=2시간평균), 빈 구간은 점 생략 —
// FE는 받은 point만 그대로 그린다. 값 필드는 부분 응답 허용(전부 nullable).
export interface EnvironmentHistoryPoint {
  capturedAt: string;
  outdoorTemp?: number | null;
  outdoorHumidity?: number | null;
  indoorTemp?: number | null;
  indoorHumidity?: number | null;
}

export interface EnvironmentHistoryResponse {
  range: EnvironmentHistoryRange;
  points: EnvironmentHistoryPoint[];
}

export interface EnvThresholdsRequest {
  enabled: boolean;
  indoorTempMin?: number | null;
  indoorTempMax?: number | null;
  indoorHumidityMin?: number | null;
  indoorHumidityMax?: number | null;
}

// 미설정 농장은 GET에서 enabled=false 기본값으로 온다(updatedAt 없음).
export interface EnvThresholdsResponse extends EnvThresholdsRequest {
  updatedAt?: string;
}

// ── 작업일지 (contract §4.8, 이슈 #56·#57) ──────────────
export interface FarmLogRequest {
  logDate: string; // "YYYY-MM-DD"
  type: FarmLogType;
  memo?: string;
}

export interface FarmLogResponse {
  id: number;
  logDate: string;
  type: FarmLogType;
  memo?: string;
  createdBy: number;
  createdAt: string;
}

// ── 날씨예보 (contract §4.8, 이슈 #57) ──────────────
// backend가 KMA 단기예보를 조회해 향후 24h·1시간 간격으로 반환. 각 포인트 값 필드는
// 부분 응답을 허용(전부 nullable) — environment/today와 동일한 관용.
export interface ForecastPoint {
  time: string;
  temp?: number | null;
  humidity?: number | null;
  sky?: WeatherSky | null;
  pop?: number | null;
}

export interface ForecastResponse {
  updatedAt: string;
  points: ForecastPoint[];
}

// ── AI 챗봇 (contract §4.7, 이슈 #54·#55) ──────────────
export interface ChatRequest {
  question: string;
}

// answer·sources는 LLM이 생성한 자유 텍스트 — FE는 React 기본 이스케이프로만 렌더하고
// dangerouslySetInnerHTML·raw HTML 마크다운 렌더러를 절대 쓰지 않는다(저장형 XSS 차단).
export interface ChatMessageResponse {
  id: number;
  question: string;
  answer: string;
  sources: string[];
  fallback: boolean;
  createdBy: number;
  createdAt: string;
}

// ── 양액 배합 계산기 (contract §4.9, 이슈 #64·#65) ──────────────
// 계산 로직(비료 투입량·EC·이온밸런스)은 전부 서버(NutrientCalculationEngine)가 수행한다 —
// FE는 아래 Response 타입 값을 그대로 표시만 하고 재구현하지 않는다.
export interface NutrientTargetRequest {
  n: number;
  p: number;
  k: number;
  ca: number;
  mg: number;
  s: number;
}

// 프리셋·저장된 레시피 응답의 목표 ppm — 요청과 필드가 동일해 별칭으로 둔다.
export type NutrientTargetResponse = NutrientTargetRequest;

export interface NutrientSourceWaterRequest {
  ca?: number;
  mg?: number;
  ec?: number;
}

// 저장하지 않았으면 세 필드 모두 null(백엔드 NutrientSourceWaterResponse).
export interface NutrientSourceWaterResponse {
  ca: number | null;
  mg: number | null;
  ec: number | null;
}

// calculate(미리보기)·저장(POST) 공용 요청 — name은 저장 시에만 필수(서버가 서비스 계층에서 검증).
export interface NutrientRecipeRequest {
  name?: string;
  stage: NutrientStage;
  target: NutrientTargetRequest;
  tankVolumeL: number;
  concentrationFactor: number;
  sourceWater?: NutrientSourceWaterRequest;
}

// 코드 상수 프리셋(cropType×stage 1건) — GET /api/nutrient-presets 응답.
export interface NutrientPresetResponse {
  cropType: CropType;
  stage: NutrientStage;
  target: NutrientTargetResponse;
}

// fertilizer=국문 표기, formula=화학식(백엔드 NutrientTankItemResponse).
export interface NutrientTankItemResponse {
  fertilizer: string;
  formula: string;
  amountG: number;
}

export interface NutrientTankAllocationResponse {
  tank: "A" | "B";
  items: NutrientTankItemResponse[];
}

export interface NutrientIonBalanceResponse {
  cationMeL: number;
  anionMeL: number;
  deviationPercent: number;
}

// 배합 계산 결과 — calculate(미리보기)·레시피 저장/조회 응답에 공통으로 실린다.
export interface NutrientCalculationResponse {
  tanks: NutrientTankAllocationResponse[];
  estimatedEc: number;
  ionBalance: NutrientIonBalanceResponse;
  warnings: string[];
}

// 레시피 단건 응답 — calculation은 저장 시점 계산 스냅샷.
export interface NutrientRecipeResponse {
  id: number;
  name: string;
  stage: NutrientStage;
  target: NutrientTargetResponse;
  tankVolumeL: number;
  concentrationFactor: number;
  sourceWater: NutrientSourceWaterResponse | null;
  calculation: NutrientCalculationResponse;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

// 레시피 목록 요약 응답.
export interface NutrientRecipeSummaryResponse {
  id: number;
  name: string;
  stage: NutrientStage;
  estimatedEc: number;
  createdBy: number;
  createdAt: string;
}

// ── 페이지네이션 ──────────────────────────────────
// contract §4 확정 스키마.
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
