import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ControlStateResponse, FarmResponse, ZoneTreeResponse } from "@/types";
import FarmControlPanel from "../FarmControlPanel";

// 이슈 #148 본체 — 데스크톱(ZoneControlPanel)·모바일(MobileZoneControl)이 CSS로만 전환되며
// 항상 둘 다 마운트된 상태에서도, 존 선택 1회당 GET /control-state가 정확히 1건만 나가는지
// 뷰포트를 흉내내지 않고(둘 다 마운트돼 있으므로 그럴 필요가 없다) 직접 고정한다.
vi.mock("@/lib/api/farms", () => ({
  getFarm: vi.fn(),
}));
vi.mock("@/lib/api/zones", () => ({
  getZoneTree: vi.fn(),
}));
vi.mock("@/lib/api/control", () => ({
  getControlState: vi.fn(),
  changeControlMode: vi.fn(),
  cancelAllControlChanges: vi.fn(),
  cancelControlChange: vi.fn(),
  applyControlChanges: vi.fn(),
  enqueueControlChange: vi.fn(),
  emergencyStop: vi.fn(),
}));

import * as farmsApi from "@/lib/api/farms";
import * as zonesApi from "@/lib/api/zones";
import * as controlApi from "@/lib/api/control";

function farm(): FarmResponse {
  return {
    id: 1,
    name: "테스트 농장",
    cropType: "TOMATO",
    myRole: "OPERATOR",
    memberCount: 1,
    createdAt: "2026-01-01T00:00:00Z",
  };
}

function tree(): ZoneTreeResponse {
  return {
    zones: [
      { id: 10, name: "1존", displayOrder: 0, racks: [] },
      { id: 20, name: "2존", displayOrder: 1, racks: [] },
    ],
  };
}

function controlState(zoneId: number): ControlStateResponse {
  return {
    zoneId,
    zoneName: `zone-${zoneId}`,
    mode: "AUTO",
    modeUpdatedBy: null,
    modeUpdatedAt: null,
    simulated: false,
    setpoints: [
      { metric: "HUMIDITY", unit: "%", targetValue: null, updatedBy: null, updatedAt: null },
    ],
    devices: [],
    pendingChanges: [],
    recentApplyLogs: [],
  };
}

describe("FarmControlPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(farmsApi.getFarm).mockResolvedValue(farm());
    vi.mocked(zonesApi.getZoneTree).mockResolvedValue(tree());
    vi.mocked(controlApi.getControlState).mockImplementation(async (_farmId, zoneId) =>
      controlState(Number(zoneId)),
    );
  });

  it("데스크톱·모바일 렌더가 동시에 마운트돼도 존 선택 1회당 GET /control-state 는 1건이다", async () => {
    render(<FarmControlPanel farmId="1" />);

    // 첫 존(1존)이 자동 선택되며 조회 1건.
    await waitFor(() =>
      expect(controlApi.getControlState).toHaveBeenCalledTimes(1),
    );
    expect(controlApi.getControlState).toHaveBeenCalledWith("1", 10);

    // 데스크톱 Chip과 모바일 버튼 둘 다 "1존"/"2존" 텍스트를 갖는 버튼으로 동시에 존재한다
    // (CSS로만 전환되고 둘 다 마운트돼 있다는 방증 — ⛔ 회귀 금지 2).
    expect(screen.getAllByRole("button", { name: "2존" })).toHaveLength(2);

    // 둘 중 하나(데스크톱 쪽)를 클릭해 2존으로 전환.
    const zoneTwoButtons = screen.getAllByRole("button", { name: "2존" });
    fireEvent.click(zoneTwoButtons[0]);

    await waitFor(() =>
      expect(controlApi.getControlState).toHaveBeenCalledTimes(2),
    );
    expect(controlApi.getControlState).toHaveBeenLastCalledWith("1", 20);

    // 훅 인스턴스가 1개뿐이라는 것 — 만약 데스크톱·모바일이 각자 훅을 불렀다면(#148 버그
    // 재현) 이 시점 호출 수는 4건이었을 것이다.
    expect(controlApi.getControlState).toHaveBeenCalledTimes(2);
  });

  it("존 전환 시 초안 입력값이 데스크톱 화면에서 리셋된다", async () => {
    render(<FarmControlPanel farmId="1" />);

    await waitFor(() =>
      expect(controlApi.getControlState).toHaveBeenCalledTimes(1),
    );

    // 데스크톱 ZoneControlPanel의 습도 입력(인덱스 0 — 모바일 restMetrics 입력도 같은
    // aria-label을 쓰므로 getAllBy로 데스크톱 쪽만 골라 쓴다. desktop 블록이 JSX상 먼저
    // 렌더되므로 [0]이 데스크톱이다).
    const humidityInputs = await screen.findAllByLabelText("습도 목표값 입력");
    fireEvent.change(humidityInputs[0], { target: { value: "55" } });
    expect((humidityInputs[0] as HTMLInputElement).value).toBe("55");

    const zoneTwoButtons = screen.getAllByRole("button", { name: "2존" });
    fireEvent.click(zoneTwoButtons[0]);

    await waitFor(() =>
      expect(controlApi.getControlState).toHaveBeenCalledTimes(2),
    );

    const humidityInputsAfter = await screen.findAllByLabelText("습도 목표값 입력");
    expect((humidityInputsAfter[0] as HTMLInputElement).value).toBe("");
  });
});
