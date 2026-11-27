package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The company that issues the platform's own invoices (§14); both null until set. */
@Schema(name = "BillingIssuer")
public record BillingIssuerRepresentation(String tenantId, String companyId) {}
