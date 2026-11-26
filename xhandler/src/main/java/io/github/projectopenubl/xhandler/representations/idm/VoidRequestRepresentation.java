package io.github.projectopenubl.xhandler.representations.idm;

import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A void request. {@code reversalCommunicatedAt} is required for percepciones and retenciones. */
@Schema(name = "VoidRequest")
public record VoidRequestRepresentation(String motivo, Boolean notDelivered, LocalDate reversalCommunicatedAt) {}
