package io.github.projectopenubl.xhandler.models.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.DocumentDraftModel;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.models.JobModel;
import io.github.projectopenubl.xhandler.models.OseEndpointModel;
import io.github.projectopenubl.xhandler.models.SeriesModel;
import io.github.projectopenubl.xhandler.models.SigningKeyModel;
import io.github.projectopenubl.xhandler.models.SupportSessionModel;
import io.github.projectopenubl.xhandler.models.TenantMemberModel;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.models.WebhookDeliveryModel;
import io.github.projectopenubl.xhandler.models.WebhookSubscriptionModel;
import io.github.projectopenubl.xhandler.representations.idm.CertificateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CompanyRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.DeliveryRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.DocumentRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.DocumentStateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.DraftRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.JobRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.MemberRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.RouteRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.SeriesRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.SessionRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.TenantRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.WebhookRepresentation;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/** Models as the API returns them. */
@ApplicationScoped
public class ModelToRepresentation {

    private final ObjectMapper json;

    public ModelToRepresentation(ObjectMapper json) {
        this.json = json;
    }

    public CompanyRepresentation toRepresentation(CompanyModel c) {
        return new CompanyRepresentation(c.getId(), c.getRuc(), c.getRazonSocial(), c.getNombreComercial(), c.getSunatEnv(), c.getEmissionMode(),
                c.getRucVerifiedAt(), c.getEmail(), c.isDeliveredOnEmitDefault(), c.isSendingPaused(), c.getDireccion(), c.getPdfFormat(),
                c.isEmailOnAccept(), c.getLogoRef() != null, c.getSubmissionRoute(), c.getLibroReclamacionesUrl(), c.getEmailSubject(),
                c.getEmailBody());
    }

    public SeriesRepresentation toRepresentation(SeriesModel s) {
        return new SeriesRepresentation(s.getId(), s.getSunatEnv(), s.getTipoComprobante(), s.getSerie(), s.getNextNumber(), s.isDefault(),
                s.isActive());
    }

    /** The company's submission route; {@code ose} is its third-party OSE, if any. */
    public RouteRepresentation toRepresentation(CompanyModel company, OseEndpointModel ose) {
        return new RouteRepresentation(company.getSubmissionRoute(), company.isOseObligated(), ose == null ? null : ose.getName(),
                ose == null ? null : ose.getInvoiceUrl(), ose == null ? null : ose.getPerceptionUrl());
    }

    /** The company's certificate; {@code matchesRuc} when it was issued to the company's RUC. */
    public CertificateRepresentation toRepresentation(SigningKeyModel k, CompanyModel company) {
        return new CertificateRepresentation(k.getSubject(), k.getIssuer(), k.getSerial(), k.getRuc(), k.getValidFrom(), k.getValidUntil(),
                company.getRuc().equals(k.getRuc()));
    }

    public TenantRepresentation toRepresentation(TenantModel tenant) {
        return new TenantRepresentation(tenant.getId(), tenant.getName(), tenant.getPlan(), tenant.getStatus(), tenant.getRetentionYears());
    }

    public MemberRepresentation toRepresentation(TenantMemberModel m) {
        return new MemberRepresentation(m.getId(), m.getKind(), m.getSubject(), m.getRole());
    }

    public SessionRepresentation toRepresentation(SupportSessionModel s) {
        return new SessionRepresentation(s.getId(), s.getOperator(), s.getReason(), s.getRequestedAt(), s.getApprovedBy(), s.getExpiresAt(),
                s.getRevokedAt());
    }

    public JobRepresentation toRepresentation(JobModel j) {
        return new JobRepresentation(j.getId(), "xh:job:" + j.getId(), j.getName(), j.getState(), j.getPriorityClass(), j.getCreatedAt(),
                j.getUnblockedAt(), j.getStartedAt(), j.getFinishedAt(), j.getWorker(), j.getAttempt(), List.of(j.getReservedResources()),
                j.getCreatedResources(), j.getResult(), j.getError(), j.getCorrelationId());
    }

    /** {@code secret} is shown once, when the subscription is created; null otherwise. */
    public WebhookRepresentation toRepresentation(WebhookSubscriptionModel s, String secret) {
        return new WebhookRepresentation(s.getId(), s.getUrl(), List.of(s.getEvents()), s.isActive(), s.getCreatedAt(), secret);
    }

    public DeliveryRepresentation toRepresentation(WebhookDeliveryModel d) {
        return new DeliveryRepresentation(d.getId(), d.getEventId(), d.getEventType(), d.getStatus(), d.getAttempts(), d.getNextAttemptAt(),
                d.getLastStatusCode(), d.getLastError(), d.getDeliveredAt(), d.getCreatedAt());
    }

    public DraftRepresentation toRepresentation(DocumentDraftModel d) {
        JsonNode spec = json.valueToTree(d.getSpec());
        JsonNode cliente = spec.path("cliente");
        DocumentRepresentation.Customer customer = cliente.isObject()
                ? new DocumentRepresentation.Customer(text(cliente, "tipo_documento_identidad"), text(cliente, "numero_documento_identidad"),
                        text(cliente, "nombre"))
                : null;
        return new DraftRepresentation(d.getId(), "xh:draft:" + d.getId(), d.getCompanyId(), d.getStatus(), d.getVersion(), d.getKind(),
                d.getTipoComprobante(), d.getSerie(), spec, d.getLabels(), customer, text(spec, "moneda"), d.getDocumentId(), d.getCreatedBy(),
                d.getCreatedAt(), d.getUpdatedAt());
    }

    public DocumentRepresentation toRepresentation(DocumentModel d) {
        return toRepresentation(d, null, null);
    }

    /** With the buyer's access link and the QR text, when the caller has them. */
    public DocumentRepresentation toRepresentation(DocumentModel d, String accessUrl, String qr) {
        return new DocumentRepresentation(d.getId(), "xh:document:" + d.getId(), d.getCompanyId(), d.getSunatEnv(), d.getKind(),
                d.getTipoComprobante(), d.getSerie(), d.getNumero(), d.serieNumero(), d.getFechaEmision(), d.getDeadlineAt(), d.getStatus(),
                d.getHoldReason(), d.getSunatCode(), d.getSunatDescription(), d.getSunatNotes(), d.getCodeClass(), d.getDigestValue(),
                d.getMoneda(), d.getTotal(),
                d.getCustomerNumero() == null ? null : new DocumentRepresentation.Customer(d.getCustomerTipo(), d.getCustomerNumero(),
                        d.getCustomerNombre()),
                d.getLabels(), d.getDeliveredAt(), d.getDeliveredSource(), d.getAttempts(), d.getNextAttemptAt(), d.getFirstSentAt(),
                d.getCdrReceivedAt(), d.getAffectedDocumentId(), d.getSummaryDocumentId(), d.getVoidedByDocumentId(), d.getCreatedAt(),
                accessUrl, qr);
    }

    /** The platform admin's view of a document's processing state. */
    public DocumentStateRepresentation toStateRepresentation(DocumentModel d) {
        return new DocumentStateRepresentation(d.getId(), d.getTenantId(), d.serieNumero(), d.getStatus(), d.getHoldReason(), d.getNextAttemptAt(),
                d.getAttempts());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }
}
