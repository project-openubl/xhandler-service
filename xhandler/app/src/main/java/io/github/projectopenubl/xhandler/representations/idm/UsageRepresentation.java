package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** What a tenant may do now, and what it used. */
@Schema(name = "Usage")
public record UsageRepresentation(PlanRepresentation plan, String source, Instant syncedAt, boolean stale, long productionThisMonth, long betaToday,
        long sandboxToday) {}
