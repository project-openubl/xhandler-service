package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A plan's limits. Null limits are unlimited. */
@Schema(name = "Plan")
public record PlanRepresentation(String code, String name, Integer productionPerMonth, Integer betaPerDay, Integer sandboxPerDay,
        boolean customSmtp, boolean webhooks) {}
