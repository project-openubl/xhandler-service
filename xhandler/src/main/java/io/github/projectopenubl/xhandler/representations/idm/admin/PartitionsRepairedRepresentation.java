package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The day (Lima) partitions were ensured for. */
@Schema(name = "PartitionsRepaired")
public record PartitionsRepairedRepresentation(String day) {}
