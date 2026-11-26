package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.ExchangeRateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.EmitRequestRepresentation;
import io.github.projectopenubl.xhandler.models.PseProvider;
import io.github.projectopenubl.xhandler.models.JobQueueProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.models.DocumentFileProvider;
import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.utils.Deadlines;
import io.github.projectopenubl.xhandler.utils.DocumentTypes;
import io.github.projectopenubl.xhandler.utils.Qr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.models.CompanyModel;
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
import jakarta.enterprise.inject.Instance;
import io.github.projectopenubl.xhandler.models.NumberingProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Emit (DESIGN.md §11.1): build, number, validate and sign inside one short transaction, reply
 * {@code 201 pending_send}, and let the reconciler send. Intake never depends on SUNAT (§24.1).
 *
 * <p>The transaction is the documented exception to "no remote call while holding row locks"
 * (§10.8 rule 3): the series row stays locked across the engine, the HSM and storage, so a series
 * emits one document at a time. Emits take a permit from a bounded semaphore first.
 */
@ApplicationScoped
public class EmitManager {

    private final ModelToRepresentation modelToRepresentation;

    private final PseProvider pseProvider;

    private final JobQueueProvider jobQueueProvider;

    private final DocumentProvider documentProvider;

    private final TenantProvider tenantProvider;

    private final DocumentFileProvider documentFileProvider;

    private final DocumentEventProvider documentEventProvider;

    private final CompanyProvider companyProvider;


    public record Emitted(int status, JsonNode body) {}

    /**
     * How a draft is issued inside the emit transaction (§11.10). {@code key} scopes the
     * Idempotency-Key's request hash to the draft. {@code guard} runs after the company lock and
     * before a number is reserved: it locks the draft row and checks it, so a losing concurrent
     * issue reserves no number and signs nothing. {@code issued} runs after the document insert and
     * marks the draft issued. Throwing from either rolls the emit back.
     */
    public record DraftIssue(String key, Runnable guard, java.util.function.Consumer<DocumentModel> issued) {}

    private final TenantAccess access;
    private final NumberingManager numbering;
    private final Engine engine;
    private final SigningService signing;
    private final SignatureLog signatureLog;
    private final FileStore files;
    private final RouteManager routes;
    private final AccessLinkManager links;
    private final IdempotencyManager idempotency;
    private final AuditManager auditor;
    private final NumberingProvider numberingProvider;
    private final ObjectMapper json;
    private final Instance<EmitQuota> quota;
    private final io.github.projectopenubl.xhandler.managers.ExchangeRateManager rates;
    private final Semaphore permits;
    private final long permitTimeoutMillis;
    private final String lockTimeout;

    public EmitManager(
            TenantAccess access,
            NumberingManager numbering,
            Engine engine,
            SigningService signing,
            SignatureLog signatureLog,
            FileStore files,
            RouteManager routes,
            AccessLinkManager links,
            IdempotencyManager idempotency,
            AuditManager auditor,
            NumberingProvider numberingProvider,
            ObjectMapper json,
            Instance<EmitQuota> quota,
            io.github.projectopenubl.xhandler.managers.ExchangeRateManager rates,
            @ConfigProperty(name = "xhandler.emit.permits", defaultValue = "32") int permits,
            @ConfigProperty(name = "xhandler.emit.permit-timeout", defaultValue = "5s") Duration permitTimeout,
            @ConfigProperty(name = "xhandler.emit.lock-timeout", defaultValue = "10s") Duration lockTimeout, CompanyProvider companyProvider, DocumentEventProvider documentEventProvider, DocumentFileProvider documentFileProvider, TenantProvider tenantProvider, DocumentProvider documentProvider, JobQueueProvider jobQueueProvider, PseProvider pseProvider, ModelToRepresentation modelToRepresentation) {
        this.modelToRepresentation = modelToRepresentation;
        this.pseProvider = pseProvider;
        this.jobQueueProvider = jobQueueProvider;
        this.documentProvider = documentProvider;
        this.tenantProvider = tenantProvider;
        this.documentFileProvider = documentFileProvider;
        this.documentEventProvider = documentEventProvider;
        this.companyProvider = companyProvider;
        this.access = access;
        this.numbering = numbering;
        this.engine = engine;
        this.signing = signing;
        this.signatureLog = signatureLog;
        this.files = files;
        this.routes = routes;
        this.links = links;
        this.idempotency = idempotency;
        this.auditor = auditor;
        this.numberingProvider = numberingProvider;
        this.json = json;
        this.quota = quota;
        this.rates = rates;
        this.permits = new Semaphore(permits);
        this.permitTimeoutMillis = permitTimeout.toMillis();
        this.lockTimeout = lockTimeout.toMillis() + "ms";
    }

    public Emitted emit(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequestRepresentation request, String correlationId) {
        return emit(tenantId, companyId, idempotencyKey, request, correlationId, false, null, "api", null);
    }

    /** Issues a draft (manual issuing, §11.10): see {@link DraftIssue}. */
    public Emitted emitDraft(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequestRepresentation request, String correlationId,
            DraftIssue draft) {
        return emit(tenantId, companyId, idempotencyKey, request, correlationId, false, null, "api", draft);
    }

    /** Emits as the platform (its own invoices, §14), not as a tenant member. */
    public Emitted emitAsPlatform(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequestRepresentation request, String correlationId) {
        return emit(tenantId, companyId, idempotencyKey, request, correlationId, true, null, "api", null);
    }

    /**
     * An adapter's document with the client's own number (the serie comes from the request, §5). The
     * caller already authenticated: {@code platform} opens the tenant without a member (token-based adapters).
     */
    public Emitted emitNumbered(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequestRepresentation request, long numero, String correlationId,
            boolean platform) {
        return emit(tenantId, companyId, idempotencyKey, request, correlationId, platform, numero, "adapter", null);
    }

    private Emitted emit(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequestRepresentation request, String correlationId,
            boolean platform, Long numeroDado, String origin, DraftIssue draft) {
        if (request == null || request.document() == null || !request.document().hasNonNull("kind")
                || !request.document().path("spec").isObject()) {
            throw ApiException.invalid("invalid_request", "Envíe el documento como {\"document\": {\"kind\": ..., \"spec\": {...}}}.", null);
        }
        // Authorize before touching the idempotency table
        QuarkusTransaction.requiringNew().run(() -> {
            if (platform) {
                access.enterAsPlatform(tenantId);
            } else {
                access.enter(tenantId, Role.OPERATOR);
            }
        });
        UUID claim = null;
        if (idempotencyKey != null) {
            IdempotencyManager.Begun begun = idempotency.begin(tenantId, idempotencyKey,
                    hash((draft == null ? "" : draft.key() + ":") + companyId, request));
            if (begun.replay() != null) {
                return new Emitted(begun.replay().status(), begun.replay().body());
            }
            claim = begun.claim();
        }
        UUID owned = claim;
        try {
            UUID documentId = Ids.newId();
            JsonNode body = admitted(tenantId, companyId, platform, !platform || "adapter".equals(origin), () -> QuarkusTransaction.requiringNew()
                    .call(() -> emitInTransaction(tenantId, companyId, documentId, idempotencyKey, owned, request, correlationId, platform,
                            numeroDado, origin, draft)));
            return new Emitted(201, body);
        } catch (RuntimeException e) {
            abandon(tenantId, idempotencyKey, owned);
            throw e;
        }
    }

    /**
     * The gates every new document passes, whatever its input (API, adapters, UBL upload, §5): the
     * quota (a replay never gets here; the platform's own invoices don't count, §14), then an emit
     * permit. Waits on a series row beyond the lock timeout are answered as busy.
     */
    public <T> T admitted(UUID tenantId, UUID companyId, boolean platform, boolean countQuota, java.util.function.Supplier<T> work) {
        if (countQuota) {
            QuarkusTransaction.requiringNew().run(() -> {
                if (platform) {
                    access.enterAsPlatform(tenantId);
                } else {
                    access.enter(tenantId, Role.OPERATOR);
                }
                CompanyModel company = companyProvider.getCompany(companyId).orElse(null);
                if (company == null || company.getDeletedAt() != null) {
                    throw ApiException.notFound();
                }
                for (EmitQuota q : quota) {
                    q.checkEmit(tenantId, company.getSunatEnv());
                }
            });
        }
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(permitTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ApiException(503, "busy", "El servicio está ocupado. Intente nuevamente en unos segundos.");
            }
            return work.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "busy", "El servicio está ocupado. Intente nuevamente en unos segundos.");
        } catch (RuntimeException e) {
            if (lockTimeout(e)) {
                throw new ApiException(503, "busy", "La serie está ocupada por otras emisiones. Intente nuevamente en unos segundos.");
            }
            throw e;
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    /** Inside an emit transaction, before locking the company: waits on the series row are bounded. */
    public void boundLockWaits() {
        numberingProvider.setLockTimeout(lockTimeout);
    }

    /** The company may emit this document type now: active, and authorized when it emits as PSE (§3.2). */
    public void checkCompany(CompanyModel company, String tipo) {
        TenantModel tenant = tenantProvider.getTenant(company.getTenantId()).orElse(null);
        if (tenant != null && "exited".equals(tenant.getStatus())) {
            // Readable until its data is deleted, but it left: no new documents (§13)
            throw ApiException.conflict("tenant_exited", "La organización dejó la plataforma: no puede emitir comprobantes.");
        }
        if (!company.isActive()) {
            throw ApiException.conflict("company_inactive", "La empresa está desactivada.");
        }
        if ("pse".equals(company.getEmissionMode()) && "production".equals(company.getSunatEnv()) && !"09".equals(tipo) && !"31".equals(tipo)
                && !pseAuthorized(company)) {
            throw ApiException.conflict("pse_not_authorized",
                    "La empresa no tiene registrada su autorización a la plataforma como PSE en SUNAT Operaciones en Línea.");
        }
    }

    private void abandon(UUID tenantId, String key, UUID claim) {
        if (key != null && claim != null) {
            idempotency.abandon(tenantId, key, claim);
        }
    }

    /** PostgreSQL's lock_not_available (55P03): another emit held the series row too long. */
    private static boolean lockTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode emitInTransaction(UUID tenantId, UUID companyId, UUID documentId, String idempotencyKey, UUID claim,
            EmitRequestRepresentation request, String correlationId, boolean platform, Long numeroDado, String origin,
            DraftIssue draft) {
        TenantScope scope = platform ? access.enterAsPlatform(tenantId) : access.enter(tenantId, Role.OPERATOR);
        // Waits on the series row (another emit holds it across engine, HSM and storage) are bounded
        boundLockWaits();
        // Lock order: company → draft → series → documents (§10.8 rule 4). FOR SHARE: an environment
        // switch (FOR UPDATE) waits for running emits and blocks new ones.
        CompanyModel company = companyProvider.getCompanyForShare(companyId).orElse(null);
        if (company == null || company.getDeletedAt() != null) {
            throw ApiException.notFound();
        }
        if (draft != null) {
            // Before any number or signature: a losing concurrent issue stops here (§11.10)
            draft.guard().run();
        }
        ObjectNode document = request.document().deepCopy();
        String kind = document.get("kind").asText();
        ObjectNode spec = (ObjectNode) document.get("spec");
        String tipo = DocumentTypes.tipoComprobante(kind, spec);
        String sunatEnv = company.getSunatEnv();

        checkCompany(company, tipo);
        RouteManager.Assignment route = routes.assign(company, sunatEnv, tipo);

        fillProveedor(spec, company);
        LocalDate fecha = issueDate(spec);
        spec.put("fecha_emision", fecha.toString());
        String serie = chooseSerie(request, spec, company, tipo);
        long numero = numeroDado != null
                ? numbering.claim(tenantId, company.getId(), sunatEnv, tipo, serie, numeroDado)
                : numbering.reserve(company.getId(), sunatEnv, tipo, serie);
        spec.put("serie_numero", serie + "-" + numero);

        EngineModels.BuildResponse built = engine.build(document);
        EngineModels.ValidateResponse validation = engine.validate(built.xml());
        if (!validation.valid()) {
            // Rolls back: the number is not consumed (§8)
            String detail = validation.errors().stream().map(f -> f.code() + ": " + f.message()).collect(Collectors.joining("; "));
            throw ApiException.invalid("invalid_document", "El documento no cumple las validaciones de SUNAT.", detail);
        }
        SigningService.Signed signed = signing.sign(company, sunatEnv, built.xml(), tipo);
        signatureLog.record(tenantId, documentId, serie + "-" + numero, signed.signingKeyId(), signed.platformKeyId(), signed.digestValue(),
                scope.actor().auditName());
        EngineModels.InspectResponse identity = engine.inspect(signed.xml());
        checkBoletaBuyer(tipo, spec, identity, fecha);

        byte[] xml = signed.xml().getBytes(StandardCharsets.UTF_8);
        FileStore.Stored xmlFile = files.put(tenantId, xml);
        FileStore.Stored input = files.put(tenantId, bytes(request.document()));
        FileStore.Stored enriched = files.put(tenantId, bytes(built.enrichedJson()));

        Instant now = Instant.now();
        DocumentModel doc = documentProvider.newDocument(documentId);
        doc.setTenantId(tenantId);
        doc.setCompanyId(company.getId());
        doc.setSunatEnv(sunatEnv);
        doc.setKind(kind);
        doc.setOrigin(origin);
        doc.setTipoComprobante(tipo);
        doc.setSerie(serie);
        doc.setNumero(numero);
        doc.setFechaEmision(fecha);
        doc.setIssueYear((short) fecha.getYear());
        doc.setDeadlineAt(Deadlines.sendDeadline(tipo, serie, fecha));
        doc.setCustomerTipo(identity.customerTipo());
        doc.setCustomerNumero(identity.customerNumero());
        doc.setCustomerNombre(DocumentTypes.customerName(spec));
        doc.setMoneda(identity.moneda());
        doc.setTotal(identity.total() == null ? null : new BigDecimal(identity.total()));
        doc.setAmounts(DocumentModel.amountsOf(identity.amounts()));
        doc.setPaymentTerms(paymentTerms(tipo, spec));
        doc.setStatus("pending_send");
        doc.setDigestValue(signed.digestValue());
        doc.setEndpoint(route.endpoint());
        doc.setCredentialId(route.credentialId());
        // GRE is always signed and sent by the issuer itself, never by the PSE
        doc.setEmissionMode(doc.isGuia() ? "software" : company.getEmissionMode());
        doc.setAffectedDocumentId(affectedDocument(company.getId(), sunatEnv, spec));
        doc.setNextAttemptAt(now);
        doc.setProgressAt(now);
        boolean delivered = request.delivered() != null ? request.delivered() : company.isDeliveredOnEmitDefault();
        if (delivered) {
            doc.setDeliveredAt(now);
            doc.setDeliveredSource("client");
        }
        doc.setLabels(request.labels() == null ? Map.of() : Map.copyOf(request.labels()));
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        try {
            documentProvider.persist(doc);
            documentProvider.flush();
        } catch (jakarta.persistence.PersistenceException e) {
            if (io.github.projectopenubl.xhandler.utils.Constraints.violated(e).filter(c -> c.startsWith("document")).isPresent()) {
                throw ApiException.conflict("number_taken", "El número ya fue usado en esta serie. Revise el número siguiente de la serie.");
            }
            throw e;
        }
        documentFileProvider.addDocumentFile(doc, "xml_signed", xmlFile);
        documentFileProvider.addDocumentFile(doc, "input", input);
        documentFileProvider.addDocumentFile(doc, "enriched", enriched);
        documentEventProvider.addDocumentEvent(doc, null, "emitted", scope.actor().auditName(), correlationId);
        auditor.record(scope, "document.emitted", "document", doc.getId(), Map.of("serie_numero", doc.serieNumero()));
        if (draft != null) {
            draft.issued().accept(doc);
        }
        String token = links.create(doc);
        jobQueueProvider.notifyReconcile();

        String qr = Qr.of(company.getRuc(), doc, built.enrichedJson());
        JsonNode body = json.valueToTree(modelToRepresentation.toRepresentation(doc, links.url(token), qr));
        if (idempotencyKey != null) {
            // Stored without the buyer link: its token is never stored in clear (§12); a replay
            // gets everything else, and a new link can be created
            idempotency.complete(tenantId, idempotencyKey, claim, 201, json.valueToTree(modelToRepresentation.toRepresentation(doc, null, qr)));
        }
        return body;
    }

    /**
     * A boleta above S/ 700 must identify its buyer (validator 2514, §3.1). The engine checks PEN;
     * foreign-currency boletas are checked here at the document's tipo_cambio, or SUNAT's rate for D.
     */
    private void checkBoletaBuyer(String tipo, ObjectNode spec, EngineModels.InspectResponse identity, LocalDate fecha) {
        if (!"03".equals(tipo) || identity.total() == null || identity.moneda() == null || "PEN".equals(identity.moneda())) {
            return;
        }
        BigDecimal rate;
        if (spec.hasNonNull("tipo_cambio")) {
            try {
                rate = new BigDecimal(spec.get("tipo_cambio").asText());
            } catch (NumberFormatException e) {
                throw ApiException.invalid("invalid_document", "tipo_cambio debe ser un número.", null);
            }
            if (rate.signum() <= 0) {
                throw ApiException.invalid("invalid_document", "tipo_cambio debe ser mayor que cero.", null);
            }
        } else {
            rate = rates.on(fecha).map(ExchangeRateRepresentation::venta).orElse(null);
        }
        if (rate == null) {
            throw ApiException.invalid("invalid_document",
                    "No hay tipo de cambio para " + fecha + ": indique tipo_cambio en la boleta.", null);
        }
        BigDecimal pen = new BigDecimal(identity.total()).multiply(rate);
        String numero = identity.customerNumero() == null ? "" : identity.customerNumero().strip();
        boolean anonymous = identity.customerTipo() == null || "0".equals(identity.customerTipo()) || "-".equals(identity.customerTipo())
                || numero.isEmpty() || numero.chars().allMatch(c -> c == '0' || c == '-');
        if (pen.compareTo(new BigDecimal("700")) > 0 && anonymous) {
            throw ApiException.invalid("invalid_document", "Una boleta mayor a S/ 700 debe identificar al comprador.",
                    "2514: equivalente S/ " + pen.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        }
    }

    /** Facturas on credit (cuotas, or forma_pago.tipo Credito) are facturas negociables (§11.8). */
    static String paymentTerms(String tipo, ObjectNode spec) {
        if (!"01".equals(tipo)) {
            return null;
        }
        JsonNode forma = spec.path("forma_pago");
        boolean credito = forma.path("cuotas").isArray() && !forma.path("cuotas").isEmpty()
                || "credito".equalsIgnoreCase(forma.path("tipo").asText(""));
        return credito ? "credito" : "contado";
    }

    private boolean pseAuthorized(CompanyModel company) {
        return pseProvider.hasApprovedAuthorization(company.getId());
    }

    /** D defaults to today in Lima; SUNAT refuses more than 2 days ahead (2329) and late documents lose their validity. */
    private static LocalDate issueDate(ObjectNode spec) {
        LocalDate today = Deadlines.todayInLima();
        if (!spec.hasNonNull("fecha_emision")) {
            return today;
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(spec.get("fecha_emision").asText());
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.invalid("invalid_document", "fecha_emision debe tener el formato AAAA-MM-DD.", null);
        }
        return checkIssueDate(fecha);
    }

    /** SUNAT refuses more than 2 days ahead (2329); older than 7 days is too late to send. */
    public static LocalDate checkIssueDate(LocalDate fecha) {
        LocalDate today = Deadlines.todayInLima();
        if (fecha.isAfter(today.plusDays(2)) || fecha.isBefore(today.minusDays(7))) {
            throw ApiException.invalid("invalid_document",
                    "fecha_emision debe estar entre " + today.minusDays(7) + " y " + today.plusDays(2) + " (hora de Lima).", "2329");
        }
        return fecha;
    }

    private String chooseSerie(EmitRequestRepresentation request, ObjectNode spec, CompanyModel company, String tipo) {
        if (request.serie() != null) {
            return request.serie();
        }
        if (spec.hasNonNull("serie_numero")) {
            String given = spec.get("serie_numero").asText();
            int dash = given.indexOf('-');
            return dash > 0 ? given.substring(0, dash) : given;
        }
        String prefix = null;
        if ("07".equals(tipo) || "08".equals(tipo)) {
            String affected = spec.path("comprobante_afectado_serie_numero").asText("");
            prefix = affected.startsWith("B") ? "B" : "F";
        } else if ("01".equals(tipo)) {
            prefix = "F";
        } else if ("03".equals(tipo)) {
            prefix = "B";
        }
        return numbering.defaultSerie(company.getId(), company.getSunatEnv(), tipo, prefix);
    }

    private static void fillProveedor(ObjectNode spec, CompanyModel company) {
        ObjectNode proveedor = spec.hasNonNull("proveedor") && spec.get("proveedor").isObject()
                ? (ObjectNode) spec.get("proveedor")
                : spec.putObject("proveedor");
        if (proveedor.hasNonNull("ruc") && !company.getRuc().equals(proveedor.get("ruc").asText())) {
            throw ApiException.invalid("invalid_document", "El RUC del proveedor no coincide con el de la empresa.", null);
        }
        proveedor.put("ruc", company.getRuc());
        if (!proveedor.hasNonNull("razon_social")) {
            proveedor.put("razon_social", company.getRazonSocial());
        }
    }

    /** The document a note affects, if it was issued here (same company and environment). */
    private UUID affectedDocument(UUID companyId, String sunatEnv, ObjectNode spec) {
        String affected = spec.path("comprobante_afectado_serie_numero").asText(null);
        if (affected == null || !affected.contains("-")) {
            return null;
        }
        String serie = affected.substring(0, affected.indexOf('-'));
        String tipoAfectado = spec.path("comprobante_afectado_tipo").asText(serie.startsWith("B") ? "03" : "01");
        long numero;
        try {
            numero = Long.parseLong(affected.substring(affected.indexOf('-') + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        return documentProvider.getIssuedDocument(companyId, sunatEnv, tipoAfectado, serie, numero)
                .map(d -> d.getId())
                .orElse(null);
    }

    private byte[] bytes(JsonNode node) {
        try {
            return json.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Hash of the request in canonical JSON (keys sorted), so reordered fields are the same request. */
    private String hash(Object scope, EmitRequestRepresentation request) {
        try {
            JsonNode canonical = canonical(json.valueToTree(request));
            return FileStore.sha256((scope + ":" + json.writeValueAsString(canonical)).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            java.util.List<String> names = new java.util.ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            java.util.Collections.sort(names);
            for (String name : names) {
                sorted.set(name, canonical(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode array = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> array.add(canonical(item)));
            return array;
        }
        return node;
    }
}
