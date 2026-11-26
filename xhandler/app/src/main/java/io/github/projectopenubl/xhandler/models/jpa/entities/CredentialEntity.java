package io.github.projectopenubl.xhandler.models.jpa.entities;

import io.github.projectopenubl.xhandler.keys.SecretBox;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A company's secret (SOL user, GRE client, SMTP...), sealed by {@link SecretBox}. */
@Entity
@Table(name = "credential")
public class CredentialEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    private UUID tenantId;
    private UUID companyId;
    /** sol, gre, sire, validity, smtp, lookup */
    private String kind;
    /** Non-secret label shown in the API, e.g. the masked user */
    private String label;
    private byte[] ciphertext;
    private byte[] wrappedDataKey;
    private String masterKeyId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /** Associated data binding the ciphertext to this row (see {@link SecretBox#seal}). */
    public String context() {
        return "credential:" + id + ":" + tenantId + ":" + companyId + ":" + kind;
    }

    public SecretBox.Sealed sealed() {
        return new SecretBox.Sealed(ciphertext, wrappedDataKey, masterKeyId);
    }

    public void seal(SecretBox.Sealed sealed) {
        ciphertext = sealed.ciphertext();
        wrappedDataKey = sealed.wrappedDataKey();
        masterKeyId = sealed.masterKeyId();
    }

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

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(byte[] ciphertext) {
        this.ciphertext = ciphertext;
    }

    public byte[] getWrappedDataKey() {
        return wrappedDataKey;
    }

    public void setWrappedDataKey(byte[] wrappedDataKey) {
        this.wrappedDataKey = wrappedDataKey;
    }

    public String getMasterKeyId() {
        return masterKeyId;
    }

    public void setMasterKeyId(String masterKeyId) {
        this.masterKeyId = masterKeyId;
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
}
