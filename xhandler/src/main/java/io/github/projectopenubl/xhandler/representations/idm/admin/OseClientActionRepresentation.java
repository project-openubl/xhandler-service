package io.github.projectopenubl.xhandler.representations.idm.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** An action on an OSE user; {@code password} only after a reset, shown once. */
@Schema(name = "OseClientAction")
public record OseClientActionRepresentation(UUID id, String action, @JsonInclude(JsonInclude.Include.NON_NULL) String password) {}
