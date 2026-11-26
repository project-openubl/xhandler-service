package io.github.projectopenubl.xhandler.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DocumentModel extends Model {

    java.util.Set<String> RETRYABLE = java.util.Set.of("pending_send", "in_progress", "unknown");
    java.util.Set<String> ACCEPTED = java.util.Set.of("accepted", "accepted_with_observations");

    short getIssueYear();

    void setIssueYear(short issueYear);

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    UUID getCompanyId();

    void setCompanyId(UUID companyId);

    /** beta, production or fake */
    String getSunatEnv();

    void setSunatEnv(String sunatEnv);

    String getOrigin();

    void setOrigin(String origin);

    /** xbuilder kind: Invoice, CreditNote, DebitNote, DespatchAdvice, Perception, Retention, SummaryDocuments, VoidedDocuments */
    String getKind();

    void setKind(String kind);

    /** Catalog 01 code, or RC/RA/RR for summaries */
    String getTipoComprobante();

    void setTipoComprobante(String tipoComprobante);

    String getSerie();

    void setSerie(String serie);

    long getNumero();

    void setNumero(long numero);

    LocalDate getFechaEmision();

    void setFechaEmision(LocalDate fechaEmision);

    Instant getDeadlineAt();

    void setDeadlineAt(Instant deadlineAt);

    String getCustomerTipo();

    void setCustomerTipo(String customerTipo);

    String getCustomerNumero();

    void setCustomerNumero(String customerNumero);

    String getCustomerNombre();

    void setCustomerNombre(String customerNombre);

    String getMoneda();

    void setMoneda(String moneda);

    BigDecimal getTipoCambio();

    void setTipoCambio(BigDecimal tipoCambio);

    BigDecimal getTotal();

    void setTotal(BigDecimal total);

    String getStatus();

    void setStatus(String status);

    String getStatusBeforeVoiding();

    void setStatusBeforeVoiding(String statusBeforeVoiding);

    Integer getSunatCode();

    void setSunatCode(Integer sunatCode);

    String getSunatDescription();

    void setSunatDescription(String sunatDescription);

    List<String> getSunatNotes();

    void setSunatNotes(List<String> sunatNotes);

    String getCodeClass();

    void setCodeClass(String codeClass);

    String getTicket();

    void setTicket(String ticket);

    String getDigestValue();

    void setDigestValue(String digestValue);

    /** Where it is sent: {@code <env>:<service>} or {@code ose:<id>} (§24.2) */
    String getEndpoint();

    void setEndpoint(String endpoint);

    /** SOL credential used to send (credential slots, §10.2); null = the environment's shared user */
    UUID getCredentialId();

    void setCredentialId(UUID credentialId);

    UUID getAffectedDocumentId();

    void setAffectedDocumentId(UUID affectedDocumentId);

    UUID getVoidedByDocumentId();

    void setVoidedByDocumentId(UUID voidedByDocumentId);

    UUID getSummaryDocumentId();

    void setSummaryDocumentId(UUID summaryDocumentId);

    /** null, needs_attention or operator */
    String getHoldReason();

    void setHoldReason(String holdReason);

    int getAttempts();

    void setAttempts(int attempts);

    Instant getNextAttemptAt();

    void setNextAttemptAt(Instant nextAttemptAt);

    UUID getAttemptJobId();

    void setAttemptJobId(UUID attemptJobId);

    Instant getSendStartedAt();

    void setSendStartedAt(Instant sendStartedAt);

    Instant getFirstSentAt();

    void setFirstSentAt(Instant firstSentAt);

    Instant getCdrReceivedAt();

    void setCdrReceivedAt(Instant cdrReceivedAt);

    Instant getReversalCommunicatedAt();

    void setReversalCommunicatedAt(Instant reversalCommunicatedAt);

    String getVoidReason();

    void setVoidReason(String voidReason);

    /** software (the issuer signs and sends) or pse (the platform does, DESIGN.md §1.1) */
    String getEmissionMode();

    void setEmissionMode(String emissionMode);

    String getLastOutcome();

    void setLastOutcome(String lastOutcome);

    int getSameOutcomeCount();

    void setSameOutcomeCount(int sameOutcomeCount);

    Instant getProgressAt();

    void setProgressAt(Instant progressAt);

    Instant getDeliveredAt();

    void setDeliveredAt(Instant deliveredAt);

    Instant getDeadlineAlertedAt();

    void setDeadlineAlertedAt(Instant deadlineAlertedAt);

    String getDeliveredSource();

    void setDeliveredSource(String deliveredSource);

    Map<String, String> getLabels();

    void setLabels(Map<String, String> labels);

    /** Amounts by tax from the signed XML (registers, §11.9): gravada, igv, exonerada… */
    Map<String, String> getAmounts();

    void setAmounts(Map<String, String> amounts);

    /** contado / credito (facturas negociables, §11.8); null when unknown. */
    String getPaymentTerms();

    void setPaymentTerms(String paymentTerms);

    String getDeviceId();

    void setDeviceId(String deviceId);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);

    static Map<String, String> amountsOf(io.github.projectopenubl.xhandler.xml.EngineModels.Amounts a) {
        if (a == null) {
            return null;
        }
        Map<String, String> m = new java.util.LinkedHashMap<>();
        java.util.function.BiConsumer<String, String> put = (k, v) -> {
            if (v != null) {
                m.put(k, v);
            }
        };
        put.accept("gravada", a.gravada());
        put.accept("igv", a.igv());
        put.accept("exonerada", a.exonerada());
        put.accept("inafecta", a.inafecta());
        put.accept("exportacion", a.exportacion());
        put.accept("gratuita", a.gratuita());
        put.accept("ivap_base", a.ivapBase());
        put.accept("ivap", a.ivap());
        put.accept("isc", a.isc());
        put.accept("icbper", a.icbper());
        put.accept("otros", a.otros());
        return m;
    }

    default String serieNumero() {
        return getSerie() + "-" + getNumero();
    }

    default boolean definitive() {
        return !RETRYABLE.contains(getStatus()) && !"draft".equals(getStatus());
    }

    default boolean isSummary() {
        return "RC".equals(getTipoComprobante()) || "RA".equals(getTipoComprobante()) || "RR".equals(getTipoComprobante());
    }

    /** Boletas and their notes (series starting with B) */
    default boolean isBoletaFamily() {
        return "03".equals(getTipoComprobante()) || (("07".equals(getTipoComprobante()) || "08".equals(getTipoComprobante())) && getSerie().startsWith("B"));
    }

    /** Facturas and their notes (series starting with F) */
    default boolean isFacturaFamily() {
        return "01".equals(getTipoComprobante()) || (("07".equals(getTipoComprobante()) || "08".equals(getTipoComprobante())) && getSerie().startsWith("F"));
    }

    default boolean isGuia() {
        return "09".equals(getTipoComprobante()) || "31".equals(getTipoComprobante());
    }
}
