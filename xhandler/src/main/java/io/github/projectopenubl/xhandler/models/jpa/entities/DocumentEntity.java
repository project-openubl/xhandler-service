package io.github.projectopenubl.xhandler.models.jpa.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An issued document (DESIGN.md §7, §9). Never deleted; once issued only its status columns
 * change. The table is partitioned by {@code issueYear}; ids are unique by construction (UUIDv7).
 */
@Entity
@Table(name = "document")
public class DocumentEntity extends PanacheEntityBase {

    /** No definitive answer yet: the reconciler keeps attempting these (§24.2). */


    @Id
    private UUID id;

    private short issueYear;
    private UUID tenantId;
    private UUID companyId;
    /** beta, production or fake */
    private String sunatEnv;
    private String origin = "api";
    /** xbuilder kind: Invoice, CreditNote, DebitNote, DespatchAdvice, Perception, Retention, SummaryDocuments, VoidedDocuments */
    private String kind;
    /** Catalog 01 code, or RC/RA/RR for summaries */
    private String tipoComprobante;
    private String serie;
    private long numero;
    private LocalDate fechaEmision;
    private Instant deadlineAt;
    private String customerTipo;
    private String customerNumero;
    private String customerNombre;
    private String moneda;
    private BigDecimal tipoCambio;
    private BigDecimal total;
    private String status;
    private String statusBeforeVoiding;
    private Integer sunatCode;
    private String sunatDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> sunatNotes;

    private String codeClass;
    private String ticket;
    private String digestValue;
    /** Where it is sent: {@code <env>:<service>} (§24.2) */
    private String endpoint;
    /** SOL credential used to send (credential slots, §10.2); null = the environment's shared user */
    private UUID credentialId;
    private UUID affectedDocumentId;
    private UUID voidedByDocumentId;
    private UUID summaryDocumentId;
    /** null, needs_attention or operator */
    private String holdReason;
    private int attempts;
    private Instant nextAttemptAt;
    private UUID attemptJobId;
    private Instant sendStartedAt;
    private Instant firstSentAt;
    private Instant cdrReceivedAt;
    private Instant reversalCommunicatedAt;
    private String voidReason;
    /** software (the issuer signs and sends) or pse (the platform does, DESIGN.md §1.1) */
    private String emissionMode = "software";
    private String lastOutcome;
    private int sameOutcomeCount;
    private Instant progressAt;
    private Instant deliveredAt;
    private Instant deadlineAlertedAt;
    private String deliveredSource;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> labels = Map.of();

    /** Amounts by tax from the signed XML (registers, §11.9): gravada, igv, exonerada… */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> amounts;

    /** contado / credito (facturas negociables, §11.8); null when unknown. */
    private String paymentTerms;

    private String deviceId;
    private Instant createdAt;
    private Instant updatedAt;

    /** The engine's amounts as stored on documents and receptions (absent taxes left out). */

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public short getIssueYear() {
        return issueYear;
    }

    public void setIssueYear(short issueYear) {
        this.issueYear = issueYear;
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

    public String getSunatEnv() {
        return sunatEnv;
    }

    public void setSunatEnv(String sunatEnv) {
        this.sunatEnv = sunatEnv;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTipoComprobante() {
        return tipoComprobante;
    }

    public void setTipoComprobante(String tipoComprobante) {
        this.tipoComprobante = tipoComprobante;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public long getNumero() {
        return numero;
    }

    public void setNumero(long numero) {
        this.numero = numero;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDate fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(Instant deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public String getCustomerTipo() {
        return customerTipo;
    }

    public void setCustomerTipo(String customerTipo) {
        this.customerTipo = customerTipo;
    }

    public String getCustomerNumero() {
        return customerNumero;
    }

    public void setCustomerNumero(String customerNumero) {
        this.customerNumero = customerNumero;
    }

    public String getCustomerNombre() {
        return customerNombre;
    }

    public void setCustomerNombre(String customerNombre) {
        this.customerNombre = customerNombre;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getTipoCambio() {
        return tipoCambio;
    }

    public void setTipoCambio(BigDecimal tipoCambio) {
        this.tipoCambio = tipoCambio;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusBeforeVoiding() {
        return statusBeforeVoiding;
    }

    public void setStatusBeforeVoiding(String statusBeforeVoiding) {
        this.statusBeforeVoiding = statusBeforeVoiding;
    }

    public Integer getSunatCode() {
        return sunatCode;
    }

    public void setSunatCode(Integer sunatCode) {
        this.sunatCode = sunatCode;
    }

    public String getSunatDescription() {
        return sunatDescription;
    }

    public void setSunatDescription(String sunatDescription) {
        this.sunatDescription = sunatDescription;
    }

    public List<String> getSunatNotes() {
        return sunatNotes;
    }

    public void setSunatNotes(List<String> sunatNotes) {
        this.sunatNotes = sunatNotes;
    }

    public String getCodeClass() {
        return codeClass;
    }

    public void setCodeClass(String codeClass) {
        this.codeClass = codeClass;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public String getDigestValue() {
        return digestValue;
    }

    public void setDigestValue(String digestValue) {
        this.digestValue = digestValue;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public UUID getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(UUID credentialId) {
        this.credentialId = credentialId;
    }

    public UUID getAffectedDocumentId() {
        return affectedDocumentId;
    }

    public void setAffectedDocumentId(UUID affectedDocumentId) {
        this.affectedDocumentId = affectedDocumentId;
    }

    public UUID getVoidedByDocumentId() {
        return voidedByDocumentId;
    }

    public void setVoidedByDocumentId(UUID voidedByDocumentId) {
        this.voidedByDocumentId = voidedByDocumentId;
    }

    public UUID getSummaryDocumentId() {
        return summaryDocumentId;
    }

    public void setSummaryDocumentId(UUID summaryDocumentId) {
        this.summaryDocumentId = summaryDocumentId;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public void setHoldReason(String holdReason) {
        this.holdReason = holdReason;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public UUID getAttemptJobId() {
        return attemptJobId;
    }

    public void setAttemptJobId(UUID attemptJobId) {
        this.attemptJobId = attemptJobId;
    }

    public Instant getSendStartedAt() {
        return sendStartedAt;
    }

    public void setSendStartedAt(Instant sendStartedAt) {
        this.sendStartedAt = sendStartedAt;
    }

    public Instant getFirstSentAt() {
        return firstSentAt;
    }

    public void setFirstSentAt(Instant firstSentAt) {
        this.firstSentAt = firstSentAt;
    }

    public Instant getCdrReceivedAt() {
        return cdrReceivedAt;
    }

    public void setCdrReceivedAt(Instant cdrReceivedAt) {
        this.cdrReceivedAt = cdrReceivedAt;
    }

    public Instant getReversalCommunicatedAt() {
        return reversalCommunicatedAt;
    }

    public void setReversalCommunicatedAt(Instant reversalCommunicatedAt) {
        this.reversalCommunicatedAt = reversalCommunicatedAt;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public String getEmissionMode() {
        return emissionMode;
    }

    public void setEmissionMode(String emissionMode) {
        this.emissionMode = emissionMode;
    }

    public String getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(String lastOutcome) {
        this.lastOutcome = lastOutcome;
    }

    public int getSameOutcomeCount() {
        return sameOutcomeCount;
    }

    public void setSameOutcomeCount(int sameOutcomeCount) {
        this.sameOutcomeCount = sameOutcomeCount;
    }

    public Instant getProgressAt() {
        return progressAt;
    }

    public void setProgressAt(Instant progressAt) {
        this.progressAt = progressAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getDeadlineAlertedAt() {
        return deadlineAlertedAt;
    }

    public void setDeadlineAlertedAt(Instant deadlineAlertedAt) {
        this.deadlineAlertedAt = deadlineAlertedAt;
    }

    public String getDeliveredSource() {
        return deliveredSource;
    }

    public void setDeliveredSource(String deliveredSource) {
        this.deliveredSource = deliveredSource;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public Map<String, String> getAmounts() {
        return amounts;
    }

    public void setAmounts(Map<String, String> amounts) {
        this.amounts = amounts;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
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
