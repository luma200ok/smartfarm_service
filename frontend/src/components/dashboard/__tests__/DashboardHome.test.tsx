import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DashboardFarmsResponse, FarmDashboardResponse } from "@/types";
import DashboardHome from "../DashboardHome";

// 이슈 #140 본체 — GET /api/dashboard/farms 응답이 평문 배열에서
// { farms, totalCount, truncated } 래퍼로 바뀌었다. truncated=true일 때 카드가 왜
// 사라졌는지 실제 숫자(totalCount·farms.length)로 알리는지, truncated=false일 때는
// 아무 안내도 뜨지 않는지를 고정한다.
//
// FarmRackPanel·FarmTrendChart는 선택 농장이 정해지면 각자 API를 호출하는 독립 컴포넌트라
// 이 테스트의 관심사가 아니다 — 단순 스텁으로 대체해 이 화면 자체의 로직만 검증한다.
vi.mock("@/components/dashboard/FarmRackPanel", () => ({
  default: () => null,
}));
vi.mock("@/components/dashboard/FarmTrendChart", () => ({
  default: () => null,
}));

vi.mock("@/lib/api/dashboard", () => ({
  getDashboardFarms: vi.fn(),
}));
vi.mock("@/lib/api/briefing", () => ({
  getFarmBriefing: vi.fn(),
}));

import * as briefingApi from "@/lib/api/briefing";
import * as dashboardApi from "@/lib/api/dashboard";

function farm(id: number, overrides: Partial<FarmDashboardResponse> = {}): FarmDashboardResponse {
  return {
    id,
    name: `농장${id}`,
    cropType: "TOMATO",
    rackCount: 2,
    levelCount: 4,
    status: "NORMAL",
    unacknowledgedAlarmCount: 0,
    metrics: [],
    trend7d: [],
    latestAlarmMessage: null,
    ...overrides,
  };
}

function response(overrides: Partial<DashboardFarmsResponse> = {}): DashboardFarmsResponse {
  const farms = [farm(1), farm(2)];
  return {
    farms,
    totalCount: farms.length,
    truncated: false,
    ...overrides,
  };
}

describe("DashboardHome — 절단 안내 (이슈 #140)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(briefingApi.getFarmBriefing).mockResolvedValue({
      actionRequiredCount: 0,
      calibrationDueSoonCount: 0,
    });
  });

  it("truncated=true면 실제 숫자(totalCount·farms.length)로 절단 안내를 띄운다", async () => {
    vi.mocked(dashboardApi.getDashboardFarms).mockResolvedValue(
      response({ farms: [farm(1), farm(2)], totalCount: 7, truncated: true }),
    );

    render(<DashboardHome />);

    const notice = await screen.findByRole("status");
    expect(notice.textContent).toBe("농장 7개 중 2개만 표시하고 있습니다.");
  });

  it("truncated=false면 절단 안내를 렌더하지 않는다", async () => {
    vi.mocked(dashboardApi.getDashboardFarms).mockResolvedValue(
      response({ farms: [farm(1), farm(2)], totalCount: 2, truncated: false }),
    );

    render(<DashboardHome />);

    // 카드 그리드가 뜰 때까지 기다린 뒤(로딩 완료 확인) status 안내가 없는지 확인한다.
    await screen.findByText("농장1");
    expect(screen.queryByRole("status")).toBeNull();
  });
});
