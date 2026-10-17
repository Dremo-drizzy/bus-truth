/**
 * Dates and moments in words, for a reader rather than a log.
 *
 * Assembled from parts rather than handed to `toLocaleString`, for two reasons. The
 * ordering would otherwise follow the browser's locale — en-CA renders "October 6,
 * 2026" while every date written on this site reads "6 October 2026" — and a label the
 * test suite asserts should not depend on which ICU version the browser shipped with.
 *
 * Always Halifax time, because the moment belongs to the agency's clock and not the
 * reader's.
 *
 * Its own module rather than living in the map page, because the method page needs it
 * too and the map page is loaded on demand: importing from it would drag the map into
 * the main bundle.
 */
function partsOf(
  isoTimestamp: string,
  withTime: boolean,
): (type: Intl.DateTimeFormatPartTypes) => string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    ...(withTime ? { hour: "2-digit", minute: "2-digit", hour12: false } : {}),
    timeZone: "America/Halifax",
  }).formatToParts(new Date(isoTimestamp));

  return (type) => parts.find((candidate) => candidate.type === type)?.value ?? "";
}

/** "Tuesday 6 October 2026" */
export function describeDay(isoTimestamp: string): string {
  const part = partsOf(isoTimestamp, false);
  return `${part("weekday")} ${part("day")} ${part("month")} ${part("year")}`;
}

/** "Tuesday 6 October 2026, 17:08" */
export function describeMoment(isoTimestamp: string): string {
  const part = partsOf(isoTimestamp, true);
  return `${describeDay(isoTimestamp)}, ${part("hour")}:${part("minute")}`;
}
