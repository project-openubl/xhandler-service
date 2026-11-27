package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A platform certificate (PSE) in the vault; never its key. */
@Schema(name = "PlatformCertificate")
public record PlatformCertificateRepresentation(String subject, String ruc, Instant validUntil) {}
