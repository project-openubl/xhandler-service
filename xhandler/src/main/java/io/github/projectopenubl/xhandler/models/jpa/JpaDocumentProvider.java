package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.utils.Times;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDate;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.DocumentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaDocumentProvider implements DocumentProvider {

    /** Lines a resumen diario may take: SUNAT can't have them and no attempt is in flight (§9.2.4). */
    static final String SUMMARY_CANDIDATES = """
            companyId = ?1 and sunatEnv = ?2 and fechaEmision = ?3 and summaryDocumentId is null
            and (tipoComprobante = '03' or (tipoComprobante in ('07', '08') and serie like 'B%'))
            and (fechaEmision < ?4 or codeClass = 'route_change')
            and (status = 'pending_send' or (status = 'exception' and codeClass = 'route_change'))
            and (codeClass is null or codeClass <> 'summary_failed')
            and (holdReason is null or holdReason <> 'operator')
            order by id""";

    private final DocumentRepository repository;

    public JpaDocumentProvider(DocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentModel newDocument(UUID id) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(id);
        return new DocumentAdapter(entity);
    }

    @Override
    public void persist(DocumentModel document) {
        repository.persist(DocumentAdapter.toEntity(document));
    }

    @Override
    public void delete(DocumentModel document) {
        repository.delete(DocumentAdapter.toEntity(document));
    }

    @Override
    public DocumentModel addDocument(UUID id, Consumer<DocumentModel> init) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(id);
        DocumentModel model = new DocumentAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<DocumentModel> getDocument(UUID id) {
        return repository.findByIdOptional(id).map(DocumentAdapter::new);
    }

    @Override
    public Optional<DocumentModel> getDocumentForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(DocumentAdapter::new);
    }

    @Override
    public Optional<DocumentModel> getDocumentForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(DocumentAdapter::new);
    }

    @Override
    public void refresh(DocumentModel document) {
        repository.getEntityManager().refresh(DocumentAdapter.toEntity(document));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public List<UUID> getReversalIds(UUID companyId, String sunatEnv, String serie) {
        return repository.list("companyId = ?1 and sunatEnv = ?2 and tipoComprobante = 'RR' and serie = ?3 "
                + "and status not in ('rejected')", companyId, sunatEnv, serie).stream().map(DocumentEntity::getId).toList();
    }

    @Override
    public List<DocumentModel> getReversedDocuments(List<UUID> reversalIds, String tipoComprobante, LocalDate fechaEmision) {
        return wrap(repository.list("voidedByDocumentId in ?1 and tipoComprobante = ?2 and fechaEmision = ?3 "
                + "and status in ('voided', 'voiding') order by id", reversalIds, tipoComprobante, fechaEmision));
    }

    @Override
    public Optional<DocumentModel> getIssuedDocument(UUID companyId, String sunatEnv, String tipoComprobante, String serie, long numero) {
        return repository.find(
                        "companyId = ?1 and sunatEnv = ?2 and tipoComprobante = ?3 and serie = ?4 and numero = ?5",
                        companyId, sunatEnv, tipoComprobante, serie, numero)
                .firstResultOptional().map(DocumentAdapter::new);
    }

    @Override
    public List<DocumentModel> getDocumentsByNumber(UUID companyId, String sunatEnv, String tipoComprobante, String serie, long numero) {
        return wrap(repository.list("companyId = ?1 and sunatEnv = ?2 and tipoComprobante = ?3 and serie = ?4 and numero = ?5"
                + " order by createdAt desc", companyId, sunatEnv, tipoComprobante, serie, numero));
    }

    @Override
    public List<DocumentModel> getDocumentsToRetryForUpdate(Set<String> statuses, UUID companyId, LocalDate from, LocalDate to) {
        StringBuilder query = new StringBuilder("status in :statuses and summaryDocumentId is null");
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", statuses);
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
        return wrap(repository.find(query.toString(), params).withLock(LockModeType.PESSIMISTIC_WRITE).list());
    }

    @Override
    public Optional<DocumentModel> getDocumentByLabel(UUID companyId, String label, String value) {
        return repository.find("companyId = ?1 and function('jsonb_extract_path_text', labels, ?2) = ?3", companyId, label, value)
                .firstResultOptional().map(DocumentAdapter::new);
    }

    @Override
    public List<DocumentModel> getSummaryLines(UUID summaryId) {
        return wrap(repository.list("summaryDocumentId = ?1 order by id", summaryId));
    }

    @Override
    public List<DocumentModel> getSummaryLinesForUpdate(UUID summaryId) {
        return wrap(repository.find("summaryDocumentId = ?1 order by id", summaryId).withLock(LockModeType.PESSIMISTIC_WRITE).list());
    }

    @Override
    public List<DocumentModel> getVoidedBy(UUID voidingId) {
        return wrap(repository.list("voidedByDocumentId = ?1 order by id", voidingId));
    }

    @Override
    public List<DocumentModel> getVoidingOriginalsForUpdate(UUID voidingId) {
        return wrap(repository.find("voidedByDocumentId = ?1 and status = 'voiding' order by id", voidingId)
                .withLock(LockModeType.PESSIMISTIC_WRITE).list());
    }

    @Override
    public List<DocumentModel> getSummaryCandidatesForUpdate(UUID companyId, String sunatEnv, LocalDate fecha, LocalDate cutoff, int max,
            boolean skipLocked) {
        var query = repository.find(SUMMARY_CANDIDATES, companyId, sunatEnv, fecha, cutoff).withLock(LockModeType.PESSIMISTIC_WRITE);
        if (skipLocked) {
            query = query.withHint("jakarta.persistence.lock.timeout", -2);
        }
        return wrap(query.page(0, max).list());
    }

    @Override
    public int revokeAccessLinks(UUID documentId) {
        return repository.getEntityManager()
                .createNativeStatement("UPDATE xhandler.access_token SET expires_at = now() WHERE document_id = :id AND expires_at > now()")
                .setParameter("id", documentId)
                .execute();
    }

    @Override
    public List<DocumentModel> searchDocuments(DocumentSearch search, int offset, int limit) {
        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();
        equal(query, params, "companyId", search.companyId());
        equal(query, params, "status", search.status());
        equal(query, params, "tipoComprobante", search.tipoComprobante());
        equal(query, params, "serie", search.serie());
        equal(query, params, "holdReason", search.holdReason());
        if (search.customerNumero() != null) {
            query.append(" and customerNumero = :customerNumero");
            params.put("customerNumero", search.customerNumero());
        }
        if (search.customerNombrePrefix() != null) {
            // A prefix match, served by the document_customer_nombre index
            query.append(" and lower(customerNombre) like :customerNombre escape '\\'");
            params.put("customerNombre", search.customerNombrePrefix());
        }
        if (search.from() != null) {
            query.append(" and fechaEmision >= :from");
            params.put("from", search.from());
        }
        if (search.to() != null) {
            query.append(" and fechaEmision <= :to");
            params.put("to", search.to());
        }
        int i = 0;
        for (Map.Entry<String, String> label : search.labels().entrySet()) {
            query.append(" and function('jsonb_extract_path_text', labels, :labelKey").append(i).append(") = :labelValue").append(i);
            params.put("labelKey" + i, label.getKey());
            params.put("labelValue" + i, label.getValue());
            i++;
        }
        query.append(" order by createdAt desc, id desc");
        return wrap(repository.find(query.toString(), params).range(offset, offset + limit - 1).list());
    }


    @Override
    public int markVoiding(UUID documentId, UUID voidingId, String motivo, java.time.Instant reversalCommunicatedAt) {
        return repository.getEntityManager().createNativeStatement("""
                        UPDATE xhandler.document SET status_before_voiding = status, status = 'voiding', voided_by_document_id = :voiding,
                            void_reason = :motivo, reversal_communicated_at = :reversal, updated_at = now()
                        WHERE id = :id AND status IN ('accepted', 'accepted_with_observations') AND delivered_at IS NULL
                          AND voided_by_document_id IS NULL""")
                .setParameter("voiding", voidingId)
                .setParameter("motivo", motivo)
                .setParameter("reversal", reversalCommunicatedAt)
                .setParameter("id", documentId)
                .execute();
    }

    @Override
    public void addPortalDelivery(UUID id, UUID tenantId, UUID documentId) {
        repository.getEntityManager().createNativeStatement("""
                        INSERT INTO xhandler.delivery (id, tenant_id, document_id, channel, result) VALUES (:id, :tenant, :document, 'portal', 'downloaded')""")
                .setParameter("id", id)
                .setParameter("tenant", tenantId)
                .setParameter("document", documentId)
                .execute();
    }

    @Override
    public void addSunatCall(String endpoint, boolean healthy, java.time.Instant startedAt) {
        repository.getEntityManager()
                .createNativeStatement("INSERT INTO xhandler.sunat_call (endpoint, healthy, started_at) VALUES (:endpoint, :healthy, :started)")
                .setParameter("endpoint", endpoint)
                .setParameter("healthy", healthy)
                .setParameter("started", startedAt)
                .execute();
    }

    @Override
    public List<SummaryGroup> getBoletasNeedingSummary(LocalDate today) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = repository.getEntityManager().createNativeQuery(
                        "SELECT tenant_id, company_id, sunat_env, fecha_emision FROM xhandler.boletas_needing_summary(:today)")
                .setParameter("today", today)
                .getResultList();
        return rows.stream().map(r -> new SummaryGroup((UUID) r[0], (UUID) r[1], (String) r[2], Times.toLocalDate(r[3]))).toList();
    }


    @Override
    public List<TenantRow> getDocumentsAtRisk(java.time.Duration horizon) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = repository.getEntityManager().createNativeQuery(
                        "SELECT tenant_id, document_id FROM xhandler.documents_at_risk(make_interval(secs => :secs))")
                .setParameter("secs", (double) horizon.toSeconds())
                .getResultList();
        return rows.stream().map(r -> new TenantRow((UUID) r[0], (UUID) r[1])).toList();
    }

    @Override
    public List<TenantRow> getDocumentsWithoutAmounts() {
        @SuppressWarnings("unchecked")
        List<Object[]> docs = repository.getEntityManager()
                .createNativeQuery("SELECT tenant_id, document_id FROM xhandler.documents_without_amounts(200)").getResultList();
        return docs.stream().map(r -> new TenantRow((UUID) r[0], (UUID) r[1])).toList();
    }



    @Override
    public Optional<String> getSignedXmlStorageKey(UUID documentId) {
        List<?> ref = repository.getEntityManager()
                .createNativeQuery("SELECT storage_key FROM xhandler.document_file WHERE document_id = :id AND kind = 'xml_signed'")
                .setParameter("id", documentId).getResultList();
        return ref.isEmpty() ? Optional.empty() : Optional.ofNullable((String) ref.get(0));
    }


    @Override
    public void setDocumentAmounts(UUID documentId, String amountsJson, String paymentTerms) {
        repository.getEntityManager().createNativeStatement("""
                        UPDATE xhandler.document SET amounts = cast(:a AS jsonb),
                            payment_terms = CASE WHEN tipo_comprobante = '01' AND payment_terms IS NULL THEN :terms ELSE payment_terms END
                        WHERE id = :id AND amounts IS NULL""")
                .setParameter("a", amountsJson).setParameter("terms", paymentTerms)
                .setParameter("id", documentId).execute();
    }



    private static List<DocumentModel> wrap(List<DocumentEntity> entities) {
        return entities.stream().map(e -> (DocumentModel) new DocumentAdapter(e)).toList();
    }

    private static void equal(StringBuilder query, Map<String, Object> params, String field, Object value) {
        if (value != null) {
            query.append(" and ").append(field).append(" = :").append(field);
            params.put(field, value);
        }
    }

    @Override
    public void markAmountsUnreadable(UUID documentId) {
        repository.getEntityManager().createNativeStatement(
                        "UPDATE xhandler.document SET amounts = cast('{\"_error\": \"unreadable\"}' AS jsonb) WHERE id = :id AND amounts IS NULL")
                .setParameter("id", documentId).execute();
    }
}
