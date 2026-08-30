import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { fileURLToPath } from "node:url";

const dirname = path.dirname(fileURLToPath(import.meta.url));

// 프론트 유닛 테스트 러너(이슈 #148에서 도입 — 이 브랜치는 backend/followup-145-140-135 위에
// 쌓여 있어 #148의 vitest 설정을 상속받지 못한다. #140 완료 기준(절단 안내 테스트)을 위해
// 동일 구성으로 재도입한다). 최소 구성만: jsdom 환경 + tsconfig의 "@/*" 별칭만 맞춘다.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(dirname, "./src"),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
  },
});
