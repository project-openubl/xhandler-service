package io.github.projectopenubl.xhandler.models.jpa;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDate;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.AuditLogEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.AuditLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaAuditLogProvider implements AuditLogProvider {

    private final AuditLogRepository repository;

    public JpaAuditLogProvider(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditLogModel newAuditLog(UUID id) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(id);
        return new AuditLogAdapter(entity);
    }

    @Override
    public void persist(AuditLogModel auditLog) {
        repository.persist(AuditLogAdapter.toEntity(auditLog));
    }

    @Override
    public void delete(AuditLogModel auditLog) {
        repository.delete(AuditLogAdapter.toEntity(auditLog));
    }

    @Override
    public AuditLogModel addAuditLog(UUID id, Consumer<AuditLogModel> init) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(id);
        AuditLogModel model = new AuditLogAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<AuditLogModel> getAuditLog(UUID id) {
        return repository.findByIdOptional(id).map(AuditLogAdapter::new);
    }

    @Override
    public Optional<AuditLogModel> getAuditLogForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(AuditLogAdapter::new);
    }

    @Override
    public Optional<AuditLogModel> getAuditLogForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(AuditLogAdapter::new);
    }

    @Override
    public void refresh(AuditLogModel auditLog) {
        repository.getEntityManager().refresh(AuditLogAdapter.toEntity(auditLog));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public List<AuditLogModel> getAuditLogs(int offset, int limit) {
        return wrap(repository.find("order by at desc, id desc").range(offset, offset + limit - 1).list());
    }

    @Override
    public long countAuditLogs(String action, String objectId) {
        return repository.count("action = ?1 and objectId = ?2", action, objectId);
    }

    private static List<AuditLogModel> wrap(List<AuditLogEntity> entities) {
        return entities.stream().map(e -> (AuditLogModel) new AuditLogAdapter(e)).toList();
    }
}
