package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "RouteView")
public record RouteRepresentation(String submissionRoute, boolean oseObligated, String oseName, String oseInvoiceUrl, String osePerceptionUrl) {}
