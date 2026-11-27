package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.jpa.entities.CompanyEntity;
import java.time.Instant;
import java.util.UUID;

public class CompanyAdapter implements CompanyModel, JpaModel<CompanyEntity> {

    private final CompanyEntity entity;

    public CompanyAdapter(CompanyEntity entity) {
        this.entity = entity;
    }

    public static CompanyEntity toEntity(CompanyModel model) {
        return ((CompanyAdapter) model).getEntity();
    }

    @Override
    public CompanyEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.COMPANY;
    }

    @Override
    public UUID getTenantId() {
        return entity.getTenantId();
    }

    @Override
    public void setTenantId(UUID tenantId) {
        entity.setTenantId(tenantId);
    }

    @Override
    public String getRuc() {
        return entity.getRuc();
    }

    @Override
    public void setRuc(String ruc) {
        entity.setRuc(ruc);
    }

    @Override
    public String getRazonSocial() {
        return entity.getRazonSocial();
    }

    @Override
    public void setRazonSocial(String razonSocial) {
        entity.setRazonSocial(razonSocial);
    }

    @Override
    public String getNombreComercial() {
        return entity.getNombreComercial();
    }

    @Override
    public void setNombreComercial(String nombreComercial) {
        entity.setNombreComercial(nombreComercial);
    }

    @Override
    public String getSunatEnv() {
        return entity.getSunatEnv();
    }

    @Override
    public void setSunatEnv(String sunatEnv) {
        entity.setSunatEnv(sunatEnv);
    }

    @Override
    public String getEmissionMode() {
        return entity.getEmissionMode();
    }

    @Override
    public void setEmissionMode(String emissionMode) {
        entity.setEmissionMode(emissionMode);
    }

    @Override
    public Instant getRucVerifiedAt() {
        return entity.getRucVerifiedAt();
    }

    @Override
    public void setRucVerifiedAt(Instant rucVerifiedAt) {
        entity.setRucVerifiedAt(rucVerifiedAt);
    }

    @Override
    public String getEmail() {
        return entity.getEmail();
    }

    @Override
    public void setEmail(String email) {
        entity.setEmail(email);
    }

    @Override
    public boolean isDeliveredOnEmitDefault() {
        return entity.isDeliveredOnEmitDefault();
    }

    @Override
    public void setDeliveredOnEmitDefault(boolean deliveredOnEmitDefault) {
        entity.setDeliveredOnEmitDefault(deliveredOnEmitDefault);
    }

    @Override
    public String getPdfFormat() {
        return entity.getPdfFormat();
    }

    @Override
    public void setPdfFormat(String pdfFormat) {
        entity.setPdfFormat(pdfFormat);
    }

    @Override
    public java.util.Map<String, Object> getPdfOptions() {
        return entity.getPdfOptions();
    }

    @Override
    public void setPdfOptions(java.util.Map<String, Object> pdfOptions) {
        entity.setPdfOptions(pdfOptions);
    }

    @Override
    public String getLogoRef() {
        return entity.getLogoRef();
    }

    @Override
    public void setLogoRef(String logoRef) {
        entity.setLogoRef(logoRef);
    }

    @Override
    public String getDireccion() {
        return entity.getDireccion();
    }

    @Override
    public void setDireccion(String direccion) {
        entity.setDireccion(direccion);
    }

    @Override
    public boolean isEmailOnAccept() {
        return entity.isEmailOnAccept();
    }

    @Override
    public void setEmailOnAccept(boolean emailOnAccept) {
        entity.setEmailOnAccept(emailOnAccept);
    }

    @Override
    public String getEmailSubject() {
        return entity.getEmailSubject();
    }

    @Override
    public void setEmailSubject(String emailSubject) {
        entity.setEmailSubject(emailSubject);
    }

    @Override
    public String getEmailBody() {
        return entity.getEmailBody();
    }

    @Override
    public void setEmailBody(String emailBody) {
        entity.setEmailBody(emailBody);
    }

    @Override
    public String getLookupProvider() {
        return entity.getLookupProvider();
    }

    @Override
    public void setLookupProvider(String lookupProvider) {
        entity.setLookupProvider(lookupProvider);
    }

    @Override
    public boolean isActive() {
        return entity.isActive();
    }

    @Override
    public void setActive(boolean active) {
        entity.setActive(active);
    }

    @Override
    public boolean isSendingPaused() {
        return entity.isSendingPaused();
    }

    @Override
    public void setSendingPaused(boolean sendingPaused) {
        entity.setSendingPaused(sendingPaused);
    }

    @Override
    public String getLibroReclamacionesUrl() {
        return entity.getLibroReclamacionesUrl();
    }

    @Override
    public void setLibroReclamacionesUrl(String libroReclamacionesUrl) {
        entity.setLibroReclamacionesUrl(libroReclamacionesUrl);
    }

    @Override
    public Instant getCreatedAt() {
        return entity.getCreatedAt();
    }

    @Override
    public void setCreatedAt(Instant createdAt) {
        entity.setCreatedAt(createdAt);
    }

    @Override
    public Instant getUpdatedAt() {
        return entity.getUpdatedAt();
    }

    @Override
    public void setUpdatedAt(Instant updatedAt) {
        entity.setUpdatedAt(updatedAt);
    }

    @Override
    public Instant getDeletedAt() {
        return entity.getDeletedAt();
    }

    @Override
    public void setDeletedAt(Instant deletedAt) {
        entity.setDeletedAt(deletedAt);
    }
}
