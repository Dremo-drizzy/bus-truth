/**
 * Shared immutable domain records for Bus Truth.
 *
 * <p>Types that more than one module needs to agree on live here — the shapes that
 * travel between collector, loader, processor and api. Nothing that knows about
 * Kafka, HTTP or the database belongs in this package.
 *
 * <p>Empty for now; Stage 2 adds the first records.
 */
package io.github.dremo_drizzy.bustruth.common;
