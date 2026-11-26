package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.models.JobQueueProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.managers.Summaries;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.models.DocumentEventModel;
import io.github.projectopenubl.xhandler.utils.Deadlines;

import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Boletas past D+5 go by resumen diario (DESIGN.md §9.2.4): every hour, boletas (and their notes)
 * still not accepted when D+5 passed in Lima, or answered 1079, are grouped by issue day into an RC
 * (estado 1) sent before D+7. Each boleta gets {@code summary_document_id} and stops being sent on
 * its own; it follows the RC's outcome ({@link SummaryOutcomes}).
 */
@ApplicationScoped
public class BoletaSummaryFallback implements JobHandler {

    private final JobQueueProvider jobQueueProvider;

    private final DocumentProvider documentProvider;

    private final DocumentEventProvider documentEventProvider;

    private final CompanyProvider companyProvider;

    private static final Logger LOG = Logger.getLogger(BoletaSummaryFallback.class);

    private final TenantAccess access;
    private final Summaries summaries;

    public BoletaSummaryFallback(TenantAccess access, Summaries summaries, CompanyProvider companyProvider, DocumentEventProvider documentEventProvider, DocumentProvider documentProvider, JobQueueProvider jobQueueProvider) {
        this.jobQueueProvider = jobQueueProvider;
        this.documentProvider = documentProvider;
        this.documentEventProvider = documentEventProvider;
        this.companyProvider = companyProvider;
        this.access = access;
        this.summaries = summaries;
    }

    @Override
    public String name() {
        return "documents.boleta-summary-fallback";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        record Group(UUID tenantId, UUID companyId, String sunatEnv, LocalDate fecha) {}
        LocalDate today = Deadlines.todayInLima();
        List<Group> groups = QuarkusTransaction.requiringNew().call(() -> {
            return documentProvider.getBoletasNeedingSummary(today).stream()
                    .map(r -> new Group(r.tenantId(), r.companyId(), r.sunatEnv(), r.fecha())).toList();
        });
        int summariesCreated = 0;
        List<String> failures = new ArrayList<>();
        for (Group group : groups) {
            // One transaction per RC: a bad chunk can't block the rest of the group
            while (true) {
                Chunk chunk;
                try {
                    chunk = QuarkusTransaction.requiringNew().call(() ->
                            summarizeChunk(group.tenantId(), group.companyId(), group.sunatEnv(), group.fecha(), today, context.correlationId()));
                } catch (RuntimeException e) {
                    LOG.errorf(e, "resumen diario for company %s, day %s failed", group.companyId(), group.fecha());
                    failures.add(group.companyId() + "/" + group.fecha() + ": " + e.getMessage());
                    setAside(group.tenantId(), group.companyId(), group.sunatEnv(), group.fecha(), today, e.getMessage(), context.correlationId());
                    break;
                }
                if (chunk.lines() == 0) {
                    break;
                }
                summariesCreated++;
                if (chunk.lines() < Summaries.MAX_LINES) {
                    break;
                }
            }
        }
        return Map.of("groups", groups.size(), "summaries", summariesCreated, "failures", failures);
    }

    record Chunk(int lines) {}

    private Chunk summarizeChunk(UUID tenantId, UUID companyId, String sunatEnv, LocalDate fecha, LocalDate today, String correlationId) {
        TenantScope scope = access.enterAsPlatform(tenantId);
        // Lock order (§10.8 rule 4): company → daily counter → documents
        CompanyModel company = companyProvider.getCompanyForShare(companyId).orElse(null);
        int numero = summaries.reserveResumenDiario(company, sunatEnv);
        List<DocumentModel> boletas = documentProvider.getSummaryCandidatesForUpdate(companyId, sunatEnv, fecha, today.minusDays(5),
                Summaries.MAX_LINES, true);
        boletas = boletas.stream().filter(d -> !attemptActive(d.getId())).toList();
        if (boletas.isEmpty()) {
            // Rolls back: the reserved number is not consumed
            QuarkusTransaction.setRollbackOnly();
            return new Chunk(0);
        }
        DocumentModel rc = summaries.resumenDiario(scope, company, sunatEnv, fecha, boletas, "1", correlationId, Deadlines.todayInLima(), numero);
        Instant now = Instant.now();
        for (DocumentModel boleta : boletas) {
            boleta.setSummaryDocumentId(rc.getId());
            boleta.setNextAttemptAt(null);
            boleta.setUpdatedAt(now);
            documentEventProvider.addDocumentEvent(boleta, null, "moved into resumen diario " + rc.serieNumero(), "platform:worker", correlationId);
        }
        return new Chunk(boletas.size());
    }

    private boolean attemptActive(UUID documentId) {
        return jobQueueProvider.hasActiveJob(documentId);
    }

    /** A chunk whose RC couldn't be built: set its lines aside for a person instead of retrying every hour. */
    private void setAside(UUID tenantId, UUID companyId, String sunatEnv, LocalDate fecha, LocalDate today, String reason,
            String correlationId) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                access.enterAsPlatform(tenantId);
                List<DocumentModel> boletas = documentProvider.getSummaryCandidatesForUpdate(companyId, sunatEnv, fecha, today.minusDays(5),
                        Summaries.MAX_LINES, false);
                for (DocumentModel boleta : boletas) {
                    boleta.setCodeClass("summary_failed");
                    boleta.setHoldReason("needs_attention");
                    boleta.setUpdatedAt(Instant.now());
                    documentEventProvider.addDocumentEvent(boleta, null, "could not build its resumen diario: " + reason, "platform:worker", correlationId);
                }
            });
        } catch (RuntimeException e) {
            LOG.errorf(e, "could not set aside boletas of company %s, day %s", companyId, fecha);
        }
    }
}
