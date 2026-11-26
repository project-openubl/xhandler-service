package io.github.projectopenubl.xhandler.models.jpa.repositories;

import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class DocumentRepository implements PanacheRepositoryBase<DocumentEntity, UUID> {
}
