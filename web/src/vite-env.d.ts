/// <reference types="vite/client" />

/**
 * The build-time configuration this app accepts.
 *
 * Declared so that a typo in an env var name is a compile error rather than an
 * undefined at runtime — which, for an API base URL, means every request silently
 * going to the wrong origin.
 */
interface ImportMetaEnv {
  /**
   * Origin of the API, e.g. https://bus-truth-api.onrender.com.
   *
   * Unset means same-origin, which is correct for local development behind the Vite
   * proxy and for any deployment that serves both from one host.
   */
  readonly VITE_API_BASE_URL?: string;

  /**
   * "snapshot" makes the map draw one named historical minute instead of watching a
   * live feed. Set for deployments with no collector, where the live endpoint is
   * permanently empty. Anything else, including unset, means live.
   */
  readonly VITE_MAP_MODE?: "live" | "snapshot";
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
