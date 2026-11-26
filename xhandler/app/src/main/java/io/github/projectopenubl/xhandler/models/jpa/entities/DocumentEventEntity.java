package io.github.projectopenubl.xhandler.models.jpa.entities;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One entry of a document's timeline (DESIGN.md §7.1). Append-only. */
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "document_event")
public class DocumentEventEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    private Instant at;
    private UUID tenantId;
    private UUID documentId;
    private String status;
    private Integer code;
    private String message;
    private String actor;
    private String correlationId;

    public static DocumentEventEntity record(DocumentEntity document, Integer code, String message, String actor, String correlationId) {
        DocumentEventEntity event = new DocumentEventEntity();
        event.id = Ids.newId();
        event.at = Instant.now();
        event.tenantId = document.getTenantId();
        event.documentId = document.getId();
        event.status = document.getStatus();
        event.code = code;
        event.message = message;
        event.actor = actor;
        event.correlationId = correlationId;
        event.persist();
        return event;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
