package io.github.projectopenubl.xhandler.representations.idm;

import java.math.BigDecimal;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Rate")
public record PseRejectedRateRepresentation(String period, long accepted, long rejected, BigDecimal rate, BigDecimal limit, boolean breached) {}
