package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.UUID;

public interface CompanyModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    String getRuc();

    void setRuc(String ruc);

    String getRazonSocial();

    void setRazonSocial(String razonSocial);

    String getNombreComercial();

    void setNombreComercial(String nombreComercial);

    /** fake (the sandbox every company starts on, DESIGN.md §26.2), beta or production */
    String getSunatEnv();

    void setSunatEnv(String sunatEnv);

    /** software or pse */
    String getEmissionMode();

    void setEmissionMode(String emissionMode);

    Instant getRucVerifiedAt();

    void setRucVerifiedAt(Instant rucVerifiedAt);

    String getEmail();

    void setEmail(String email);

    /** Default of {@code delivered} on emit (usually true for POS, DESIGN.md §9.3) */
    boolean isDeliveredOnEmitDefault();

    void setDeliveredOnEmitDefault(boolean deliveredOnEmitDefault);

    /** sunat or ose (a third-party OSE, DESIGN.md §3.2) */
    String getSubmissionRoute();

    void setSubmissionRoute(String submissionRoute);

    /** Must send through an OSE (a regulatory status, set by the platform) */
    boolean isOseObligated();

    void setOseObligated(boolean oseObligated);

    /** a4, ticket80 or ticket58 (DESIGN.md §11.6) */
    String getPdfFormat();

    void setPdfFormat(String pdfFormat);

    java.util.Map<String, Object> getPdfOptions();

    void setPdfOptions(java.util.Map<String, Object> pdfOptions);

    String getLogoRef();

    void setLogoRef(String logoRef);

    String getDireccion();

    void setDireccion(String direccion);

    /** Email the buyer when SUNAT accepts (to the document's customer email) */
    boolean isEmailOnAccept();

    void setEmailOnAccept(boolean emailOnAccept);

    String getEmailSubject();

    void setEmailSubject(String emailSubject);

    String getEmailBody();

    void setEmailBody(String emailBody);

    /** RUC/DNI lookup provider (DESIGN.md §17): searchpe or a commercial one with a tenant token */
    String getLookupProvider();

    void setLookupProvider(String lookupProvider);

    boolean isActive();

    void setActive(boolean active);

    /** Holds SUNAT attempts for all its documents (DESIGN.md §24.1) */
    boolean isSendingPaused();

    void setSendingPaused(boolean sendingPaused);

    /** The issuer's virtual Libro de Reclamaciones (Ley 32495), linked from the buyer portal (DESIGN.md §12) */
    String getLibroReclamacionesUrl();

    void setLibroReclamacionesUrl(String libroReclamacionesUrl);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);

    Instant getDeletedAt();

    void setDeletedAt(Instant deletedAt);
}
