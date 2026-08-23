import { DEFAULT_ERROR_MESSAGE, ERROR_MESSAGES } from "@/constants";
import { ApiError } from "./client";

// status/code로 분기하는 공용 에러 메시지 매핑 (메시지 문자열 매칭 금지 — contract §5 ErrorCode 기준).
export function resolveErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    return (err.code && ERROR_MESSAGES[err.code]) || err.message || DEFAULT_ERROR_MESSAGE;
  }
  return DEFAULT_ERROR_MESSAGE;
}

export function isNotFound(err: unknown): boolean {
  return err instanceof ApiError && err.status === 404;
}

export function isForbidden(err: unknown): boolean {
  return err instanceof ApiError && err.status === 403;
}

export function isTooManyRequests(err: unknown): boolean {
  return err instanceof ApiError && err.status === 429;
}

// P004(처방 대기 한도 초과)는 P003(AI 서버 혼잡)과 같은 429이지만 의미가 다르다 —
// 재시도 유도가 아니라 한도 안내로 별도 렌더해야 하므로 status가 아닌 code로 구분한다.
export function isPrescriptionLimitExceeded(err: unknown): boolean {
  return err instanceof ApiError && err.code === "P004";
}

// N003(배합 불가 — 탱크 침전 위험·원수 보정 과다 등)은 서버가 상세 사유를 message에 직접
// 담아 보낸다(contract §4.9). ERROR_MESSAGES의 고정 문구로 대체하지 않고 서버 메시지를
// 그대로 노출해야 하므로, 계산/저장 에러 처리에서는 resolveErrorMessage 대신 이 함수를 쓴다.
export function resolveNutrientCalculationErrorMessage(err: unknown): string {
  if (err instanceof ApiError && err.code === "N003") {
    return err.message;
  }
  return resolveErrorMessage(err);
}
