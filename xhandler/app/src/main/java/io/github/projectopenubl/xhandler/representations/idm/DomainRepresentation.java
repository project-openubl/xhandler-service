package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "DomainView")
public record DomainRepresentation(UUID id, String domain, String txtName, String txtValue, Instant verifiedAt) {}
