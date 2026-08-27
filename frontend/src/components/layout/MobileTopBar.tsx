"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { CROP_LABELS } from "@/constants";
import { getMe } from "@/lib/api/auth";
import type { FarmSummaryResponse, UserResponse } from "@/types";

// 모바일 전용 상단 다크 바(이슈 #147, 시안 m1~m4) — 기존 GlobalBar(데스크톱·태블릿, ≥768px)는
// 그대로 두고 <768px에서만 이 컴포넌트로 완전히 대체한다(AppShell에서 hidden/min-[768px]:hidden
// 클래스로 갈라 둘 다 동시에 마운트되지 않게 한다).
//
// 시안 스펙: 홈은 농장 전환 블록, 더보기는 브랜드+아바타(+본인 정보), 그 외 화면은
// 뒤로가기+화면명. "액션 하나"(예: 알람 화면의 전체확인, 제어 화면의 자동/수동 토글)는 이 바가
// 아니라 각 화면 콘텐츠 상단에 둔다 — 셸이 페이지별 상태(모드·필터 등)에 prop-drilling 없이
// 접근할 방법이 없어, 화면 소유 상태는 화면이 직접 렌더하는 편이 더 정확하고 덜 깨진다.
export type MobileTopBarVariant = "home" | "more" | "sub";

interface MobileTopBarProps {
  variant: MobileTopBarVariant;
  className?: string;
  // home
  farms?: FarmSummaryResponse[] | null;
  farmsLoadFailed?: boolean;
  effectiveFarmId?: string | null;
  unackCount?: number | null;
  onSelectFarm?: (farmId: string) => void;
  // sub
  title?: string | null;
}

export default function MobileTopBar({
  variant,
  className = "",
  farms = null,
  farmsLoadFailed = false,
  effectiveFarmId = null,
  unackCount = null,
  onSelectFarm,
  title = null,
}: MobileTopBarProps) {
  const router = useRouter();

  return (
    <header
      className={`sticky top-0 z-40 flex-none bg-[#14171a] text-white ${className}`}
    >
      {variant === "home" && (
        <HomeHeader
          farms={farms}
          farmsLoadFailed={farmsLoadFailed}
          effectiveFarmId={effectiveFarmId}
          unackCount={unackCount}
          onSelectFarm={onSelectFarm}
        />
      )}
      {variant === "more" && <MoreHeader farmCount={farms?.length ?? null} />}
      {variant === "sub" && (
        <div className="flex h-14 items-center gap-2.5 px-4">
          <button
            type="button"
            onClick={() => router.back()}
            aria-label="뒤로 가기"
            className="flex h-11 w-11 flex-none items-center justify-center text-white/70"
          >
            <span aria-hidden="true" className="text-base">
              ‹
            </span>
          </button>
          <h1 className="min-w-0 flex-1 truncate text-[14.5px] leading-none font-semibold">
            {title ?? ""}
          </h1>
        </div>
      )}
    </header>
  );
}

function HomeHeader({
  farms,
  farmsLoadFailed,
  effectiveFarmId,
  unackCount,
  onSelectFarm,
}: {
  farms: FarmSummaryResponse[] | null;
  farmsLoadFailed: boolean;
  effectiveFarmId: string | null;
  unackCount: number | null;
  onSelectFarm?: (farmId: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const currentFarm =
    farms?.find((f) => String(f.id) === effectiveFarmId) ?? null;

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const label = farmsLoadFailed
    ? "농장 목록 오류"
    : farms === null
      ? "불러오는 중..."
      : currentFarm
        ? currentFarm.name
        : farms.length === 0
          ? "농장 없음"
          : "농장 선택";

  return (
    <div ref={containerRef} className="px-[18px] pt-3.5 pb-3.5">
      <div className="flex items-center gap-2.5">
        <div className="text-[14px] leading-none font-bold">
          스마트팜 <span className="text-[#5fd08c]">DFX</span>
        </div>
        <div className="flex-1" />
        {effectiveFarmId && unackCount !== null && (
          <Link
            href={`/farms/${effectiveFarmId}/alarms`}
            className="flex h-11 items-center gap-1.5 text-[11.5px] leading-none text-white/70"
          >
            알람
            <span
              className={`rounded-[9px] px-[7px] py-0.5 font-mono text-[10px] leading-[1.4] font-semibold ${
                unackCount > 0
                  ? "bg-dp-red text-white"
                  : "bg-white/10 text-white/50"
              }`}
            >
              {unackCount}
            </span>
          </Link>
        )}
      </div>

      {farms !== null && farms.length > 0 && (
        <button
          type="button"
          aria-expanded={open}
          onClick={() => setOpen((prev) => !prev)}
          className="mt-3 flex min-h-11 w-full items-center justify-between rounded-[9px] bg-white/10 px-[13px] py-[11px] text-left"
        >
          <span className="min-w-0">
            <span className="block truncate text-[13.5px] leading-none font-semibold">
              {label}
            </span>
            {currentFarm && (
              <span className="mt-[5px] block text-[11px] leading-none text-white/55">
                {CROP_LABELS[currentFarm.cropType] ?? currentFarm.cropType}
              </span>
            )}
          </span>
          <span
            aria-hidden="true"
            className="flex-none text-[13px] text-white/55"
          >
            전환 ▾
          </span>
        </button>
      )}

      {open && farms !== null && farms.length > 0 && (
        <ul className="mt-1.5 flex flex-col overflow-hidden rounded-[9px] bg-white text-dp-body shadow-lg">
          {farms.map((farm) => {
            const active = String(farm.id) === effectiveFarmId;
            return (
              <li key={farm.id}>
                <button
                  type="button"
                  onClick={() => {
                    onSelectFarm?.(String(farm.id));
                    setOpen(false);
                  }}
                  aria-current={active ? "true" : undefined}
                  className={`flex min-h-11 w-full items-center px-4 py-3 text-left text-[13.5px] ${
                    active
                      ? "bg-dp-green-tint font-semibold text-dp-green-ink"
                      : "text-dp-ink"
                  }`}
                >
                  {farm.name}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function MoreHeader({ farmCount }: { farmCount: number | null }) {
  const [user, setUser] = useState<UserResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    getMe()
      .then((data) => {
        if (!cancelled) setUser(data);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="px-[18px] pt-4 pb-[18px]">
      <div className="flex items-center gap-2.5">
        <div className="text-[14.5px] leading-none font-bold">
          스마트팜 <span className="text-[#5fd08c]">DFX</span>
        </div>
        <div className="flex-1" />
        <span
          aria-hidden="true"
          className="flex h-[26px] w-[26px] flex-none items-center justify-center rounded-full bg-dp-green text-[11px] leading-none font-semibold text-dp-on-green"
        >
          {user?.nickname ? user.nickname.charAt(0).toUpperCase() : ""}
        </span>
      </div>
      {user && (
        <>
          <div className="mt-3.5 text-[15px] leading-[1.3] font-semibold">
            {user.nickname}
          </div>
          <div className="mt-[5px] text-[11.5px] leading-none text-white/55">
            {user.email}
            {farmCount !== null ? ` · 농장 ${farmCount}곳` : ""}
          </div>
        </>
      )}
    </div>
  );
}
