package io.github.projectopenubl.xhandler.utils;

/** The status a buyer sees on the public portal and lookup. */
public final class PublicStatus {

    private PublicStatus() {}

    /** Buyers see the answer, not the internals: pending until SUNAT's definitive answer. */
    public static String of(String status) {
        return switch (status) {
            case "accepted", "accepted_with_observations" -> "aceptado";
            case "rejected" -> "rechazado";
            case "voided" -> "anulado";
            case "voiding" -> "en_anulacion";
            default -> "pendiente";
        };
    }
}
