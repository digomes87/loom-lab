import type { NextConfig } from "next";

// Proxy same-origin: o browser chama /api/* e o Next repassa ao backend Spring.
// Em containers, basta definir BACKEND_URL=http://backend:8080.
const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default nextConfig;
