import type { ErrorCode } from "@/types";

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
} as const;

export const DEFAULT_ERROR_MESSAGE = "요청 처리 중 오류가 발생했습니다.";

// 환경 대시보드 장치명 한글 라벨 (docs/_local/handoff/fs-22-dashboard-next.md FE 범위 6)
export const DEVICE_LABELS: Record<string, string> = {
  dehumidifier: "제습기",
  humidifier: "가습기",
  cooling_fan: "쿨링팬",
  heater: "히터",
};

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
  D001: "진단 이력을 찾을 수 없습니다.",
  D002: "이미지 형식 또는 크기를 확인해주세요.",
  D003: "AI 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
  P001: "처방 이력을 찾을 수 없습니다.",
  P002: "처방 생성에 실패했습니다.",
  P003: "AI 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.",
  P004: "처방 대기 한도 초과 — 진행 중인 처방이 끝나면 다시 시도해 주세요.",
};
