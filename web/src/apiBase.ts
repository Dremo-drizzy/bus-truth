/**
 * Where the API lives, resolved once from the build's configuration.
 *
 * Empty means same-origin, which is correct in development behind the Vite proxy, in
 * any deployment that serves the site and the API from one host, and in the browser
 * test suite. A configured value makes every request absolute, which is what a static
 * site calling a separate API service needs.
 *
 * <p>Three shapes have to work, because the value has been wrong in two of them:
 *
 *   - a full origin, "https://bus-truth-api.onrender.com" — passed through untouched
 *   - a bare hostname, "bus-truth-api.onrender.com" — given https://
 *   - either, with a trailing slash — trimmed, so the joined path never has "//api"
 *
 * The hard-won part is what this CANNOT fix. Render's `fromService: property: host`
 * yields a service's INTERNAL networking name — "bus-truth-api", with no domain — and
 * prepending a scheme to that produces https://bus-truth-api, a host that does not
 * exist on the public internet. The browser's fetch then fails before leaving the
 * machine, which reads as "Failed to fetch" with no status to go on. The fix for that
 * is in render.yaml, where both origins are now written out literally; no amount of
 * normalising here could have saved it.
 */
export function resolveApiBase(configured: string | undefined): string {
  const value = (configured ?? "").trim();
  if (value === "") return "";
  const withScheme = value.startsWith("http") ? value : `https://${value}`;
  return withScheme.replace(/\/+$/, "");
}

/** The base this build was compiled with. */
// Optional chaining so this module can be imported outside a Vite build — which is
// how resolveApiBase is exercised directly rather than inferred from the bundle.
export const API_BASE = resolveApiBase(import.meta.env?.VITE_API_BASE_URL);
