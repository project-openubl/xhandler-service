package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CertificateView")
public record CertificateRepresentation(String subject, String issuer, String serial, String ruc, Instant validFrom, Instant validUntil, boolean rucMatches) {}
