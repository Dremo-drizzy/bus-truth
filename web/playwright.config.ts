import { defineConfig, devices } from "@playwright/test";

/**
 * The smoke suite runs against the built site, not the dev server.
 *
 * `vite preview` serves `dist/`, which is what a visitor gets: minified, code-split,
 * and with the lazy page chunks actually fetched over HTTP. A dev-server run would
 * exercise a bundle nobody is ever served and would hide a broken dynamic import.
 *
 * Two viewports, because the requirement is explicit about both: a desktop width and
 * 400px, where the no-horizontal-scroll rule has to hold.
 */
export default defineConfig({
  testDir: "./tests",
  // A failing smoke test must fail, not be retried into passing. A flake here is a
  // finding about the page, not noise to be smoothed over.
  retries: 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["list"]],
  outputDir: "test-results",

  use: {
    baseURL: "http://localhost:4173",
    screenshot: "only-on-failure",
    trace: process.env.CI ? "retain-on-failure" : "off",
  },

  projects: [
    {
      name: "desktop",
      use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 800 } },
    },
    {
      name: "phone",
      // 400px wide, as specified. Not a device preset, because the number is the
      // requirement and a preset could change it under us in a later release.
      use: { ...devices["Desktop Chrome"], viewport: { width: 400, height: 900 } },
    },
  ],

  webServer: {
    command: "npm run preview -- --port 4173 --strictPort",
    url: "http://localhost:4173",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
