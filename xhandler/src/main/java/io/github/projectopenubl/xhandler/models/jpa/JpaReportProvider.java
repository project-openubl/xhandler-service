package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ReportProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaReportProvider implements ReportProvider {

    private final EntityManager em;

    public JpaReportProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public void addReport(UUID id, UUID tenantId, UUID companyId, String sunatEnv, String register, String format, String period,
            String requestedBy) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.report (id, tenant_id, company_id, sunat_env, register, format, period, requested_by)
                        VALUES (:id, :t, :c, :env, :register, :format, :period, :by)""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("c", companyId).setParameter("env", sunatEnv)
                .setParameter("register", register).setParameter("format", format).setParameter("period", period)
                .setParameter("by", requestedBy).execute();
    }

    @Override
    public void setReportJob(UUID id, UUID jobId) {
        em.createNativeStatement("UPDATE xhandler.report SET job_id = :job WHERE id = :id").setParameter("job", jobId).setParameter("id", id)
                .execute();
    }

    @Override
    public List<UUID> getReportIds(UUID companyId, int limit, int offset) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("""
                        SELECT id FROM xhandler.report WHERE company_id = :c ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset""")
                .setParameter("c", companyId).setParameter("limit", limit).setParameter("offset", offset).getResultList();
        return ids;
    }

    @Override
    public Optional<ReportRow> getReport(UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, register, format, period, sunat_env, status, file_name, rows, detail, job_id, created_at, done_at
                        FROM xhandler.report WHERE id = :id""").setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = rows.get(0);
        return Optional.of(new ReportRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5], (String) r[6],
                r[7] == null ? null : ((Number) r[7]).intValue(), (String) r[8], (UUID) r[9], Times.toInstant(r[10]),
                Times.toInstant(r[11])));
    }

    @Override
    public Optional<UUID> getReportCompanyId(UUID id) {
        List<?> found = em.createNativeQuery("SELECT company_id FROM xhandler.report WHERE id = :id").setParameter("id", id)
                .getResultList();
        return found.isEmpty() ? Optional.empty() : Optional.ofNullable((UUID) found.get(0));
    }

    @Override
    public Optional<ReportFile> getReportFile(UUID id, UUID companyId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT file_ref, file_name, format FROM xhandler.report WHERE id = :id AND company_id = :c")
                .setParameter("id", id).setParameter("c", companyId).getResultList();
        return rows.stream().findFirst().map(r -> new ReportFile((String) r[0], (String) r[1], (String) r[2]));
    }

    @Override
    public ReportRequestRow getReportRequest(UUID id) {
        Object[] r = (Object[]) em.createNativeQuery("SELECT company_id, sunat_env, register, format, period FROM xhandler.report WHERE id = :id")
                .setParameter("id", id).getSingleResult();
        return new ReportRequestRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4]);
    }

    @Override
    public void finishReport(UUID id, String status, String fileRef, String fileName, Integer rows, String detail) {
        em.createNativeStatement("""
                        UPDATE xhandler.report SET status = :s, file_ref = :ref, file_name = :name, rows = :rows, detail = :detail, done_at = now()
                        WHERE id = :id""")
                .setParameter("s", status).setParameter("ref", fileRef).setParameter("name", fileName).setParameter("rows", rows)
                .setParameter("detail", detail).setParameter("id", id).execute();
    }

    @Override
    public List<SalesRow> getSalesRows(UUID companyId, String sunatEnv, LocalDate from, LocalDate to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT d.fecha_emision, d.tipo_comprobante, d.serie, d.numero, d.customer_tipo, d.customer_numero, d.customer_nombre,
                               d.moneda,
                               coalesce(d.tipo_cambio, CASE WHEN d.moneda = 'USD' THEN (SELECT r.venta FROM xhandler.exchange_rate r
                                   WHERE r.day <= d.fecha_emision ORDER BY r.day DESC LIMIT 1) END),
                               cast(d.amounts AS text), d.total, d.status,
                               a.fecha_emision, a.tipo_comprobante, a.serie, a.numero
                        FROM xhandler.document d LEFT JOIN xhandler.document a ON a.id = d.affected_document_id
                        WHERE d.company_id = :c AND d.sunat_env = :env AND d.tipo_comprobante IN ('01', '03', '07', '08')
                          AND d.fecha_emision BETWEEN :from AND :to
                          AND d.status IN ('accepted', 'accepted_with_observations', 'voiding', 'voided')
                        ORDER BY d.fecha_emision, d.tipo_comprobante, d.serie, d.numero""")
                .setParameter("c", companyId).setParameter("env", sunatEnv)
                .setParameter("from", from).setParameter("to", to)
                .getResultList();
        List<SalesRow> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new SalesRow(date(r[0]), (String) r[1], (String) r[2], ((Number) r[3]).longValue(), (String) r[4], (String) r[5], (String) r[6],
                    (String) r[7], (BigDecimal) r[8], (String) r[9], (BigDecimal) r[10], (String) r[11],
                    date(r[12]), (String) r[13], (String) r[14], r[15] == null ? null : ((Number) r[15]).longValue()));
        }
        return out;
    }

    @Override
    public List<PurchaseRow> getPurchaseRows(UUID companyId, LocalDate from, LocalDate to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT fecha_emision, tipo_comprobante, serie, numero, supplier_ruc, supplier_name, moneda, cast(amounts AS text), total,
                               affected_serie_numero, affected_tipo,
                               CASE WHEN moneda = 'USD' THEN (SELECT r.venta FROM xhandler.exchange_rate r
                                   WHERE r.day <= reception.fecha_emision ORDER BY r.day DESC LIMIT 1) END
                        FROM xhandler.reception
                        WHERE company_id = :c AND fecha_emision BETWEEN :from AND :to AND tipo_comprobante IN ('01', '03', '07', '08')
                          AND validity_state NOT IN ('not_found', 'voided', 'rejected')
                        ORDER BY fecha_emision, supplier_ruc, tipo_comprobante, serie, numero""")
                .setParameter("c", companyId).setParameter("from", from).setParameter("to", to)
                .getResultList();
        List<PurchaseRow> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new PurchaseRow(date(r[0]), (String) r[1], (String) r[2], ((Number) r[3]).longValue(), (String) r[4], (String) r[5],
                    (String) r[6], (String) r[7], (BigDecimal) r[8], (String) r[9], (String) r[10], (BigDecimal) r[11]));
        }
        return out;
    }

    @Override
    public long countUnresolvedDocuments(UUID companyId, String sunatEnv, LocalDate from, LocalDate to) {
        return ((Number) em.createNativeQuery("""
                        SELECT count(*) FROM xhandler.document
                        WHERE company_id = :c AND sunat_env = :env AND tipo_comprobante IN ('01', '03', '07', '08')
                          AND fecha_emision BETWEEN :from AND :to AND status IN ('pending_send', 'in_progress', 'unknown', 'exception')""")
                .setParameter("c", companyId).setParameter("env", sunatEnv).setParameter("from", from)
                .setParameter("to", to).getSingleResult()).longValue();
    }

    private static LocalDate date(Object value) {
        if (value == null) {
            return null;
        }
        return Times.toLocalDate(value);
    }
}
