package io.github.projectopenubl.xhandler.representations.idm;

/** Error body of every API error: {@code kind} is stable, {@code message} is Spanish. */
public record ApiError(String kind, String message, String detail) {}
