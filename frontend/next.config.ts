import type { NextConfig } from "next";

// 프로덕션 빌드(next build)는 NODE_ENV=production으로 실행되므로,
// NEXT_PUBLIC_API_URL 미설정 시 여기서 빌드를 실패시킨다. (docs/api-contract.md §6)
if (process.env.NODE_ENV === "production" && !process.env.NEXT_PUBLIC_API_URL) {
  throw new Error(
    "NEXT_PUBLIC_API_URL 환경변수가 설정되지 않았습니다. 프로덕션 빌드를 진행할 수 없습니다."
  );
}

const nextConfig: NextConfig = {
  output: "standalone",
};

export default nextConfig;
