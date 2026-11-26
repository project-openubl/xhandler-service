package io.github.projectopenubl.xhandler.models.jpa;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDate;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentFileEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.DocumentFileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaDocumentFileProvider implements DocumentFileProvider {

    private final DocumentFileRepository repository;

    public JpaDocumentFileProvider(DocumentFileRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentFileModel newDocumentFile(UUID id) {
        DocumentFileEntity entity = new DocumentFileEntity();
        entity.setId(id);
        return new DocumentFileAdapter(entity);
    }

    @Override
    public void persist(DocumentFileModel documentFile) {
        repository.persist(DocumentFileAdapter.toEntity(documentFile));
    }

    @Override
    public void delete(DocumentFileModel documentFile) {
        repository.delete(DocumentFileAdapter.toEntity(documentFile));
    }

    @Override
    public DocumentFileModel addDocumentFile(UUID id, Consumer<DocumentFileModel> init) {
        DocumentFileEntity entity = new DocumentFileEntity();
        entity.setId(id);
        DocumentFileModel model = new DocumentFileAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<DocumentFileModel> getDocumentFile(UUID id) {
        return repository.findByIdOptional(id).map(DocumentFileAdapter::new);
    }

    @Override
    public Optional<DocumentFileModel> getDocumentFileForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(DocumentFileAdapter::new);
    }

    @Override
    public Optional<DocumentFileModel> getDocumentFileForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(DocumentFileAdapter::new);
    }

    @Override
    public void refresh(DocumentFileModel documentFile) {
        repository.getEntityManager().refresh(DocumentFileAdapter.toEntity(documentFile));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public Optional<DocumentFileModel> getDocumentFile(UUID documentId, String kind) {
        return repository.find("documentId = ?1 and kind = ?2", documentId, kind).firstResultOptional().map(DocumentFileAdapter::new);
    }

    @Override
    public DocumentFileModel addDocumentFile(DocumentModel document, String kind, io.github.projectopenubl.xhandler.files.FileStore.Stored stored) {
        return addDocumentFile(io.github.projectopenubl.xhandler.utils.Ids.newId(), file -> {
            file.setTenantId(document.getTenantId());
            file.setDocumentId(document.getId());
            file.setKind(kind);
            file.setStorageKey(stored.key());
            file.setSha256(stored.sha256());
            file.setSize(stored.size());
            file.setCreatedAt(java.time.Instant.now());
        });
    }

    @Override
    public List<DocumentFileModel> getDocumentFiles(UUID documentId) {
        return repository.list("documentId", documentId).stream().map(e -> (DocumentFileModel) new DocumentFileAdapter(e)).toList();
    }

    private static List<DocumentFileModel> wrap(List<DocumentFileEntity> entities) {
        return entities.stream().map(e -> (DocumentFileModel) new DocumentFileAdapter(e)).toList();
    }
}
