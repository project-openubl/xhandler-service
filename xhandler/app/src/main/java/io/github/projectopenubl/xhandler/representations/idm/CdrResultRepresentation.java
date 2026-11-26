package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The CDR-OSE: {@code result} is always {@code cdr} (an inconsistency is a 422). */
@Schema(name = "CdrResult")
public record CdrResultRepresentation(String result, String name, String cdrZipBase64) {}
