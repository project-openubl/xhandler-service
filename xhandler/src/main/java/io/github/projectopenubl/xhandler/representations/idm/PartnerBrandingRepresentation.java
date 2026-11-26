package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PartnerBrandingView")
public record PartnerBrandingRepresentation(UUID partnerId, BrandingRepresentation branding,
        @Schema(description = "Si el socio subió un logo; los PDF de sus cuentas lo usan cuando la empresa no tiene uno.") boolean hasLogo) {}
