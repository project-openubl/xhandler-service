package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Sets a tax-audit hold: nothing of the tenant is deleted while it holds. */
@Schema(name = "AuditHoldRequest")
public record AuditHoldRequestRepresentation(Boolean auditHold) {}
