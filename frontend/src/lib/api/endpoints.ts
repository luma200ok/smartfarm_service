// docs/api-contract.md §3(핵심 엔드포인트) 기준 URL 상수. 하드코딩 대신 여기서만 관리.
export const ENDPOINTS = {
  auth: {
    signup: "/api/auth/signup",
    login: "/api/auth/login",
    refresh: "/api/auth/refresh",
    logout: "/api/auth/logout",
  },
  users: {
    me: "/api/users/me",
  },
  farms: {
    list: "/api/farms",
    create: "/api/farms",
    detail: (farmId: number | string) => `/api/farms/${farmId}`,
    update: (farmId: number | string) => `/api/farms/${farmId}`,
    remove: (farmId: number | string) => `/api/farms/${farmId}`,
    invitations: (farmId: number | string) => `/api/farms/${farmId}/invitations`,
    members: (farmId: number | string) => `/api/farms/${farmId}/members`,
    removeMember: (farmId: number | string, memberId: number | string) =>
      `/api/farms/${farmId}/members/${memberId}`,
    diagnoses: (farmId: number | string) => `/api/farms/${farmId}/diagnoses`,
    diagnosisDetail: (farmId: number | string, diagnosisId: number | string) =>
      `/api/farms/${farmId}/diagnoses/${diagnosisId}`,
    prescriptions: (farmId: number | string) => `/api/farms/${farmId}/prescriptions`,
    prescriptionDetail: (farmId: number | string, prescriptionId: number | string) =>
      `/api/farms/${farmId}/prescriptions/${prescriptionId}`,
    environmentToday: (farmId: number | string) => `/api/farms/${farmId}/environment/today`,
  },
  invitations: {
    accept: "/api/invitations/accept",
  },
} as const;
