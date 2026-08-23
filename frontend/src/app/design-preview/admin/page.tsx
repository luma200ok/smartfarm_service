"use client";

import { useState } from "react";
import {
  PageTitle,
  Screen,
  ScreenBody,
  ScreenMain,
  SubNav,
  TopBar,
} from "@/components/design-preview/chrome";
import type { DeviceKind } from "@/components/design-preview/mock";
import {
  DEVICE_FOOTER,
  DEVICE_KPIS,
  DEVICES,
  MEMBERS,
  SYSTEM_LOG,
} from "@/components/design-preview/mock";
import { Avatar, Card, CardTitle, Chip, GhostButton, PrimaryButton } from "@/components/design-preview/ui";

// 시안 2e — 관리 · 장비/센서 목록과 사용자 권한.
// 좌측 장비 목록의 종류 탭(전체/센서/제어기/통신 장치)은 실제로 필터링한다.

type Kind = "전체" | DeviceKind;
const KINDS: Kind[] = ["전체", "센서", "제어기", "통신 장치"];

const ROW_COLS = "grid-cols-[1fr_104px_92px_96px_78px]";

export default function DesignPreviewAdminPage() {
  const [kind, setKind] = useState<Kind>("전체");
  const visible = kind === "전체" ? DEVICES : DEVICES.filter((d) => d.kind === kind);

  return (
    <Screen>
      <TopBar status={["알람 3"]} compact />

      <ScreenBody>
        <SubNav section="관리" />

        <ScreenMain>
          <PageTitle title="장비 · 센서 관리">
            <div className="flex-1" />
            <GhostButton size="sm">보정 일정</GhostButton>
            <PrimaryButton size="sm">장비 추가</PrimaryButton>
          </PageTitle>

          <div className="grid grid-cols-4 gap-3">
            {DEVICE_KPIS.map((kpi) => (
              <Card key={kpi.label} className="px-4 py-3.5">
                <div className="text-[11.5px] leading-none font-medium text-dp-muted">{kpi.label}</div>
                <div
                  className={`mt-2.5 text-[24px] leading-none font-bold ${
                    kpi.tone === "ok"
                      ? "text-dp-green"
                      : kpi.tone === "critical"
                        ? "text-dp-red-ink"
                        : kpi.tone === "warning"
                          ? "text-dp-amber-ink"
                          : "text-dp-ink"
                  }`}
                >
                  {kpi.value}
                </div>
              </Card>
            ))}
          </div>

          <div className="grid min-h-0 flex-1 grid-cols-[1fr_344px] gap-3">
            <Card className="flex flex-col overflow-hidden">
              <div className="flex items-center gap-2 border-b border-dp-line px-4 py-3">
                {KINDS.map((k) => (
                  <Chip
                    key={k}
                    as="button"
                    active={k === kind}
                    size="sm"
                    onClick={() => setKind(k)}
                  >
                    {k}
                  </Chip>
                ))}
                <div className="flex-1" />
                <span className="rounded-md bg-dp-inset px-3 py-1.5 text-[11.5px] leading-none text-dp-faint">
                  장비명 · 시리얼 검색
                </span>
              </div>

              <div
                className={`grid ${ROW_COLS} gap-2.5 border-b border-dp-line bg-dp-inset-alt px-4 py-3 font-mono text-[10.5px] leading-none font-semibold tracking-[0.04em] text-dp-muted`}
              >
                <span>장비</span>
                <span>위치</span>
                <span>최종 수신</span>
                <span>보정 예정</span>
                <span>상태</span>
              </div>

              <div className="min-h-0 flex-1 overflow-y-auto">
                {visible.map((device) => (
                  <div
                    key={device.name}
                    className={`grid ${ROW_COLS} gap-2.5 border-b border-dp-line px-4 py-3 ${
                      device.statusTone === "critical" ? "bg-dp-red-tint" : ""
                    }`}
                  >
                    <span className="text-[12.5px] leading-[1.4] font-semibold text-dp-ink">
                      {device.name} <span className="font-normal text-dp-muted">· {device.count}</span>
                    </span>
                    <span className="text-[12px] leading-[1.4] font-medium text-dp-body">{device.location}</span>
                    <span
                      className={`font-mono text-[11.5px] leading-[1.4] ${
                        device.lastSeenAlert ? "font-semibold text-dp-red-ink" : "font-medium text-dp-muted"
                      }`}
                    >
                      {device.lastSeen}
                    </span>
                    <span
                      className={`text-[11.5px] leading-[1.4] ${
                        device.calibrationWarn ? "font-semibold text-dp-amber-ink" : "font-medium text-dp-body"
                      }`}
                    >
                      {device.calibration}
                    </span>
                    <span
                      className={`text-[11px] leading-none font-semibold ${
                        device.statusTone === "ok"
                          ? "text-dp-green"
                          : device.statusTone === "warning"
                            ? "text-dp-amber-ink"
                            : "text-dp-red-ink"
                      }`}
                    >
                      {device.status}
                    </span>
                  </div>
                ))}
              </div>

              <div className="border-t border-dp-line px-4 py-3 text-[11.5px] leading-none text-dp-muted">
                {kind === "전체" ? DEVICE_FOOTER : `${visible.length}개 그룹 · ${kind}`}
              </div>
            </Card>

            <div className="flex min-h-0 flex-col gap-3">
              <MembersCard />
              <SystemLogCard />
            </div>
          </div>
        </ScreenMain>
      </ScreenBody>
    </Screen>
  );
}

function MembersCard() {
  return (
    <Card className="flex flex-col px-4 py-3.5">
      <div className="mb-3 flex items-baseline">
        <CardTitle>사용자 · 권한</CardTitle>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none font-semibold text-dp-green">초대</span>
      </div>
      {MEMBERS.map((member, i) => (
        <div
          key={member.name}
          className={`flex items-center gap-3 py-2.5 ${i < MEMBERS.length - 1 ? "border-b border-dp-line" : ""}`}
        >
          <Avatar
            initial={member.initial}
            size={28}
            tone={member.tone === "admin" ? "green" : member.tone === "pending" ? "faint" : "gray"}
          />
          <span className="flex-1 min-w-0">
            <span className="block truncate text-[12.5px] leading-[1.3] font-semibold text-dp-ink">{member.name}</span>
            <span className="mt-1 block text-[11px] leading-none text-dp-muted">{member.scope}</span>
          </span>
          <span
            className={`rounded-[5px] px-2 py-1 text-[10.5px] leading-[1.4] font-semibold ${
              member.tone === "admin"
                ? "bg-dp-green-tint-2 text-dp-green-ink"
                : member.tone === "pending"
                  ? "bg-dp-inset text-dp-muted"
                  : "bg-dp-inset text-dp-body"
            }`}
          >
            {member.role}
          </span>
        </div>
      ))}
    </Card>
  );
}

function SystemLogCard() {
  return (
    <Card className="flex min-h-0 flex-1 flex-col overflow-hidden px-4 py-3.5">
      <div className="mb-2.5">
        <CardTitle>시스템 로그</CardTitle>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {SYSTEM_LOG.map((entry, i) => (
          <div
            key={entry.time}
            className={`flex gap-2.5 py-[7px] ${i < SYSTEM_LOG.length - 1 ? "border-b border-dp-line" : ""}`}
          >
            <span className="w-[42px] flex-none font-mono text-[11px] leading-[1.4] font-medium text-dp-muted">
              {entry.time}
            </span>
            <span className="flex-1 text-[12px] leading-[1.4] font-medium text-dp-ink">{entry.text}</span>
          </div>
        ))}
      </div>
      <div className="mt-2.5 rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body">
        전체 로그
      </div>
    </Card>
  );
}
