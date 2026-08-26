// docs/api-contract.md §3(핵심 엔드포인트) 기준 URL 상수. 하드코딩 대신 여기서만 관리.
export const ENDPOINTS = {
  auth: {
    signup: "/api/auth/signup",
    login: "/api/auth/login",
    refresh: "/api/auth/refresh",
    logout: "/api/auth/logout",
    demoLogin: "/api/auth/demo-login",
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
    // 멤버 역할 변경(이슈 #122/#123) — 초대 수락자(PENDING) 승인도 이 경로.
    memberRole: (farmId: number | string, memberId: number | string) =>
      `/api/farms/${farmId}/members/${memberId}/role`,
    diagnoses: (farmId: number | string) => `/api/farms/${farmId}/diagnoses`,
    diagnosisDetail: (farmId: number | string, diagnosisId: number | string) =>
      `/api/farms/${farmId}/diagnoses/${diagnosisId}`,
    prescriptions: (farmId: number | string) => `/api/farms/${farmId}/prescriptions`,
    prescriptionDetail: (farmId: number | string, prescriptionId: number | string) =>
      `/api/farms/${farmId}/prescriptions/${prescriptionId}`,
    environmentToday: (farmId: number | string) => `/api/farms/${farmId}/environment/today`,
    environmentHistory: (farmId: number | string) => `/api/farms/${farmId}/environment/history`,
    environmentForecast: (farmId: number | string) => `/api/farms/${farmId}/environment/forecast`,
    envThresholds: (farmId: number | string) => `/api/farms/${farmId}/env-thresholds`,
    logs: (farmId: number | string) => `/api/farms/${farmId}/logs`,
    logDetail: (farmId: number | string, logId: number | string) => `/api/farms/${farmId}/logs/${logId}`,
    chat: (farmId: number | string) => `/api/farms/${farmId}/chat`,
    nutrientCalculate: (farmId: number | string) => `/api/farms/${farmId}/nutrient-recipes/calculate`,
    nutrientRecipes: (farmId: number | string) => `/api/farms/${farmId}/nutrient-recipes`,
    nutrientRecipeDetail: (farmId: number | string, recipeId: number | string) =>
      `/api/farms/${farmId}/nutrient-recipes/${recipeId}`,
    // 존·랙 구조 (contract §4.10, 이슈 #89)
    zones: (farmId: number | string) => `/api/farms/${farmId}/zones`,
    zoneDetail: (farmId: number | string, zoneId: number | string) =>
      `/api/farms/${farmId}/zones/${zoneId}`,
    racksUnderZone: (farmId: number | string, zoneId: number | string) =>
      `/api/farms/${farmId}/zones/${zoneId}/racks`,
    rackDetail: (farmId: number | string, rackId: number | string) =>
      `/api/farms/${farmId}/racks/${rackId}`,
    // 장비/센서 레지스트리 (contract §4.10, 이슈 #89)
    devices: (farmId: number | string) => `/api/farms/${farmId}/devices`,
    deviceSummary: (farmId: number | string) => `/api/farms/${farmId}/devices/summary`,
    deviceDetail: (farmId: number | string, deviceId: number | string) =>
      `/api/farms/${farmId}/devices/${deviceId}`,
    // 센서 측정값 (contract §4.11, 이슈 #90)
    readingsSeries: (farmId: number | string) => `/api/farms/${farmId}/readings/series`,
    readingsLatest: (farmId: number | string) => `/api/farms/${farmId}/readings/latest`,
    readingsLevelSummary: (farmId: number | string) => `/api/farms/${farmId}/readings/level-summary`,
    // 제어 도메인 (contract §4.12, 이슈 #100/#108)
    control: (farmId: number | string, zoneId: number | string) =>
      `/api/farms/${farmId}/zones/${zoneId}/control`,
    controlMode: (farmId: number | string, zoneId: number | string) =>
      `/api/farms/${farmId}/zones/${zoneId}/control/mode`,
    controlChanges: (farmId: number | string, zoneId: number | string) =>
      `/api/farms/${farmId}/zones/${zoneId}/control/changes`,
    controlChangeDetail: (farmId: number | string, zoneId: number | string, changeId: number | string) =>
      `/api/farms/${farmId}/zones/${zoneId}/control/changes/${changeId}`,
    controlApply: (farmId: number | string, zoneId: number | string) =>
      `/api/farms/${farmId}/zones/${zoneId}/control/apply`,
    emergencyStop: (farmId: number | string) => `/api/farms/${farmId}/control/emergency-stop`,
    // 알람 이벤트 (contract §4.13, 이슈 #116/#118)
    alarmEvents: (farmId: number | string) => `/api/farms/${farmId}/alarm-events`,
    alarmEventDetail: (farmId: number | string, alarmEventId: number | string) =>
      `/api/farms/${farmId}/alarm-events/${alarmEventId}`,
    alarmEventAcknowledge: (farmId: number | string, alarmEventId: number | string) =>
      `/api/farms/${farmId}/alarm-events/${alarmEventId}/acknowledge`,
    alarmEventResolve: (farmId: number | string, alarmEventId: number | string) =>
      `/api/farms/${farmId}/alarm-events/${alarmEventId}/resolve`,
    alarmEventMemo: (farmId: number | string, alarmEventId: number | string) =>
      `/api/farms/${farmId}/alarm-events/${alarmEventId}/memo`,
    alarmEventsAcknowledgeAll: (farmId: number | string) => `/api/farms/${farmId}/alarm-events/acknowledge-all`,
    alarmEventsStats: (farmId: number | string) => `/api/farms/${farmId}/alarm-events/stats`,
    alarmEventsUnacknowledgedCount: (farmId: number | string) =>
      `/api/farms/${farmId}/alarm-events/unacknowledged-count`,
    // 알람 규칙 (contract §4.13, 이슈 #118) — 상세 패널 "규칙" 한 줄 요약 조회용 단건만 쓴다.
    alarmRuleDetail: (farmId: number | string, ruleId: number | string) =>
      `/api/farms/${farmId}/alarm-rules/${ruleId}`,
  },
  invitations: {
    accept: "/api/invitations/accept",
  },
  nutrientPresets: "/api/nutrient-presets",
} as const;
