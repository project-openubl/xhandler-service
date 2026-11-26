package io.github.projectopenubl.xhandler.models.jpa;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDate;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.SupportSessionEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.SupportSessionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaSupportSessionProvider implements SupportSessionProvider {

    private final SupportSessionRepository repository;

    public JpaSupportSessionProvider(SupportSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public SupportSessionModel newSupportSession(UUID id) {
        SupportSessionEntity entity = new SupportSessionEntity();
        entity.setId(id);
        return new SupportSessionAdapter(entity);
    }

    @Override
    public void persist(SupportSessionModel supportSession) {
        repository.persist(SupportSessionAdapter.toEntity(supportSession));
    }

    @Override
    public void delete(SupportSessionModel supportSession) {
        repository.delete(SupportSessionAdapter.toEntity(supportSession));
    }

    @Override
    public SupportSessionModel addSupportSession(UUID id, Consumer<SupportSessionModel> init) {
        SupportSessionEntity entity = new SupportSessionEntity();
        entity.setId(id);
        SupportSessionModel model = new SupportSessionAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<SupportSessionModel> getSupportSession(UUID id) {
        return repository.findByIdOptional(id).map(SupportSessionAdapter::new);
    }

    @Override
    public Optional<SupportSessionModel> getSupportSessionForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(SupportSessionAdapter::new);
    }

    @Override
    public Optional<SupportSessionModel> getSupportSessionForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(SupportSessionAdapter::new);
    }

    @Override
    public void refresh(SupportSessionModel supportSession) {
        repository.getEntityManager().refresh(SupportSessionAdapter.toEntity(supportSession));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public List<SupportSessionModel> getSupportSessions() {
        return wrap(repository.list("order by requestedAt desc"));
    }

    private static List<SupportSessionModel> wrap(List<SupportSessionEntity> entities) {
        return entities.stream().map(e -> (SupportSessionModel) new SupportSessionAdapter(e)).toList();
    }
}
