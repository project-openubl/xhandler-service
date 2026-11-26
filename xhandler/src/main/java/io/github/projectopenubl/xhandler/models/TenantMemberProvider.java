package io.github.projectopenubl.xhandler.models;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link TenantMemberModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface TenantMemberProvider {

    /** Creates a tenantMember with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted tenantMember with {@code id}; see {@link #persist}. */
    TenantMemberModel newTenantMember(UUID id);

    void persist(TenantMemberModel tenantMember);

    void delete(TenantMemberModel tenantMember);

    TenantMemberModel addTenantMember(UUID id, Consumer<TenantMemberModel> init);

    Optional<TenantMemberModel> getTenantMember(UUID id);

    /** Same as {@link #getTenantMember}, locking the row until the transaction ends. */
    Optional<TenantMemberModel> getTenantMemberForUpdate(UUID id);

    /** Same as {@link #getTenantMember}, share-locking the row until the transaction ends. */
    Optional<TenantMemberModel> getTenantMemberForShare(UUID id);

    /** Re-reads the tenantMember from the database (after native updates that bypass the session). */
    void refresh(TenantMemberModel tenantMember);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    List<TenantMemberModel> getTenantMembers();

    long countTenantMembers(String role);
}
