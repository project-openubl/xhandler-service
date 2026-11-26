package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "BillingView")
public record BillingRepresentation(UsageRepresentation usage, String billingRuc, String billingName, String billingEmail) {}
