package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.TenantExitProvider;
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
public class JpaTenantExitProvider implements TenantExitProvider {

    private final EntityManager em;

    public JpaTenantExitProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public boolean hasRecentExport() {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.tenant_export WHERE status = 'pending' OR created_at > now() - interval '1 hour'")
                .getResultList().isEmpty();
    }

    @Override
    public void addExport(UUID id, UUID tenantId, String requestedBy) {
        em.createNativeStatement("INSERT INTO xhandler.tenant_export (id, tenant_id, requested_by) VALUES (:id, :t, :by)")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("by", requestedBy).execute();
    }

    @Override
    public void setExportJob(UUID id, UUID jobId) {
        em.createNativeStatement("UPDATE xhandler.tenant_export SET job_id = :j WHERE id = :id").setParameter("j", jobId).setParameter("id", id)
                .execute();
    }

    @Override
    public int failInterruptedExports() {
        return em.createNativeStatement("""
                        UPDATE xhandler.tenant_export e SET status = 'failed', detail = 'La exportación se interrumpió. Solicítela de nuevo.',
                            done_at = now()
                        WHERE e.status = 'pending' AND e.job_id IS NOT NULL
                          AND (SELECT state FROM xhandler.job_summary(e.job_id)) IN ('failed', 'canceled')""").execute();
    }

    @Override
    public List<UUID> getRecentExportIds() {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("SELECT id FROM xhandler.tenant_export ORDER BY created_at DESC LIMIT 20").getResultList();
        return ids;
    }

    @Override
    public Optional<ExportRow> getExport(UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, status, cast(parts AS text), documents, detail, created_at, done_at FROM xhandler.tenant_export WHERE id = :id""")
                .setParameter("id", id).getResultList();
        return rows.stream().findFirst().map(r -> new ExportRow((UUID) r[0], (String) r[1], (String) r[2],
                r[3] == null ? null : ((Number) r[3]).intValue(), (String) r[4], Times.toInstant(r[5]), Times.toInstant(r[6])));
    }

    @Override
    public Optional<ExportParts> getExportParts(UUID id) {
        List<?> rows = em.createNativeQuery("SELECT cast(parts AS text) FROM xhandler.tenant_export WHERE id = :id").setParameter("id", id)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ExportParts((String) rows.get(0)));
    }

    @Override
    public void markExportDone(UUID id, String partsJson, int documents) {
        em.createNativeStatement("""
                        UPDATE xhandler.tenant_export SET status = 'done', parts = cast(:p AS jsonb), documents = :n, done_at = now()
                        WHERE id = :id""").setParameter("p", partsJson).setParameter("n", documents).setParameter("id", id).execute();
    }

    @Override
    public void markExportFailed(UUID id, String detail) {
        em.createNativeStatement("UPDATE xhandler.tenant_export SET status = 'failed', detail = :d, done_at = now() WHERE id = :id")
                .setParameter("d", detail).setParameter("id", id).execute();
    }

    @Override
    public long countOpenDocuments() {
        return ((Number) em.createNativeQuery("""
                        SELECT count(*) FROM xhandler.document WHERE status IN ('pending_send', 'in_progress', 'unknown', 'voiding')""")
                .getSingleResult()).longValue();
    }

    @Override
    public boolean hasExportCoveringAllDocuments() {
        return !em.createNativeQuery("""
                        SELECT 1 FROM xhandler.tenant_export e WHERE e.status = 'done' AND e.done_at > now() - interval '30 days'
                          AND e.created_at > coalesce((SELECT max(updated_at) FROM xhandler.document), '-infinity')""")
                .getResultList().isEmpty();
    }

    @Override
    public void markExited(UUID tenantId, Instant deleteAfter) {
        em.createNativeStatement("""
                        UPDATE xhandler.tenant SET exit_requested_at = now(), delete_after = :d, status = 'exited', updated_at = now()
                        WHERE id = :id""").setParameter("d", deleteAfter).setParameter("id", tenantId).execute();
    }

    @Override
    public List<ExportDocumentRow> getExportDocuments(Instant afterCreated, UUID afterId, int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT d.id, c.ruc, d.sunat_env, d.tipo_comprobante, d.serie, d.numero, d.fecha_emision, d.status, d.sunat_code,
                               d.moneda, d.total, d.customer_tipo, d.customer_numero, d.created_at,
                               (SELECT storage_key FROM xhandler.document_file f WHERE f.document_id = d.id AND f.kind = 'xml_signed'),
                               (SELECT storage_key FROM xhandler.document_file f WHERE f.document_id = d.id AND f.kind = 'cdr_zip')
                        FROM xhandler.document d JOIN xhandler.company c ON c.id = d.company_id
                        WHERE (d.created_at, d.id) > (:at, :id)
                        ORDER BY d.created_at, d.id LIMIT :n""")
                .setParameter("at", afterCreated).setParameter("id", afterId).setParameter("n", limit).getResultList();
        return rows.stream().map(r -> new ExportDocumentRow((UUID) r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11],
                r[12], Times.toInstant(r[13]), (String) r[14], (String) r[15])).toList();
    }

    @Override
    public List<ExportReceptionRow> getExportReceptions(UUID afterId, int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT r.id, c.ruc, r.supplier_ruc, r.tipo_comprobante, r.serie, r.numero, r.fecha_emision, r.validity_state,
                               r.xml_ref, r.pdf_ref, r.cdr_ref
                        FROM xhandler.reception r JOIN xhandler.company c ON c.id = r.company_id
                        WHERE r.id > :after ORDER BY r.id LIMIT :n""").setParameter("after", afterId).setParameter("n", limit)
                .getResultList();
        return rows.stream().map(r -> new ExportReceptionRow((UUID) r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], (String) r[8],
                (String) r[9], (String) r[10])).toList();
    }

    @Override
    public List<UUID> getTenantsToDelete() {
        @SuppressWarnings("unchecked")
        List<UUID> due = em.createNativeQuery("SELECT tenant_id FROM xhandler.tenants_to_delete()").getResultList();
        return due;
    }

    @Override
    public String purgeTenant(UUID tenantId, int filesDeleted) {
        return String.valueOf(em.createNativeQuery("SELECT cast(xhandler.purge_tenant(:t, :f) AS text)").setParameter("t", tenantId)
                .setParameter("f", filesDeleted).getSingleResult());
    }
}
