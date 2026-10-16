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
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
