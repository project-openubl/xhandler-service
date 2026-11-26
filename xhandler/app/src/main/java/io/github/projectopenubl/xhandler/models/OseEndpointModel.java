package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.UUID;

public interface OseEndpointModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    UUID getCompanyId();

    void setCompanyId(UUID companyId);

    String getName();

    void setName(String name);

    String getInvoiceUrl();

    void setInvoiceUrl(String invoiceUrl);

    String getPerceptionUrl();

    void setPerceptionUrl(String perceptionUrl);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);
}
