package io.github.projectopenubl.xhandler.models.jpa;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDate;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.TenantMemberEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.TenantMemberRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaTenantMemberProvider implements TenantMemberProvider {

    private final TenantMemberRepository repository;

    public JpaTenantMemberProvider(TenantMemberRepository repository) {
        this.repository = repository;
    }

    @Override
    public TenantMemberModel newTenantMember(UUID id) {
        TenantMemberEntity entity = new TenantMemberEntity();
        entity.setId(id);
        return new TenantMemberAdapter(entity);
    }

    @Override
    public void persist(TenantMemberModel tenantMember) {
        repository.persist(TenantMemberAdapter.toEntity(tenantMember));
    }

    @Override
    public void delete(TenantMemberModel tenantMember) {
        repository.delete(TenantMemberAdapter.toEntity(tenantMember));
    }

    @Override
    public TenantMemberModel addTenantMember(UUID id, Consumer<TenantMemberModel> init) {
        TenantMemberEntity entity = new TenantMemberEntity();
        entity.setId(id);
        TenantMemberModel model = new TenantMemberAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<TenantMemberModel> getTenantMember(UUID id) {
        return repository.findByIdOptional(id).map(TenantMemberAdapter::new);
    }

    @Override
    public Optional<TenantMemberModel> getTenantMemberForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(TenantMemberAdapter::new);
    }

    @Override
    public Optional<TenantMemberModel> getTenantMemberForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(TenantMemberAdapter::new);
    }

    @Override
    public void refresh(TenantMemberModel tenantMember) {
        repository.getEntityManager().refresh(TenantMemberAdapter.toEntity(tenantMember));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public List<TenantMemberModel> getTenantMembers() {
        return wrap(repository.list("order by createdAt"));
    }

    @Override
    public long countTenantMembers(String role) {
        return repository.count("role = ?1", role);
    }

    private static List<TenantMemberModel> wrap(List<TenantMemberEntity> entities) {
        return entities.stream().map(e -> (TenantMemberModel) new TenantMemberAdapter(e)).toList();
    }
}
