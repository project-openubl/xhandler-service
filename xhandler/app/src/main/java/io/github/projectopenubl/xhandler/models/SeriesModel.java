package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.UUID;

public interface SeriesModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    UUID getCompanyId();

    void setCompanyId(UUID companyId);

    String getSunatEnv();

    void setSunatEnv(String sunatEnv);

    /** SUNAT catalog 01 code: 01 factura, 03 boleta, 07 nota de crédito, ... */
    String getTipoComprobante();

    void setTipoComprobante(String tipoComprobante);

    String getSerie();

    void setSerie(String serie);

    long getNextNumber();

    void setNextNumber(long nextNumber);

    boolean isDefault();

    void setDefault(boolean isDefault);

    boolean isActive();

    void setActive(boolean active);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);
}
