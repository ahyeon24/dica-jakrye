import type { NextConfig } from "next";

// 백엔드(Spring Boot) 주소. 서버 컴포넌트는 이 주소로 직접 부르고,
// 브라우저는 같은 출처의 /api/* 로 부르면 아래 rewrites가 백엔드로 넘겨 준다. (CORS 없이 개발)
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
