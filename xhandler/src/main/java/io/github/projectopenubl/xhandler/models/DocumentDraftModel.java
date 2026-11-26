package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface DocumentDraftModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    UUID getCompanyId();

    void setCompanyId(UUID companyId);

    /** xbuilder kind: Invoice, CreditNote, DebitNote… */
    String getKind();

    void setKind(String kind);

    String getTipoComprobante();

    void setTipoComprobante(String tipoComprobante);

    /** The series to number it in; null = the default series when issued */
    String getSerie();

    void setSerie(String serie);

    Map<String, Object> getSpec();

    void setSpec(Map<String, Object> spec);

    Map<String, String> getLabels();

    void setLabels(Map<String, String> labels);

    /** draft or issued */
    String getStatus();

    void setStatus(String status);

    /** Incremented on every change: issuing and edits compare it (§10.8 rule 2) */
    int getVersion();

    void setVersion(int version);

    /** The issued document */
    UUID getDocumentId();

    void setDocumentId(UUID documentId);

    String getCreatedBy();

    void setCreatedBy(String createdBy);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);

    Instant getDeletedAt();

    void setDeletedAt(Instant deletedAt);
}
