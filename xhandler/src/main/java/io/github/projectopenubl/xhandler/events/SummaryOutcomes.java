package io.github.projectopenubl.xhandler.events;

import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.DocumentModel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import java.time.Instant;
import java.util.List;

/**
 * Documents in a resumen diario follow its outcome (DESIGN.md §9.2.4). Runs in the transaction that
 * wrote the RC's outcome, with its tenant open.
 */
@ApplicationScoped
public class SummaryOutcomes {

    private final DocumentProvider documentProvider;

    private final DocumentEventProvider documentEventProvider;

    private final Event<DocumentStatusChanged> statusChanged;

    public SummaryOutcomes(Event<DocumentStatusChanged> statusChanged, DocumentEventProvider documentEventProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.documentEventProvider = documentEventProvider;
        this.statusChanged = statusChanged;
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        DocumentModel rc = change.document();
        if (!"RC".equals(rc.getTipoComprobante()) || !rc.definitive() || "ticket_lost".equals(rc.getCodeClass())) {
            // A lost ticket was resolved line by line already (AttemptHandler)
            return;
        }
        List<DocumentModel> lines = documentProvider.getSummaryLinesForUpdate(rc.getId());
        Instant now = Instant.now();
        for (DocumentModel line : lines) {
            if (line.definitive() && !"exception".equals(line.getStatus())) {
                continue;
            }
            String previous = line.getStatus();
            if (DocumentModel.ACCEPTED.contains(rc.getStatus())) {
                line.setStatus(rc.getStatus());
                line.setSunatCode(rc.getSunatCode());
                line.setSunatDescription("Aceptado en el resumen diario " + rc.serieNumero());
                line.setHoldReason(null);
            } else {
                // The RC failed: the boletas need a person (retrying puts them in a new RC)
                line.setStatus("exception");
                line.setSunatCode(rc.getSunatCode());
                line.setSunatDescription("El resumen diario " + rc.serieNumero() + " no fue aceptado: " + rc.getSunatDescription());
                line.setHoldReason("needs_attention");
            }
            line.setNextAttemptAt(null);
            line.setProgressAt(now);
            line.setUpdatedAt(now);
            documentEventProvider.addDocumentEvent(line, line.getSunatCode(), line.getSunatDescription(), "platform:worker", change.correlationId());
            if (!line.getStatus().equals(previous)) {
                statusChanged.fire(new DocumentStatusChanged(line, previous, change.correlationId()));
            }
        }
    }
}
