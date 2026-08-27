// docs/api-contract.md §4(DTO 스키마) §5(ErrorCode) 기준 타입 정의.
// 백엔드가 아직 없으므로 contract 스펙만 보고 작성 — 실제 응답 필드가 달라지면 여기부터 갱신.

// 농장 멤버 역할 4단계(이슈 #122/#123, contract §2) — 백엔드 FarmRole(rank: ADMIN=3/OPERATOR=2/
// VIEWER=1/PENDING=0)과 값이 동일해야 한다. 서열 비교는 이 유니온 자체가 아니라
// lib/roles.ts의 FARM_ROLE_RANK/hasFarmRoleAtLeast를 통해서만 한다(비교식 컴포넌트별 복붙 금지).
export type FarmRole = "ADMIN" | "OPERATOR" | "VIEWER" | "PENDING";

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

// 존·랙·장비 레지스트리 enum (contract §4.10, 이슈 #89)
export type DeviceKind = "SENSOR" | "CONTROLLER" | "GATEWAY";
// OFF는 2026-08-24 사이클 3(제어 도메인, contract §4.12)에서 추가 — 통신두절(OFFLINE, 장애)과
// 구분되는 "의도적으로 꺼진 상태"(제어 조작 결과)다.
export type DeviceStatus = "NORMAL" | "WARNING" | "FAULT" | "OFFLINE" | "OFF";

// 센서 측정 지표 enum (contract §4.11, 이슈 #90) — unit은 서버가 응답에 함께 실어준다.
export type SensorMetric = "TEMPERATURE" | "HUMIDITY" | "CO2" | "EC" | "PH" | "PPFD" | "POWER";

// 랙 도면·층별 비교표 상태 enum (contract §4.11)
export type ReadingCellState = "OK" | "WARNING" | "CRITICAL" | "IDLE";

// design-preview/ui.tsx RackGrid·StatusBadge가 쓰는 팔레트 타입 — 원래 mock.ts에 있었으나
// 운영 화면(FarmRackPanel·FarmStatusCard 등)도 함께 쓰면서 운영 코드가 프리뷰 모듈에 의존하게
// 됐다(이슈 #99 리뷰 반영). 공용 타입 모듈로 옮기고 mock.ts가 여기서 재수출하도록 방향을
// 뒤집는다 — /design-preview 정리 시 운영 화면이 깨지지 않게.
export type PreviewSeverity = "critical" | "warning" | "done";
export type PreviewCellState = "ok" | "ok-soft" | "warning" | "critical" | "idle";

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
  | "F007"
  | "F008"
  | "F009"
  | "R001"
  | "R002"
  | "R003"
  | "R004"
  | "E001"
  | "E002"
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
  | "N003"
  | "CT001"
  | "CT002"
  | "CT003"
  | "CT004"
  | "CT005"
  | "AL001"
  | "AL002"
  | "ALR001"
  | "ALR002"
  | "ALR003"
  | "ALR004";

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

// ⚠️ id는 농장 id, memberId는 요청자 본인의 멤버십 id다(백엔드 FarmSummaryResponse javadoc,
// 이슈 #122) — 혼동해서 DELETE /members/{memberId}에 농장 id를 넣으면 F008이 난다.
// memberId는 승인 대기(PENDING) 본인이 스스로 대기를 취소(DELETE .../members/{memberId})할
// 유일한 도달 경로라서 실린다 — 멤버 목록·농장 상세엔 이 값이 없다.
export interface FarmSummaryResponse {
  id: number;
  name: string;
  cropType: CropType;
  myRole: FarmRole;
  memberId: number;
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
// pending은 role에서 파생된 서버 계산 필드(role === "PENDING"과 동치, 이슈 #122) — role 문자열
// 비교보다 명시적이라 FE는 이 필드로 대기자 UI를 분기한다.
export interface MemberResponse {
  memberId: number;
  userId: number;
  nickname: string;
  role: FarmRole;
  pending: boolean;
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

// calculate(미리보기)·저장(POST/PATCH) 공용 요청 — name은 서버가 서비스 계층에서 "저장 시 필수"로
// 검증한다(계산은 미리보기라 name 불필요). 이 공용 타입만으로는 그 구분이 타입 레벨에서 강제되지
// 않으므로, 저장 경로는 아래 NutrientRecipeSaveRequest(name 필수)를 대신 쓴다(리뷰 픽스 #65 P2-2).
export interface NutrientRecipeRequest {
  name?: string;
  stage: NutrientStage;
  target: NutrientTargetRequest;
  tankVolumeL: number;
  concentrationFactor: number;
  sourceWater?: NutrientSourceWaterRequest;
}

// 저장(POST 생성·PATCH 수정) 전용 요청 — name을 컴파일 타임에 필수로 강제한다.
export type NutrientRecipeSaveRequest = Omit<NutrientRecipeRequest, "name"> & { name: string };

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

// ── 존·랙 구조 (contract §4.10, 이슈 #89) ──────────────
export interface ZoneTreeLevelNode {
  id: number;
  levelNo: number;
  label: string;
}

export interface ZoneTreeRackNode {
  id: number;
  code: string;
  levelCount: number;
  displayOrder: number;
  levels: ZoneTreeLevelNode[];
}

export interface ZoneTreeZoneNode {
  id: number;
  name: string;
  displayOrder: number;
  racks: ZoneTreeRackNode[];
}

// 존+랙+층 트리 — 랙 도면 렌더용 1회 조회(GET /zones).
export interface ZoneTreeResponse {
  zones: ZoneTreeZoneNode[];
}

// 존 생성 요청 — displayOrder 미지정 시 서버가 0으로 채운다.
export interface ZoneRequest {
  name: string;
  displayOrder?: number;
}

// PATCH 부분 수정 — 필드 생략(undefined)은 미변경(서버 ZoneUpdateRequest와 동일 관용).
export type ZoneUpdateRequest = Partial<ZoneRequest>;

export interface ZoneResponse {
  id: number;
  name: string;
  displayOrder: number;
  createdAt: string;
}

// 랙 생성 요청 — levelCount(1~50)만큼 층이 서버에서 자동 생성된다.
export interface RackRequest {
  code: string;
  levelCount: number;
  displayOrder?: number;
}

// PATCH 부분 수정 — levelCount 축소는 하위에 활성 장비가 있으면 서버가 409 R004로 거부한다.
export type RackUpdateRequest = Partial<RackRequest>;

export interface RackResponse {
  id: number;
  zoneId: number;
  code: string;
  levelCount: number;
  displayOrder: number;
  createdAt: string;
}

// ── 장비/센서 레지스트리 (contract §4.10, 이슈 #89) ──────────────
// PATCH는 부분 수정이라 전 필드가 옵셔널. null은 "해제"(위치 FK는 1차 미지원), undefined는 "미변경".
export interface DeviceRequest {
  zoneId?: number | null;
  rackId?: number | null;
  rackLevelId?: number | null;
  name?: string;
  kind?: DeviceKind;
  model?: string | null;
  serial?: string | null;
  status?: DeviceStatus;
  calibrationDueAt?: string | null;
  installedOn?: string | null;
  metrics?: SensorMetric[];
}

export interface DeviceResponse {
  id: number;
  zoneId: number | null;
  rackId: number | null;
  rackLevelId: number | null;
  name: string;
  kind: DeviceKind;
  model: string | null;
  serial: string | null;
  status: DeviceStatus;
  lastSeenAt: string | null;
  calibrationDueAt: string | null;
  installedOn: string | null;
  metrics: SensorMetric[];
  createdAt: string;
}

export interface DeviceListResponse {
  devices: DeviceResponse[];
}

// byModel의 status = 그룹 내 최악 상태(FAULT > OFFLINE > WARNING > NORMAL, 백엔드 주석 기준).
export interface DeviceSummaryByModel {
  name: string;
  kind: DeviceKind;
  count: number;
  status: DeviceStatus;
}

// off는 2026-08-24 사이클 3에서 추가(§4.10 리뷰 반영) — 불변식: total = normal + warning +
// faultOrOffline + off. 없으면 비상 정지 직후 농장 전체가 멈췄는데 "이상 없음"으로 보인다.
export interface DeviceSummaryResponse {
  total: number;
  normal: number;
  warning: number;
  faultOrOffline: number;
  off: number;
  calibrationDueSoon: number;
  byModel: DeviceSummaryByModel[];
}

// ── 센서 측정값 (contract §4.11, 이슈 #90) ──────────────
// range는 §4.6과 동일한 다운샘플 규칙(24h/7d/30d) 재사용 — EnvironmentHistoryRange와 값이 같다.
export type ReadingRange = EnvironmentHistoryRange;

export interface ReadingSeriesPoint {
  at: string;
  value: number | null;
}

export interface ReadingSeriesSeries {
  metric: SensorMetric;
  unit: string;
  points: ReadingSeriesPoint[];
}

export interface ReadingSeriesResponse {
  range: string;
  scope: string;
  simulated: boolean;
  series: ReadingSeriesSeries[];
}

// 신선도 상한을 넘기면 state=IDLE·value=null로 떨어지지만 measuredAt은 마지막 실측 시각을
// 그대로 싣는다(백엔드 LevelCell 주석) — FE가 "마지막으로 언제 봤는지"를 판단할 수 있게.
export interface ReadingMatrixLevelCell {
  levelNo: number;
  value: number | null;
  measuredAt: string | null;
  state: ReadingCellState;
}

export interface ReadingMatrixRackRow {
  rackId: number;
  code: string;
  levels: ReadingMatrixLevelCell[];
}

export interface ReadingMatrixResponse {
  metric: SensorMetric;
  unit: string;
  simulated: boolean;
  racks: ReadingMatrixRackRow[];
}

// 데이터 없는 (층,지표) 조합도 average=null·state="IDLE"로 채워져 온다(표 형태 유지 목적).
export interface LevelSummaryMetricCell {
  metric: SensorMetric;
  unit: string;
  average: number | null;
  deviationPercent: number | null;
  state: ReadingCellState;
}

export interface LevelSummaryLevelRow {
  levelNo: number;
  label: string;
  metrics: LevelSummaryMetricCell[];
}

export interface LevelSummaryResponse {
  rackId: number;
  code: string;
  range: string;
  simulated: boolean;
  levels: LevelSummaryLevelRow[];
}

// ── 제어 도메인 (contract §4.12, 이슈 #100/#108) ──────────────
// 응답 DTO는 backend/src/main/java/com/smartfarm/service/dto/Control*.java 9개를 그대로 반영.
export type OperationMode = "AUTO" | "MANUAL";
export type ControlChangeKind = "SETPOINT" | "DEVICE";
export type ControlChangeStatus = "PENDING" | "APPLIED" | "DISCARDED";

// 제어 가능한 지표(백엔드 SensorMetric#isControllable) — 목표값 카드가 항상 이 4종 전부를 싣는다.
export type ControllableMetric = Extract<SensorMetric, "TEMPERATURE" | "HUMIDITY" | "CO2" | "PPFD">;

export interface ControlSetpointResponse {
  metric: SensorMetric;
  unit: string;
  targetValue: number | null;
  updatedBy: number | null;
  updatedAt: string | null;
}

export interface ControlDeviceResponse {
  id: number;
  name: string;
  kind: DeviceKind;
  status: DeviceStatus;
}

export interface ControlChangeResponse {
  id: number;
  kind: ControlChangeKind;
  metric: SensorMetric | null;
  unit: string | null;
  deviceId: number | null;
  fromValue: string | null;
  toValue: string;
  status: ControlChangeStatus;
  createdBy: number;
  createdAt: string;
  appliedBy: number | null;
  appliedAt: string | null;
}

export interface ControlApplyLogResponse {
  id: number;
  summary: string;
  itemCount: number;
  appliedBy: number;
  appliedAt: string;
}

export interface ControlStateResponse {
  zoneId: number;
  zoneName: string;
  mode: OperationMode;
  modeUpdatedBy: number | null;
  modeUpdatedAt: string | null;
  simulated: boolean;
  setpoints: ControlSetpointResponse[];
  devices: ControlDeviceResponse[];
  pendingChanges: ControlChangeResponse[];
  recentApplyLogs: ControlApplyLogResponse[];
}

export interface ControlModeRequest {
  mode: OperationMode;
}

// kind별 필수 필드가 다르다 — SETPOINT는 metric·targetValue, DEVICE는 deviceId·targetStatus.
export interface ControlChangeRequest {
  kind: ControlChangeKind;
  metric?: SensorMetric;
  targetValue?: number;
  deviceId?: number;
  // 켜기(NORMAL)/끄기(OFF)만 허용(contract §4.12) — 나머지 상태는 관측 결과라 조작 대상이 아니다.
  targetStatus?: Extract<DeviceStatus, "NORMAL" | "OFF">;
}

// expectedChangeIds는 필수(낙관적 검증) — 빈 배열도 유효한 값("지금 큐가 비어있다고 알고 있다").
export interface ControlApplyRequest {
  expectedChangeIds: number[];
}

export interface ControlApplyResponse {
  zoneId: number;
  appliedCount: number;
  skippedCount: number;
  appliedAt: string;
  simulated: boolean;
  state: ControlStateResponse;
}

export interface EmergencyStopResponse {
  farmId: number;
  zoneCount: number;
  stoppedDeviceCount: number;
  discardedChangeCount: number;
  stoppedAt: string;
  simulated: boolean;
}

// CT005 전용 오류 본문(contract §4.12 동시성 1) — 표준 {timestamp,code,message}에
// pendingChanges만 덧붙인 상위 호환 형태. 클라이언트는 이 목록을 그대로 화면에 반영해
// 재확인시킨다(별도 GET 왕복 불필요).
export interface ControlQueueConflictResponse extends ApiErrorResponse {
  pendingChanges: ControlChangeResponse[];
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

// ── 알람 이벤트 (이슈 #116/#118, 프론트 #136) ──────────────
export type AlarmSeverity = "CRITICAL" | "WARNING";
export type AlarmEventStatus = "UNACKNOWLEDGED" | "ACKNOWLEDGED" | "RESOLVED";
// #118 이전에 생성된 과거 이벤트는 scopeType/scopeId가 모두 null(=농장 단위)이다
// (백엔드 AlarmEventResponse javadoc). FE는 null을 "FARM"과 동일하게 취급한다.
export type AlarmScopeType = "FARM" | "ZONE" | "RACK" | "LEVEL";
export type AlarmSourceType = "ENV_THRESHOLD" | "SENSOR_THRESHOLD" | "DEVICE_HEARTBEAT";
export type AlarmEventLogAction = "CREATED" | "ACKNOWLEDGED" | "RESOLVED" | "MEMO_ADDED";

export interface AlarmEventResponse {
  id: number;
  farmId: number;
  severity: AlarmSeverity;
  sourceType: AlarmSourceType;
  metricKey: string;
  message: string;
  status: AlarmEventStatus;
  occurredAt: string;
  acknowledgedAt: string | null;
  acknowledgedBy: number | null;
  resolvedAt: string | null;
  resolvedBy: number | null;
  thresholdId: number | null;
  ruleId: number | null;
  scopeType: AlarmScopeType | null;
  scopeId: number | null;
  createdAt: string;
}

// actorId만 실린다(사용자 이름 없음) — 화면은 이름을 지어내지 말고 시각·행위만 표기할 것
// (이슈 #136 핸드오프 "없는 데이터를 지어내지 말 것").
export interface AlarmEventLogResponse {
  id: number;
  action: AlarmEventLogAction;
  actorId: number | null;
  note: string | null;
  createdAt: string;
}

export interface AlarmEventDetailResponse {
  event: AlarmEventResponse;
  timeline: AlarmEventLogResponse[];
}

export interface AlarmMemoRequest {
  note: string;
}

// countBySeverity는 AlarmSeverity 전 항목이 0으로 채워져 온다(백엔드 AlarmStatsResponse.of).
// avgAcknowledgeMinutes는 확인된 이벤트가 하나도 없으면 null.
export interface AlarmStatsResponse {
  days: number;
  countBySeverity: Record<AlarmSeverity, number>;
  avgAcknowledgeMinutes: number | null;
}

export interface AlarmUnacknowledgedCountResponse {
  count: number;
}

export interface AlarmAcknowledgeAllResponse {
  acknowledgedCount: number;
}

// ── 알람 규칙 (이슈 #118, 상세 패널 "규칙" 한 줄 요약 조회용) ──────────────
export type AlarmRuleSource = "ENV_SNAPSHOT" | "SENSOR_READING" | "DEVICE_HEARTBEAT";
export type AlarmComparator = "GT" | "LT" | "OUTSIDE_RANGE" | "ABSENT";

export interface AlarmRuleResponse {
  id: number;
  farmId: number;
  name: string;
  enabled: boolean;
  source: AlarmRuleSource;
  metric: string | null;
  comparator: AlarmComparator;
  thresholdValue: number | null;
  thresholdMin: number | null;
  thresholdMax: number | null;
  durationSeconds: number | null;
  severity: AlarmSeverity;
  scopeType: AlarmScopeType;
  scopeId: number | null;
  derived: boolean;
  createdAt: string;
  updatedAt: string;
}

// ── 홈 대시보드 (이슈 #139/#142) ──────────────────────────────
// GET /api/dashboard/farms — 내 활성 농장 전체를 배치 조회로 한 번에 반환한다(N+1 방지).
// PENDING 멤버십 농장은 응답에서 제외된다(백엔드 DashboardService javadoc) — /api/farms의
// 좌측 목록과 카드 개수가 다를 수 있는 게 정상이다.
export type FarmDashboardStatus = "CRITICAL" | "WARNING" | "NORMAL";

// value가 null이면 측정 이력이 없거나 신선도 상한을 넘긴 것 — 이때 outOfRange는 항상 false
// (판정할 값 자체가 없으므로).
export interface FarmDashboardMetricValue {
  metric: SensorMetric;
  unit: string;
  value: number | null;
  outOfRange: boolean;
}

// 대표 지표(TEMPERATURE 고정) 일별 평균. 그날 측정 이력이 없으면 value=null·state="IDLE".
export interface FarmDashboardTrendPoint {
  date: string;
  value: number | null;
  state: ReadingCellState;
}

// ⚠️ 재배 사이클(정식일·수확 예정) 필드가 없다 — 도메인 부재(#130). 0/임의값을 채우면 거짓
// 정보가 되므로 FE에서도 지어내지 말 것(카드 메타는 작물·랙/층 수만 표기).
export interface FarmDashboardResponse {
  id: number;
  name: string;
  cropType: CropType;
  rackCount: number;
  levelCount: number;
  status: FarmDashboardStatus;
  unacknowledgedAlarmCount: number;
  metrics: FarmDashboardMetricValue[];
  trend7d: FarmDashboardTrendPoint[];
  latestAlarmMessage: string | null;
}

// GET /api/farms/{farmId}/briefing — 농장 단건 기준 "오늘 할일" 집계(이슈 #129-B).
// ⚠️ harvestDueSoon 필드는 의도적으로 없다(#130). actionRequiredCount는 이 농장의 미확인
// "건수"이지 시안 브리핑 pill의 "N곳"(농장 수) 단위가 아니다 — 혼동 주의(백엔드 javadoc).
export interface FarmBriefingResponse {
  actionRequiredCount: number;
  calibrationDueSoonCount: number;
}
