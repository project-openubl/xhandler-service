package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.OseEndpointEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.OseEndpointRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaOseEndpointProvider implements OseEndpointProvider {

    private final OseEndpointRepository repository;

    public JpaOseEndpointProvider(OseEndpointRepository repository) {
        this.repository = repository;
    }

    @Override
    public OseEndpointModel newOseEndpoint(UUID id) {
        OseEndpointEntity entity = new OseEndpointEntity();
        entity.setId(id);
        return new OseEndpointAdapter(entity);
    }

    @Override
    public void persist(OseEndpointModel oseEndpoint) {
        repository.persist(OseEndpointAdapter.toEntity(oseEndpoint));
    }

    @Override
    public void delete(OseEndpointModel oseEndpoint) {
        repository.delete(OseEndpointAdapter.toEntity(oseEndpoint));
    }

    @Override
    public OseEndpointModel addOseEndpoint(UUID id, Consumer<OseEndpointModel> init) {
        OseEndpointEntity entity = new OseEndpointEntity();
        entity.setId(id);
        OseEndpointModel model = new OseEndpointAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<OseEndpointModel> getOseEndpoint(UUID id) {
        return repository.findByIdOptional(id).map(OseEndpointAdapter::new);
    }

    @Override
    public Optional<OseEndpointModel> getOseEndpointForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(OseEndpointAdapter::new);
    }

    @Override
    public Optional<OseEndpointModel> getOseEndpointForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(OseEndpointAdapter::new);
    }

    @Override
    public void refresh(OseEndpointModel oseEndpoint) {
        repository.getEntityManager().refresh(OseEndpointAdapter.toEntity(oseEndpoint));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public Optional<OseEndpointModel> getOseEndpointByCompany(UUID companyId) {
        return repository.find("companyId", companyId).firstResultOptional().map(OseEndpointAdapter::new);
    }
}
