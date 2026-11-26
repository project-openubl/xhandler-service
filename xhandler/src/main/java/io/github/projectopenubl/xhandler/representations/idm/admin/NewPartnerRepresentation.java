package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A partner to create (§1.2). */
@Schema(name = "NewPartner")
public record NewPartnerRepresentation(String name) {}
