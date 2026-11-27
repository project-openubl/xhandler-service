package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A tenant of a partner, as the partner console lists it. */
@Schema(name = "PartnerTenantSummary")
public record PartnerTenantSummaryRepresentation(UUID id, String name, String status) {}
