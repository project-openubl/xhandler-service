package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A partner with how many members and live tenants it has. */
@Schema(name = "AdminPartner")
public record AdminPartnerRepresentation(UUID id, String name, String status, Instant createdAt, long members, long tenants) {}
