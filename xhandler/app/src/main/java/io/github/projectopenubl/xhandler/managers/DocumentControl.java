package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.events.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEventEntity;
import io.github.projectopenubl.xhandler.utils.Deadlines;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manual control of SUNAT attempts (DESIGN.md §24.1 rule 8): hold, release and retry, per document
 * or in bulk. The same actions back the tenant API and xhandler-admin. Every method runs with the
 * document's tenant open and changes rows under a row lock.
 */
@ApplicationScoped
public class DocumentControl {

    /** Statuses a manual retry accepts: non-definitive ones, and exception (resend with the same number). */
    static final Set<String> RETRY_FROM = Set.of("pending_send", "in_progress", "unknown", "exception");

    private final Auditor auditor;
    private final EntityManager em;
    private final io.github.projectopenubl.xhandler.jobs.JobStore jobs;
    private final Event<DocumentStatusChanged> statusChanged;

    public DocumentControl(Auditor auditor, EntityManager em, Event<DocumentStatusChanged> statusChanged,
            io.github.projectopenubl.xhandler.jobs.JobStore jobs) {
        this.auditor = auditor;
        this.jobs = jobs;
        this.em = em;
        this.statusChanged = statusChanged;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentEntity lock(UUID documentId) {
        DocumentEntity doc = DocumentEntity.findById(documentId, LockModeType.PESSIMISTIC_WRITE);
        if (doc == null) {
            throw ApiException.notFound();
        }
        return doc;
    }

    /** Stops automatic attempts until released (hold_reason = operator). */
    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentEntity hold(TenantScope scope, UUID documentId) {
        DocumentEntity doc = lock(documentId);
        if (doc.definitive()) {
            throw ApiException.conflict("document_final", "El documento ya tiene una respuesta definitiva.");
        }
        doc.setHoldReason("operator");
        doc.setUpdatedAt(Instant.now());
        DocumentEventEntity.record(doc, null, "held", scope.actor().auditName(), null);
        auditor.record(scope, "document.held", "document", doc.getId());
        return doc;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentEntity release(TenantScope scope, UUID documentId) {
        DocumentEntity doc = lock(documentId);
        if (doc.getHoldReason() == null) {
            return doc;
        }
        doc.setHoldReason(null);
        if (DocumentEntity.RETRYABLE.contains(doc.getStatus())) {
            doc.setNextAttemptAt(Instant.now());
            notifyReconciler();
        }
        doc.setUpdatedAt(Instant.now());
        DocumentEventEntity.record(doc, null, "released", scope.actor().auditName(), null);
        auditor.record(scope, "document.released", "document", doc.getId());
        return doc;
    }

    /**
     * Clears the hold and attempts now. A document in {@code exception} goes back to {@code
     * pending_send} and is resent with the same number and bytes, once the user fixed the cause.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentEntity retry(TenantScope scope, UUID documentId) {
        DocumentEntity doc = lock(documentId);
        if (doc.getSummaryDocumentId() != null) {
            // In a resumen diario: only once that summary failed. It leaves the summary and goes
            // to SUNAT again (past D+5 that means a new resumen diario, §9.2.4)
            DocumentEntity summary = DocumentEntity.findById(doc.getSummaryDocumentId());
            if (summary != null && !"rejected".equals(summary.getStatus()) && !"exception".equals(summary.getStatus())) {
                throw ApiException.conflict("document_in_summary", "El documento sigue el resultado de su resumen diario.");
            }
            doc.setSummaryDocumentId(null);
        }
        if (!RETRY_FROM.contains(doc.getStatus()) || "ticket_lost".equals(doc.getCodeClass())) {
            // A summary resolved line by line must not be resent: its lines went to new summaries
            throw ApiException.conflict("document_not_retryable", "El documento no se puede reenviar en su estado actual.");
        }
        retryLocked(scope, doc);
        if (doc.isBoletaFamily() && Deadlines.boletaPastIndividualWindow(doc.getFechaEmision(), Deadlines.todayInLima())) {
            // Past D+5 a boleta can't go on its own: the resumen diario fallback takes it now
            doc.setStatus("pending_send");
            doc.setCodeClass("route_change");
            doc.setNextAttemptAt(null);
            jobs.enqueuePlatform("documents.boleta-summary-fallback", 2, Map.of("trigger", "retry"),
                    List.of("schedule:boleta-summary-fallback"));
        }
        notifyReconciler();
        return doc;
    }

    /** Bulk retry in the open tenant by filter (§24.1). Returns how many documents were retried. */
    @Transactional(Transactional.TxType.MANDATORY)
    public int retryWhere(TenantScope scope, UUID companyId, String status, LocalDate from, LocalDate to) {
        StringBuilder query = new StringBuilder("status in :statuses and summaryDocumentId is null");
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("statuses", status == null ? RETRY_FROM : Set.of(status));
        if (companyId != null) {
            query.append(" and companyId = :company");
            params.put("company", companyId);
        }
        if (from != null) {
            query.append(" and fechaEmision >= :from");
            params.put("from", from);
        }
        if (to != null) {
            query.append(" and fechaEmision <= :to");
            params.put("to", to);
        }
        query.append(" order by id");
        List<DocumentEntity> docs = DocumentEntity.<DocumentEntity>find(query.toString(), params).withLock(LockModeType.PESSIMISTIC_WRITE).list();
        int n = 0;
        for (DocumentEntity doc : docs) {
            if (RETRY_FROM.contains(doc.getStatus())) {
                retryLocked(scope, doc);
                n++;
            }
        }
        auditor.record(scope, "document.bulk_retry", "tenant", scope.tenantId(),
                Map.of("count", n, "status", String.valueOf(status), "company_id", String.valueOf(companyId)));
        if (n > 0) {
            notifyReconciler();
        }
        return n;
    }

    private void retryLocked(TenantScope scope, DocumentEntity doc) {
        String previous = doc.getStatus();
        Instant now = Instant.now();
        if ("exception".equals(doc.getStatus())) {
            doc.setStatus("pending_send");
        }
        doc.setHoldReason(null);
        doc.setNextAttemptAt(now);
        doc.setSameOutcomeCount(0);
        doc.setLastOutcome(null);
        if ("summary_failed".equals(doc.getCodeClass())) {
            doc.setCodeClass(null);
        }
        doc.setProgressAt(now);
        doc.setUpdatedAt(now);
        DocumentEventEntity.record(doc, null, "manual retry", scope.actor().auditName(), null);
        auditor.record(scope, "document.retried", "document", doc.getId());
        if (!doc.getStatus().equals(previous)) {
            statusChanged.fire(new DocumentStatusChanged(doc, previous, null));
        }
    }

    private void notifyReconciler() {
        em.createNativeQuery("SELECT pg_notify('xhandler_reconcile', '')").getSingleResult();
    }
}
