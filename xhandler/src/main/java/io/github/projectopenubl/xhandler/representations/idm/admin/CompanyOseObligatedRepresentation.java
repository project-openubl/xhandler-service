package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A company's OSE obligation. */
@Schema(name = "CompanyOseObligated")
public record CompanyOseObligatedRepresentation(UUID companyId, boolean oseObligated) {}
