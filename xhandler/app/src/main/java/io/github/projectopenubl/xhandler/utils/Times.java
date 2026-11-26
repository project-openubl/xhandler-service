package io.github.projectopenubl.xhandler.utils;

import java.time.Instant;

/** Conversions for timestamps read through native queries. */
public final class Times {

    private Times() {}

    public static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.time.OffsetDateTime o) {
            return o.toInstant();
        }
        if (value instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        throw new IllegalArgumentException("not a timestamp: " + value.getClass());
    }
}
