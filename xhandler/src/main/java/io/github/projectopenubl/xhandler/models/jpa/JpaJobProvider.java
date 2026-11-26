package io.github.projectopenubl.xhandler.models.jpa;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.JobEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.JobRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaJobProvider implements JobProvider {

    private final JobRepository repository;

    public JpaJobProvider(JobRepository repository) {
        this.repository = repository;
    }

    @Override
    public JobModel newJob(UUID id) {
        JobEntity entity = new JobEntity();
        entity.setId(id);
        return new JobAdapter(entity);
    }

    @Override
    public void persist(JobModel job) {
        repository.persist(JobAdapter.toEntity(job));
    }

    @Override
    public void delete(JobModel job) {
        repository.delete(JobAdapter.toEntity(job));
    }

    @Override
    public JobModel addJob(UUID id, Consumer<JobModel> init) {
        JobEntity entity = new JobEntity();
        entity.setId(id);
        JobModel model = new JobAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<JobModel> getJob(UUID id) {
        return repository.findByIdOptional(id).map(JobAdapter::new);
    }

    @Override
    public Optional<JobModel> getJobForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(JobAdapter::new);
    }

    @Override
    public Optional<JobModel> getJobForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(JobAdapter::new);
    }

    @Override
    public void refresh(JobModel job) {
        repository.getEntityManager().refresh(JobAdapter.toEntity(job));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public List<JobModel> getJobs(String state, String name, int offset, int limit) {
        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (state != null) {
            query.append(" and state = :state");
            params.put("state", state);
        }
        if (name != null) {
            query.append(" and name = :name");
            params.put("name", name);
        }
        query.append(" order by createdAt desc");
        return wrap(repository.find(query.toString(), params).range(offset, offset + limit - 1).list());
    }

    @Override
    public String cancelTenantJob(UUID jobId) {
        Object state = repository.getEntityManager().createNativeQuery("SELECT xhandler.cancel_tenant_job(:id)")
                .setParameter("id", jobId)
                .getSingleResult();
        return state == null ? null : state.toString();
    }

    @Override
    public long countActiveJobs(String endpoint) {
        return repository.count("endpoint = ?1 and state in ('waiting', 'running')", endpoint);
    }

    private static List<JobModel> wrap(List<JobEntity> entities) {
        return entities.stream().map(e -> (JobModel) new JobAdapter(e)).toList();
    }
}
