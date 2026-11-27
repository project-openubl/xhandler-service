package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.TenantEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.TenantRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaTenantProvider implements TenantProvider {

    private final TenantRepository repository;

    public JpaTenantProvider(TenantRepository repository) {
        this.repository = repository;
    }

    @Override
    public TenantModel newTenant(UUID id) {
        TenantEntity entity = new TenantEntity();
        entity.setId(id);
        return new TenantAdapter(entity);
    }

    @Override
    public void persist(TenantModel tenant) {
        repository.persist(TenantAdapter.toEntity(tenant));
    }

    @Override
    public void delete(TenantModel tenant) {
        repository.delete(TenantAdapter.toEntity(tenant));
    }

    @Override
    public TenantModel addTenant(UUID id, Consumer<TenantModel> init) {
        TenantEntity entity = new TenantEntity();
        entity.setId(id);
        TenantModel model = new TenantAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<TenantModel> getTenant(UUID id) {
        return repository.findByIdOptional(id).map(TenantAdapter::new);
    }

    @Override
    public Optional<TenantModel> getTenantForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(TenantAdapter::new);
    }

    @Override
    public Optional<TenantModel> getTenantForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(TenantAdapter::new);
    }

    @Override
    public void refresh(TenantModel tenant) {
        repository.getEntityManager().refresh(TenantAdapter.toEntity(tenant));
    }
}
