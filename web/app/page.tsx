import LiveMap from "./LiveMap";

export default function Home() {
  return (
    <div className="page">
      <LiveMap />
      <footer>
        Map data ©{" "}
        <a href="https://www.openstreetmap.org/copyright" rel="noreferrer">
          OpenStreetMap
        </a>{" "}
        contributors, tiles by{" "}
        <a href="https://openfreemap.org/" rel="noreferrer">
          OpenFreeMap
        </a>
        . Contains information licenced under the Open Government Licence – Halifax.
      </footer>
    </div>
  );
}
