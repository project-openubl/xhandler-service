package io.github.projectopenubl.xhandler.representations.idm;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PersonalData")
public record PersonalDataRepresentation(DataSubjectRequestRepresentation request, List<PersonalDocumentRepresentation> documents, List<PersonalDraftRepresentation> drafts) {}
