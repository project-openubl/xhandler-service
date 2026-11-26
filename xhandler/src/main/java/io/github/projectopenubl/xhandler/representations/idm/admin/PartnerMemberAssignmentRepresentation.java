package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A partner member as set. */
@Schema(name = "PartnerMemberAssignment")
public record PartnerMemberAssignmentRepresentation(UUID partnerId, String kind, String subject, String role) {}
