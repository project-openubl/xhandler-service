package io.github.projectopenubl.xhandler.models.jpa;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDate;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.SeriesEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.SeriesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaSeriesProvider implements SeriesProvider {

    private final SeriesRepository repository;

    public JpaSeriesProvider(SeriesRepository repository) {
        this.repository = repository;
    }

    @Override
    public SeriesModel newSeries(UUID id) {
        SeriesEntity entity = new SeriesEntity();
        entity.setId(id);
        return new SeriesAdapter(entity);
    }

    @Override
    public void persist(SeriesModel series) {
        repository.persist(SeriesAdapter.toEntity(series));
    }

    @Override
    public void delete(SeriesModel series) {
        repository.delete(SeriesAdapter.toEntity(series));
    }

    @Override
    public SeriesModel addSeries(UUID id, Consumer<SeriesModel> init) {
        SeriesEntity entity = new SeriesEntity();
        entity.setId(id);
        SeriesModel model = new SeriesAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<SeriesModel> getSeries(UUID id) {
        return repository.findByIdOptional(id).map(SeriesAdapter::new);
    }

    @Override
    public Optional<SeriesModel> getSeriesForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(SeriesAdapter::new);
    }

    @Override
    public Optional<SeriesModel> getSeriesForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(SeriesAdapter::new);
    }

    @Override
    public void refresh(SeriesModel series) {
        repository.getEntityManager().refresh(SeriesAdapter.toEntity(series));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public List<SeriesModel> getActiveSeries(UUID companyId, String sunatEnv, String tipoComprobante) {
        return wrap(repository.list("companyId = ?1 and sunatEnv = ?2 and tipoComprobante = ?3 and active order by isDefault desc, serie",
                companyId, sunatEnv, tipoComprobante));
    }

    @Override
    public List<SeriesModel> getSeries(UUID companyId, String sunatEnv) {
        return wrap(repository.list("companyId = ?1 and sunatEnv = ?2 order by tipoComprobante, serie", companyId, sunatEnv));
    }

    @Override
    public boolean seriesExists(UUID companyId, UUID seriesId) {
        return repository.count("id = ?1 and companyId = ?2", seriesId, companyId) > 0;
    }

    @Override
    public boolean seriesExists(UUID companyId, String sunatEnv, String tipoComprobante, String serie) {
        return repository.count("companyId = ?1 and sunatEnv = ?2 and tipoComprobante = ?3 and serie = ?4", companyId, sunatEnv,
                tipoComprobante, serie) > 0;
    }

    @Override
    public int moveNextNumber(UUID seriesId, long nextNumber) {
        return repository.getEntityManager().createNativeQuery("""
                        UPDATE xhandler.series SET next_number = :next, updated_at = now()
                        WHERE id = :id AND next_number <= :next""")
                .setParameter("next", nextNumber).setParameter("id", seriesId).executeUpdate();
    }

    @Override
    public void setActive(UUID seriesId, boolean active) {
        repository.getEntityManager().createNativeQuery("UPDATE xhandler.series SET active = :active, updated_at = now() WHERE id = :id")
                .setParameter("active", active).setParameter("id", seriesId).executeUpdate();
    }

    private static List<SeriesModel> wrap(List<SeriesEntity> entities) {
        return entities.stream().map(e -> (SeriesModel) new SeriesAdapter(e)).toList();
    }
}
