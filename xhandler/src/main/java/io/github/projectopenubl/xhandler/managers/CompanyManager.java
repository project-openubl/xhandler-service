package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

/** Companies (RUCs) of the open tenant. */
@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class CompanyManager {

    private final CompanyProvider companyProvider;

    public CompanyManager(CompanyProvider companyProvider) {
        this.companyProvider = companyProvider;
    }

    /** A company of the open tenant (row-level security already limits the query to it); 404 if deleted. */
    public CompanyModel getCompany(UUID companyId) {
        return check(companyProvider.getCompany(companyId));
    }

    /** Same as {@link #getCompany}, locking the row until the transaction ends. */
    public CompanyModel getCompanyForUpdate(UUID companyId) {
        return check(companyProvider.getCompanyForUpdate(companyId));
    }

    private static CompanyModel check(Optional<CompanyModel> company) {
        return company.filter(c -> c.getDeletedAt() == null).orElseThrow(ApiException::notFound);
    }
}
