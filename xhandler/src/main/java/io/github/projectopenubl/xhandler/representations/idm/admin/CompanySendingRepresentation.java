package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Whether sending to SUNAT is paused for a company. */
@Schema(name = "CompanySending")
public record CompanySendingRepresentation(UUID companyId, boolean sendingPaused) {}
