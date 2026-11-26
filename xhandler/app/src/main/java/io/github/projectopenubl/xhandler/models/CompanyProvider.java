package io.github.projectopenubl.xhandler.models;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link CompanyModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface CompanyProvider {

    /** Creates a company with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted company with {@code id}; see {@link #persist}. */
    CompanyModel newCompany(UUID id);

    void persist(CompanyModel company);

    void delete(CompanyModel company);

    CompanyModel addCompany(UUID id, Consumer<CompanyModel> init);

    Optional<CompanyModel> getCompany(UUID id);

    /** Same as {@link #getCompany}, locking the row until the transaction ends. */
    Optional<CompanyModel> getCompanyForUpdate(UUID id);

    /** Same as {@link #getCompany}, share-locking the row until the transaction ends. */
    Optional<CompanyModel> getCompanyForShare(UUID id);

    /** Re-reads the company from the database (after native updates that bypass the session). */
    void refresh(CompanyModel company);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    long countUnverifiedCompanies();

    long countVerifiedCompanies();

    /** Every company row the open tenant can see, deleted ones included. */
    long countCompanies();

    /** Companies not deleted, oldest first. */
    List<CompanyModel> getCompanies(int offset, int limit);

    /** Failed RUC verifications of a RUC in the last hour, across tenants. */
    int countRecentFailedVerifications(String ruc);

}
