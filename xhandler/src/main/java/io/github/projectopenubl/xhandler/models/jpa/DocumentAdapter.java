package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DocumentAdapter implements DocumentModel, JpaModel<DocumentEntity> {

    private final DocumentEntity entity;

    public DocumentAdapter(DocumentEntity entity) {
        this.entity = entity;
    }

    public static DocumentEntity toEntity(DocumentModel model) {
        return ((DocumentAdapter) model).getEntity();
    }

    @Override
    public DocumentEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.DOCUMENT;
    }

    @Override
    public short getIssueYear() {
        return entity.getIssueYear();
    }

    @Override
    public void setIssueYear(short issueYear) {
        entity.setIssueYear(issueYear);
    }

    @Override
    public UUID getTenantId() {
        return entity.getTenantId();
    }

    @Override
    public void setTenantId(UUID tenantId) {
        entity.setTenantId(tenantId);
    }

    @Override
    public UUID getCompanyId() {
        return entity.getCompanyId();
    }

    @Override
    public void setCompanyId(UUID companyId) {
        entity.setCompanyId(companyId);
    }

    @Override
    public String getSunatEnv() {
        return entity.getSunatEnv();
    }

    @Override
    public void setSunatEnv(String sunatEnv) {
        entity.setSunatEnv(sunatEnv);
    }

    @Override
    public String getOrigin() {
        return entity.getOrigin();
    }

    @Override
    public void setOrigin(String origin) {
        entity.setOrigin(origin);
    }

    @Override
    public String getKind() {
        return entity.getKind();
    }

    @Override
    public void setKind(String kind) {
        entity.setKind(kind);
    }

    @Override
    public String getTipoComprobante() {
        return entity.getTipoComprobante();
    }

    @Override
    public void setTipoComprobante(String tipoComprobante) {
        entity.setTipoComprobante(tipoComprobante);
    }

    @Override
    public String getSerie() {
        return entity.getSerie();
    }

    @Override
    public void setSerie(String serie) {
        entity.setSerie(serie);
    }

    @Override
    public long getNumero() {
        return entity.getNumero();
    }

    @Override
    public void setNumero(long numero) {
        entity.setNumero(numero);
    }

    @Override
    public LocalDate getFechaEmision() {
        return entity.getFechaEmision();
    }

    @Override
    public void setFechaEmision(LocalDate fechaEmision) {
        entity.setFechaEmision(fechaEmision);
    }

    @Override
    public Instant getDeadlineAt() {
        return entity.getDeadlineAt();
    }

    @Override
    public void setDeadlineAt(Instant deadlineAt) {
        entity.setDeadlineAt(deadlineAt);
    }

    @Override
    public String getCustomerTipo() {
        return entity.getCustomerTipo();
    }

    @Override
    public void setCustomerTipo(String customerTipo) {
        entity.setCustomerTipo(customerTipo);
    }

    @Override
    public String getCustomerNumero() {
        return entity.getCustomerNumero();
    }

    @Override
    public void setCustomerNumero(String customerNumero) {
        entity.setCustomerNumero(customerNumero);
    }

    @Override
    public String getCustomerNombre() {
        return entity.getCustomerNombre();
    }

    @Override
    public void setCustomerNombre(String customerNombre) {
        entity.setCustomerNombre(customerNombre);
    }

    @Override
    public String getMoneda() {
        return entity.getMoneda();
    }

    @Override
    public void setMoneda(String moneda) {
        entity.setMoneda(moneda);
    }

    @Override
    public BigDecimal getTipoCambio() {
        return entity.getTipoCambio();
    }

    @Override
    public void setTipoCambio(BigDecimal tipoCambio) {
        entity.setTipoCambio(tipoCambio);
    }

    @Override
    public BigDecimal getTotal() {
        return entity.getTotal();
    }

    @Override
    public void setTotal(BigDecimal total) {
        entity.setTotal(total);
    }

    @Override
    public String getStatus() {
        return entity.getStatus();
    }

    @Override
    public void setStatus(String status) {
        entity.setStatus(status);
    }

    @Override
    public String getStatusBeforeVoiding() {
        return entity.getStatusBeforeVoiding();
    }

    @Override
    public void setStatusBeforeVoiding(String statusBeforeVoiding) {
        entity.setStatusBeforeVoiding(statusBeforeVoiding);
    }

    @Override
    public Integer getSunatCode() {
        return entity.getSunatCode();
    }

    @Override
    public void setSunatCode(Integer sunatCode) {
        entity.setSunatCode(sunatCode);
    }

    @Override
    public String getSunatDescription() {
        return entity.getSunatDescription();
    }

    @Override
    public void setSunatDescription(String sunatDescription) {
        entity.setSunatDescription(sunatDescription);
    }

    @Override
    public List<String> getSunatNotes() {
        return entity.getSunatNotes();
    }

    @Override
    public void setSunatNotes(List<String> sunatNotes) {
        entity.setSunatNotes(sunatNotes);
    }

    @Override
    public String getCodeClass() {
        return entity.getCodeClass();
    }

    @Override
    public void setCodeClass(String codeClass) {
        entity.setCodeClass(codeClass);
    }

    @Override
    public String getTicket() {
        return entity.getTicket();
    }

    @Override
    public void setTicket(String ticket) {
        entity.setTicket(ticket);
    }

    @Override
    public String getDigestValue() {
        return entity.getDigestValue();
    }

    @Override
    public void setDigestValue(String digestValue) {
        entity.setDigestValue(digestValue);
    }

    @Override
    public String getEndpoint() {
        return entity.getEndpoint();
    }

    @Override
    public void setEndpoint(String endpoint) {
        entity.setEndpoint(endpoint);
    }

    @Override
    public UUID getCredentialId() {
        return entity.getCredentialId();
    }

    @Override
    public void setCredentialId(UUID credentialId) {
        entity.setCredentialId(credentialId);
    }

    @Override
    public UUID getAffectedDocumentId() {
        return entity.getAffectedDocumentId();
    }

    @Override
    public void setAffectedDocumentId(UUID affectedDocumentId) {
        entity.setAffectedDocumentId(affectedDocumentId);
    }

    @Override
    public UUID getVoidedByDocumentId() {
        return entity.getVoidedByDocumentId();
    }

    @Override
    public void setVoidedByDocumentId(UUID voidedByDocumentId) {
        entity.setVoidedByDocumentId(voidedByDocumentId);
    }

    @Override
    public UUID getSummaryDocumentId() {
        return entity.getSummaryDocumentId();
    }

    @Override
    public void setSummaryDocumentId(UUID summaryDocumentId) {
        entity.setSummaryDocumentId(summaryDocumentId);
    }

    @Override
    public String getHoldReason() {
        return entity.getHoldReason();
    }

    @Override
    public void setHoldReason(String holdReason) {
        entity.setHoldReason(holdReason);
    }

    @Override
    public int getAttempts() {
        return entity.getAttempts();
    }

    @Override
    public void setAttempts(int attempts) {
        entity.setAttempts(attempts);
    }

    @Override
    public Instant getNextAttemptAt() {
        return entity.getNextAttemptAt();
    }

    @Override
    public void setNextAttemptAt(Instant nextAttemptAt) {
        entity.setNextAttemptAt(nextAttemptAt);
    }

    @Override
    public UUID getAttemptJobId() {
        return entity.getAttemptJobId();
    }

    @Override
    public void setAttemptJobId(UUID attemptJobId) {
        entity.setAttemptJobId(attemptJobId);
    }

    @Override
    public Instant getSendStartedAt() {
        return entity.getSendStartedAt();
    }

    @Override
    public void setSendStartedAt(Instant sendStartedAt) {
        entity.setSendStartedAt(sendStartedAt);
    }

    @Override
    public Instant getFirstSentAt() {
        return entity.getFirstSentAt();
    }

    @Override
    public void setFirstSentAt(Instant firstSentAt) {
        entity.setFirstSentAt(firstSentAt);
    }

    @Override
    public Instant getCdrReceivedAt() {
        return entity.getCdrReceivedAt();
    }

    @Override
    public void setCdrReceivedAt(Instant cdrReceivedAt) {
        entity.setCdrReceivedAt(cdrReceivedAt);
    }

    @Override
    public Instant getReversalCommunicatedAt() {
        return entity.getReversalCommunicatedAt();
    }

    @Override
    public void setReversalCommunicatedAt(Instant reversalCommunicatedAt) {
        entity.setReversalCommunicatedAt(reversalCommunicatedAt);
    }

    @Override
    public String getVoidReason() {
        return entity.getVoidReason();
    }

    @Override
    public void setVoidReason(String voidReason) {
        entity.setVoidReason(voidReason);
    }

    @Override
    public String getEmissionMode() {
        return entity.getEmissionMode();
    }

    @Override
    public void setEmissionMode(String emissionMode) {
        entity.setEmissionMode(emissionMode);
    }

    @Override
    public String getLastOutcome() {
        return entity.getLastOutcome();
    }

    @Override
    public void setLastOutcome(String lastOutcome) {
        entity.setLastOutcome(lastOutcome);
    }

    @Override
    public int getSameOutcomeCount() {
        return entity.getSameOutcomeCount();
    }

    @Override
    public void setSameOutcomeCount(int sameOutcomeCount) {
        entity.setSameOutcomeCount(sameOutcomeCount);
    }

    @Override
    public Instant getProgressAt() {
        return entity.getProgressAt();
    }

    @Override
    public void setProgressAt(Instant progressAt) {
        entity.setProgressAt(progressAt);
    }

    @Override
    public Instant getDeliveredAt() {
        return entity.getDeliveredAt();
    }

    @Override
    public void setDeliveredAt(Instant deliveredAt) {
        entity.setDeliveredAt(deliveredAt);
    }

    @Override
    public Instant getDeadlineAlertedAt() {
        return entity.getDeadlineAlertedAt();
    }

    @Override
    public void setDeadlineAlertedAt(Instant deadlineAlertedAt) {
        entity.setDeadlineAlertedAt(deadlineAlertedAt);
    }

    @Override
    public String getDeliveredSource() {
        return entity.getDeliveredSource();
    }

    @Override
    public void setDeliveredSource(String deliveredSource) {
        entity.setDeliveredSource(deliveredSource);
    }

    @Override
    public Map<String, String> getLabels() {
        return entity.getLabels();
    }

    @Override
    public void setLabels(Map<String, String> labels) {
        entity.setLabels(labels);
    }

    @Override
    public Map<String, String> getAmounts() {
        return entity.getAmounts();
    }

    @Override
    public void setAmounts(Map<String, String> amounts) {
        entity.setAmounts(amounts);
    }

    @Override
    public String getPaymentTerms() {
        return entity.getPaymentTerms();
    }

    @Override
    public void setPaymentTerms(String paymentTerms) {
        entity.setPaymentTerms(paymentTerms);
    }

    @Override
    public String getDeviceId() {
        return entity.getDeviceId();
    }

    @Override
    public void setDeviceId(String deviceId) {
        entity.setDeviceId(deviceId);
    }

    @Override
    public Instant getCreatedAt() {
        return entity.getCreatedAt();
    }

    @Override
    public void setCreatedAt(Instant createdAt) {
        entity.setCreatedAt(createdAt);
    }

    @Override
    public Instant getUpdatedAt() {
        return entity.getUpdatedAt();
    }

    @Override
    public void setUpdatedAt(Instant updatedAt) {
        entity.setUpdatedAt(updatedAt);
    }
}
