package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.models.JobQueueProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.DocumentFileProvider;
import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.utils.Deadlines;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.representations.idm.DocumentRepresentation;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.keys.SignatureLog;
import io.github.projectopenubl.xhandler.keys.SigningService;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UBL XML upload (DESIGN.md §5): a document built elsewhere, validated here, signed if unsigned
 * (software or PSE), stored and sent like any other. It keeps its own number. The XML is untrusted
 * input: size-capped here, and the engine refuses DTDs and entities.
 */
@ApplicationScoped
public class UblUploadManager {

    private final ModelToRepresentation modelToRepresentation;

    private final JobQueueProvider jobQueueProvider;

    private final DocumentProvider documentProvider;

    private final DocumentFileProvider documentFileProvider;

    private final DocumentEventProvider documentEventProvider;

    private final CompanyProvider companyProvider;

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final Set<String> TIPOS = Set.of("01", "03", "07", "08", "09", "31", "20", "40");

    private final TenantAccess access;
    private final Engine engine;
    private final SigningService signing;
    private final SignatureLog signatureLog;
    private final FileStore files;
    private final NumberingManager numbering;
    private final RouteManager routes;
    private final AccessLinkManager links;
    private final AuditManager auditor;
    private final ObjectMapper json;
    private final io.github.projectopenubl.xhandler.managers.EmitManager emit;

    public UblUploadManager(TenantAccess access, Engine engine, SigningService signing, SignatureLog signatureLog, FileStore files,
            NumberingManager numbering, RouteManager routes, AccessLinkManager links, AuditManager auditor, ObjectMapper json,
            io.github.projectopenubl.xhandler.managers.EmitManager emit, CompanyProvider companyProvider, DocumentEventProvider documentEventProvider, DocumentFileProvider documentFileProvider, DocumentProvider documentProvider, JobQueueProvider jobQueueProvider, ModelToRepresentation modelToRepresentation) {
        this.modelToRepresentation = modelToRepresentation;
        this.jobQueueProvider = jobQueueProvider;
        this.documentProvider = documentProvider;
        this.documentFileProvider = documentFileProvider;
        this.documentEventProvider = documentEventProvider;
        this.companyProvider = companyProvider;
        this.emit = emit;
        this.access = access;
        this.engine = engine;
        this.signing = signing;
        this.signatureLog = signatureLog;
        this.files = files;
        this.numbering = numbering;
        this.routes = routes;
        this.links = links;
        this.auditor = auditor;
        this.json = json;
    }

    public DocumentRepresentation upload(UUID tenantId, UUID companyId, byte[] xmlBytes, String correlationId) {
        if (xmlBytes == null || xmlBytes.length == 0 || xmlBytes.length > MAX_BYTES) {
            throw ApiException.invalid("invalid_document", "El XML debe tener entre 1 byte y 5 MB.", null);
        }
        // Authorize before the engine parses anything
        QuarkusTransaction.requiringNew().run(() -> access.enter(tenantId, Role.OPERATOR));
        // In the encoding it declares, so a pre-signed ISO-8859-1 document keeps its exact bytes
        String xml = io.github.projectopenubl.xhandler.utils.XmlText.decode(xmlBytes);
        EngineModels.InspectResponse identity = engine.inspect(xml);
        if (identity.tipoComprobante() == null || !TIPOS.contains(identity.tipoComprobante()) || identity.numero() == null
                || identity.serie() == null) {
            throw ApiException.invalid("invalid_document", "El XML no es un comprobante que se pueda emitir por este medio.", null);
        }
        EngineModels.ValidateResponse validation = engine.validate(xml);
        if (!validation.valid()) {
            String detail = validation.errors().stream().map(f -> f.code() + ": " + f.message()).collect(Collectors.joining("; "));
            throw ApiException.invalid("invalid_document", "El documento no cumple las validaciones de SUNAT.", detail);
        }
        UUID documentId = Ids.newId();
        // The same gates as any emit (§5): quota, permits, bounded series waits, company checks, issue date
        return emit.admitted(tenantId, companyId, false, true, () -> QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.OPERATOR);
            emit.boundLockWaits();
            CompanyModel company = companyProvider.getCompanyForShare(companyId).orElse(null);
            if (company == null || company.getDeletedAt() != null) {
                throw ApiException.notFound();
            }
            if (!company.getRuc().equals(identity.ruc())) {
                throw ApiException.invalid("invalid_document", "El RUC del emisor del XML no es el de la empresa.", null);
            }
            String tipo = identity.tipoComprobante();
            boolean guia = "09".equals(tipo) || "31".equals(tipo);
            emit.checkCompany(company, tipo);
            boolean pse = !guia && "pse".equals(company.getEmissionMode());
            if (identity.signed() && pse) {
                // As PSE the platform signs: it doesn't send what someone else signed in its name (§3.2)
                throw ApiException.invalid("invalid_document", "La empresa emite con la plataforma como PSE: envíe el XML sin firmar.", null);
            }
            LocalDate fecha = io.github.projectopenubl.xhandler.managers.EmitManager.checkIssueDate(
                    identity.fechaEmision() == null ? Deadlines.todayInLima() : LocalDate.parse(identity.fechaEmision()));
            RouteManager.Assignment route = routes.assign(company, company.getSunatEnv(), tipo);
            long numero = numbering.claim(tenantId, company.getId(), company.getSunatEnv(), tipo, identity.serie(), identity.numero());
            String signedXml = xml;
            String digest = identity.digestValue();
            if (!identity.signed()) {
                SigningService.Signed signed = signing.sign(company, company.getSunatEnv(), xml, tipo);
                signatureLog.record(tenantId, documentId, identity.serie() + "-" + numero, signed.signingKeyId(), signed.platformKeyId(), signed.digestValue(),
                        scope.actor().auditName());
                signedXml = signed.xml();
                digest = signed.digestValue();
            }
            FileStore.Stored input = files.put(tenantId, xmlBytes);
            FileStore.Stored stored = files.put(tenantId, signedXml.getBytes(StandardCharsets.UTF_8));
            Instant now = Instant.now();
            DocumentModel doc = documentProvider.newDocument(documentId);
            doc.setTenantId(tenantId);
            doc.setCompanyId(company.getId());
            doc.setSunatEnv(company.getSunatEnv());
            doc.setOrigin("adapter");
            doc.setKind(identity.documentType());
            doc.setTipoComprobante(tipo);
            doc.setSerie(identity.serie());
            doc.setNumero(numero);
            doc.setFechaEmision(fecha);
            doc.setIssueYear((short) fecha.getYear());
            doc.setDeadlineAt(Deadlines.sendDeadline(tipo, identity.serie(), fecha));
            doc.setCustomerTipo(identity.customerTipo());
            doc.setCustomerNumero(identity.customerNumero());
            doc.setMoneda(identity.moneda());
            doc.setTotal(identity.total() == null ? null : new BigDecimal(identity.total()));
            doc.setAmounts(DocumentModel.amountsOf(identity.amounts()));
            doc.setPaymentTerms("01".equals(tipo) ? (identity.paymentTerms() == null ? "contado" : identity.paymentTerms()) : null);
            doc.setStatus("pending_send");
            doc.setDigestValue(digest);
            doc.setEndpoint(route.endpoint());
            doc.setCredentialId(route.credentialId());
            // Signed by the issuer before upload: its own signature, never the platform's as PSE
            doc.setEmissionMode(pse && !identity.signed() ? "pse" : "software");
            doc.setNextAttemptAt(now);
            doc.setProgressAt(now);
            doc.setCreatedAt(now);
            doc.setUpdatedAt(now);
            try {
                documentProvider.persist(doc);
                documentProvider.flush();
            } catch (jakarta.persistence.PersistenceException e) {
                if (io.github.projectopenubl.xhandler.utils.Constraints.violated(e)
                        .filter(name -> name.startsWith("document_number") || name.contains("tipo_comprobante_serie")).isPresent()) {
                    throw ApiException.conflict("number_taken", "Ya existe un documento con ese número.");
                }
                throw e;
            }
            documentFileProvider.addDocumentFile(doc, "input", input);
            documentFileProvider.addDocumentFile(doc, "xml_signed", stored);
            documentEventProvider.addDocumentEvent(doc, null, identity.signed() ? "uploaded (UBL XML, signed by the issuer)" : "uploaded (UBL XML)",
                    scope.actor().auditName(), correlationId);
            auditor.record(scope, "document.uploaded", "document", doc.getId(), Map.of("serie_numero", doc.serieNumero()));
            String token = links.create(doc);
            jobQueueProvider.notifyReconcile();
            return modelToRepresentation.toRepresentation(doc, links.url(token), null);
        }));
    }
}
