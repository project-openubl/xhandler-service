package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "MemberView")
public record MemberRepresentation(UUID id, String kind, String subject, String role) {}
