package io.github.projectopenubl.xhandler.representations.idm;

import tools.jackson.databind.JsonNode;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** What a client sends: an xbuilder document plus options. */
@Schema(name = "EmitRequest")
public record EmitRequestRepresentation(JsonNode document, String serie, Boolean delivered, Map<String, String> labels) {}
