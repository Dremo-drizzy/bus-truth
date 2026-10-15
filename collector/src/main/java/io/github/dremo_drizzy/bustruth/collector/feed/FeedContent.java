package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Identifies what a snapshot actually says, ignoring when it was said.
 *
 * <p>The feed header carries a timestamp that changes on every poll, so two
 * snapshots with identical content are never byte-identical and their headers never
 * match. Comparing headers therefore skips nothing: the alerts feed alone would
 * republish around 475 unchanged records every 20 seconds, roughly two million
 * messages a day describing a handful of disruptions.
 *
 * <p>scripts/save_feeds.py could not do better — comparing content means decoding
 * protobuf, and the saver deliberately has no dependencies. The collector has
 * already decoded the message by this point, so this costs one hash of data that is
 * in memory anyway.
 */
final class FeedContent {

    private FeedContent() {
    }

    /**
     * SHA-256 over each entity's bytes, so the answer depends only on what the feed
     * says and not on when it said it.
     *
     * <p>The entities are hashed one by one rather than by clearing the header and
     * re-serialising the message: {@code header} is a required field in the
     * GTFS-Realtime schema, so a message without one cannot be serialised at all.
     *
     * <p>Re-serialising a parsed entity is deterministic for a given input — the
     * fields keep the order they were parsed in.
     */
    static String hash(FeedMessage feed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (FeedEntity entity : feed.getEntityList()) {
                digest.update(entity.toByteArray());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            // Every JVM is required to provide SHA-256.
            throw new IllegalStateException(impossible);
        }
    }
}
