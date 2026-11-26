package io.github.projectopenubl.xhandler.models.jpa;

import java.util.List;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.WebhookDeliveryEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.WebhookDeliveryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaWebhookDeliveryProvider implements WebhookDeliveryProvider {

    private final WebhookDeliveryRepository repository;

    public JpaWebhookDeliveryProvider(WebhookDeliveryRepository repository) {
        this.repository = repository;
    }

    @Override
    public WebhookDeliveryModel newWebhookDelivery(UUID id) {
        WebhookDeliveryEntity entity = new WebhookDeliveryEntity();
        entity.setId(id);
        return new WebhookDeliveryAdapter(entity);
    }

    @Override
    public void persist(WebhookDeliveryModel webhookDelivery) {
        repository.persist(WebhookDeliveryAdapter.toEntity(webhookDelivery));
    }

    @Override
    public void delete(WebhookDeliveryModel webhookDelivery) {
        repository.delete(WebhookDeliveryAdapter.toEntity(webhookDelivery));
    }

    @Override
    public WebhookDeliveryModel addWebhookDelivery(UUID id, Consumer<WebhookDeliveryModel> init) {
        WebhookDeliveryEntity entity = new WebhookDeliveryEntity();
        entity.setId(id);
        WebhookDeliveryModel model = new WebhookDeliveryAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<WebhookDeliveryModel> getWebhookDelivery(UUID id) {
        return repository.findByIdOptional(id).map(WebhookDeliveryAdapter::new);
    }

    @Override
    public Optional<WebhookDeliveryModel> getWebhookDeliveryForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(WebhookDeliveryAdapter::new);
    }

    @Override
    public Optional<WebhookDeliveryModel> getWebhookDeliveryForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(WebhookDeliveryAdapter::new);
    }

    @Override
    public void refresh(WebhookDeliveryModel webhookDelivery) {
        repository.getEntityManager().refresh(WebhookDeliveryAdapter.toEntity(webhookDelivery));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public List<WebhookDeliveryModel> getWebhookDeliveries(UUID subscriptionId, String status, int offset, int limit) {
        var query = status == null
                ? repository.find("subscriptionId = ?1 order by createdAt desc", subscriptionId)
                : repository.find("subscriptionId = ?1 and status = ?2 order by createdAt desc", subscriptionId, status);
        return wrap(query.range(offset, offset + limit - 1).list());
    }

    private static List<WebhookDeliveryModel> wrap(List<WebhookDeliveryEntity> entities) {
        return entities.stream().map(e -> (WebhookDeliveryModel) new WebhookDeliveryAdapter(e)).toList();
    }
}
