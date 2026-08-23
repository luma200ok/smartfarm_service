"use client";

import { useState } from "react";
import { DETAIL_COLS, PageTitle, Screen, ScreenBody, TopBar } from "@/components/design-preview/chrome";
import type { DeviceKind } from "@/components/design-preview/mock";
import {
  DEVICE_KINDS,
  DEVICE_KPIS,
  DEVICE_TOTAL_LABEL,
  DEVICES,
  MEMBERS,
  SYSTEM_LOG,
  UNREAD_ALARMS,
} from "@/components/design-preview/mock";
import {
  Avatar,
  Card,
  CardTitle,
  Chip,
  GhostButton,
  PrimaryButton,
  TimelineRow,
} from "@/components/design-preview/ui";

// 화면 06 — 장비·센서 관리. 핸드오프 `Screens › 06` + `Interactions › 관리`.
// 필터 탭은 단일 선택, 검색은 장비명 부분 일치.
// 좁은 폭에서는 보정 예정 → 최종 수신 → 위치 순으로 열을 숨긴다(핸드오프 Responsive).

type Kind = "전체" | DeviceKind;

const ROW_COLS =
  "grid grid-cols-[1fr_78px] gap-2.5 min-[768px]:grid-cols-[1fr_104px_78px] min-[1280px]:grid-cols-[1fr_104px_92px_78px] min-[1440px]:grid-cols-[1fr_104px_92px_96px_78px]";

export default function DesignPreviewAdminPage() {
  const [kind, setKind] = useState<Kind>("전체");
  const [query, setQuery] = useState("");

  const visible = DEVICES.filter(
    (d) => (kind === "전체" || d.kind === kind) && d.name.toLowerCase().includes(query.trim().toLowerCase()),
  );

  return (
    <Screen>
      <TopBar status={[`알람 ${UNREAD_ALARMS}`]} />

      <ScreenBody section="admin">
        <PageTitle title="장비 · 센서 관리">
          <div className="hidden flex-1 min-[768px]:block" />
          <GhostButton size="sm">보정 일정</GhostButton>
          <PrimaryButton size="sm">장비 추가</PrimaryButton>
        </PageTitle>

        <div className="grid grid-cols-2 gap-3 min-[1440px]:grid-cols-4">
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

        <div className={DETAIL_COLS[440]}>
          <Card className="flex flex-col overflow-hidden">
            <div className="flex flex-wrap items-center gap-2 border-b border-dp-line px-4 py-3">
              {DEVICE_KINDS.map((k) => (
                <Chip key={k} as="button" size="sm" active={k === kind} onClick={() => setKind(k)}>
                  {k}
                </Chip>
              ))}
              <div className="hidden flex-1 min-[768px]:block" />
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="장비명 · 시리얼 검색"
                aria-label="장비명 · 시리얼 검색"
                className="rounded-md bg-dp-inset px-3 py-1.5 text-[11.5px] leading-none text-dp-ink placeholder:text-dp-faint focus:outline-2 focus:outline-dp-green-line"
              />
            </div>

            <div
              className={`${ROW_COLS} border-b border-dp-line bg-dp-inset-alt px-4 py-3 font-mono text-[10.5px] leading-none font-semibold tracking-[0.04em] text-dp-muted`}
            >
              <span>장비</span>
              <span className="hidden min-[768px]:block">위치</span>
              <span className="hidden min-[1280px]:block">최종 수신</span>
              <span className="hidden min-[1440px]:block">보정 예정</span>
              <span>상태</span>
            </div>

            <div>
              {visible.length === 0 ? (
                <p className="px-4 py-8 text-center text-[12px] leading-none text-dp-muted">
                  조건에 맞는 장비가 없습니다.
                </p>
              ) : (
                visible.map((device, i) => (
                  <div
                    key={device.name}
                    className={`${ROW_COLS} px-4 py-3 ${i < visible.length - 1 ? "border-b border-dp-line-row" : ""} ${
                      device.statusTone === "critical" ? "bg-dp-red-tint" : ""
                    }`}
                  >
                    <span className="min-w-0 text-[12.5px] leading-[1.4] font-semibold text-dp-ink">
                      {device.name} <span className="font-normal text-dp-muted">· {device.count}</span>
                    </span>
                    <span className="hidden text-[12px] leading-[1.4] font-medium text-dp-body min-[768px]:block">
                      {device.location}
                    </span>
                    <span
                      className={`hidden font-mono text-[11.5px] leading-[1.4] min-[1280px]:block ${
                        device.lastSeenAlert ? "font-semibold text-dp-red-ink" : "font-medium text-dp-muted"
                      }`}
                    >
                      {device.lastSeen}
                    </span>
                    <span
                      className={`hidden text-[11.5px] leading-[1.4] min-[1440px]:block ${
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
                ))
              )}
            </div>

            <div className="border-t border-dp-line px-4 py-3 text-[11.5px] leading-none text-dp-muted">
              {kind === "전체" && !query ? DEVICE_TOTAL_LABEL : `${visible.length}개 그룹 표시`}
            </div>
          </Card>

          <div className="flex flex-col gap-3">
            <MembersCard />
            <SystemLogCard />
          </div>
        </div>
      </ScreenBody>
    </Screen>
  );
}

function MembersCard() {
  return (
    <Card className="flex flex-col px-4 py-[15px]">
      <div className="mb-3 flex items-baseline">
        <CardTitle>사용자 · 권한</CardTitle>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none font-semibold text-dp-green">초대</span>
      </div>
      {MEMBERS.map((member, i) => (
        <div
          key={member.name}
          className={`flex items-center gap-3 py-2.5 ${i < MEMBERS.length - 1 ? "border-b border-dp-line-row" : ""}`}
        >
          <Avatar
            initial={member.initial}
            size={28}
            tone={member.tone === "admin" ? "green" : member.tone === "pending" ? "faint" : "gray"}
          />
          <span className="min-w-0 flex-1">
            <span className="block truncate text-[12.5px] leading-[1.3] font-semibold text-dp-ink">{member.name}</span>
            <span className="mt-1 block truncate text-[11px] leading-none text-dp-muted">{member.scope}</span>
          </span>
          <span
            className={`flex-none rounded-[5px] px-[9px] py-1 text-[10.5px] leading-[1.4] font-semibold ${
              member.tone === "admin"
                ? "bg-dp-green-tint-2 text-dp-green-ink"
                : member.tone === "pending"
                  ? "bg-dp-badge-neutral text-dp-muted"
                  : "bg-dp-badge-neutral text-dp-body"
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
    <Card className="flex flex-1 flex-col px-4 py-[15px]">
      <div className="mb-2.5">
        <CardTitle>시스템 로그</CardTitle>
      </div>
      <div>
        {SYSTEM_LOG.map((entry, i) => (
          <TimelineRow
            key={`${entry.time}-${i}`}
            time={entry.time}
            text={entry.text}
            dim={entry.dim}
            last={i === SYSTEM_LOG.length - 1}
          />
        ))}
      </div>
      <div className="flex-1" />
      <div className="mt-2.5 rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body">
        전체 로그
      </div>
    </Card>
  );
}
