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
 * Originals follow their RA/RC/RR (DESIGN.md §11.3): voided when it is accepted, back to their
 * previous status (and the user told) when it is not. Runs in the transaction that wrote the
 * voiding document's outcome, with its tenant open.
 */
@ApplicationScoped
public class VoidingOutcomes {

    private final DocumentProvider documentProvider;

    private final DocumentEventProvider documentEventProvider;

    private final Event<DocumentStatusChanged> statusChanged;
    private final WebhookEvents webhooks;

    public VoidingOutcomes(Event<DocumentStatusChanged> statusChanged, WebhookEvents webhooks, DocumentEventProvider documentEventProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.documentEventProvider = documentEventProvider;
        this.statusChanged = statusChanged;
        this.webhooks = webhooks;
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        DocumentModel voiding = change.document();
        if (!voiding.isSummary() || !voiding.definitive() || "ticket_lost".equals(voiding.getCodeClass())) {
            // A lost ticket was resolved line by line already (AttemptHandler)
            return;
        }
        List<DocumentModel> originals = documentProvider.getVoidingOriginalsForUpdate(voiding.getId());
        Instant now = Instant.now();
        if ("exception".equals(voiding.getStatus())) {
            // Not a rejection: the voiding can still be fixed and resent, so the originals stay in
            // voiding (the user may cancel the void instead, POST …/void/cancel)
            for (DocumentModel original : originals) {
                documentEventProvider.addDocumentEvent(original, voiding.getSunatCode(), "voiding " + voiding.serieNumero() + " needs attention: "
                        + voiding.getSunatDescription(), "platform:worker", change.correlationId());
            }
            return;
        }
        boolean accepted = DocumentModel.ACCEPTED.contains(voiding.getStatus()) || "voided".equals(voiding.getStatus());
        for (DocumentModel original : originals) {
            String previous = original.getStatus();
            if (accepted) {
                original.setStatus("voided");
                documentEventProvider.addDocumentEvent(original, voiding.getSunatCode(), "voided by " + voiding.serieNumero(), "platform:worker",
                        change.correlationId());
            } else {
                original.setStatus(original.getStatusBeforeVoiding() == null ? "accepted" : original.getStatusBeforeVoiding());
                original.setVoidedByDocumentId(null);
                documentEventProvider.addDocumentEvent(original, voiding.getSunatCode(), "voiding " + voiding.serieNumero() + " not accepted: "
                        + voiding.getSunatDescription(), "platform:worker", change.correlationId());
                webhooks.publish(original.getTenantId(), "document.void_rejected", "xh:document:" + original.getId(), WebhookEvents.DOCUMENT_SCHEMA,
                        WebhookEvents.documentData(original));
            }
            original.setStatusBeforeVoiding(null);
            original.setUpdatedAt(now);
            statusChanged.fire(new DocumentStatusChanged(original, previous, change.correlationId()));
        }
    }
}
