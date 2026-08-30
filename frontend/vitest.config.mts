import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { fileURLToPath } from "node:url";

const dirname = path.dirname(fileURLToPath(import.meta.url));

// 프론트 유닛 테스트 러너(이슈 #148 — 이전까지 테스트 러너 자체가 없었다, STATUS.md §알려진 이슈
// 참고). 최소 구성만: jsdom 환경 + tsconfig의 "@/*" 별칭만 맞춘다. E2E(Playwright)와는 별도.
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
