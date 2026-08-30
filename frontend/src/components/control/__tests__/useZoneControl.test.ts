import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ControlApplyResponse, ControlStateResponse } from "@/types";
import { useZoneControl } from "../useZoneControl";

// 이슈 #148 — 훅을 FarmControlPanel로 한 단계 끌어올리며 사라진 `key={zoneId}` 리마운트가
// 하던 세 가지 역할을 이 테스트로 고정한다:
//  ① 존(zoneId)이 바뀔 때마다 getControlState가 정확히 1번씩만 나가는지
//  ② 존이 바뀌면 이전 존의 초안 입력값(drafts)·에러 배너(actionError)·조회 결과(state)가
//     새 존으로 새지 않고 리셋되는지(useZoneControl.ts의 useLayoutEffect가 이 책임을 진다)
//  ③ (code-reviewer P1) 액션 진행 중 존을 전환하면, 늦게 도착한 옛 존의 응답이 새 존 화면을
//     덮어쓰지 않는지 — 예전엔 key={zoneId} 리마운트가 옛 훅 인스턴스를 통째로 없애 늦은 응답의
//     setState가 무해했지만, 지금은 인스턴스가 하나뿐이라 operationTokenRef 가드로 대신 막는다
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

  it("handleApply 진행 중 존을 전환하면, 늦게 도착한 옛 존 응답이 새 존 state를 덮지 않는다", async () => {
    const getControlState = vi.mocked(controlApi.getControlState);
    getControlState.mockImplementation(async (_farmId, zoneId) => {
      const zid = Number(zoneId);
      return makeState(zid, {
        // 존 10만 대기 변경이 있어야 handleApply가 실제로 applyControlChanges를 호출한다
        // (state.pendingChanges.length===0이면 handleApply는 아무 일도 하지 않고 return한다).
        pendingChanges:
          zid === 10
            ? [
                {
                  id: 1,
                  kind: "DEVICE",
                  metric: null,
                  unit: null,
                  deviceId: 5,
                  fromValue: "OFF",
                  toValue: "NORMAL",
                  status: "PENDING",
                  createdBy: 1,
                  createdAt: "2026-01-01T00:00:00Z",
                  appliedBy: null,
                  appliedAt: null,
                },
              ]
            : [],
      });
    });

    const applyControlChanges = vi.mocked(controlApi.applyControlChanges);
    let resolveApply: (value: ControlApplyResponse) => void = () => {};
    applyControlChanges.mockImplementation(
      () =>
        new Promise<ControlApplyResponse>((resolve) => {
          resolveApply = resolve;
        }),
    );

    const { result, rerender } = renderHook(
      ({ zoneId }: { zoneId: number }) => useZoneControl("farm-1", zoneId),
      { initialProps: { zoneId: 10 } },
    );

    await waitFor(() => expect(result.current.state?.zoneId).toBe(10));

    // 존 10에서 "적용" 클릭 — applyControlChanges 응답을 아직 보류해둔다.
    act(() => {
      void result.current.handleApply();
    });
    expect(result.current.busy).toBe(true);

    // 응답 전에 존 20으로 전환.
    rerender({ zoneId: 20 });
    await waitFor(() => expect(result.current.state?.zoneId).toBe(20));
    // 존 전환 리셋으로 busy도 이미 풀려 있어야 한다(존 20은 아무 액션도 진행 중이 아니므로).
    expect(result.current.busy).toBe(false);

    // 이제야 존 10의 applyControlChanges 응답이 도착 — mode를 "MANUAL"로 표시해 존 10의
    // 응답임을 구분한다(존 20의 조회 응답은 항상 기본값 "AUTO").
    await act(async () => {
      resolveApply({
        zoneId: 10,
        appliedCount: 1,
        skippedCount: 0,
        appliedAt: "2026-01-01T00:00:01Z",
        simulated: false,
        state: makeState(10, { mode: "MANUAL" }),
      });
      await Promise.resolve();
      await Promise.resolve();
    });

    // 여전히 존 20 화면이어야 한다 — 존 10의 지연 응답이 새 존을 덮으면 zoneId가 10으로
    // 바뀌거나 mode가 "MANUAL"로 오염된다.
    expect(result.current.state?.zoneId).toBe(20);
    expect(result.current.state?.mode).toBe("AUTO");
    expect(result.current.busy).toBe(false);
    expect(result.current.infoMessage).toBeNull();
  });

  it("액션이 실패하는 도중 존을 전환하면, 늦게 도착한 옛 존의 에러 배너가 새 존에 뜨지 않는다", async () => {
    const getControlState = vi.mocked(controlApi.getControlState);
    getControlState.mockImplementation(async (_farmId, zoneId) =>
      makeState(Number(zoneId)),
    );

    const enqueueControlChange = vi.mocked(controlApi.enqueueControlChange);
    let rejectEnqueue: (err: unknown) => void = () => {};
    enqueueControlChange.mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectEnqueue = reject;
        }),
    );

    const { result, rerender } = renderHook(
      ({ zoneId }: { zoneId: number }) => useZoneControl("farm-1", zoneId),
      { initialProps: { zoneId: 10 } },
    );

    await waitFor(() => expect(result.current.state?.zoneId).toBe(10));

    act(() => {
      result.current.setDrafts({ HUMIDITY: "55" });
    });
    act(() => {
      void result.current.handleEnqueueSetpoint("HUMIDITY");
    });
    expect(result.current.busy).toBe(true);

    // 응답(실패) 전에 존 20으로 전환.
    rerender({ zoneId: 20 });
    await waitFor(() => expect(result.current.state?.zoneId).toBe(20));
    expect(result.current.busy).toBe(false);

    // 이제야 존 10의 enqueueControlChange가 실패 응답으로 도착.
    await act(async () => {
      rejectEnqueue(new Error("network error"));
      await Promise.resolve();
      await Promise.resolve();
    });

    // 존 20 화면엔 존 10의 에러 배너가 뜨면 안 되고, busy도 그대로 false여야 한다(존 10의
    // finally가 존 20의 busy 락을 걷어차지 않아야 한다 — 이 테스트 시점엔 존 20에서 새 액션을
    // 시작하지 않았으므로 원래도 false지만, 가드가 없으면 이 finally가 무조건 setBusy(false)를
    // 불러 "우연히 결과가 같아 보이는" 착시가 생길 수 있어 별도로 busy만 보는 뮤테이션은 위
    // handleApply 테스트가 이미 상태 오염 쪽으로 잡아준다).
    expect(result.current.actionError).toBeNull();
    expect(result.current.busy).toBe(false);
  });
});
