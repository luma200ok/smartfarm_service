import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ControlStateResponse } from "@/types";
import { useZoneControl } from "../useZoneControl";

// 이슈 #148 — 훅을 FarmControlPanel로 한 단계 끌어올리며 사라진 `key={zoneId}` 리마운트가
// 하던 두 가지 역할을 이 테스트로 고정한다:
//  ① 존(zoneId)이 바뀔 때마다 getControlState가 정확히 1번씩만 나가는지
//  ② 존이 바뀌면 이전 존의 초안 입력값(drafts)·에러 배너(actionError)·조회 결과(state)가
//     새 존으로 새지 않고 리셋되는지(useZoneControl.ts의 useLayoutEffect가 이 책임을 진다)
vi.mock("@/lib/api/control", () => ({
  getControlState: vi.fn(),
  changeControlMode: vi.fn(),
  cancelAllControlChanges: vi.fn(),
  cancelControlChange: vi.fn(),
  applyControlChanges: vi.fn(),
  enqueueControlChange: vi.fn(),
  emergencyStop: vi.fn(),
}));

import * as controlApi from "@/lib/api/control";

function makeState(
  zoneId: number,
  overrides: Partial<ControlStateResponse> = {},
): ControlStateResponse {
  return {
    zoneId,
    zoneName: `zone-${zoneId}`,
    mode: "AUTO",
    modeUpdatedBy: null,
    modeUpdatedAt: null,
    simulated: false,
    setpoints: [],
    devices: [],
    pendingChanges: [],
    recentApplyLogs: [],
    ...overrides,
  };
}

describe("useZoneControl", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("존 선택 1회당 getControlState 호출이 정확히 1건이다", async () => {
    const getControlState = vi.mocked(controlApi.getControlState);
    getControlState.mockImplementation(async (_farmId, zoneId) =>
      makeState(Number(zoneId)),
    );

    const { result, rerender } = renderHook(
      ({ zoneId }: { zoneId: number }) => useZoneControl("farm-1", zoneId),
      { initialProps: { zoneId: 10 } },
    );

    await waitFor(() => expect(result.current.state?.zoneId).toBe(10));
    expect(getControlState).toHaveBeenCalledTimes(1);

    rerender({ zoneId: 20 });
    await waitFor(() => expect(result.current.state?.zoneId).toBe(20));
    expect(getControlState).toHaveBeenCalledTimes(2);

    // 같은 존을 다시 선택해도(재클릭 등) 새 fetch가 나가지 않는다 — 이 훅은 zoneId 값 자체의
    // 변화에만 반응한다.
    rerender({ zoneId: 20 });
    expect(getControlState).toHaveBeenCalledTimes(2);
  });

  it("존이 바뀌면 이전 존의 drafts·actionError·state가 새 존으로 새지 않는다", async () => {
    const getControlState = vi.mocked(controlApi.getControlState);
    let resolveZone20: (value: ControlStateResponse) => void = () => {};
    getControlState.mockImplementation(async (_farmId, zoneId) => {
      if (Number(zoneId) === 20) {
        return new Promise<ControlStateResponse>((resolve) => {
          resolveZone20 = resolve;
        });
      }
      return makeState(Number(zoneId));
    });

    const { result, rerender } = renderHook(
      ({ zoneId }: { zoneId: number }) => useZoneControl("farm-1", zoneId),
      { initialProps: { zoneId: 10 } },
    );

    await waitFor(() => expect(result.current.state?.zoneId).toBe(10));

    // 존 10에서 초안 입력값을 남기고, 숫자가 아닌 값을 예약 시도해 actionError 배너도 띄운다
    // (handleEnqueueSetpoint는 네트워크 호출 없이 로컬 검증만으로 actionError를 세팅한다).
    act(() => {
      result.current.setDrafts({ HUMIDITY: "not-a-number" });
    });
    await act(async () => {
      await result.current.handleEnqueueSetpoint("HUMIDITY");
    });
    expect(result.current.drafts.HUMIDITY).toBe("not-a-number");
    expect(result.current.actionError).toBe("숫자를 입력해주세요.");

    // 존 20으로 전환 — 새 존의 fetch가 아직 안 끝난 시점에도(useLayoutEffect가 페인트 전에
    // 커밋되므로) 이전 존의 drafts·actionError·state가 즉시 비어야 한다.
    rerender({ zoneId: 20 });

    expect(result.current.drafts).toEqual({});
    expect(result.current.actionError).toBeNull();
    expect(result.current.state).toBeNull();

    act(() => resolveZone20(makeState(20)));
    await waitFor(() => expect(result.current.state?.zoneId).toBe(20));
    expect(result.current.drafts).toEqual({});
    expect(result.current.actionError).toBeNull();
  });
});
