package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A new adapter token: {@code token} is shown only here. */
@Schema(name = "NubefactToken")
public record NubefactTokenRepresentation(UUID id, String token, String url) {}
