import type { Metadata } from "next";
import PreviewToolbar from "@/components/design-preview/PreviewToolbar";

export const metadata: Metadata = {
  title: "스마트팜 플랫폼 · 디자인 시안 프리뷰",
  description: "claude.ai/design 시안(스마트팜 플랫폼 DFX-APP)의 화면 구현 — 데이터는 모두 예시값",
};

// 디자인 시안 프리뷰(이슈 #83) 전용 레이아웃.
// 시안이 지정한 Pretendard를 CDN에서 불러온다. 실패하면 globals.css의 --font-dp-sans
// 폴백 스택(Apple SD Gothic Neo / Malgun Gothic)으로 자연스럽게 떨어진다.
// 운영 화면은 기존대로 Geist를 쓰므로 이 레이아웃 밖에는 영향이 없다.
export default function DesignPreviewLayout({ children }: LayoutProps<"/design-preview">) {
  return (
    <>
      <link
        rel="stylesheet"
        href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css"
      />
      <div className="flex-1 font-dp-sans">{children}</div>
      <PreviewToolbar />
    </>
  );
}
