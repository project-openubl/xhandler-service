package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.models.DocumentFileProvider;
import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.mail.EmailSender;
import io.github.projectopenubl.xhandler.mail.HtmlSanitizer;
import io.github.projectopenubl.xhandler.resources.EmailSuppressions;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.managers.AccessLinks;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.models.DocumentEventModel;
import io.github.projectopenubl.xhandler.models.DocumentFileModel;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.pdf.DocumentPdf;
import io.github.projectopenubl.xhandler.managers.CredentialService;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emails a document to its buyer (DESIGN.md §11.6): PDF, XML and optionally the CDR, with the
 * buyer's capability link. Sent through the tenant's SMTP when it has one, else the platform
 * sender. Suppressed addresses (hard bounces, complaints) are skipped. Every delivery is logged,
 * and the first successful one marks the document delivered.
 */
@ApplicationScoped
public class DocumentEmailJob implements JobHandler {

    private final DocumentProvider documentProvider;

    private final DocumentFileProvider documentFileProvider;

    private final DocumentEventProvider documentEventProvider;

    private final CompanyProvider companyProvider;

    public static final String NAME = "document.email";

    private final TenantAccess access;
    private final DocumentPdf pdf;
    private final FileStore files;
    private final AccessLinks links;
    private final CredentialService credentials;
    private final EmailSender sender;
    private final EntityManager em;
    private final io.github.projectopenubl.xhandler.clients.Egress egress;
    private final io.github.projectopenubl.xhandler.managers.Entitlements entitlements;

    public DocumentEmailJob(TenantAccess access, DocumentPdf pdf, FileStore files, AccessLinks links, CredentialService credentials,
            EmailSender sender, EntityManager em, io.github.projectopenubl.xhandler.clients.Egress egress,
            io.github.projectopenubl.xhandler.managers.Entitlements entitlements, CompanyProvider companyProvider, DocumentEventProvider documentEventProvider, DocumentFileProvider documentFileProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.documentFileProvider = documentFileProvider;
        this.documentEventProvider = documentEventProvider;
        this.companyProvider = companyProvider;
        this.entitlements = entitlements;
        this.egress = egress;
        this.access = access;
        this.pdf = pdf;
        this.files = files;
        this.links = links;
        this.credentials = credentials;
        this.sender = sender;
        this.em = em;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        UUID documentId = context.uuidArg("document_id");
        List<String> givenTo = strings(context.args().path("to"));
        List<String> cc = strings(context.args().path("cc"));
        boolean toCustomer = context.args().path("to_customer").asBoolean(false);
        boolean includeCdr = context.args().path("include_cdr").asBoolean(false);
        record Prepared(EmailSender.Message message, EmailSender.Smtp smtp, List<String> skipped) {}
        Prepared prepared = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(context.tenantId());
            DocumentModel doc = documentProvider.getDocument(documentId).orElse(null);
            CompanyModel company = companyProvider.getCompany(doc.getCompanyId()).orElse(null);
            List<String> skipped = new ArrayList<>();
            List<String> recipients = new ArrayList<>();
            List<String> to = new ArrayList<>(givenTo);
            if (toCustomer) {
                customerEmail(doc).ifPresent(to::add);
            }
            List<String> ccKept = cc.stream().filter(a -> !EmailSuppressions.suppressed(em, a)).toList();
            for (String address : to) {
                if (EmailSuppressions.suppressed(em, address)) {
                    skipped.add(address);
                } else {
                    recipients.add(address);
                }
            }
            if (recipients.isEmpty()) {
                return new Prepared(null, null, skipped);
            }
            String link = links.url(links.create(doc));
            List<EmailSender.Attachment> attachments = new ArrayList<>();
            String base = doc.getTipoComprobante() + "-" + doc.serieNumero();
            attachments.add(new EmailSender.Attachment(base + ".pdf", "application/pdf", pdf.render(doc.getId(), link)));
            documentFileProvider.getDocumentFile(doc.getId(), "xml_signed").ifPresent(f ->
                    attachments.add(new EmailSender.Attachment(base + ".xml", "application/xml", files.get(f.getStorageKey()))));
            if (includeCdr) {
                documentFileProvider.getDocumentFile(doc.getId(), "cdr_zip").ifPresent(f ->
                        attachments.add(new EmailSender.Attachment("R-" + base + ".zip", "application/zip", files.get(f.getStorageKey()))));
            }
            String subject = subject(company.getEmailSubject(), doc, company);
            String body = HtmlSanitizer.sanitize(render(company.getEmailBody(), DEFAULT_BODY, doc, company, link));
            // The plan may have changed since the SMTP server was saved (downgrade, stale entitlement)
            EmailSender.Smtp smtp = entitlements.usage(doc.getTenantId()).plan().customSmtp()
                    ? credentials.read(scope, company, "smtp", "email:" + doc.getId()).map(EmailSender::smtp).orElse(null)
                    : null;
            return new Prepared(new EmailSender.Message(sender(company), company.getEmail(), recipients, ccKept, subject, body, attachments),
                    smtp, skipped);
        });
        if (prepared.message() == null) {
            return Map.of("sent", 0, "skipped", prepared.skipped());
        }
        String result = "sent";
        String detail = null;
        try {
            if (prepared.smtp() != null) {
                // Re-checked at send time: the host may resolve elsewhere since it was saved (§16)
                if (!java.util.Set.of(25, 465, 587, 2525).contains(prepared.smtp().port())) {
                    throw new SmtpRefused("Puerto SMTP no permitido.");
                }
                try {
                    egress.checkHost(prepared.smtp().host());
                } catch (io.github.projectopenubl.xhandler.resources.exceptions.ApiException e) {
                    throw new SmtpRefused("El servidor SMTP apunta a una dirección no permitida.");
                }
                sender.sendTenantSmtp(prepared.smtp(), prepared.message());
            } else {
                sender.sendPlatform(prepared.message());
            }
        } catch (RuntimeException e) {
            // Logged here; the tenant sees a generic reason (no oracle on its SMTP target)
            org.jboss.logging.Logger.getLogger(DocumentEmailJob.class).warnf("email for document %s failed: %s", documentId, e.getMessage());
            result = "failed";
            detail = e instanceof SmtpRefused refused ? refused.getMessage() : "No se pudo enviar el correo.";
        }
        String finalResult = result;
        String finalDetail = detail;
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(context.tenantId());
            DocumentModel doc = documentProvider.getDocumentForUpdate(documentId).orElse(null);
            for (String recipient : prepared.message().to()) {
                em.createNativeQuery("""
                                INSERT INTO xhandler.delivery (id, tenant_id, document_id, channel, recipient, result, detail)
                                VALUES (:id, :tenant, :document, 'email', :recipient, :result, :detail)""")
                        .setParameter("id", Ids.newId())
                        .setParameter("tenant", doc.getTenantId())
                        .setParameter("document", doc.getId())
                        .setParameter("recipient", recipient)
                        .setParameter("result", finalResult)
                        .setParameter("detail", finalDetail == null ? null : finalDetail.substring(0, Math.min(500, finalDetail.length())))
                        .executeUpdate();
            }
            if ("sent".equals(finalResult) && doc.getDeliveredAt() == null) {
                doc.setDeliveredAt(Instant.now());
                doc.setDeliveredSource("service");
                doc.setUpdatedAt(doc.getDeliveredAt());
                documentEventProvider.addDocumentEvent(doc, null, "delivered (email)", "platform:worker", context.correlationId());
            }
        });
        if (!"sent".equals(result)) {
            throw new IllegalStateException("email not sent: " + detail);
        }
        return Map.of("sent", prepared.message().to().size(), "skipped", prepared.skipped());
    }

    public static final String DEFAULT_BODY = """
            <p>Estimado cliente:</p>
            <p>{razon_social} le envía su {tipo_nombre} <b>{serie_numero}</b> del {fecha}, por {moneda} {total}.</p>
            <p>Adjuntamos la representación impresa (PDF) y el archivo XML. También puede consultarlo en:<br/>
            <a href="{link}">{link}</a></p>
            <p>Atentamente,<br/>{razon_social}</p>""";

    static final Map<String, String> TIPOS = Map.of("01", "factura electrónica", "03", "boleta de venta electrónica",
            "07", "nota de crédito electrónica", "08", "nota de débito electrónica", "09", "guía de remisión electrónica",
            "31", "guía de remisión electrónica", "20", "comprobante de retención", "40", "comprobante de percepción");

    /** {placeholders} in the company's template; values are HTML-escaped. */
    static String render(String template, String fallback, DocumentModel doc, CompanyModel company, String link) {
        String text = template == null || template.isBlank() ? fallback : template;
        Map<String, String> values = Map.of(
                "{tipo}", doc.getTipoComprobante(), "{tipo_nombre}", TIPOS.getOrDefault(doc.getTipoComprobante(), "comprobante electrónico"),
                "{serie_numero}", doc.serieNumero(), "{razon_social}", company.getRazonSocial(), "{fecha}", doc.getFechaEmision().toString(),
                "{moneda}", doc.getMoneda() == null ? "" : doc.getMoneda(), "{total}", doc.getTotal() == null ? "" : doc.getTotal().toPlainString(),
                "{link}", link);
        for (Map.Entry<String, String> e : values.entrySet()) {
            text = text.replace(e.getKey(), escape(e.getValue()));
        }
        return text;
    }

    /** The subject: plain text, one line (no header injection). */
    static String subject(String template, DocumentModel doc, CompanyModel company) {
        String text = template == null || template.isBlank() ? "{tipo} {serie_numero} - {razon_social}" : template;
        Map<String, String> values = Map.of("{tipo}", TIPOS.getOrDefault(doc.getTipoComprobante(), doc.getTipoComprobante()),
                "{serie_numero}", doc.serieNumero(), "{razon_social}", company.getRazonSocial(), "{fecha}", doc.getFechaEmision().toString());
        for (Map.Entry<String, String> e : values.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }
        return text.replaceAll("[\\r\\n\\t]+", " ").strip();
    }

    /** The buyer's email as the client gave it ({@code cliente.contacto.email} in the input). */
    private java.util.Optional<String> customerEmail(DocumentModel doc) {
        return documentFileProvider.getDocumentFile(doc.getId(), "input").map(f -> {
            try {
                JsonNode email = new com.fasterxml.jackson.databind.ObjectMapper().readTree(files.get(f.getStorageKey()))
                        .path("spec").path("cliente").path("contacto").path("email");
                return email.isMissingNode() || email.isNull() ? null : email.asText();
            } catch (java.io.IOException e) {
                return null;
            }
        }).filter(a -> a.matches("[^@\\s<>\"]+@[^@\\s<>\"]+\\.[A-Za-z]{2,}"));
    }

    /** As the company's own address when the tenant verified its domain, else the platform's sender. */
    private String sender(CompanyModel company) {
        if (company.getEmail() == null || !company.getEmail().contains("@")) {
            return null;
        }
        String domain = company.getEmail().substring(company.getEmail().indexOf('@') + 1).toLowerCase(java.util.Locale.ROOT);
        boolean verified = !em.createNativeQuery("SELECT 1 FROM xhandler.sender_domain WHERE domain = :d AND verified_at IS NOT NULL")
                .setParameter("d", domain).getResultList().isEmpty();
        return verified ? company.getRazonSocial().replaceAll("[\\r\\n\"<>]", "") + " <" + company.getEmail() + ">" : null;
    }

    /** An SMTP target refused at send time (the message is safe to show the tenant). */
    static class SmtpRefused extends RuntimeException {
        SmtpRefused(String message) {
            super(message);
        }
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asText()));
        return values;
    }
}
