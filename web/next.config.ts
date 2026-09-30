import type { NextConfig } from "next";

/**
 * The browser only ever talks to this app's own origin: /api/* is proxied to the
 * Spring service. That means no CORS configuration on the api, and it matches how
 * this deploys — a reverse proxy in front of both — rather than working only because
 * a development CORS rule is open.
 */
const apiOrigin = process.env.BUS_TRUTH_API_ORIGIN ?? "http://localhost:8084";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiOrigin}/api/:path*` }];
  },
};

export default nextConfig;
