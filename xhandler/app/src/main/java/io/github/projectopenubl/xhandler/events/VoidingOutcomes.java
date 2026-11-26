package io.github.projectopenubl.xhandler.events;

import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEventEntity;

import io.github.projectopenubl.xhandler.events.WebhookEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;

/**
 * Originals follow their RA/RC/RR (DESIGN.md §11.3): voided when it is accepted, back to their
 * previous status (and the user told) when it is not. Runs in the transaction that wrote the
 * voiding document's outcome, with its tenant open.
 */
@ApplicationScoped
public class VoidingOutcomes {

    private final Event<DocumentStatusChanged> statusChanged;
    private final WebhookEvents webhooks;

    public VoidingOutcomes(Event<DocumentStatusChanged> statusChanged, WebhookEvents webhooks) {
        this.statusChanged = statusChanged;
        this.webhooks = webhooks;
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        DocumentEntity voiding = change.document();
        if (!voiding.isSummary() || !voiding.definitive() || "ticket_lost".equals(voiding.getCodeClass())) {
            // A lost ticket was resolved line by line already (AttemptHandler)
            return;
        }
        List<DocumentEntity> originals = DocumentEntity.<DocumentEntity>find("voidedByDocumentId = ?1 and status = 'voiding' order by id", voiding.getId())
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
        Instant now = Instant.now();
        if ("exception".equals(voiding.getStatus())) {
            // Not a rejection: the voiding can still be fixed and resent, so the originals stay in
            // voiding (the user may cancel the void instead, POST …/void/cancel)
            for (DocumentEntity original : originals) {
                DocumentEventEntity.record(original, voiding.getSunatCode(), "voiding " + voiding.serieNumero() + " needs attention: "
                        + voiding.getSunatDescription(), "platform:worker", change.correlationId());
            }
            return;
        }
        boolean accepted = DocumentEntity.ACCEPTED.contains(voiding.getStatus()) || "voided".equals(voiding.getStatus());
        for (DocumentEntity original : originals) {
            String previous = original.getStatus();
            if (accepted) {
                original.setStatus("voided");
                DocumentEventEntity.record(original, voiding.getSunatCode(), "voided by " + voiding.serieNumero(), "platform:worker",
                        change.correlationId());
            } else {
                original.setStatus(original.getStatusBeforeVoiding() == null ? "accepted" : original.getStatusBeforeVoiding());
                original.setVoidedByDocumentId(null);
                DocumentEventEntity.record(original, voiding.getSunatCode(), "voiding " + voiding.serieNumero() + " not accepted: "
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
