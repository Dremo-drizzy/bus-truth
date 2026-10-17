import { StrictMode, Suspense, lazy } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import App from "./App";
import { Loading } from "./components/Figures";
import RoutesPage from "./pages/RoutesPage";
import "./styles.css";

/**
 * The two heavy pages load on demand.
 *
 * MapLibre and Recharts are most of the bundle, and each is needed on exactly one
 * page: in a single chunk every visitor downloaded a map renderer to read a table of
 * percentages. Splitting them took the first load from 1,731 kB to 271 kB — 87 kB
 * gzipped — with the map's 1,056 kB and the charts' 387 kB fetched only by the pages
 * that draw them. On a phone at a bus stop that is the difference that matters.
 */
const LiveMapPage = lazy(() => import("./pages/LiveMapPage"));
const RouteDetailPage = lazy(() => import("./pages/RouteDetailPage"));
const StopPage = lazy(() => import("./pages/StopPage"));
const AboutPage = lazy(() => import("./pages/AboutPage"));

const root = document.getElementById("root");
if (root === null) throw new Error("no #root element");

createRoot(root).render(
  <StrictMode>
    <BrowserRouter>
      <Suspense fallback={<Loading what="the page" />}>
        <Routes>
          <Route element={<App />}>
            <Route index element={<LiveMapPage />} />
            <Route path="routes" element={<RoutesPage />} />
            <Route path="routes/:routeId" element={<RouteDetailPage />} />
            <Route path="stops/:stopId" element={<StopPage />} />
            <Route path="about" element={<AboutPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  </StrictMode>,
);
