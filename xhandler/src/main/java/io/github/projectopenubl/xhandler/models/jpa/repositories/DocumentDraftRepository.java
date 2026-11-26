package io.github.projectopenubl.xhandler.models.jpa.repositories;

import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentDraftEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class DocumentDraftRepository implements PanacheRepositoryBase<DocumentDraftEntity, UUID> {
}
