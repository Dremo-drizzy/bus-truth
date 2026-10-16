import type { Share } from "../api";
import { formatDelay, tooFew } from "../api";
import { useRange } from "../range";

/**
 * The small pieces every page shares, and the one rule they all enforce.
 *
 * A share never renders as a bare number here. Either it renders with its
 * denominator, or — below the floor the API derives and serves — it refuses to render
 * a percentage at all and says why. That refusal is the whole point of the site: the
 * feed reports a trip's position about 6.8 times across 38.3 stops, so thin
 * denominators are the normal case, and "0% on time" over two arrivals is a false
 * claim about a bus route rather than a measurement of one.
 */

export function SharePercent({ share }: { share: Share }) {
  const { displayFloor } = useRange();
  if (share.percent === null) {
    return (
      <span className="figure muted" title="Nothing was observed in this range">
        no data
      </span>
    );
  }
  if (tooFew(share, displayFloor)) {
    return (
      <span
        className="figure muted"
        title={`${share.count} of ${share.of} arrivals — fewer than the ${displayFloor} this project will report a percentage over`}
      >
        too few observations
      </span>
    );
  }
  return (
    <span className="figure">
      <strong>{share.percent.toFixed(1)}%</strong>
      <small>
        {share.count.toLocaleString()} of {share.of.toLocaleString()} arrivals
      </small>
    </span>
  );
}

/** A share as one line, for table cells too narrow for the stacked form. */
export function ShareInline({ share }: { share: Share }) {
  const { displayFloor } = useRange();
  if (share.percent === null) return <span className="muted">no data</span>;
  if (tooFew(share, displayFloor)) {
    return <span className="muted">too few ({share.of})</span>;
  }
  return (
    <>
      {share.percent.toFixed(1)}% <span className="muted">of {share.of.toLocaleString()}</span>
    </>
  );
}

export function Delay({ seconds }: { seconds: number | null }) {
  if (seconds === null) return <span className="muted">—</span>;
  return (
    <span className={seconds > 60 ? "late" : seconds < -60 ? "early" : ""}>
      {formatDelay(seconds)}
    </span>
  );
}

export function Loading({ what }: { what: string }) {
  return (
    <p className="state" role="status">
      Loading {what}…
    </p>
  );
}

export function Failed({ what, detail }: { what: string; detail: string }) {
  return (
    <p className="state error" role="alert">
      <strong>Could not load {what}.</strong> <span className="muted">{detail}</span>
    </p>
  );
}

/**
 * An empty result is an answer, not a failure.
 *
 * The site measures its own coverage, and a range with nothing in it usually means
 * the collector was not running — which is a fact worth stating plainly rather than
 * leaving as a blank page the reader has to interpret.
 */
export function Empty({ children }: { children: React.ReactNode }) {
  return (
    <p className="state" role="status">
      {children}
    </p>
  );
}
