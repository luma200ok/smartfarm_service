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
