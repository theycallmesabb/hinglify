import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactCompiler: true,
  async rewrites() {
    return [
      {
        source: "/api/jobs",
        destination: "http://localhost:8080/jobs",
      },
      {
        source: "/api/jobs/:path*",
        destination: "http://localhost:8080/jobs/:path*",
      },
    ];
  },
};

export default nextConfig;
