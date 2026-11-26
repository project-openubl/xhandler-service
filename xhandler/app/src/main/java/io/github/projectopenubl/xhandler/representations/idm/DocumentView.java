package io.github.projectopenubl.xhandler.representations.idm;

import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A document as the API shows it. {@code accessUrl} (the buyer's capability link) is only present
 * when the link was just created: the token is never stored in clear.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentView(
        UUID id,
        String prn,
        UUID companyId,
        String sunatEnv,
        String kind,
        String tipoComprobante,
        String serie,
        long numero,
        String serieNumero,
        LocalDate fechaEmision,
        Instant deadlineAt,
        String status,
        String holdReason,
        Integer sunatCode,
        String sunatDescription,
        List<String> sunatNotes,
        String codeClass,
        String digestValue,
        String moneda,
        BigDecimal total,
        Customer customer,
        Map<String, String> labels,
        Instant deliveredAt,
        String deliveredSource,
        int attempts,
        Instant nextAttemptAt,
        Instant firstSentAt,
        Instant cdrReceivedAt,
        UUID affectedDocumentId,
        UUID summaryDocumentId,
        UUID voidedByDocumentId,
        Instant createdAt,
        String accessUrl,
        String qr) {

    public record Customer(String tipo, String numero, String nombre) {}

    public static DocumentView of(DocumentEntity d) {
        return of(d, null, null);
    }

    public static DocumentView of(DocumentEntity d, String accessUrl, String qr) {
        return new DocumentView(d.getId(), "xh:document:" + d.getId(), d.getCompanyId(), d.getSunatEnv(), d.getKind(), d.getTipoComprobante(), d.getSerie(), d.getNumero(),
                d.serieNumero(), d.getFechaEmision(), d.getDeadlineAt(), d.getStatus(), d.getHoldReason(), d.getSunatCode(), d.getSunatDescription(), d.getSunatNotes(),
                d.getCodeClass(), d.getDigestValue(), d.getMoneda(), d.getTotal(),
                d.getCustomerNumero() == null ? null : new Customer(d.getCustomerTipo(), d.getCustomerNumero(), d.getCustomerNombre()), d.getLabels(),
                d.getDeliveredAt(), d.getDeliveredSource(), d.getAttempts(), d.getNextAttemptAt(), d.getFirstSentAt(), d.getCdrReceivedAt(), d.getAffectedDocumentId(),
                d.getSummaryDocumentId(), d.getVoidedByDocumentId(), d.getCreatedAt(), accessUrl, qr);
    }
}
