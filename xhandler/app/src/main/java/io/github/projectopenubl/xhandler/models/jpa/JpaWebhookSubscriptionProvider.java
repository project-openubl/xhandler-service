package io.github.projectopenubl.xhandler.models.jpa;

import java.util.List;
import io.github.projectopenubl.xhandler.models.*;
import io.github.projectopenubl.xhandler.models.jpa.entities.WebhookSubscriptionEntity;
import io.github.projectopenubl.xhandler.models.jpa.repositories.WebhookSubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaWebhookSubscriptionProvider implements WebhookSubscriptionProvider {

    private final WebhookSubscriptionRepository repository;

    public JpaWebhookSubscriptionProvider(WebhookSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Override
    public WebhookSubscriptionModel newWebhookSubscription(UUID id) {
        WebhookSubscriptionEntity entity = new WebhookSubscriptionEntity();
        entity.setId(id);
        return new WebhookSubscriptionAdapter(entity);
    }

    @Override
    public void persist(WebhookSubscriptionModel webhookSubscription) {
        repository.persist(WebhookSubscriptionAdapter.toEntity(webhookSubscription));
    }

    @Override
    public void delete(WebhookSubscriptionModel webhookSubscription) {
        repository.delete(WebhookSubscriptionAdapter.toEntity(webhookSubscription));
    }

    @Override
    public WebhookSubscriptionModel addWebhookSubscription(UUID id, Consumer<WebhookSubscriptionModel> init) {
        WebhookSubscriptionEntity entity = new WebhookSubscriptionEntity();
        entity.setId(id);
        WebhookSubscriptionModel model = new WebhookSubscriptionAdapter(entity);
        init.accept(model);
        repository.persist(entity);
        return model;
    }

    @Override
    public Optional<WebhookSubscriptionModel> getWebhookSubscription(UUID id) {
        return repository.findByIdOptional(id).map(WebhookSubscriptionAdapter::new);
    }

    @Override
    public Optional<WebhookSubscriptionModel> getWebhookSubscriptionForUpdate(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(WebhookSubscriptionAdapter::new);
    }

    @Override
    public Optional<WebhookSubscriptionModel> getWebhookSubscriptionForShare(UUID id) {
        return repository.findByIdOptional(id, LockModeType.PESSIMISTIC_READ).map(WebhookSubscriptionAdapter::new);
    }

    @Override
    public void refresh(WebhookSubscriptionModel webhookSubscription) {
        repository.getEntityManager().refresh(WebhookSubscriptionAdapter.toEntity(webhookSubscription));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public List<WebhookSubscriptionModel> getActiveWebhookSubscriptions(UUID tenantId) {
        return wrap(repository.list("tenantId = ?1 and active and deletedAt is null", tenantId));
    }

    @Override
    public List<WebhookSubscriptionModel> getWebhookSubscriptions() {
        return wrap(repository.list("deletedAt is null order by createdAt"));
    }

    private static List<WebhookSubscriptionModel> wrap(List<WebhookSubscriptionEntity> entities) {
        return entities.stream().map(e -> (WebhookSubscriptionModel) new WebhookSubscriptionAdapter(e)).toList();
    }
}
