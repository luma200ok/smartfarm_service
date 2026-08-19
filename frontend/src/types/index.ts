// docs/api-contract.md §4(DTO 스키마) §5(ErrorCode) 기준 타입 정의.
// 백엔드가 아직 없으므로 contract 스펙만 보고 작성 — 실제 응답 필드가 달라지면 여기부터 갱신.

export type FarmRole = "OWNER" | "MEMBER";

// cropType enum: 1차 TOMATO만(ai-server 모델 전용), 확장 대비 string union 유지
export type CropType = "TOMATO";

export type DiagnosisStatus = "ok" | "ood_blocked";

export type PrescriptionStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export type ErrorCode =
  | "C001"
  | "C002"
  | "A001"
  | "A002"
  | "A003"
  | "A004"
  | "A005"
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
  | "P003";

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

// 목록 Summary: 상세에서 무거운 필드(camPngBase64 등) 제외 (contract §4 비고)
export type DiagnosisSummaryResponse = Omit<DiagnosisResponse, "camPngBase64" | "reason">;

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

// 목록 Summary: result 본문 제외 (contract §4 비고)
export type PrescriptionSummaryResponse = Omit<PrescriptionResponse, "result">;

// ── 페이지네이션 ──────────────────────────────────
// 백엔드 미구현 상태에서의 가정 — Spring Data Page 표준 필드 기준.
// 실제 backend 응답과 다르면 이슈 #6에서 조정 필요.
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
