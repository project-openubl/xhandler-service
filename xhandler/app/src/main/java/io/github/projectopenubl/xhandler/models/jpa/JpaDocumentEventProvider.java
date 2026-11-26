package io.github.projectopenubl.xhandler.models.jpa;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDate;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEventEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.DocumentEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaDocumentEventProvider implements DocumentEventProvider {

    private final DocumentEventRepository repository;

    public JpaDocumentEventProvider(DocumentEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentEventModel newDocumentEvent(UUID id) {
        DocumentEventEntity entity = new DocumentEventEntity();
        entity.setId(id);
        return new DocumentEventAdapter(entity);
    }

    @Override
    public void persist(DocumentEventModel documentEvent) {
        repository.persist(DocumentEventAdapter.toEntity(documentEvent));
    }

    @Override
    public void delete(DocumentEventModel documentEvent) {
        repository.delete(DocumentEventAdapter.toEntity(documentEvent));
    }

    @Override
    public DocumentEventModel addDocumentEvent(UUID id, Consumer<DocumentEventModel> init) {
        DocumentEventEntity entity = new DocumentEventEntity();
        entity.setId(id);
        DocumentEventModel model = new DocumentEventAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<DocumentEventModel> getDocumentEvent(UUID id) {
        return repository.findByIdOptional(id).map(DocumentEventAdapter::new);
    }

    @Override
    public Optional<DocumentEventModel> getDocumentEventForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(DocumentEventAdapter::new);
    }

    @Override
    public Optional<DocumentEventModel> getDocumentEventForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(DocumentEventAdapter::new);
    }

    @Override
    public void refresh(DocumentEventModel documentEvent) {
        repository.getEntityManager().refresh(DocumentEventAdapter.toEntity(documentEvent));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public DocumentEventModel addDocumentEvent(DocumentModel document, Integer code, String message, String actor, String correlationId) {
        return addDocumentEvent(io.github.projectopenubl.xhandler.utils.Ids.newId(), event -> {
            event.setAt(java.time.Instant.now());
            event.setTenantId(document.getTenantId());
            event.setDocumentId(document.getId());
            event.setStatus(document.getStatus());
            event.setCode(code);
            event.setMessage(message);
            event.setActor(actor);
            event.setCorrelationId(correlationId);
        });
    }

    @Override
    public List<DocumentEventModel> getDocumentEvents(UUID documentId) {
        return repository.list("documentId = ?1 order by at, id", documentId).stream().map(e -> (DocumentEventModel) new DocumentEventAdapter(e)).toList();
    }

    private static List<DocumentEventModel> wrap(List<DocumentEventEntity> entities) {
        return entities.stream().map(e -> (DocumentEventModel) new DocumentEventAdapter(e)).toList();
    }
}
