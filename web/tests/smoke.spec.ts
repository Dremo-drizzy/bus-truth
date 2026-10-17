import { expect, test } from "@playwright/test";
import type { Page, Route } from "@playwright/test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

/**
 * Every page, loaded in a real browser, at 1280px and 400px.
 *
 * This suite exists to retire a caveat rather than to decorate the build. R7 shipped
 * with "the pages have not been opened in a browser, so the rendering and the
 * no-sideways-scroll requirement are unverified" — a claim about what had not been
 * checked. These assertions check it, on every push.
 *
 * Three things are asserted for all five pages:
 *
 *   1. no console errors and no failed requests
 *   2. `document.scrollWidth` never exceeds the viewport, so nothing scrolls sideways
 *   3. the page reaches its loaded state and shows real content — not a spinner, not
 *      an error panel
 *
 * The API is served from fixtures captured from the real API against the real
 * database (`tests/fixtures/`), so the suite is deterministic and needs no PostgreSQL
 * in CI. They are recorded responses, not invented ones: 67 routes, 679 arrivals on
 * route 1, 243 vehicles. The one adjustment is the live map's timestamps, which are
 * shifted to "now" at request time — the endpoint only returns the last ten minutes,
 * so a recorded response would otherwise always render as "no recent data" and the
 * map's live path would never be exercised.
 */

const fixture = (name: string): unknown =>
  JSON.parse(
    readFileSync(fileURLToPath(new URL(`./fixtures/${name}`, import.meta.url)), "utf8"),
  );

const COVERAGE = fixture("coverage.json");
const META = fixture("meta.json");
const SUMMARY = fixture("summary.json");
const SNAPSHOT = fixture("vehicles-snapshot.json");
const ROUTES = fixture("routes.json");
const ROUTES_PERFORMANCE = fixture("routes-performance.json");
const ROUTE_ONE = fixture("route-1.json");
const STOP = fixture("stop.json");
const LIVE = fixture("vehicles-live.json") as {
  generated_at: string;
  newest_position_at: string;
  features: { properties: { vehicle_timestamp: string } }[];
};

/** The live fixture with every timestamp moved to now, so the map sees fresh data. */
function freshLive() {
  const now = new Date();
  const iso = now.toISOString();
  return {
    ...LIVE,
    generated_at: iso,
    newest_position_at: iso,
    features: LIVE.features.map((feature, index) => ({
      ...feature,
      properties: {
        ...feature.properties,
        // Spread over the ten-minute window so the per-dot opacity ramp is exercised
        // rather than every dot rendering identically.
        vehicle_timestamp: new Date(now.getTime() - (index % 10) * 60_000).toISOString(),
      },
    })),
  };
}

const json = (route: Route, body: unknown) =>
  route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });

/**
 * Fixtures by default; the real API when BT_LIVE is set.
 *
 * CI has no PostgreSQL, so the committed responses are what make this suite
 * deterministic there. Running it with BT_LIVE=1 against a local api and database
 * proves the same pages against live numbers, which is the half a fixture cannot
 * cover — the fixtures were captured from exactly that setup.
 */
const LIVE_API = process.env.BT_LIVE === "1";

async function serveApi(page: Page) {
  if (LIVE_API) return;
  await page.route("**/api/coverage", (route) => json(route, COVERAGE));
  await page.route("**/api/meta", (route) => json(route, META));
  await page.route("**/api/performance/summary*", (route) => json(route, SUMMARY));
  await page.route("**/api/vehicles/live", (route) => json(route, freshLive()));
  await page.route("**/api/vehicles/snapshot", (route) => json(route, SNAPSHOT));
  await page.route("**/api/routes/performance*", (route) => json(route, ROUTES_PERFORMANCE));
  await page.route("**/api/routes/*/performance*", (route) => json(route, ROUTE_ONE));
  await page.route("**/api/stops/*/reliability*", (route) => json(route, STOP));
  await page.route("**/api/routes", (route) => json(route, ROUTES));
}

/** Console errors and failed requests, collected for the life of the page. */
function watchForProblems(page: Page): string[] {
  const problems: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") problems.push(`console: ${message.text()}`);
  });
  page.on("pageerror", (error) => problems.push(`uncaught: ${error.message}`));
  page.on("requestfailed", (request) => {
    // The basemap tiles come from a third party and are not this project's concern;
    // a tile that fails to load is not a broken page.
    if (!request.url().includes("openfreemap.org") && !request.url().includes("tiles")) {
      problems.push(`request failed: ${request.url()}`);
    }
  });
  return problems;
}

const PAGES = [
  { name: "live map", path: "/", expect: /vehicles|no recent data|0 vehicles/i },
  { name: "routes leaderboard", path: "/routes", expect: /on-time performance/i },
  { name: "route detail", path: "/routes/1", expect: /by hour of the day/i },
  { name: "stop reliability", path: "/stops/8640", expect: /by route|arrivals were derived/i },
  { name: "method", path: "/about", expect: /how this is measured/i },
];

for (const subject of PAGES) {
  test(`${subject.name} renders with no console errors and no sideways scroll`, async ({
    page,
  }, testInfo) => {
    const problems = watchForProblems(page);
    await serveApi(page);

    await page.goto(subject.path);
    await expect(page.getByText(subject.expect).first()).toBeVisible({ timeout: 15_000 });

    // Never still loading once settled, and never an error panel — with one honest
    // exception. Against the live API the map legitimately raises an alert when no
    // positions have arrived in the last ten minutes, which is one of the three states
    // it exists to distinguish. Running live with the collector replaying the archive
    // rather than polling is exactly that case, so the alert is asserted to be that
    // notice rather than asserted away.
    await expect(page.getByText(/^Loading /)).toHaveCount(0);
    if (LIVE_API && subject.path === "/") {
      for (const alert of await page.getByRole("alert").all()) {
        await expect(alert).toContainText(/no recent data|cannot reach the API/i);
      }
    } else {
      await expect(page.getByRole("alert")).toHaveCount(0);
    }

    const overflow = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth,
    }));
    expect(
      overflow.scrollWidth,
      `${subject.name} scrolls sideways: content ${overflow.scrollWidth}px in a ${overflow.innerWidth}px viewport`,
    ).toBeLessThanOrEqual(overflow.innerWidth);

    await testInfo.attach(`${subject.name}-${testInfo.project.name}.png`, {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });

    expect(problems, problems.join("\n")).toEqual([]);
  });
}

test("the leaderboard shows a real percentage beside its arrival count", async ({ page }) => {
  await serveApi(page);
  await page.goto("/routes");

  // A number from the API, not a placeholder: the fixture's best rankable route.
  const table = page.locator("table").first();
  await expect(table).toBeVisible();
  await expect(table.locator("tbody tr").first()).toContainText(/%/);
  await expect(table.locator("tbody tr").first()).toContainText(/of [\d,]+/);
});

test("the leaderboard leads with the week and the days inside it", async ({ page }) => {
  await serveApi(page);
  await page.goto("/routes");

  // No page may present a single percentage as "Halifax's on-time performance": the
  // figure appears with its period, and the days it averages appear beside it.
  await expect(page.getByRole("heading", { name: /on time, 2026-\d\d-\d\d to/i })).toBeVisible();
  await expect(page.getByRole("heading", { name: /by hour of the day/i })).toBeVisible();
  await expect(page.getByRole("heading", { name: /by service date/i })).toBeVisible();
  await expect(page.getByText(/points.{0,40}between the best and worst/i)).toBeVisible();
});

test("a thin route is shown apart rather than ranked", async ({ page }) => {
  test.skip(LIVE_API, "depends on the fixture's 13 unrankable routes");
  await serveApi(page);
  await page.goto("/routes");

  // The fixture carries 13 routes with rankable: false. They must not sit in the main
  // ranking, because a route seen once at 100% would otherwise top it.
  await expect(page.getByRole("heading", { name: /too thinly observed to rank/i })).toBeVisible();
});

test("too few observations is said in words, not printed as a percentage", async ({ page }) => {
  test.skip(LIVE_API, "depends on the fixture's 20 held-back stops");
  await serveApi(page);
  await page.goto("/routes/1");

  // Route 1's fixture holds stops below the ranking floor and hourly buckets below the
  // display floor. Wherever that happens the page must say so rather than print a
  // number from a handful of rows. The count is not asserted: it is a property of
  // whichever week the fixture was captured from, and a test that pins it would fail
  // on a re-capture rather than on a defect.
  await expect(page.getByText(/further stops? (was|were) left out/i)).toBeVisible();
  await expect(page.getByText(/Ranked among stops with at least \d+ derived/i)).toBeVisible();
});

test("a slow first request explains itself instead of spinning", async ({ page }) => {
  test.skip(LIVE_API, "needs a deliberately delayed response");

  // The free instance sleeps and takes about thirty seconds to wake. A spinner that
  // sits there reads as broken, so after three seconds the page says why. Delayed to
  // five seconds here to cross that threshold.
  await serveApi(page);
  await page.route("**/api/performance/summary*", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 5000));
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(SUMMARY),
    });
  });

  await page.goto("/routes");

  await expect(page.getByText(/server is waking up/i)).toBeVisible({ timeout: 10_000 });
  // And it goes away once the data lands, rather than persisting as a false warning.
  await expect(page.getByRole("heading", { name: /by hour of the day/i })).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(/server is waking up/i)).toHaveCount(0);
});

test("a fast response never mentions waking up", async ({ page }) => {
  // The fixtures answer in milliseconds, so the notice must never appear — otherwise
  // every page in this suite would be carrying a warning about a problem it does not
  // have.
  await serveApi(page);
  await page.goto("/routes");

  await expect(page.getByRole("heading", { name: /by hour of the day/i })).toBeVisible();
  await expect(page.getByText(/server is waking up/i)).toHaveCount(0);
});

/**
 * The map is actually drawn, not merely initialised.
 *
 * Every other check on this page passed while the deployed map was invisible: no
 * console errors, no overflow, real numbers in the status line, and MapLibre's
 * controls present — they are absolutely positioned and do not need the container.
 * The container itself was 0px tall, because maplibre-gl.css (loaded later, in the
 * map's own chunk) overrode our `position: absolute` with `relative`, and a relative
 * `height: 100%` had no definite height to resolve against.
 *
 * Three assertions, because each alone can pass against that bug. MapLibre falls back
 * to a 300px canvas inside a 0px container, so "the canvas has a size" passed; and in
 * headless Chromium that strip still fetched a few tiles, so "a tile was requested"
 * passed too. What failed was the container: it must fill the space laid out for it.
 */
test("the map fills its space and fetches basemap tiles", async ({ page }) => {
  await serveApi(page);
  // Registered before navigation: the first tiles can arrive before the page settles.
  const tile = page.waitForRequest(/tiles\.openfreemap\.org\/planet\/.+\.pbf/, {
    timeout: 20_000,
  });
  await page.goto("/");
  await expect(page.locator(".maplibregl-canvas")).toBeVisible();

  const measure = () =>
    page.evaluate(() => {
      const height = (selector: string) =>
        document.querySelector(selector)?.getBoundingClientRect().height ?? -1;
      const canvas = document.querySelector<HTMLCanvasElement>(".maplibregl-canvas");
      return {
        wrap: height(".map-wrap"),
        map: height(".maplibregl-map"),
        canvas: height(".maplibregl-canvas"),
        canvasPixels: canvas ? canvas.width * canvas.height : 0,
      };
    });

  // Polled, not read once. The status line fills in after the data arrives and can
  // wrap onto a second line on a phone, shrinking the space by a line; MapLibre's
  // resize observer then re-fits the canvas a frame later. What must hold is that it
  // settles — a map that never re-measured would fail here after five seconds.
  await expect
    .poll(async () => {
      const { wrap, map, canvas } = await measure();
      // Within a pixel, for sub-pixel rounding.
      return Math.abs(map - wrap) <= 1 && Math.abs(canvas - wrap) <= 1;
    }, { message: "map and canvas fill the space laid out for them", timeout: 5_000 })
    .toBe(true);

  const sizes = await measure();
  expect(sizes.wrap, "the space laid out for the map").toBeGreaterThan(200);
  expect(sizes.canvasPixels).toBeGreaterThan(0);
  await tile;
});

/**
 * Pixels on the map in the dots' fill colour (#1d4ed8), counted from a screenshot.
 *
 * Counted in the page because the suite has no image library, and adding one for a
 * single assertion would be a new dependency.
 */
async function dotColouredPixels(page: Page): Promise<number> {
  const png = (await page.locator(".map").screenshot()).toString("base64");
  return page.evaluate(async (data) => {
    const image = new Image();
    image.src = `data:image/png;base64,${data}`;
    await image.decode();
    const canvas = document.createElement("canvas");
    canvas.width = image.width;
    canvas.height = image.height;
    const context = canvas.getContext("2d")!;
    context.drawImage(image, 0, 0);
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    let count = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      if (
        Math.abs(pixels[i] - 0x1d) < 25 &&
        Math.abs(pixels[i + 1] - 0x4e) < 25 &&
        Math.abs(pixels[i + 2] - 0xd8) < 25
      ) {
        count++;
      }
    }
    return count;
  }, png);
}

/**
 * The dots are drawn even when the positions arrive before the map is ready.
 *
 * Snapshot mode fetches once, so if that response beat the basemap's style the
 * positions used to be dropped without an error, and the hosted map showed 290
 * vehicles in its status line and none on the map. The style is delayed here to force
 * that order every time rather than whenever the style server happens to be slow.
 *
 * Snapshot mode only: its dots are all drawn at full opacity, so their colour is
 * exact. Live dots fade with age and would make a colour count a guess. Both modes
 * share the code path that was fixed.
 */
test("the map draws its dots even when the data beats the basemap", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "snapshot-mode", "full-opacity dots are snapshot mode's");
  await serveApi(page);
  await page.route("**/styles/liberty*", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 3_000));
    await route.continue();
  });
  await page.goto("/");
  await expect(page.locator(".status")).toContainText("290 vehicles");

  // The floor sits above what the page shows in this colour with no dots at all. The
  // notice card's heading — the date, in the same blue — measured ~1,770 pixels on
  // its own, while 290 dots measured ~12,700 with the heading included. 5,000 is well
  // clear of the first and well under the second, so a missing layer fails and a few
  // dots hidden under the card or off the edge do not.
  await expect
    .poll(() => dotColouredPixels(page), {
      message: "dot-coloured pixels on the map",
      timeout: 15_000,
    })
    .toBeGreaterThan(5_000);
});

test("the map names the minute it is showing, and does not claim to be live", async ({
  page,
}, testInfo) => {
  // Only meaningful in the snapshot-mode build, which is what the hosted site is.
  test.skip(
    testInfo.project.name !== "snapshot-mode",
    "the live-mode build watches the feed instead",
  );
  await serveApi(page);

  await page.goto("/");

  // The moment, in words — the same discipline as every figure on the site carrying
  // its period.
  await expect(page.getByText(/Tuesday 6 October 2026, 17:08/i).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(/290 vehicles/).first()).toBeVisible();
  // And it says plainly that this is not live.
  await expect(page.getByText(/history, not a live feed/i)).toBeVisible();
  await expect(page.getByText(/no recent data/i)).toHaveCount(0);
  await expect(page.getByRole("heading", { name: /the network, one minute/i })).toBeVisible();
});

test("the method page says when the coverage counts were taken", async ({ page }) => {
  await serveApi(page);
  await page.goto("/about");

  // The counts are stored, not recounted per visit, so a deployed copy goes stale.
  // The date in the fixture is 15:38 UTC on 17 October 2026, a Saturday in Halifax.
  await expect(page.getByTestId("coverage-counted-at")).toHaveText(
    /taken on Saturday 17 October 2026\./,
  );
});

test("the method page states the licence and that this is not official", async ({ page }) => {
  await serveApi(page);
  await page.goto("/about");

  // first(): both statements appear twice, once in the page body and once in the
  // footer that every page carries. Two is correct here, so the locator takes the
  // first rather than the assertion demanding there be only one.
  await expect(
    page.getByText(/not an official Halifax Transit product/i).first(),
  ).toBeVisible();
  await expect(
    page.getByText(/Contains information licenced under the Open Government Licence/i).first(),
  ).toBeVisible();
});
