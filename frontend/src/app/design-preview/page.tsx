import Link from "next/link";
import { ARTBOARDS, DESIGN_TAGS, IA_PRINCIPLES } from "@/components/design-preview/mock";

// 프리뷰 인덱스(이슈 #83) — 시안 `스마트팜 플랫폼.dc.html`의 아트보드 8개로 가는 입구.
// 시안 캔버스 자체의 설명(설계 전제·다음 시도)도 함께 옮겨 맥락 없이 화면만 남지 않게 했다.
export default function DesignPreviewIndexPage() {
  return (
    <div className="min-h-dvh bg-dp-canvas px-6 py-12 sm:px-10">
      <div className="mx-auto max-w-5xl">
        <p className="font-mono text-[11px] leading-none font-semibold tracking-[0.08em] text-dp-green">
          DESIGN PREVIEW
        </p>
        <h1 className="mt-3 text-[28px] leading-tight font-bold text-dp-ink">
          스마트팜 플랫폼 DFX-APP
        </h1>
        <p className="mt-2 text-[14px] leading-relaxed text-dp-body">
          식물공장 / 다농장 매니저용 시안을 화면으로 구현한 프리뷰입니다. 표시되는 값은{" "}
          <b className="text-dp-ink">전부 예시 데이터</b>이며 실제 API에 연결되어 있지 않습니다.
        </p>

        <div className="mt-4 flex flex-wrap gap-2">
          {DESIGN_TAGS.map((tag) => (
            <span
              key={tag}
              className="rounded-[20px] border border-dp-line-strong px-2.5 py-1.5 text-[12px] text-dp-body"
            >
              {tag}
            </span>
          ))}
        </div>

        {ARTBOARDS.map((group) => (
          <section key={group.turn} className="mt-12">
            <h2 className="text-[16px] leading-none font-bold text-dp-ink">{group.turn}</h2>
            <p className="mt-2.5 max-w-3xl text-[13px] leading-[1.75] text-dp-body">{group.note}</p>

            <ul className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {group.boards.map((board) => (
                <li key={board.id}>
                  <Link
                    href={board.href}
                    className="flex h-full flex-col rounded-[10px] border border-dp-line bg-dp-surface px-4 py-4 transition-colors hover:border-dp-green-line hover:bg-dp-green-tint"
                  >
                    <span className="flex items-baseline gap-2">
                      <span className="rounded-[5px] bg-dp-ink px-1.5 py-1 font-mono text-[10.5px] leading-none font-semibold text-dp-surface">
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

        <section className="mt-12 border-t border-dp-line pt-6">
          <h2 className="text-[16px] leading-none font-bold text-dp-ink">IA 원칙</h2>
          <div className="mt-4 grid gap-5 sm:grid-cols-3">
            {IA_PRINCIPLES.map((p) => (
              <div key={p.title} className="text-[12px] leading-[1.6] text-dp-muted">
                <b className="text-dp-ink">{p.title}</b>
                <br />
                {p.body}
              </div>
            ))}
          </div>
        </section>

        <p className="mt-10 pb-8 text-[12px] leading-[1.6] text-dp-muted">
          시안 원본은 claude.ai/design 프로젝트의{" "}
          <span className="font-mono text-dp-body">스마트팜 플랫폼.dc.html</span> 입니다. 이 프리뷰는 운영
          화면(<span className="font-mono text-dp-body">/dashboard</span>,{" "}
          <span className="font-mono text-dp-body">/farms</span>)과 완전히 분리되어 있습니다.
        </p>
      </div>
    </div>
  );
}
