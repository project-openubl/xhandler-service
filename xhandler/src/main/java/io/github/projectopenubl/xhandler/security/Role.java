package io.github.projectopenubl.xhandler.security;

import java.util.Locale;

/** Role of an actor in a tenant, from most to least privileged. */
public enum Role {
    /** Platform work without a person (workers, schedules): never granted to a member. */
    PLATFORM,
    OWNER,
    ADMIN,
    OPERATOR,
    VIEWER;

    /** True when this role may do what {@code minimum} allows. */
    public boolean allows(Role minimum) {
        return ordinal() <= minimum.ordinal();
    }

    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Role fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
