package io.github.projectopenubl.xhandler.models.jpa;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDate;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentDraftEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.DocumentDraftRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaDocumentDraftProvider implements DocumentDraftProvider {

    private final DocumentDraftRepository repository;

    public JpaDocumentDraftProvider(DocumentDraftRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentDraftModel newDocumentDraft(UUID id) {
        DocumentDraftEntity entity = new DocumentDraftEntity();
        entity.setId(id);
        return new DocumentDraftAdapter(entity);
    }

    @Override
    public void persist(DocumentDraftModel documentDraft) {
        repository.persist(DocumentDraftAdapter.toEntity(documentDraft));
    }

    @Override
    public void delete(DocumentDraftModel documentDraft) {
        repository.delete(DocumentDraftAdapter.toEntity(documentDraft));
    }

    @Override
    public DocumentDraftModel addDocumentDraft(UUID id, Consumer<DocumentDraftModel> init) {
        DocumentDraftEntity entity = new DocumentDraftEntity();
        entity.setId(id);
        DocumentDraftModel model = new DocumentDraftAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<DocumentDraftModel> getDocumentDraft(UUID id) {
        return repository.findByIdOptional(id).map(DocumentDraftAdapter::new);
    }

    @Override
    public Optional<DocumentDraftModel> getDocumentDraftForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(DocumentDraftAdapter::new);
    }

    @Override
    public Optional<DocumentDraftModel> getDocumentDraftForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(DocumentDraftAdapter::new);
    }

    @Override
    public void refresh(DocumentDraftModel documentDraft) {
        repository.getEntityManager().refresh(DocumentDraftAdapter.toEntity(documentDraft));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public List<DocumentDraftModel> getDocumentDrafts(UUID companyId, String status, int offset, int limit) {
        StringBuilder query = new StringBuilder("deletedAt is null");
        Map<String, Object> params = new HashMap<>();
        if (companyId != null) {
            query.append(" and companyId = :companyId");
            params.put("companyId", companyId);
        }
        if (status != null) {
            query.append(" and status = :status");
            params.put("status", status);
        }
        query.append(" order by createdAt desc, id desc");
        return repository.find(query.toString(), params).range(offset, offset + limit - 1).list().stream()
                .map(e -> (DocumentDraftModel) new DocumentDraftAdapter(e)).toList();
    }

    private static List<DocumentDraftModel> wrap(List<DocumentDraftEntity> entities) {
        return entities.stream().map(e -> (DocumentDraftModel) new DocumentDraftAdapter(e)).toList();
    }
}
