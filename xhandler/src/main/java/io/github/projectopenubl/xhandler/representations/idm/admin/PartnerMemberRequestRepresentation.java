package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Adds or updates a partner member: {@code kind} user|client (default user), {@code role} admin|viewer (default admin). */
@Schema(name = "PartnerMemberRequest")
public record PartnerMemberRequestRepresentation(String kind, String subject, String role) {}
