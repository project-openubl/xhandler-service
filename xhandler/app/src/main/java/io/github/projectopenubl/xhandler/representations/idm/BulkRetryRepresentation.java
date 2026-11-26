package io.github.projectopenubl.xhandler.representations.idm;

import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "BulkRetry")
public record BulkRetryRepresentation(UUID companyId, String status, LocalDate from, LocalDate to) {}
