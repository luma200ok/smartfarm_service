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
  | "W001";

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

// ── 페이지네이션 ──────────────────────────────────
// contract §4 확정 스키마.
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
