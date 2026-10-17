import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // Same-origin /api in development, so there is no CORS configuration to get
    // wrong and the fetch code is identical in dev and production.
    proxy: { "/api": "http://localhost:8084" },
  },
  // The same proxy for `vite preview`, so the production bundle can be checked
  // against the real API rather than only the dev server being exercised.
  preview: {
    proxy: { "/api": "http://localhost:8084" },
  },
});
