package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.CatalogProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaCatalogProvider implements CatalogProvider {

    private final EntityManager em;

    public JpaCatalogProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<CatalogRow> searchEntries(UUID companyId, String kind, String q, int limit, int offset) {
        // Searches values people look for (not JSON keys): key, name, description, document, plate
        String filter = q == null || q.isBlank() ? "" : " AND (key ILIKE :q OR data ->> 'nombre' ILIKE :q OR data ->> 'descripcion' ILIKE :q"
                + " OR data ->> 'numero_documento_identidad' ILIKE :q OR data ->> 'placa' ILIKE :q OR data ->> 'direccion' ILIKE :q)";
        var query = em.createNativeQuery("SELECT id, kind, key, CAST(data AS text), created_at, updated_at FROM xhandler.catalog_entry "
                        + "WHERE company_id = :c AND kind = :k AND deleted_at IS NULL" + filter + " ORDER BY key LIMIT :l OFFSET :o")
                .setParameter("c", companyId).setParameter("k", kind).setParameter("l", limit).setParameter("o", offset);
        if (!filter.isEmpty()) {
            query.setParameter("q", "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(JpaCatalogProvider::row).toList();
    }

    @Override
    public Optional<CatalogRow> getEntry(UUID companyId, String kind, UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, kind, key, CAST(data AS text), created_at, updated_at FROM xhandler.catalog_entry "
                        + "WHERE id = :id AND company_id = :c AND kind = :k AND deleted_at IS NULL")
                .setParameter("id", id).setParameter("c", companyId).setParameter("k", kind).getResultList();
        return rows.stream().findFirst().map(JpaCatalogProvider::row);
    }

    @Override
    public Optional<CatalogRow> getEntryByKey(UUID companyId, String kind, String key) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, kind, key, CAST(data AS text), created_at, updated_at FROM xhandler.catalog_entry "
                        + "WHERE company_id = :c AND kind = :k AND key = :key AND deleted_at IS NULL")
                .setParameter("c", companyId).setParameter("k", kind).setParameter("key", key).getResultList();
        return rows.stream().findFirst().map(JpaCatalogProvider::row);
    }

    @Override
    public void addEntry(UUID id, UUID tenantId, UUID companyId, String kind, String key, String data, String deviceId) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.catalog_entry (id, tenant_id, company_id, kind, key, data, device_id)
                        VALUES (:id, :t, :c, :k, :key, CAST(:data AS jsonb), :device)""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("c", companyId).setParameter("k", kind)
                .setParameter("key", key).setParameter("data", data)
                .setParameter("device", deviceId)
                .executeUpdate();
    }

    @Override
    public void updateEntry(UUID id, String key, String data) {
        em.createNativeQuery("UPDATE xhandler.catalog_entry SET key = :key, data = CAST(:data AS jsonb), updated_at = now() WHERE id = :id")
                .setParameter("key", key).setParameter("data", data).setParameter("id", id).executeUpdate();
    }

    @Override
    public void deleteEntry(UUID id) {
        em.createNativeQuery("UPDATE xhandler.catalog_entry SET deleted_at = now(), updated_at = now() WHERE id = :id")
                .setParameter("id", id).executeUpdate();
    }

    @Override
    public void flush() {
        em.flush();
    }


    private static CatalogRow row(Object[] r) {
        return new CatalogRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], Times.toInstant(r[4]), Times.toInstant(r[5]));
    }
}
