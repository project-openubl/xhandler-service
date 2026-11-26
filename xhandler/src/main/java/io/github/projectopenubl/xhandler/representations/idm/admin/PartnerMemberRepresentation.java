package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A member of a partner: a person ({@code user}, token sub) or a machine client ({@code client}, token azp). */
@Schema(name = "PartnerMember")
public record PartnerMemberRepresentation(String kind, String subject, String role, Instant createdAt) {}
