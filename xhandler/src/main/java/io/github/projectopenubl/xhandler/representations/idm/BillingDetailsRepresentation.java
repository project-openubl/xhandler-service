package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "BillingDetails")
public record BillingDetailsRepresentation(@Pattern(regexp = "\\d{11}") String billingRuc, @Size(max = 300) String billingName,
        @Email String billingEmail) {}
