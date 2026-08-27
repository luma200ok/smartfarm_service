"use client";

import { useEffect, useState } from "react";
import ForecastWidget from "@/components/environment/ForecastWidget";
import PesticideCard from "@/components/pesticide/PesticideCard";
import { getFarm } from "@/lib/api/farms";
import type { FarmResponse } from "@/types";
import FarmChat from "./FarmChat";

interface FarmServicesLayoutProps {
  farmId: string;
}

// 부가 서비스 화면(#144 시안 05) — AI 챗봇(flex:1) + 우측 430px 컬럼(날씨 예보·농약 정보).
// 농약 카드는 cropType이 필요해(§4.16 인증만·farm-scoped 아님) 여기서 농장을 조회해 내려준다
// — FarmChat은 자체적으로도 farm을 조회하지만(쓰기 권한 판정용) 두 컴포넌트가 독립적으로
// 조회하는 기존 관례를 따른다(FarmDeviceManagement·FarmMembers도 각자 getFarm 호출).
export default function FarmServicesLayout({ farmId }: FarmServicesLayoutProps) {
  const [farm, setFarm] = useState<FarmResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    getFarm(farmId)
      .then((res) => {
        if (!cancelled) setFarm(res);
      })
      .catch(() => {
        // 농약 카드만 못 뜨고 챗봇·날씨는 계속 보여준다(보조 위젯 실패가 전체를 막지 않는다).
      });
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  return (
    <div className="flex flex-col gap-3.5 p-[26px_30px] min-[1100px]:flex-row">
      <div className="min-w-0 flex-1 overflow-hidden rounded-[10px] border border-dp-line bg-dp-surface">
        <FarmChat farmId={farmId} />
      </div>

      <div className="flex w-full flex-none flex-col gap-3 min-[1100px]:w-[430px]">
        <ForecastWidget farmId={farmId} />
        {farm && <PesticideCard cropType={farm.cropType} />}
      </div>
    </div>
  );
}
