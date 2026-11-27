package io.github.projectopenubl.xhandler.models.jpa;

import java.util.List;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.CompanyEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.CompanyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaCompanyProvider implements CompanyProvider {

    private final CompanyRepository repository;

    public JpaCompanyProvider(CompanyRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompanyModel newCompany(UUID id) {
        CompanyEntity entity = new CompanyEntity();
        entity.setId(id);
        return new CompanyAdapter(entity);
    }

    @Override
    public void persist(CompanyModel company) {
        repository.persist(CompanyAdapter.toEntity(company));
    }

    @Override
    public void delete(CompanyModel company) {
        repository.delete(CompanyAdapter.toEntity(company));
    }

    @Override
    public CompanyModel addCompany(UUID id, Consumer<CompanyModel> init) {
        CompanyEntity entity = new CompanyEntity();
        entity.setId(id);
        CompanyModel model = new CompanyAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<CompanyModel> getCompany(UUID id) {
        return repository.findByIdOptional(id).map(CompanyAdapter::new);
    }

    @Override
    public Optional<CompanyModel> getCompanyForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(CompanyAdapter::new);
    }

    @Override
    public Optional<CompanyModel> getCompanyForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(CompanyAdapter::new);
    }

    @Override
    public void refresh(CompanyModel company) {
        repository.getEntityManager().refresh(CompanyAdapter.toEntity(company));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public long countCompanies() {
        return repository.count();
    }

    @Override
    public List<CompanyModel> getCompanies(int offset, int limit) {
        return wrap(repository.find("deletedAt is null order by createdAt").range(offset, offset + limit - 1).list());
    }

    @Override
    public int countRecentFailedVerifications(String ruc) {
        Object failures = repository.getEntityManager()
                .createNativeQuery("SELECT xhandler.recent_failed_verifications(:ruc, interval '1 hour')")
                .setParameter("ruc", ruc)
                .getSingleResult();
        return ((Number) failures).intValue();
    }


    private static List<CompanyModel> wrap(List<CompanyEntity> entities) {
        return entities.stream().map(e -> (CompanyModel) new CompanyAdapter(e)).toList();
    }
}
