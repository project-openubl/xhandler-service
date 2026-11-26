package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.events.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.managers.Routes;
import io.github.projectopenubl.xhandler.models.Outcome;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEventEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentFileEntity;
import io.github.projectopenubl.xhandler.utils.OutcomePolicy;

import io.github.projectopenubl.xhandler.models.jpa.entities.CompanyEntity;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineException;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * One SUNAT attempt for a document (DESIGN.md §9.2, §10.4, §11.2), created only by the reconciler.
 * The job holds {@code document:{id}}, so attempts on a document run one at a time.
 *
 * <ol>
 *   <li>Transaction 1: re-read the document; do nothing once it is definitive; increment {@code
 *       attempts} (to n), record {@code send_started_at}; commit.
 *   <li>Call SUNAT through the engine with no transaction open.
 *   <li>Transaction 2: write the outcome only if {@code attempts} is still n. A stale worker that
 *       wakes up after a newer attempt started writes nothing.
 * </ol>
 */
@ApplicationScoped
public class AttemptHandler implements JobHandler {

    private static final Logger LOG = Logger.getLogger(AttemptHandler.class);

    enum Action {
        SEND,
        VERIFY_TICKET,
        RESOLVE
    }

    /** A line of a summary whose ticket was lost: checked on its own (§9.2.2). {@code adds}: estado 1 line. */
    record Line(UUID documentId, boolean adds, EngineModels.ValidityRequest check) {}

    /** What transaction 1 decided, as plain data (entities never leave their transaction). */
    record Plan(
            UUID documentId,
            UUID tenantId,
            int attempt,
            Action action,
            Routes.Target target,
            String xmlKey,
            String ticket,
            EngineModels.DocumentKey key,
            boolean facturaFamily,
            boolean summary,
            Instant sendStartedAt,
            String endpoint,
            List<Line> lines,
            boolean viaOse,
            boolean ticketLost,
            Instant firstSentAt) {

        /** Consult and validity services are SUNAT's, with SOL credentials: not for documents sent through an OSE. */
        boolean canConsult() {
            return target.environment().hasConsultServices() && !viaOse;
        }
    }

    /** A summary is only resolved line by line once its ticket is known lost or SUNAT had hours to process it. */
    static final Duration SUMMARY_PROCESSING = Duration.ofHours(6);

    private final TenantAccess access;
    private final Routes routes;
    private final Engine engine;
    private final FileStore files;
    private final EntityManager em;
    private final Event<DocumentStatusChanged> statusChanged;
    private final MeterRegistry metrics;
    private final Duration resolveSafety;
    private final Duration lease;

    public AttemptHandler(
            TenantAccess access,
            Routes routes,
            Engine engine,
            FileStore files,
            EntityManager em,
            Event<DocumentStatusChanged> statusChanged,
            MeterRegistry metrics,
            // SUNAT read timeout (120 s) + margin; nests inside the app → engine timeout (§10.8)
            @ConfigProperty(name = "xhandler.documents.resolve-safety", defaultValue = "160s") Duration resolveSafety,
            io.github.projectopenubl.xhandler.jobs.JobConfig jobs) {
        this.access = access;
        this.routes = routes;
        this.engine = engine;
        this.files = files;
        this.em = em;
        this.statusChanged = statusChanged;
        this.metrics = metrics;
        this.resolveSafety = resolveSafety;
        this.lease = jobs.lease();
    }

    @Override
    public String name() {
        return "document.attempt";
    }

    /** One span per attempt, tagged with the document, so its trace (→ engine → SUNAT) can be found. */
    @Override
    @io.opentelemetry.instrumentation.annotations.WithSpan("document.attempt")
    public Map<String, Object> run(JobContext context) {
        UUID documentId = context.uuidArg("document_id");
        io.opentelemetry.api.trace.Span.current().setAttribute("xhandler.document_id", documentId.toString());
        Object begun = QuarkusTransaction.requiringNew().call(() -> begin(context, documentId));
        if (begun instanceof String skipped) {
            return Map.of("skipped", skipped);
        }
        Plan plan = (Plan) begun;
        long started = System.nanoTime();
        Instant callStartedAt = Instant.now();
        Map<UUID, String> lineStates = new java.util.HashMap<>();
        Outcome outcome = execute(plan, lineStates);
        metrics.timer("xhandler.sunat.attempt", "endpoint", plan.endpoint(), "outcome", outcome.kind().name().toLowerCase())
                .record(Duration.ofNanos(System.nanoTime() - started));
        try {
            // Store the CDR before transaction 2 (content-addressed: an orphan is harmless)
            FileStore.Stored cdr = null;
            if (outcome.cdrZip() != null) {
                cdr = files.put(plan.tenantId(), Base64.getDecoder().decode(outcome.cdrZip()));
            }
            FileStore.Stored storedCdr = cdr;
            boolean written = QuarkusTransaction.requiringNew().call(() ->
                    finish(context, plan, outcome, storedCdr, callStartedAt, lineStates));
            return Map.of("action", plan.action().name().toLowerCase(), "outcome", outcome.kind().name().toLowerCase(),
                    "written", written);
        } catch (RuntimeException e) {
            // SUNAT may have answered, but we could not record it: ask before anything else (§9.2.1)
            if (plan.action() != Action.VERIFY_TICKET) {
                markUnknown(context, plan);
            }
            throw e;
        }
    }

    /** After a failure past the send: the document goes to unknown (fenced), so the next attempt resolves first. */
    private void markUnknown(JobContext context, Plan plan) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                access.enterAsPlatform(plan.tenantId());
                DocumentEntity doc = DocumentEntity.findById(plan.documentId(), LockModeType.PESSIMISTIC_WRITE);
                if (doc == null || doc.getAttempts() != plan.attempt() || !DocumentEntity.RETRYABLE.contains(doc.getStatus())) {
                    return;
                }
                String previous = doc.getStatus();
                doc.setStatus("unknown");
                doc.setAttemptJobId(null);
                doc.setNextAttemptAt(plan.sendStartedAt().plus(resolveSafety));
                doc.setUpdatedAt(Instant.now());
                DocumentEventEntity.record(doc, null, "unknown: the outcome could not be recorded", "platform:worker", context.correlationId());
                if (!previous.equals(doc.getStatus())) {
                    statusChanged.fire(new DocumentStatusChanged(doc, previous, context.correlationId()));
                }
            });
        } catch (RuntimeException e) {
            // Recovery (lease expiry → resolve) still covers it
            LOG.errorf(e, "could not move document %s to unknown", plan.documentId());
        }
    }

    private Object begin(JobContext context, UUID documentId) {
        TenantScope scope = access.enterAsPlatform(context.tenantId());
        DocumentEntity doc = DocumentEntity.findById(documentId, LockModeType.PESSIMISTIC_WRITE);
        if (doc == null) {
            return "missing";
        }
        if (!DocumentEntity.RETRYABLE.contains(doc.getStatus())) {
            return "definitive";
        }
        if (doc.getSummaryDocumentId() != null) {
            // It follows its summary's outcome: no retry state of its own
            doc.setNextAttemptAt(null);
            return "in_summary";
        }
        if ("operator".equals(doc.getHoldReason())) {
            return "held";
        }
        Instant now = Instant.now();
        Action action;
        if ("in_progress".equals(doc.getStatus()) && doc.getTicket() != null) {
            action = Action.VERIFY_TICKET;
        } else if ("unknown".equals(doc.getStatus())) {
            // A stale call may still be in flight until its timeout passes (§9.2.1)
            Instant notBefore = doc.getSendStartedAt() == null ? now : doc.getSendStartedAt().plus(resolveSafety);
            if (now.isBefore(notBefore)) {
                doc.setNextAttemptAt(notBefore);
                return "waiting_for_stale_call";
            }
            action = Action.RESOLVE;
        } else {
            action = Action.SEND;
        }
        DocumentFileEntity xml = DocumentFileEntity.of(doc.getId(), "xml_signed").orElse(null);
        if (xml == null) {
            // Can't happen through emit; a person must look instead of re-leasing it forever
            doc.setNextAttemptAt(null);
            doc.setHoldReason("needs_attention");
            DocumentEventEntity.record(doc, null, "no signed XML stored: needs attention", "platform:worker", context.correlationId());
            return "no_signed_xml";
        }
        CompanyEntity company = CompanyEntity.findById(doc.getCompanyId());
        Routes.Target target;
        try {
            target = routes.target(scope, company, doc);
        } catch (Routes.MissingCredentials e) {
            // The user must add credentials; the document waits in exception, resendable later
            applyAndRecord(doc, OutcomePolicy.decide(state(doc), Outcome.of(Outcome.Kind.PERMANENT, null, e.getMessage()),
                    now, 1.0, null), Outcome.of(Outcome.Kind.PERMANENT, null, e.getMessage()), context.correlationId());
            return "missing_credentials";
        }
        doc.setAttempts(doc.getAttempts() + 1);
        doc.setAttemptJobId(context.id());
        // Throughput per PSE SOL user (the pool, §10.2); tenant credentials are not tagged (cardinality)
        metrics.counter("xhandler.sunat.attempts", "mode", doc.getEmissionMode(),
                "credential", "pse".equals(doc.getEmissionMode()) && doc.getCredentialId() != null ? doc.getCredentialId().toString() : "tenant").increment();
        if (action != Action.VERIFY_TICKET) {
            doc.setSendStartedAt(now);
        }
        // Renew the lease: the attempt may have waited in the queue (§24.2)
        doc.setNextAttemptAt(now.plus(lease));
        doc.setUpdatedAt(now);
        boolean viaOse = doc.getEndpoint().startsWith("ose:");
        // validaCDPcriterios doesn't cover percepciones and retenciones: RR lines can't be checked
        List<Line> lines = action == Action.RESOLVE && doc.isSummary() && doc.getTicket() == null && target.environment().hasConsultServices()
                && !viaOse && !"RR".equals(doc.getTipoComprobante())
                ? summaryLines(company, doc, target)
                : List.of();
        boolean ticketLost = doc.getLastOutcome() != null && doc.getLastOutcome().startsWith("TICKET_LOST");
        return new Plan(doc.getId(), doc.getTenantId(), doc.getAttempts(), action, target, xml.getStorageKey(), doc.getTicket(),
                new EngineModels.DocumentKey(company.getRuc(), doc.getTipoComprobante(), doc.getSerie(), doc.getNumero()), doc.isFacturaFamily(),
                doc.isSummary(), doc.getSendStartedAt(), doc.getEndpoint(), lines, viaOse, ticketLost, doc.getFirstSentAt());
    }

    /** The lines of a summary: documents it adds (RC estado 1) and documents it voids (RA, RR, RC estado 3). */
    private static List<Line> summaryLines(CompanyEntity company, DocumentEntity summary, Routes.Target target) {
        List<Line> lines = new java.util.ArrayList<>();
        List<DocumentEntity> added = DocumentEntity.list("summaryDocumentId = ?1 order by id", summary.getId());
        List<DocumentEntity> voided = DocumentEntity.list("voidedByDocumentId = ?1 order by id", summary.getId());
        for (DocumentEntity d : added) {
            lines.add(new Line(d.getId(), true, validityCheck(company, d, target)));
        }
        for (DocumentEntity d : voided) {
            lines.add(new Line(d.getId(), false, validityCheck(company, d, target)));
        }
        return lines;
    }

    private static EngineModels.ValidityRequest validityCheck(CompanyEntity company, DocumentEntity d, Routes.Target target) {
        // Anonymous boletas have no buyer document: SUNAT's convention is type 0 / number "-"
        return new EngineModels.ValidityRequest(target.environment(), target.credentials(),
                new EngineModels.DocumentKey(company.getRuc(), d.getTipoComprobante(), d.getSerie(), d.getNumero()),
                d.getCustomerTipo() == null ? "0" : d.getCustomerTipo(), d.getCustomerNumero() == null ? "-" : d.getCustomerNumero(),
                d.getFechaEmision().toString(), d.getTotal() == null ? "0.00" : d.getTotal().toPlainString());
    }

    private Outcome execute(Plan plan, Map<UUID, String> lineStates) {
        String xml = new String(files.get(plan.xmlKey()), StandardCharsets.UTF_8);
        Routes.Target t = plan.target();
        try {
            return switch (plan.action()) {
                case SEND -> afterSend(plan, xml, engine.send(xml, t.environment(), t.credentials(), t.urls()));
                case VERIFY_TICKET -> Outcome.ofSend(engine.verifyTicket(xml, plan.ticket(), t.environment(), t.credentials(), t.urls()));
                case RESOLVE -> resolve(plan, xml, lineStates);
            };
        } catch (EngineException e) {
            if (e.status() == 422) {
                return Outcome.of(Outcome.Kind.LOCAL_ERROR, null, e.getMessage());
            }
            LOG.warnf("engine failed during %s of document %s: %s", plan.action(), plan.documentId(), e.getMessage());
            return Outcome.of(Outcome.Kind.ENGINE_UNAVAILABLE, null, "Servicio interno no disponible: " + e.kind());
        }
    }

    /**
     * Resolves an ambiguous send per document type (§9.2.2). Production facturas ask SUNAT's consult
     * service; summaries with a ticket check it; everything else resends the same bytes (a 1033
     * then goes through the digest check).
     */
    private Outcome resolve(Plan plan, String xml, Map<UUID, String> lineStates) {
        Routes.Target t = plan.target();
        if (plan.summary() && plan.ticket() != null) {
            return Outcome.ofSend(engine.verifyTicket(xml, plan.ticket(), t.environment(), t.credentials(), t.urls()));
        }
        if (plan.summary() && plan.ticket() == null && plan.viaOse() && plan.attempt() > 1) {
            return Outcome.of(Outcome.Kind.PERMANENT, null, "Resumen sin ticket enviado por el OSE: resuélvalo con su OSE");
        }
        if (plan.summary() && plan.ticket() == null && "RR".equals(plan.key().tipoComprobante()) && plan.attempt() > 1) {
            return Outcome.of(Outcome.Kind.PERMANENT, null, "Resumen de reversiones sin ticket: revise su estado en SUNAT Operaciones en Línea");
        }
        if (plan.summary() && plan.ticket() == null && !plan.lines().isEmpty()) {
            boolean waitedEnough = plan.firstSentAt() != null && plan.firstSentAt().isBefore(Instant.now().minus(SUMMARY_PROCESSING));
            if (!plan.ticketLost() && !waitedEnough) {
                // SUNAT may still be processing it: never decide from SUNAT's view too early
                return Outcome.of(Outcome.Kind.SUMMARY_PENDING, null, "Resumen sin ticket: SUNAT puede estar procesándolo");
            }
            // Lost ticket: SUNAT's view of each line decides; the rest goes in a new summary (§9.2.2)
            for (Line line : plan.lines()) {
                String state = engine.validity(line.check()).state();
                if ("unknown".equals(state) || "not_delivered".equals(state) || ("not_found".equals(state) && !waitedEnough)) {
                    // Every line must be known (and old enough to be truly absent) before anything moves
                    return Outcome.of(Outcome.Kind.SUMMARY_PENDING, null, "No se pudo confirmar el estado de todas las líneas del resumen");
                }
                lineStates.put(line.documentId(), state);
            }
            return Outcome.of(Outcome.Kind.SUMMARY_RESOLVED, null, "Ticket perdido: líneas resueltas una por una con SUNAT");
        }
        if (plan.summary() && plan.ticket() == null && !t.environment().hasConsultServices() && plan.attempt() > 1) {
            return Outcome.of(Outcome.Kind.PERMANENT, null,
                    "Ticket perdido en SUNAT beta (sin servicio de consulta): revise el resumen manualmente");
        }
        if (plan.facturaFamily() && plan.canConsult()) {
            EngineModels.StatusCdrResponse status = engine.statusCdr(t.environment(), t.credentials(), plan.key());
            return switch (status.status()) {
                case "accepted" -> new Outcome(Outcome.Kind.ACCEPTED, 0, null, status.description(), List.of(), null, status.cdrZip());
                case "rejected" -> Outcome.of(Outcome.Kind.REJECTED, null, status.description());
                case "voided" -> Outcome.of(Outcome.Kind.VOIDED, null, status.description());
                // SUNAT doesn't have it: the same bytes go again
                case "not_found" -> afterSend(plan, xml, engine.send(xml, t.environment(), t.credentials(), t.urls()));
                case "not_delivered" -> Outcome.of(Outcome.Kind.NOT_DELIVERED, null, status.description());
                default -> Outcome.of(Outcome.Kind.AMBIGUOUS, null, status.description());
            };
        }
        return afterSend(plan, xml, engine.send(xml, t.environment(), t.credentials(), t.urls()));
    }

    private Outcome afterSend(Plan plan, String xml, EngineModels.SendResponse response) {
        Outcome outcome = Outcome.ofSend(response);
        try {
            return switch (outcome.kind()) {
                case ALREADY_REGISTERED -> digestCheck(plan, xml, outcome);
                case ALREADY_FINAL -> alreadyFinal(plan, xml, outcome);
                default -> outcome;
            };
        } catch (EngineException e) {
            // The send happened: a failing follow-up can't make the document a local error
            LOG.warnf("follow-up of %s for document %s failed: %s", outcome.kind(), plan.documentId(), e.getMessage());
            return Outcome.of(Outcome.Kind.AMBIGUOUS, outcome.code(), outcome.description());
        }
    }

    /**
     * 1033 "registrado previamente con otros datos": does SUNAT hold exactly our bytes? (§9.2.1)
     * The validity service compares the file. Beta has no such service: a first attempt that gets
     * 1033 collides (someone used the number), a later one is taken as our earlier send.
     */
    private Outcome digestCheck(Plan plan, String xml, Outcome sunat) {
        Routes.Target t = plan.target();
        if (!plan.canConsult()) {
            // Beta, or a third-party OSE: no consult service to ask with these credentials
            return plan.attempt() > 1
                    ? new Outcome(Outcome.Kind.ACCEPTED, 0, null, "Registrado previamente (SUNAT beta, sin verificación)", List.of(),
                            null, null)
                    : collision(sunat);
        }
        if (plan.facturaFamily()) {
            // Facturas: SUNAT's record comes with its CDR, which we keep (§9.2.2)
            EngineModels.StatusCdrResponse status = engine.statusCdr(t.environment(), t.credentials(), plan.key());
            if ("accepted".equals(status.status()) && status.cdrZip() != null) {
                EngineModels.ValidityResponse same = engine.validityFile(xml, t.environment(), t.credentials());
                if ("matches".equals(same.state())) {
                    return new Outcome(Outcome.Kind.ACCEPTED, 0, null, status.description(), List.of(), null, status.cdrZip());
                }
                if ("differs".equals(same.state())) {
                    return collision(sunat);
                }
            }
        }
        EngineModels.ValidityResponse validity = engine.validityFile(xml, t.environment(), t.credentials());
        return switch (validity.state()) {
            case "matches" -> new Outcome(Outcome.Kind.ACCEPTED, 0, null, "Registrado previamente con los mismos datos", List.of(),
                    null, null);
            case "differs" -> collision(sunat);
            case "not_delivered" -> Outcome.of(Outcome.Kind.NOT_DELIVERED, sunat.code(), validity.description());
            default -> Outcome.of(Outcome.Kind.AMBIGUOUS, sunat.code(), validity.description());
        };
    }

    /** 1032: already reported, voided or rejected. Production facturas can ask; otherwise it ends here. */
    private Outcome alreadyFinal(Plan plan, String xml, Outcome sunat) {
        Routes.Target t = plan.target();
        if (plan.facturaFamily() && plan.canConsult()) {
            EngineModels.StatusCdrResponse status = engine.statusCdr(t.environment(), t.credentials(), plan.key());
            return switch (status.status()) {
                case "accepted" -> new Outcome(Outcome.Kind.ACCEPTED, 0, null, status.description(), List.of(), null, status.cdrZip());
                case "rejected" -> Outcome.of(Outcome.Kind.REJECTED, sunat.code(), status.description());
                case "voided" -> Outcome.of(Outcome.Kind.VOIDED, sunat.code(), status.description());
                default -> sunat;
            };
        }
        if (plan.canConsult()) {
            EngineModels.ValidityResponse validity = engine.validityFile(xml, t.environment(), t.credentials());
            if ("matches".equals(validity.state())) {
                return new Outcome(Outcome.Kind.ACCEPTED, 0, null, sunat.description(), List.of(), null, null);
            }
        }
        return new Outcome(Outcome.Kind.PERMANENT, sunat.code(), sunat.codeClass(), sunat.description(), sunat.notes(), null, null);
    }

    private static Outcome collision(Outcome sunat) {
        return new Outcome(Outcome.Kind.COLLISION, sunat.code(), sunat.codeClass(),
                "Colisión de numeración: SUNAT ya tiene este número con otros datos. " + sunat.description(), sunat.notes(), null, null);
    }

    private boolean finish(JobContext context, Plan plan, Outcome outcome, FileStore.Stored cdr, Instant callStartedAt,
            Map<UUID, String> lineStates) {
        access.enterAsPlatform(plan.tenantId());
        DocumentEntity doc = DocumentEntity.findById(plan.documentId(), LockModeType.PESSIMISTIC_WRITE);
        if (doc == null || doc.getAttempts() != plan.attempt()) {
            LOG.infof("stale attempt %d of document %s: a newer attempt started, nothing written", plan.attempt(), plan.documentId());
            return false;
        }
        if (outcome.kind() != Outcome.Kind.LOCAL_ERROR && outcome.kind() != Outcome.Kind.ENGINE_UNAVAILABLE) {
            em.createNativeQuery("INSERT INTO xhandler.sunat_call (endpoint, healthy, started_at) VALUES (:endpoint, :healthy, :started)")
                    .setParameter("endpoint", plan.endpoint())
                    .setParameter("healthy", outcome.healthy())
                    .setParameter("started", callStartedAt)
                    .executeUpdate();
        }
        Instant now = Instant.now();
        if (cdr != null && DocumentFileEntity.of(doc.getId(), "cdr_zip").isEmpty()) {
            DocumentFileEntity.record(doc, "cdr_zip", cdr);
            doc.setCdrReceivedAt(now);
        }
        if (plan.action() != Action.VERIFY_TICKET && doc.getFirstSentAt() == null && outcome.kind() != Outcome.Kind.NOT_DELIVERED
                && outcome.kind() != Outcome.Kind.LOCAL_ERROR) {
            doc.setFirstSentAt(plan.sendStartedAt());
        }
        if (outcome.kind() == Outcome.Kind.TICKET) {
            doc.setTicket(outcome.ticket());
        } else if (outcome.kind() == Outcome.Kind.TICKET_LOST) {
            // Never check a lost ticket again: resolution takes the lost-ticket path (§9.2.2)
            doc.setTicket(null);
        }
        // The outcome is written: recovery must not turn it into unknown
        doc.setAttemptJobId(null);
        if (outcome.kind() == Outcome.Kind.SUMMARY_RESOLVED) {
            applyLines(doc, plan, lineStates, context.correlationId());
            doc.setCodeClass("ticket_lost");
        }
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        Instant resolveNotBefore = plan.sendStartedAt() == null ? null : plan.sendStartedAt().plus(resolveSafety);
        applyAndRecord(doc, OutcomePolicy.decide(state(doc), outcome, now, jitter, resolveNotBefore), outcome, context.correlationId());
        return true;
    }

    /** Lost-ticket resolution of a summary's lines (§9.2.2), with the lines locked in id order. */
    private void applyLines(DocumentEntity summary, Plan plan, Map<UUID, String> states, String correlationId) {
        Instant now = Instant.now();
        for (Line line : plan.lines().stream().sorted(java.util.Comparator.comparing(Line::documentId)).toList()) {
            DocumentEntity d = DocumentEntity.findById(line.documentId(), LockModeType.PESSIMISTIC_WRITE);
            String state = states.getOrDefault(line.documentId(), "unknown");
            String previous = d.getStatus();
            String note;
            if (line.adds()) {
                switch (state) {
                    case "valid" -> {
                        d.setStatus("accepted");
                        note = "registered by SUNAT (resumen " + summary.serieNumero() + ", ticket lost)";
                    }
                    case "not_found" -> {
                        // Not in SUNAT: the D+5 fallback puts it in a new resumen diario
                        d.setSummaryDocumentId(null);
                        d.setCodeClass("route_change");
                        note = "not registered by SUNAT: goes in a new resumen diario";
                    }
                    case "voided" -> {
                        d.setStatus("voided");
                        note = "SUNAT has it voided";
                    }
                    default -> {
                        d.setHoldReason("needs_attention");
                        note = "SUNAT's state is unknown (" + state + "): needs attention";
                    }
                }
            } else {
                switch (state) {
                    case "voided" -> {
                        d.setStatus("voided");
                        note = "voided (" + summary.serieNumero() + ", ticket lost)";
                    }
                    case "valid" -> {
                        d.setStatus(d.getStatusBeforeVoiding() == null ? "accepted" : d.getStatusBeforeVoiding());
                        d.setStatusBeforeVoiding(null);
                        d.setVoidedByDocumentId(null);
                        note = "voiding not registered by SUNAT (ticket lost): request it again";
                    }
                    default -> {
                        d.setHoldReason("needs_attention");
                        note = "SUNAT's state is unknown (" + state + "): needs attention";
                    }
                }
            }
            d.setNextAttemptAt(null);
            d.setUpdatedAt(now);
            DocumentEventEntity.record(d, null, note, "platform:worker", correlationId);
            if (!d.getStatus().equals(previous)) {
                statusChanged.fire(new DocumentStatusChanged(d, previous, correlationId));
            }
        }
    }

    private void applyAndRecord(DocumentEntity doc, OutcomePolicy.Decision decision, Outcome outcome, String correlationId) {
        String previous = doc.getStatus();
        Instant now = Instant.now();
        doc.setStatus(decision.status());
        doc.setNextAttemptAt(decision.nextAttemptAt());
        if (!"operator".equals(doc.getHoldReason())) {
            doc.setHoldReason(decision.holdReason());
        }
        doc.setLastOutcome(decision.outcomeKey());
        doc.setSameOutcomeCount(decision.sameOutcomeCount());
        if (decision.progress()) {
            doc.setProgressAt(now);
        }
        if (outcome.code() != null || outcome.description() != null) {
            doc.setSunatCode(outcome.code());
            doc.setSunatDescription(outcome.description());
            doc.setSunatNotes(outcome.notes());
            doc.setCodeClass(outcome.kind() == Outcome.Kind.ROUTE_CHANGE ? "route_change"
                    : outcome.kind() == Outcome.Kind.SUMMARY_RESOLVED ? "ticket_lost" : outcome.codeClass());
        }
        doc.setUpdatedAt(now);
        DocumentEventEntity.record(doc, outcome.code(), describe(outcome), "platform:worker", correlationId);
        if (!doc.getStatus().equals(previous)) {
            statusChanged.fire(new DocumentStatusChanged(doc, previous, correlationId));
        }
    }

    private static String describe(Outcome outcome) {
        String kind = outcome.kind().name().toLowerCase();
        return outcome.description() == null ? kind : kind + ": " + outcome.description();
    }

    private static OutcomePolicy.State state(DocumentEntity doc) {
        return new OutcomePolicy.State(doc.getStatus(), doc.getLastOutcome(), doc.getSameOutcomeCount(), doc.getProgressAt(), doc.getHoldReason());
    }
}
