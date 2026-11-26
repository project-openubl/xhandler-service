package io.github.projectopenubl.xhandler.models;

import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.utils.Ids;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DocumentFileModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    UUID getDocumentId();

    void setDocumentId(UUID documentId);

    /** xml_signed, cdr_zip, pdf, input, enriched */
    String getKind();

    void setKind(String kind);

    String getStorageKey();

    void setStorageKey(String storageKey);

    String getSha256();

    void setSha256(String sha256);

    long getSize();

    void setSize(long size);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);
}
