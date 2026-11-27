package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A plan code set by hand (contracts outside Lago). */
@Schema(name = "PlanAssignment")
public record PlanAssignmentRepresentation(String plan) {}
