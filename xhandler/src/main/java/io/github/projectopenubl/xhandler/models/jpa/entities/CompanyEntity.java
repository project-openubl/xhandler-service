package io.github.projectopenubl.xhandler.models.jpa.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** An issuer (RUC) of a tenant. A RUC exists once on the platform per environment. */
@Entity
@Table(name = "company")
public class CompanyEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    private UUID tenantId;
    private String ruc;
    private String razonSocial;
    private String nombreComercial;
    /** fake (the sandbox every company starts on, DESIGN.md §26.2), beta or production */
    private String sunatEnv = "fake";
    /** software or pse */
    private String emissionMode = "software";
    private Instant rucVerifiedAt;
    private String email;
    /** Default of {@code delivered} on emit (usually true for POS, DESIGN.md §9.3) */
    private boolean deliveredOnEmitDefault;
    /** a4, ticket80 or ticket58 (DESIGN.md §11.6) */
    private String pdfFormat = "a4";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @jakarta.persistence.Column(columnDefinition = "jsonb")
    private java.util.Map<String, Object> pdfOptions = java.util.Map.of();

    private String logoRef;
    private String direccion;
    /** Email the buyer when SUNAT accepts (to the document's customer email) */
    private boolean emailOnAccept;
    private String emailSubject;
    private String emailBody;
    /** RUC/DNI lookup provider (DESIGN.md §17): searchpe or a commercial one with a tenant token */
    private String lookupProvider;
    private boolean active = true;
    /** Holds SUNAT attempts for all its documents (DESIGN.md §24.1) */
    private boolean sendingPaused;
    /** The issuer's virtual Libro de Reclamaciones (Ley 32495), linked from the buyer portal (DESIGN.md §12) */
    private String libroReclamacionesUrl;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant deletedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getNombreComercial() {
        return nombreComercial;
    }

    public void setNombreComercial(String nombreComercial) {
        this.nombreComercial = nombreComercial;
    }

    public String getSunatEnv() {
        return sunatEnv;
    }

    public void setSunatEnv(String sunatEnv) {
        this.sunatEnv = sunatEnv;
    }

    public String getEmissionMode() {
        return emissionMode;
    }

    public void setEmissionMode(String emissionMode) {
        this.emissionMode = emissionMode;
    }

    public Instant getRucVerifiedAt() {
        return rucVerifiedAt;
    }

    public void setRucVerifiedAt(Instant rucVerifiedAt) {
        this.rucVerifiedAt = rucVerifiedAt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isDeliveredOnEmitDefault() {
        return deliveredOnEmitDefault;
    }

    public void setDeliveredOnEmitDefault(boolean deliveredOnEmitDefault) {
        this.deliveredOnEmitDefault = deliveredOnEmitDefault;
    }

    public String getPdfFormat() {
        return pdfFormat;
    }

    public void setPdfFormat(String pdfFormat) {
        this.pdfFormat = pdfFormat;
    }

    public java.util.Map<String, Object> getPdfOptions() {
        return pdfOptions;
    }

    public void setPdfOptions(java.util.Map<String, Object> pdfOptions) {
        this.pdfOptions = pdfOptions;
    }

    public String getLogoRef() {
        return logoRef;
    }

    public void setLogoRef(String logoRef) {
        this.logoRef = logoRef;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public boolean isEmailOnAccept() {
        return emailOnAccept;
    }

    public void setEmailOnAccept(boolean emailOnAccept) {
        this.emailOnAccept = emailOnAccept;
    }

    public String getEmailSubject() {
        return emailSubject;
    }

    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }

    public String getEmailBody() {
        return emailBody;
    }

    public void setEmailBody(String emailBody) {
        this.emailBody = emailBody;
    }

    public String getLookupProvider() {
        return lookupProvider;
    }

    public void setLookupProvider(String lookupProvider) {
        this.lookupProvider = lookupProvider;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSendingPaused() {
        return sendingPaused;
    }

    public void setSendingPaused(boolean sendingPaused) {
        this.sendingPaused = sendingPaused;
    }

    public String getLibroReclamacionesUrl() {
        return libroReclamacionesUrl;
    }

    public void setLibroReclamacionesUrl(String libroReclamacionesUrl) {
        this.libroReclamacionesUrl = libroReclamacionesUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
