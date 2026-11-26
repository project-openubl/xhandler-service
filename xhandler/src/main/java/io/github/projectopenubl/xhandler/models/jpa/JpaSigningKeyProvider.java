package io.github.projectopenubl.xhandler.models.jpa;

import java.util.List;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.SigningKeyEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.SigningKeyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaSigningKeyProvider implements SigningKeyProvider {

    private final SigningKeyRepository repository;

    public JpaSigningKeyProvider(SigningKeyRepository repository) {
        this.repository = repository;
    }

    @Override
    public SigningKeyModel newSigningKey(UUID id) {
        SigningKeyEntity entity = new SigningKeyEntity();
        entity.setId(id);
        return new SigningKeyAdapter(entity);
    }

    @Override
    public void persist(SigningKeyModel signingKey) {
        repository.persist(SigningKeyAdapter.toEntity(signingKey));
    }

    @Override
    public void delete(SigningKeyModel signingKey) {
        repository.delete(SigningKeyAdapter.toEntity(signingKey));
    }

    @Override
    public SigningKeyModel addSigningKey(UUID id, Consumer<SigningKeyModel> init) {
        SigningKeyEntity entity = new SigningKeyEntity();
        entity.setId(id);
        SigningKeyModel model = new SigningKeyAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<SigningKeyModel> getSigningKey(UUID id) {
        return repository.findByIdOptional(id).map(SigningKeyAdapter::new);
    }

    @Override
    public Optional<SigningKeyModel> getSigningKeyForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(SigningKeyAdapter::new);
    }

    @Override
    public Optional<SigningKeyModel> getSigningKeyForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(SigningKeyAdapter::new);
    }

    @Override
    public void refresh(SigningKeyModel signingKey) {
        repository.getEntityManager().refresh(SigningKeyAdapter.toEntity(signingKey));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public Optional<SigningKeyModel> getActiveSigningKey(UUID companyId) {
        return repository.find("companyId = ?1 and status = 'active'", companyId).firstResultOptional().map(SigningKeyAdapter::new);
    }

    @Override
    public List<SigningKeyModel> getSigningKeysForUpdate() {
        return wrap(repository.findAll().withLock(LockModeType.PESSIMISTIC_WRITE).list());
    }

    private static List<SigningKeyModel> wrap(List<SigningKeyEntity> entities) {
        return entities.stream().map(e -> (SigningKeyModel) new SigningKeyAdapter(e)).toList();
    }
}
