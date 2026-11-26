package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.PrivacyProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaPrivacyProvider implements PrivacyProvider {

    private final EntityManager em;

    public JpaPrivacyProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public void addRequest(UUID id, UUID tenantId, String subjectDocType, String subjectDocNumber, String subjectName, String kind, String channel,
            String details, Instant receivedAt, Instant forwardDueAt, Instant forwardedAt, Instant responseDueAt, String createdBy) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.data_subject_request (id, tenant_id, subject_doc_type, subject_doc_number, subject_name, kind, channel,
                            details, received_at, forward_due_at, forwarded_at, response_due_at, created_by)
                        VALUES (:id, :t, :dt, :dn, :name, :kind, :channel, :details, :now, :forwardDue, :forwarded, :due, :by)""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("dt", subjectDocType)
                .setParameter("dn", subjectDocNumber).setParameter("name", subjectName).setParameter("kind", kind)
                .setParameter("channel", channel).setParameter("details", details).setParameter("now", receivedAt)
                .setParameter("forwardDue", forwardDueAt).setParameter("forwarded", forwardedAt)
                .setParameter("due", responseDueAt).setParameter("by", createdBy).executeUpdate();
    }

    @Override
    public List<UUID> getRequestIds(String status, int limit, int offset) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("""
                        SELECT id FROM xhandler.data_subject_request WHERE (cast(:s AS text) IS NULL OR status = :s)
                        ORDER BY received_at DESC, id DESC LIMIT :limit OFFSET :offset""")
                .setParameter("s", status).setParameter("limit", limit).setParameter("offset", offset).getResultList();
        return ids;
    }

    @Override
    public Optional<DataSubjectRequestRow> getRequest(UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, subject_doc_type, subject_doc_number, subject_name, kind, channel, details, received_at, forwarded_at,
                               response_due_at, status, resolution, closed_at
                        FROM xhandler.data_subject_request WHERE id = :id""").setParameter("id", id).getResultList();
        return rows.stream().findFirst().map(r -> new DataSubjectRequestRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                (String) r[4], (String) r[5], (String) r[6], Times.toInstant(r[7]), Times.toInstant(r[8]), Times.toInstant(r[9]),
                (String) r[10], (String) r[11], Times.toInstant(r[12])));
    }

    @Override
    public int closeRequest(UUID id, String resolution, String closedBy) {
        return em.createNativeQuery("""
                        UPDATE xhandler.data_subject_request SET status = 'closed', resolution = :r, closed_at = now(), closed_by = :by
                        WHERE id = :id AND status = 'open'""")
                .setParameter("r", resolution).setParameter("by", closedBy).setParameter("id", id).executeUpdate();
    }

    @Override
    public List<PersonalDocumentRow> getPersonalDocuments(String docType, String docNumber) {
        @SuppressWarnings("unchecked")
        List<Object[]> docs = em.createNativeQuery("""
                        SELECT d.id, c.ruc, d.tipo_comprobante, d.serie, d.numero, d.fecha_emision, d.customer_nombre, d.moneda, d.total, d.status
                        FROM xhandler.document d JOIN xhandler.company c ON c.id = d.company_id
                        WHERE d.customer_numero = :n AND d.customer_tipo = :t ORDER BY d.fecha_emision DESC LIMIT 5000""")
                .setParameter("n", docNumber).setParameter("t", docType).getResultList();
        return docs.stream().map(r -> new PersonalDocumentRow((UUID) r[0], (String) r[1], (String) r[2], r[3], r[4], r[5], r[6], r[7], r[8],
                (String) r[9])).toList();
    }

    @Override
    public List<PersonalDraftRow> getPersonalDrafts(String docType, String docNumber) {
        @SuppressWarnings("unchecked")
        List<Object[]> drafts = em.createNativeQuery("""
                        SELECT dd.id, c.ruc, dd.kind, dd.tipo_comprobante, dd.serie, dd.spec->'cliente'->>'nombre', dd.status, dd.created_at,
                               dd.deleted_at
                        FROM xhandler.document_draft dd JOIN xhandler.company c ON c.id = dd.company_id
                        WHERE dd.spec->'cliente'->>'numero_documento_identidad' = :n AND dd.spec->'cliente'->>'tipo_documento_identidad' = :t
                        ORDER BY dd.created_at DESC LIMIT 5000""")
                .setParameter("n", docNumber).setParameter("t", docType).getResultList();
        return drafts.stream().map(r -> new PersonalDraftRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                (String) r[5], (String) r[6], Times.toInstant(r[7]), Times.toInstant(r[8]))).toList();
    }

    @Override
    public List<DueRequest> getRequestsDue() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, request_id, response_due_at FROM xhandler.privacy_requests_due(2)")
                .getResultList();
        return rows.stream().map(r -> new DueRequest((UUID) r[0], (UUID) r[1], Times.toInstant(r[2]))).toList();
    }

    @Override
    public int markReminded(UUID id) {
        return em.createNativeQuery(
                        "UPDATE xhandler.data_subject_request SET reminded_at = now() WHERE id = :id AND reminded_at IS NULL AND status = 'open'")
                .setParameter("id", id).executeUpdate();
    }

    @Override
    public int setAuditHold(UUID tenantId, boolean value) {
        return updateTenant(tenantId, "audit_hold = :v", value);
    }

    @Override
    public int setArchivePlan(UUID tenantId, boolean value) {
        return updateTenant(tenantId, "archive_plan = :v", value);
    }

    private int updateTenant(UUID tenantId, String set, boolean value) {
        return em.createNativeQuery("UPDATE xhandler.tenant SET " + set + ", updated_at = now() WHERE id = :id AND deleted_at IS NULL")
                .setParameter("v", value).setParameter("id", tenantId).executeUpdate();
    }

    @Override
    public void scheduleExitedTenantDeletion(UUID tenantId) {
        em.createNativeQuery("""
                        UPDATE xhandler.tenant SET delete_after = greatest(exit_requested_at + interval '730 days', now() + interval '30 days')
                        WHERE id = :id AND status = 'exited' AND delete_after IS NULL AND deleted_at IS NULL""")
                .setParameter("id", tenantId).executeUpdate();
    }

    @Override
    public int cancelExit(UUID tenantId) {
        return em.createNativeQuery("""
                        UPDATE xhandler.tenant SET exit_requested_at = NULL, delete_after = NULL, status = 'active', updated_at = now()
                        WHERE id = :id AND status = 'exited' AND deleted_at IS NULL""").setParameter("id", tenantId).executeUpdate();
    }

    @Override
    public boolean existsTenant(UUID tenantId) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.tenant WHERE id = :id AND deleted_at IS NULL").setParameter("id", tenantId)
                .getResultList().isEmpty();
    }
}
