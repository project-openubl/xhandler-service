package io.github.projectopenubl.xhandler.models.jpa;

import java.util.List;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.CredentialEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.CredentialRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaCredentialProvider implements CredentialProvider {

    private final CredentialRepository repository;

    public JpaCredentialProvider(CredentialRepository repository) {
        this.repository = repository;
    }

    @Override
    public CredentialModel newCredential(UUID id) {
        CredentialEntity entity = new CredentialEntity();
        entity.setId(id);
        return new CredentialAdapter(entity);
    }

    @Override
    public void persist(CredentialModel credential) {
        repository.persist(CredentialAdapter.toEntity(credential));
    }

    @Override
    public void delete(CredentialModel credential) {
        repository.delete(CredentialAdapter.toEntity(credential));
    }

    @Override
    public CredentialModel addCredential(UUID id, Consumer<CredentialModel> init) {
        CredentialEntity entity = new CredentialEntity();
        entity.setId(id);
        CredentialModel model = new CredentialAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<CredentialModel> getCredential(UUID id) {
        return repository.findByIdOptional(id).map(CredentialAdapter::new);
    }

    @Override
    public Optional<CredentialModel> getCredentialForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(CredentialAdapter::new);
    }

    @Override
    public Optional<CredentialModel> getCredentialForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(CredentialAdapter::new);
    }

    @Override
    public void refresh(CredentialModel credential) {
        repository.getEntityManager().refresh(CredentialAdapter.toEntity(credential));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public Optional<CredentialModel> getCredential(UUID companyId, String kind) {
        return repository.find("companyId = ?1 and kind = ?2", companyId, kind).firstResultOptional().map(CredentialAdapter::new);
    }

    @Override
    public List<CredentialModel> getCredentials(UUID companyId) {
        return wrap(repository.list("companyId = ?1 order by kind", companyId));
    }

    @Override
    public List<CredentialModel> getCredentialsToRewrapForUpdate(String masterKeyId) {
        return wrap(repository.find("masterKeyId <> ?1", masterKeyId).withLock(LockModeType.PESSIMISTIC_WRITE).list());
    }

    private static List<CredentialModel> wrap(List<CredentialEntity> entities) {
        return entities.stream().map(e -> (CredentialModel) new CredentialAdapter(e)).toList();
    }
}
