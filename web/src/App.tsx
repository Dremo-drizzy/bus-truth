import { NavLink, Outlet, useLocation } from "react-router-dom";
import { RangePicker, RangeProvider } from "./range";

/**
 * The shell: navigation, the date range, and the attribution that has to be on every
 * page.
 *
 * <p>The range picker is hidden on the live map and the about page because neither
 * takes a date range, and a control that does nothing is worse than no control.
 */
export default function App() {
  const { pathname } = useLocation();
  const takesARange = pathname !== "/" && pathname !== "/about";

  return (
    <RangeProvider>
      <div className="shell">
        <nav className="nav">
          <NavLink to="/" end className="brand">
            Bus&nbsp;Truth
          </NavLink>
          <div className="links">
            <NavLink to="/" end>
              Live map
            </NavLink>
            <NavLink to="/routes">Routes</NavLink>
            <NavLink to="/about">Method</NavLink>
          </div>
        </nav>

        {takesARange && <RangePicker />}

        <main className={pathname === "/" ? "main full" : "main"}>
          <Outlet />
        </main>

        <footer className="footer">
          <p>
            Not an official Halifax Transit product. Contains information licenced under
            the Open Government Licence – Halifax.
          </p>
        </footer>
      </div>
    </RangeProvider>
  );
}
