package io.github.projectopenubl.xhandler.models;

import java.util.Optional;
import java.util.UUID;

/** Partner data printed on PDFs (DESIGN.md §11.6); every method joins the caller's transaction. */
public interface DocumentPdfProvider {

    /** The partner's branding JSON, as text. */
    Optional<String> getPartnerBranding(UUID partnerId);

    /** The storage key of the partner's logo. */
    Optional<String> getPartnerLogoRef(UUID partnerId);
}
