import { DEFAULT_ERROR_MESSAGE } from "@/constants";
import type { ApiErrorResponse, ErrorCode } from "@/types";

// NEXT_PUBLIC_API_URL 하드코딩 금지 — next.config.ts에서 프로덕션 빌드 시 미설정을 이미 차단한다.
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "";

/**
 * 백엔드 에러 응답(status/code)을 실어 던지는 에러.
 * 호출부는 메시지 문자열 매칭이 아니라 status/code로 분기할 것.
 *
 * `data`는 파싱된 응답 본문 전체를 그대로 싣는다 — 표준 {timestamp,code,message} 외에 추가
 * 필드를 함께 보내는 상위 호환 응답(예: 제어 CT005의 `pendingChanges`, contract §4.12)을
 * 호출부가 잃지 않고 꺼내 쓸 수 있게 한다.
 */
export class ApiError extends Error {
  status: number;
  code?: ErrorCode;
  data?: unknown;

  constructor(status: number, message: string, code?: ErrorCode, data?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.data = data;
  }
}

export function authHeaders(token: string | null | undefined): HeadersInit {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function isApiErrorResponse(data: unknown): data is ApiErrorResponse {
  return (
    typeof data === "object" &&
    data !== null &&
    "message" in data &&
    typeof (data as Record<string, unknown>).message === "string"
  );
}

export type ApiRequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
};

/**
 * 공용 fetch 래퍼. JSON 요청/응답을 가정하되 FormData(multipart)도 지원한다.
 * 실패 시 ApiError(status, message, code)를 던진다.
 */
export async function api<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { body, headers, ...rest } = options;
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;

  let res: Response;
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      ...rest,
      headers: {
        ...(!isFormData && body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...headers,
      },
      body: isFormData ? (body as FormData) : body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "서버에 연결할 수 없습니다. 네트워크 상태를 확인해주세요.");
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const contentType = res.headers.get("content-type") ?? "";
  const data: unknown = contentType.includes("application/json")
    ? await res.json().catch(() => null)
    : null;

  if (!res.ok) {
    const message = isApiErrorResponse(data) ? data.message : res.statusText || DEFAULT_ERROR_MESSAGE;
    const code = isApiErrorResponse(data) ? data.code : undefined;
    throw new ApiError(res.status, message, code, data);
  }

  return data as T;
}
