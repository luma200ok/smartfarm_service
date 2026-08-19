import { STORAGE_KEYS } from "@/constants";
import type { LoginRequest, RefreshRequest, SignupRequest, TokenResponse, UserResponse } from "@/types";
import { ApiError, api, authHeaders, type ApiRequestOptions } from "./client";
import { ENDPOINTS } from "./endpoints";

// ── 토큰 저장 (localStorage['farmAccessToken']/['farmRefreshToken']) ──

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(STORAGE_KEYS.accessToken);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(STORAGE_KEYS.refreshToken);
}

export function setTokens(tokens: TokenResponse): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEYS.accessToken, tokens.accessToken);
  window.localStorage.setItem(STORAGE_KEYS.refreshToken, tokens.refreshToken);
}

export function clearTokens(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(STORAGE_KEYS.accessToken);
  window.localStorage.removeItem(STORAGE_KEYS.refreshToken);
}

export function isAuthenticated(): boolean {
  return getAccessToken() !== null;
}

function redirectToLogin(): void {
  // 이 모듈은 React 컴포넌트 트리 밖(authFetch)에서도 호출되어 router 인스턴스가 없다.
  // 인증 무효화 시 앱 상태를 완전히 리셋하기 위해 의도적으로 풀 리로드를 사용한다.
  if (typeof window !== "undefined") {
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.href = "/login";
  }
}

// ── 인증 API ──

export async function signup(payload: SignupRequest): Promise<UserResponse> {
  return api<UserResponse>(ENDPOINTS.auth.signup, { method: "POST", body: payload });
}

export async function login(payload: LoginRequest): Promise<TokenResponse> {
  const tokens = await api<TokenResponse>(ENDPOINTS.auth.login, { method: "POST", body: payload });
  setTokens(tokens);
  return tokens;
}

export async function refreshTokens(): Promise<TokenResponse> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new ApiError(401, "리프레시 토큰이 없습니다.", "A004");
  }
  const body: RefreshRequest = { refreshToken };
  const tokens = await api<TokenResponse>(ENDPOINTS.auth.refresh, { method: "POST", body });
  setTokens(tokens);
  return tokens;
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  try {
    if (refreshToken) {
      const body: RefreshRequest = { refreshToken };
      await api<void>(ENDPOINTS.auth.logout, {
        method: "POST",
        headers: authHeaders(getAccessToken()),
        body,
      });
    }
  } finally {
    clearTokens();
  }
}

export async function getMe(): Promise<UserResponse> {
  return authFetch<UserResponse>(ENDPOINTS.users.me);
}

/**
 * 인증 헤더를 자동으로 싣는 fetch. access token 만료(401 A003) 시
 * refresh를 1회 시도해 재요청하고, 재발급도 실패하면 토큰을 지우고 로그인 화면으로 보낸다.
 * (docs/_local/handoff/fe-5-auth-next.md — A003만 재시도 대상. 그 외 401(A004 등)은
 *  재사용 감지 등으로 refresh 체인 자체가 무효화된 경우이므로 즉시 로그인 화면으로 보낸다.)
 */
export async function authFetch<T>(
  path: string,
  options: ApiRequestOptions = {},
  _retried = false
): Promise<T> {
  try {
    return await api<T>(path, {
      ...options,
      headers: { ...authHeaders(getAccessToken()), ...options.headers },
    });
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      if (err.code === "A003" && !_retried) {
        try {
          await refreshTokens();
        } catch {
          clearTokens();
          redirectToLogin();
          throw err;
        }
        return authFetch<T>(path, options, true);
      }
      clearTokens();
      redirectToLogin();
    }
    throw err;
  }
}
