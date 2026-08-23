import {
  IA_PRINCIPLES,
  IA_UNSELECTED_NOTE,
  NAV_SECTIONS,
} from "@/components/design-preview/mock";

// 시안 1a — 메뉴 구조(IA) 다이어그램.
// 실제 화면이 아니라 구조 설명 카드라 상단 바도 '그림'으로 그린다(TopBar 컴포넌트 미사용).
// 앞 3개 대분류(대시보드·제어·데이터)만 초록 헤더, 뒤 3개는 먹색 헤더 — 시안 그대로.
export default function IaPage() {
  return (
    <div className="min-h-dvh bg-dp-canvas px-6 py-10">
      <div className="mx-auto max-w-[1200px]">
        <header className="mb-6">
          <h1 className="text-[20px] leading-none font-bold text-dp-ink">메뉴 구조 (IA)</h1>
          <p className="mt-2 text-[13px] leading-relaxed text-dp-body">
            선택된 10개 항목을 6개 대분류로 묶었습니다.
          </p>
        </header>

        <div className="rounded-[10px] border border-dp-line bg-dp-inset-alt px-8 py-8">
          {/* 상단 글로벌 바 (구조 설명용 도식) */}
          <div className="mb-2 flex items-center gap-2.5 rounded-lg bg-dp-bar px-4 py-3 text-white">
            <div className="text-[13px] leading-none font-bold">스마트팜 DFX</div>
            <div className="h-[15px] w-px bg-white/20" />
            <div className="rounded-md bg-white/10 px-2.5 py-1.5 text-[12px]">전체 농장 4곳 ▾</div>
            <div className="flex-1" />
            <div className="flex gap-4.5 text-[12px] text-white/70">
              <span>검색</span>
              <span>알람 3</span>
              <span>AI 챗봇</span>
              <span>계정</span>
            </div>
          </div>
          <p className="mb-6 text-[11.5px] leading-none text-dp-muted">
            상단 글로벌 바 — 농장 전환 · 검색 · 알람 · 챗봇 · 계정 (전 화면 고정)
          </p>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {NAV_SECTIONS.map((section, i) => (
              <div key={section.label} className="overflow-hidden rounded-[9px] border border-dp-line">
                <div
                  className={`flex items-baseline gap-2 px-4 py-3 ${
                    i < 3 ? "bg-dp-green text-dp-on-green" : "bg-dp-slate text-dp-on-slate"
                  }`}
                >
                  <span className={`font-mono text-[10px] leading-none font-semibold ${i < 3 ? "opacity-70" : "opacity-50"}`}>
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <span className="text-[14px] leading-none font-semibold">{section.label}</span>
                </div>
                <div className="bg-dp-surface py-2">
                  {section.items.map((item, j) => (
                    <div key={item} className="px-4 py-2 text-[13px] leading-[1.4] font-medium text-dp-ink">
                      {item}
                      {/* 대시보드 첫 항목이 홈 */}
                      {i === 0 && j === 0 ? (
                        <span className="ml-1.5 inline-block rounded-[3px] border border-dp-green px-1.5 py-1 text-[10px] leading-none font-semibold text-dp-green">
                          홈
                        </span>
                      ) : null}
                    </div>
                  ))}
                  {section.label === "부가 서비스" ? (
                    <div className="px-4 py-2 text-[12px] leading-[1.4] text-dp-muted">{IA_UNSELECTED_NOTE}</div>
                  ) : null}
                </div>
              </div>
            ))}
          </div>

          <div className="mt-6 grid gap-5.5 border-t border-dp-line pt-4.5 text-[12px] leading-[1.6] text-dp-muted md:grid-cols-3">
            {IA_PRINCIPLES.map((p) => (
              <div key={p.title}>
                <b className="text-dp-ink">{p.title}</b>
                <br />
                {p.body}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
