import type { CoverageDay } from "../api";
import { useApi } from "../api";
import { Failed, Loading, ShareInline } from "../components/Figures";
import { describeDay } from "../moment";

/**
 * How the numbers on this site are made, and what they cannot tell you.
 *
 * <p><b>NEEDS DANIEL'S REVIEW</b> — the build brief reserves this text for him. Written
 * by Claude Code so the page is not empty; every figure in it is measured and
 * reproducible, but the words are not yet the author's own.
 *
 * <p>The coverage table is served by the API rather than written into this page,
 * because a methodology page quoting a coverage figure from the day it was written is
 * the same mistake as a percentage without its denominator. The counts themselves are
 * stored, though, not recounted on each visit — the hosted database does not hold the
 * raw positions — so the page says in words when they were taken.
 */
/**
 * The newest date any coverage count was taken, or null if none was.
 *
 * The newest rather than the oldest because the counts are refreshed together; if they
 * ever were not, the table still carries each row's own date for anyone who asks.
 */
function countedAt(days: CoverageDay[]): string | null {
  return days
    .map((day) => day.coverage_computed_at)
    .filter((at): at is string => at !== null)
    .reduce<string | null>((newest, at) => (newest === null || at > newest ? at : newest), null);
}

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
        <strong>window</strong> of positions rather than a moment, and an arrival has to
        be timed from one end of that window or the other. This site takes the{" "}
        <strong>end</strong> of it.
      </p>
      <p>
        The same week, derived both ways and with nothing else changed:
      </p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">4&ndash;10 October 2026, 109,584 arrivals</th>
              <th scope="col">Window start</th>
              <th scope="col">Window end (used here)</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th scope="row">On time</th>
              <td>40.9%</td>
              <td>
                <strong>58.4%</strong>
              </td>
            </tr>
            <tr>
              <th scope="row">Early</th>
              <td>35.5%</td>
              <td>11.1%</td>
            </tr>
            <tr>
              <th scope="row">Late</th>
              <td>23.6%</td>
              <td>30.5%</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>
        <strong>
          17.5 percentage points, and not one bus behaved differently between those two
          columns.
        </strong>{" "}
        Both are published for that reason.
      </p>
      <p>
        Which end to take was decided by measurement, not preference. The realtime feed
        has a field that can say <code>STOPPED_AT</code>: the agency stating a bus is at
        a stop, rather than us inferring it. Halifax fills it in on{" "}
        <strong>0.40%</strong> of positions — far too few to measure with, and enough to
        judge the two candidates against. The window&rsquo;s end lands a median{" "}
        <strong>61 seconds late</strong>; its start, a median{" "}
        <strong>544 seconds early</strong>.
      </p>
      <p>
        <strong>That remaining 61 seconds is named, not corrected.</strong> Removing it
        from every arrival in the published week would move the figure from{" "}
        <strong>58.4%</strong> to <strong>49.7%</strong> — a margin of{" "}
        <strong>8.7 percentage points</strong>, which is this site&rsquo;s own margin of
        error and the number the observation floors below are derived from.
      </p>
      <p>
        Two limits on that, both measured and both pointing the same way.{" "}
        <code>STOPPED_AT</code> is only reported where buses sit still: 27 of the 45
        stops that ever send it are terminals or bays, and those windows are a median
        815 seconds wide against 32 seconds for windows in general. And the bias{" "}
        <em>grows</em> with the width of the window — a median of 0 seconds on windows
        under a minute wide, against 211 seconds on those over fifteen minutes. So at an
        ordinary stop the end of the window is close to the real arrival; at a terminal
        where a bus lays over, it is minutes late. The 61 seconds is measured on the
        awkward cases, which makes it cautious rather than flattering. It is not a
        handful of buses either: 234 of the 366 vehicles ever seen report it at least
        once.
      </p>
      <p>
        <strong>One thing is worth stating plainly, because the two numbers look like a
        contradiction otherwise.</strong> The two definitions sit at opposite ends of a
        window that is often minutes wide, yet the median delay differs by only 74
        seconds between them while the headline figure moves 8.7 points. Both are true,
        because <strong>50.5% of the windows that produce an arrival contain a single
        position</strong> — for half of all arrivals, the two definitions are the same
        timestamp. Everything the choice changes comes from the other half.
      </p>

      <h2>Is the figure biased by when we were collecting?</h2>
      <p>
        It would be easy for it to be. On-time performance varies 34 points across the
        day, and this project&rsquo;s collection was uneven — so if the hours we watched
        are not distributed like the hours Halifax runs buses, the headline is weighted
        by our uptime rather than by the service.
      </p>
      <p>
        So it is checked rather than assumed. Each hour&rsquo;s observed rate was
        reweighted by that hour&rsquo;s share of <strong>scheduled</strong> service —
        907,323 scheduled stop-events across the week, counted from the schedule&rsquo;s
        own calendar. The reweighted figure is <strong>60.1%</strong>, the same as the
        raw one to within <strong>0.0 points</strong>.
      </p>
      <p>
        That is a near-cancellation rather than luck, and the reason is worth knowing:
        we under-observed the morning and over-observed the late evening. Missing the
        evening peak would have flattered the figure; missing the early morning, which
        runs at 79%, penalises it. The two offset. Weighting every hour equally would
        give 63.9%, peak hours alone 45.6% — so the mix can move this number by 26
        points, and it happens not to here.
      </p>
      <p>
        One limit: reweighting fixes the <em>mix</em> of hours observed, not the hours
        missed entirely. Thursday 8 October has no observed arrivals between 07:00 and
        18:59, and no amount of reweighting can produce a figure for a day we did not
        watch.
      </p>

      <h2>How few observations is too few</h2>
      <p>
        One observation moves a percentage by 100/n points. This site publishes an
        8.7-point margin of error, so a figure that a single bus could swing further
        than that is describing its own sample size rather than the service. Two floors
        follow from that, and both are computed from the margin rather than chosen:
      </p>
      <ul>
        <li>
          a percentage is shown at all only over <strong>12</strong> or more arrivals —
          below that you will see &ldquo;too few observations&rdquo;
        </li>
        <li>
          a route or stop is <strong>ranked</strong> only over <strong>23</strong> or
          more, because a ranking compares two percentages and carries both their
          noise
        </li>
      </ul>
      <p>
        Neither number is chosen: the only difference between them is how many figures
        the claim rests on. Over the published week they cost little — 78 of the 81
        observed routes can be ranked, and the median route has 302 arrivals. On a
        single day they would exclude most of them, which is one reason the default
        range is a week.
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
        counted from the same recording the rest of the site is built from.
      </p>
      {coverage.state === "loading" && <Loading what="coverage" slow={coverage.slow} />}
      {coverage.state === "failed" && (
        <Failed what="coverage" detail={coverage.error.message} />
      )}
      {coverage.state === "loaded" && (
        <p data-testid="coverage-counted-at">
          {(() => {
            const at = countedAt(coverage.data);
            return at === null
              ? "These counts carry no date, so how current they are is unknown."
              : `These counts were taken on ${describeDay(at)}. Recording may have carried on since, and anything it captured after that is not counted here.`;
          })()}
        </p>
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
                    {day.snapshots_captured === null ? (
                      <span className="muted">not counted</span>
                    ) : (
                      <>
                        {day.snapshots_captured.toLocaleString()}{" "}
                        <span className="muted">
                          of {day.snapshots_expected.toLocaleString()}
                        </span>
                      </>
                    )}
                  </td>
                  <td>
                    {day.coverage === null ? (
                      <span className="muted">—</span>
                    ) : (
                      <ShareInline share={day.coverage} />
                    )}
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
