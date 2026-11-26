package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentFileEntity;
import io.github.projectopenubl.xhandler.utils.Deadlines;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Inserts signed, pending documents directly (what emit does), for job-system tests. Each XML
 * carries a unique {@code marker} so EngineStub scripts can tell documents apart.
 */
@ApplicationScoped
public class TestDocuments {

    private static final AtomicLong NUMERO = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    private final TenantAccess access;
    private final FileStore files;

    public TestDocuments(TenantAccess access, FileStore files) {
        this.access = access;
        this.files = files;
    }

    public record Created(UUID id, String marker) {}

    public Created pending(UUID tenantId, UUID companyId, String endpoint, Consumer<DocumentEntity> customize) {
        String marker = "doc-" + UUID.randomUUID();
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            DocumentEntity doc = new DocumentEntity();
            doc.setId(Ids.newId());
            doc.setTenantId(tenantId);
            doc.setCompanyId(companyId);
            doc.setSunatEnv("fake");
            doc.setKind("Invoice");
            doc.setTipoComprobante("01");
            doc.setSerie("F001");
            doc.setNumero(NUMERO.incrementAndGet());
            doc.setFechaEmision(Deadlines.todayInLima());
            doc.setIssueYear((short) doc.getFechaEmision().getYear());
            doc.setDeadlineAt(Deadlines.sendDeadline("01", "F001", doc.getFechaEmision()));
            doc.setStatus("pending_send");
            doc.setEndpoint(endpoint);
            doc.setNextAttemptAt(Instant.now());
            doc.setProgressAt(Instant.now());
            doc.setCreatedAt(Instant.now());
            doc.setUpdatedAt(doc.getCreatedAt());
            if (customize != null) {
                customize.accept(doc);
            }
            doc.setIssueYear((short) doc.getFechaEmision().getYear());
            doc.persist();
            String xml = "<Invoice><cbc:ID>" + doc.serieNumero() + "</cbc:ID><marker>" + marker + "</marker></Invoice>";
            FileStore.Stored stored = files.put(tenantId, xml.getBytes(StandardCharsets.UTF_8));
            DocumentFileEntity.record(doc, "xml_signed", stored);
            return new Created(doc.getId(), marker);
        });
    }

    public DocumentEntity get(UUID tenantId, UUID documentId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            DocumentEntity doc = DocumentEntity.findById(documentId);
            DocumentEntity.getEntityManager().detach(doc);
            return doc;
        });
    }

    public void update(UUID tenantId, UUID documentId, Consumer<DocumentEntity> change) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            change.accept(DocumentEntity.findById(documentId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        });
    }

    /** Makes a document due now (skips its backoff or lease). */
    public void dueNow(UUID tenantId, UUID documentId) {
        update(tenantId, documentId, d -> d.setNextAttemptAt(Instant.now().minus(Duration.ofSeconds(1))));
    }

    /** Makes several documents due in one transaction. */
    public void dueNow(UUID tenantId, java.util.List<UUID> documentIds) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            for (UUID id : documentIds.stream().sorted().toList()) {
                DocumentEntity doc = DocumentEntity.findById(id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                doc.setNextAttemptAt(Instant.now().minus(Duration.ofSeconds(1)));
            }
        });
    }

    public static LocalDate today() {
        return Deadlines.todayInLima();
    }
}
