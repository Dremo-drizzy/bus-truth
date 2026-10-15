import type { CoverageDay } from "../api";
import { useApi } from "../api";
import { Failed, Loading, ShareInline } from "../components/Figures";

/**
 * How the numbers on this site are made, and what they cannot tell you.
 *
 * <p><b>NEEDS DANIEL'S REVIEW</b> — the build brief reserves this text for him. Written
 * by Claude Code so the page is not empty; every figure in it is measured and
 * reproducible, but the words are not yet the author's own.
 *
 * <p>The coverage table is live rather than written down, because a methodology page
 * quoting a coverage figure from the day it was written is the same mistake as a
 * percentage without its denominator.
 */
export default function AboutPage() {
  const coverage = useApi<CoverageDay[]>("/api/coverage");

  return (
    <article className="prose">
      <h1>How this is measured</h1>

      <p className="lede">
        Bus Truth compares what Halifax Transit's buses actually did against what the
        agency published they would do. Both sides come from the agency's own data: the
        live GTFS-Realtime feeds, recorded every 20 seconds, and the published GTFS
        schedule. Nothing here is a survey or an estimate of how a journey felt.
      </p>

      <h2>What counts as on time</h2>
      <p>
        A bus is on time if it arrives between <strong>one minute early</strong> and{" "}
        <strong>five minutes late</strong>. That window is a judgement, not a fact, and
        it is roughly what North American transit agencies use for their own reporting.
        The early side is tighter than the late side on purpose: a bus four minutes late
        is an inconvenience, while a bus four minutes early has gone without you.
      </p>
      <p>
        The window matters more than it looks. Widening the late side from five minutes
        to ten moves a large share of arrivals from "late" to "on time" without a single
        bus changing its behaviour, which is why the window is stated here rather than
        buried in the code.
      </p>

      <h2>How an arrival is detected, and what that cannot do</h2>
      <p>
        The feed never says "arrived". It reports which stop each bus is working
        towards, and an arrival is inferred from that number advancing: when a bus stops
        reporting stop 14 and starts reporting stop 15, it is judged to have reached
        stop 14. Every row on this site is therefore a conclusion drawn from positions,
        not a report from a bus.
      </p>
      <p>
        That inference has a hard ceiling, and it is the most important limitation here.
        Halifax updates a trip's stop number about <strong>6.8 times</strong> across a
        trip of <strong>38.3 stops</strong>. The bus serves all of them; the feed only
        ever mentions a fifth. So roughly <strong>17.5%</strong> of scheduled
        stop-events have an observed arrival, and the other 82.5% are simply unseen.
      </p>
      <p>
        Those unseen stops are <strong>counted, never filled in</strong>. No arrival
        time is interpolated between two sightings, and no stop is reported as skipped
        because the feed failed to mention it — the bus almost certainly called there.
        Publishing an invented arrival, or an invented cancellation, would be a false
        claim about a transit agency.
      </p>
      <h2>The number this site is least sure about</h2>
      <p>
        Because the feed holds one stop number for minutes, each stop arrives as a{" "}
        <strong>window</strong> of positions rather than a moment. Taking that window's
        start or its end as the arrival gives two different answers from the same data:
      </p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">The same 6,550 arrivals, 9 October</th>
              <th scope="col">Window start</th>
              <th scope="col">Window end (used here)</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th scope="row">On time</th>
              <td>49.0%</td>
              <td>
                <strong>70.2%</strong>
              </td>
            </tr>
            <tr>
              <th scope="row">Early</th>
              <td>37.8%</td>
              <td>10.4%</td>
            </tr>
            <tr>
              <th scope="row">Late</th>
              <td>13.1%</td>
              <td>19.4%</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>
        Nothing Halifax Transit did differs between those two columns.{" "}
        <strong>
          The figure on this site moves 21 percentage points on our own definition of
          “arrived” — further than anything the buses did that day.
        </strong>{" "}
        Both columns are published for that reason, rather than only the one in use.
      </p>
      <p>
        The choice between them was measured rather than argued. The realtime feed has a
        field that can say <code>STOPPED_AT</code>: the agency stating a bus is at a
        stop, instead of us inferring it. Halifax fills it in on{" "}
        <strong>0.40%</strong> of positions, far too few to build the measurement on —
        but enough to judge the two candidates against. Where the agency does say{" "}
        <code>STOPPED_AT</code>, it lands a median 80% of the way through the window,
        and against those 597 cases the window&rsquo;s <strong>end</strong> is a median{" "}
        <strong>61 seconds late</strong> while its start is a median{" "}
        <strong>544 seconds early</strong>. The end is roughly nine times closer, so
        that is the definition this site uses.
      </p>
      <p>
        Its remaining bias is about a minute late, and it is stated rather than
        corrected: a correction worked out from 0.40% of the data would be a guess with
        a number attached.
      </p>

      <h2>Matching an arrival to the schedule</h2>
      <p>
        An observed arrival is matched to a published time by trip and{" "}
        <strong>stop</strong>, not by stop number. The two sources disagree about the
        numbering: they name the same stop for the same number only about{" "}
        <strong>78.5%</strong> of the time, because the live feed's trips contain stops
        the published schedule does not list, and the drift accumulates along a trip.
        Matching by number would compare a fifth of all arrivals against a neighbouring
        stop's scheduled time — a median of 94 seconds out, against a window five
        minutes wide.
      </p>
      <p>
        Each arrival is compared against the schedule <em>in force on that day</em>, not
        the newest one published, so a bus is never judged against a timetable that did
        not exist yet.
      </p>

      <h2>Trips whose id does not match</h2>
      <p>
        About <strong>4.4%</strong> of live trip ids carry a suffix like{" "}
        <code>4659595_1</code> from the agency's trip-modification extension, and no
        trip id in the published schedule contains an underscore. Stripping the suffix
        recovers the match in every case observed so far.
      </p>
      <p>
        The caveat: that rests on an assumption we cannot verify, namely that{" "}
        <code>4659595_1</code> is a modified version of <code>4659595</code> and keeps
        its stops and times. If the agency uses the suffix to mean something else, those
        arrivals are matched against the wrong schedule. They are flagged with lower
        confidence for that reason.
      </p>

      <h2>Our own coverage, and the gaps in it</h2>
      <p>
        Every figure on this site depends on the recorder having been running.
        Collection has run on a laptop, and the laptop has slept. The table below is
        live, not written down — it is the same data the rest of the site is built from.
      </p>
      {coverage.state === "loading" && <Loading what="coverage" />}
      {coverage.state === "failed" && (
        <Failed what="coverage" detail={coverage.error.message} />
      )}
      {coverage.state === "loaded" && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Day</th>
                <th scope="col">Snapshots recorded</th>
                <th scope="col">Of a full day</th>
                <th scope="col">Arrivals derived</th>
              </tr>
            </thead>
            <tbody>
              {coverage.data.map((day) => (
                <tr key={day.service_date}>
                  <th scope="row">{day.service_date}</th>
                  <td>
                    {day.snapshots_captured.toLocaleString()}{" "}
                    <span className="muted">
                      of {day.snapshots_expected.toLocaleString()}
                    </span>
                  </td>
                  <td>
                    <ShareInline share={day.coverage} />
                  </td>
                  <td>{day.arrivals_derived.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p>
        A day at less than full coverage is not a day of poor service — it is a day we
        only partly watched. Where the recording stopped, nothing is derived across the
        gap at all: a sleeping recorder and a cancelled trip look identical in this
        data, and guessing between them would mean publishing cancellations that never
        happened.
      </p>

      <h2>What this is not</h2>
      <p>
        <strong>This is not an official Halifax Transit product</strong> and the agency
        has no involvement in it. It is an independent measurement built from the open
        data they publish. Where this site and the agency's own figures disagree, theirs
        are the official ones.
      </p>
      <p>
        It also measures only what the feeds show. A trip the feed never mentions is
        invisible here, whether it ran or not, and no claim on this site should be read
        as a count of cancelled service.
      </p>

      <h2>Attribution</h2>
      <blockquote>
        Contains information licenced under the Open Government Licence – Halifax.
      </blockquote>
    </article>
  );
}
