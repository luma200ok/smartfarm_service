import Link from "next/link";
import {
  ARTBOARDS,
  NOT_DESIGNED,
  RESPONSIVE_PRINCIPLES,
  RESPONSIVE_TIERS,
} from "@/components/design-preview/mock";

// 프리뷰 인덱스(이슈 #83) — 핸드오프 `design_handoff_smartfarm_dfx/` 의 화면 10종 입구.
// 핸드오프 README의 Responsive 표와 "반영되지 않은 범위"도 함께 옮겨,
// 화면만 남고 맥락이 사라지지 않게 했다.
export default function DesignPreviewIndexPage() {
  return (
    <div className="min-h-dvh bg-dp-canvas px-6 py-12 font-dp-sans min-[640px]:px-10">
      <div className="mx-auto max-w-5xl">
        <p className="font-mono text-[11px] leading-none font-semibold tracking-[0.08em] text-dp-green">
          DESIGN HANDOFF PREVIEW
        </p>
        <h1 className="mt-3 text-[28px] leading-tight font-bold text-dp-ink">스마트팜 플랫폼 DFX</h1>
        <p className="mt-2 max-w-3xl text-[14px] leading-relaxed text-dp-body">
          식물공장을 여러 곳 운영하는 관리 매니저를 위한 통합 관제 화면입니다. PC 기준 1920 × 1080, 모바일 390 × 844.
          표시되는 <b className="text-dp-ink">수치·농장명·사용자명은 전부 예시 데이터</b>이며 실제 API에 연결되어 있지
          않습니다.
        </p>

        {ARTBOARDS.map((group) => (
          <section key={group.group} className="mt-10">
            <h2 className="font-mono text-[11px] leading-none font-semibold tracking-[0.1em] text-dp-muted">
              {group.group}
            </h2>
            <ul className="mt-4 grid gap-3 min-[640px]:grid-cols-2 min-[1024px]:grid-cols-3">
              {group.boards.map((board) => (
                <li key={board.id}>
                  <Link
                    href={board.href}
                    className="flex h-full flex-col rounded-[10px] border border-dp-line bg-dp-surface px-4 py-4 transition-colors hover:border-dp-green-line hover:bg-dp-green-tint"
                  >
                    <span className="flex items-baseline gap-2">
                      <span className="rounded-[4px] bg-dp-ink px-2 py-1 font-mono text-[11px] leading-none font-semibold text-dp-surface">
                        {board.id}
                      </span>
                      <span className="text-[14px] leading-none font-semibold text-dp-ink">{board.title}</span>
                      <span className="ml-auto font-mono text-[10.5px] leading-none text-dp-muted">
                        {board.viewport}
                      </span>
                    </span>
                    <span className="mt-2.5 text-[12.5px] leading-[1.5] text-dp-muted">{board.caption}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))}

        <section className="mt-12">
          <h2 className="font-mono text-[11px] leading-none font-semibold tracking-[0.1em] text-dp-muted">반응형 규칙</h2>
          <div className="mt-4 grid gap-4 min-[640px]:grid-cols-2 min-[1024px]:grid-cols-4">
            {RESPONSIVE_TIERS.map((tier) => (
              <div key={tier.name} className="overflow-hidden rounded-[9px] border border-dp-line bg-dp-surface">
                <div className={`px-4 py-3 text-white ${tier.primary ? "bg-dp-green" : "bg-dp-bar"}`}>
                  <div className="text-[13.5px] leading-none font-semibold">{tier.name}</div>
                  <div className={`mt-1.5 font-mono text-[10.5px] leading-none ${tier.primary ? "opacity-75" : "opacity-60"}`}>
                    {tier.range}
                  </div>
                </div>
                <div className="px-4 py-3.5 text-[12.5px] leading-[1.7] text-dp-body">
                  {tier.lines.map((line) => (
                    <div key={line}>{line}</div>
                  ))}
                </div>
              </div>
            ))}
          </div>

          <div className="mt-5 grid gap-5 border-t border-dp-line pt-4.5 text-[12px] leading-[1.65] text-dp-sub min-[640px]:grid-cols-3">
            {RESPONSIVE_PRINCIPLES.map((p) => (
              <div key={p.title}>
                <b className="text-dp-ink">{p.title}</b>
                <br />
                {p.body}
              </div>
            ))}
          </div>
        </section>

        <section className="mt-12 border-t border-dp-line pt-6">
          <h2 className="text-[16px] leading-none font-bold text-dp-ink">아직 디자인이 없는 화면</h2>
          <p className="mt-2 text-[12.5px] leading-[1.7] text-dp-muted">
            설계는 6개 대분류 / 28개 하위 메뉴를 정의했지만 이 번들에는 대분류별 대표 화면 1개씩만 들어 있습니다. 각
            화면의 좌측 서브 내비에는 메뉴가 모두 표시되어 있으니 라우팅 구조만 먼저 잡아두면 됩니다.
          </p>
          <ul className="mt-3 flex flex-col gap-1.5 text-[12.5px] leading-[1.6] text-dp-body">
            {NOT_DESIGNED.map((line) => (
              <li key={line}>· {line}</li>
            ))}
          </ul>
        </section>

        <p className="mt-10 pb-8 text-[12px] leading-[1.6] text-dp-muted">
          출처는 claude.ai/design 프로젝트의{" "}
          <span className="font-mono text-dp-body">design_handoff_smartfarm_dfx/screens.html</span> 입니다. 이 프리뷰는
          운영 화면(<span className="font-mono text-dp-body">/dashboard</span>,{" "}
          <span className="font-mono text-dp-body">/farms</span>)과 완전히 분리되어 있습니다.
        </p>
      </div>
    </div>
  );
}
