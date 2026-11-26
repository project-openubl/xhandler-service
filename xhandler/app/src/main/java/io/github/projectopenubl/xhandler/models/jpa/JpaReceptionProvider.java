package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ReceptionProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaReceptionProvider implements ReceptionProvider {

    private final EntityManager em;

    public JpaReceptionProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public void addReception(NewReception r) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.reception (id, tenant_id, company_id, supplier_ruc, tipo_comprobante, serie, numero, fecha_emision,
                            moneda, total, digest_value, xml_ref, pdf_ref, cdr_ref, amounts, affected_serie_numero, affected_tipo)
                        VALUES (:id, :t, :c, :ruc, :tipo, :serie, :numero, :fecha, :moneda, :total, :digest, :xml, :pdf, :cdr,
                            cast(:amounts AS jsonb), :affected, :affectedTipo)""")
                .setParameter("amounts", r.amounts())
                .setParameter("affected", r.affectedSerieNumero()).setParameter("affectedTipo", r.affectedTipo())
                .setParameter("id", r.id()).setParameter("t", r.tenantId()).setParameter("c", r.companyId())
                .setParameter("ruc", r.supplierRuc()).setParameter("tipo", r.tipoComprobante()).setParameter("serie", r.serie())
                .setParameter("numero", r.numero())
                .setParameter("fecha", r.fechaEmision())
                .setParameter("moneda", r.moneda())
                .setParameter("total", r.total())
                .setParameter("digest", r.digestValue())
                .setParameter("xml", r.xmlRef())
                .setParameter("pdf", r.pdfRef())
                .setParameter("cdr", r.cdrRef())
                .execute();
        em.flush();
    }

    @Override
    public List<UUID> getReceptionIds(UUID companyId, LocalDate from, LocalDate to, int limit, int offset) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("""
                        SELECT id FROM xhandler.reception WHERE company_id = :c
                          AND (CAST(:from AS date) IS NULL OR fecha_emision >= :from) AND (CAST(:to AS date) IS NULL OR fecha_emision <= :to)
                        ORDER BY fecha_emision DESC NULLS LAST, created_at DESC LIMIT :l OFFSET :o""")
                .setParameter("c", companyId).setParameter("from", from).setParameter("to", to).setParameter("l", limit).setParameter("o", offset)
                .getResultList();
        return ids;
    }

    @Override
    public boolean existsReception(UUID id, UUID companyId) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.reception WHERE id = :id AND company_id = :c")
                .setParameter("id", id).setParameter("c", companyId).getResultList().isEmpty();
    }

    @Override
    public Optional<ReceptionRow> getReception(UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, supplier_ruc, tipo_comprobante, serie || '-' || numero, fecha_emision, moneda, total, validity_state,
                               validity_checked_at, pdf_ref IS NOT NULL, cdr_ref IS NOT NULL, created_at
                        FROM xhandler.reception WHERE id = :id""").setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = rows.get(0);
        return Optional.of(new ReceptionRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], date(r[4]), (String) r[5], (BigDecimal) r[6],
                (String) r[7], Times.toInstant(r[8]), (Boolean) r[9], (Boolean) r[10], Times.toInstant(r[11])));
    }

    @Override
    public Optional<String> getFileRef(UUID id, UUID companyId, String kind) {
        String column = switch (kind) {
            case "xml" -> "xml_ref";
            case "pdf" -> "pdf_ref";
            case "cdr" -> "cdr_ref";
            default -> throw new IllegalArgumentException("unknown reception file: " + kind);
        };
        Object ref = em.createNativeQuery("SELECT " + column + " FROM xhandler.reception WHERE id = :id AND company_id = :c")
                .setParameter("id", id).setParameter("c", companyId).getResultStream().findFirst().orElse(null);
        return ref == null ? Optional.empty() : Optional.of(ref.toString());
    }

    @Override
    public List<DueReception> getReceptionsToCheck() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, reception_id FROM xhandler.receptions_to_check(200)").getResultList();
        return rows.stream().map(r -> new DueReception((UUID) r[0], (UUID) r[1])).toList();
    }

    @Override
    public ValidityRow getValidityRow(UUID id) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT company_id, supplier_ruc, tipo_comprobante, serie, numero, fecha_emision, total FROM xhandler.reception WHERE id = :id""")
                .setParameter("id", id).getSingleResult();
        return new ValidityRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], r[4] == null ? null : ((Number) r[4]).longValue(),
                date(r[5]), (BigDecimal) r[6]);
    }

    @Override
    public void stampValidity(UUID id, String state) {
        em.createNativeStatement("""
                        UPDATE xhandler.reception SET validity_state = coalesce(:s, validity_state), validity_checked_at = now(), updated_at = now()
                        WHERE id = :id""")
                .setParameter("s", state).setParameter("id", id).execute();
    }

    private static LocalDate date(Object value) {
        return value == null ? null : Times.toLocalDate(value);
    }

    @Override
    public List<ReceptionRef> getReceptionsWithoutAmounts() {
        @SuppressWarnings("unchecked")
        List<Object[]> receptions = em.createNativeQuery("SELECT tenant_id, reception_id FROM xhandler.receptions_without_amounts(200)")
                .getResultList();
        return receptions.stream().map(r -> new ReceptionRef((UUID) r[0], (UUID) r[1])).toList();
    }

    @Override
    public Optional<String> getReceptionXmlRef(UUID receptionId) {
        List<?> ref = em.createNativeQuery("SELECT xml_ref FROM xhandler.reception WHERE id = :id")
                .setParameter("id", receptionId).getResultList();
        return ref.isEmpty() ? Optional.empty() : Optional.ofNullable((String) ref.get(0));
    }

    @Override
    public void setReceptionAmounts(UUID receptionId, String amountsJson) {
        em.createNativeStatement("UPDATE xhandler.reception SET amounts = cast(:a AS jsonb) WHERE id = :id AND amounts IS NULL")
                .setParameter("a", amountsJson).setParameter("id", receptionId).execute();
    }

    @Override
    public void markReceptionAmountsUnreadable(UUID receptionId) {
        em.createNativeStatement(
                        "UPDATE xhandler.reception SET amounts = cast('{\"_error\": \"unreadable\"}' AS jsonb) WHERE id = :id AND amounts IS NULL")
                .setParameter("id", receptionId).execute();
    }
}
