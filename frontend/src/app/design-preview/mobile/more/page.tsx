import { MobileFrame } from "@/components/design-preview/MobileFrame";
import { M_MORE_FOOTER, M_MORE_GROUPS, M_MORE_USER } from "@/components/design-preview/mock";

// 화면 M4 — 모바일 더보기. 핸드오프 `Mobile`.
// 섹션 헤더가 붙은 리스트 그룹. 상태가 있는 항목은 우측에 배지(이상 2, 신규 1) 또는 값(27° 흐림).
// 데이터·부가 서비스·관리는 하단 4탭에 자리가 없어 이 화면 안으로 들어온다.

const BADGE_TONE = {
  green: "bg-dp-green-tint-2 text-dp-green-ink",
  red: "bg-dp-red-tint-2 text-dp-red-ink",
  plain: "text-dp-muted",
} as const;

export default function MobileMorePage() {
  return (
    <MobileFrame
      activeTab="more"
      header={
        <div className="px-4.5 pt-4 pb-4.5">
          <div className="flex items-center gap-2.5">
            <div className="text-[14.5px] leading-none font-bold">
              스마트팜 <span className="text-[#5fd08c]">DFX</span>
            </div>
            <div className="flex-1" />
            <span className="flex h-[26px] w-[26px] items-center justify-center rounded-full bg-dp-green text-[11px] leading-none font-semibold text-dp-on-green">
              김
            </span>
          </div>
          <div className="mt-3.5 text-[15px] leading-[1.3] font-semibold">{M_MORE_USER.name}</div>
          <div className="mt-1.5 text-[11.5px] leading-none text-white/55">{M_MORE_USER.meta}</div>
        </div>
      }
    >
      {M_MORE_GROUPS.map((group) => (
        <div key={group.title} className="overflow-hidden rounded-[10px] border border-dp-line bg-dp-surface">
          <div className="border-b border-dp-line-row bg-dp-inset-alt px-[15px] py-2.5 font-mono text-[10.5px] leading-none font-semibold tracking-[0.06em] text-dp-muted">
            {group.title}
          </div>
          {group.items.map((item, i) => (
            <div
              key={item.label}
              className={`flex items-center px-[15px] py-3 ${i < group.items.length - 1 ? "border-b border-dp-line-row" : ""}`}
            >
              <span
                className={`flex-1 text-[13.5px] leading-none ${
                  item.highlight ? "font-semibold text-dp-green-ink" : "font-medium text-dp-ink"
                }`}
              >
                {item.label}
              </span>
              <span className="flex items-center gap-2">
                {item.badge ? (
                  <span
                    className={`text-[10.5px] leading-[1.4] font-semibold ${BADGE_TONE[item.badge.tone]} ${
                      item.badge.tone === "plain" ? "text-[12px]" : "rounded-[5px] px-2 py-[3px]"
                    }`}
                  >
                    {item.badge.text}
                  </span>
                ) : null}
                <span className="text-[13px] leading-none text-dp-disabled" aria-hidden>
                  ›
                </span>
              </span>
            </div>
          ))}
        </div>
      ))}

      <div className="rounded-[10px] border border-dp-line bg-dp-surface px-[15px] py-3.5 text-[11.5px] leading-[1.6] text-dp-muted">
        {M_MORE_FOOTER[0]}
        <br />
        {M_MORE_FOOTER[1]}
      </div>
    </MobileFrame>
  );
}
