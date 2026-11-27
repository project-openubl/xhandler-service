package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.SeriesProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.models.DefaultSeries;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.SeriesModel;

import io.github.projectopenubl.xhandler.Api;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;

/** Verified companies for tests, created on every run against a database that outlives the run. */
@ApplicationScoped
public class TestCompanies {

    private final SeriesProvider seriesProvider;

    private final CompanyProvider companyProvider;

    private final TenantAccess access;

    public TestCompanies(TenantAccess access, CompanyProvider companyProvider, SeriesProvider seriesProvider) {
        this.seriesProvider = seriesProvider;
        this.companyProvider = companyProvider;
        this.access = access;
    }

    public UUID verified(UUID tenantId, String sunatEnv) {
        return verified(tenantId, sunatEnv, Api.randomRuc());
    }

    public UUID verified(UUID tenantId, String sunatEnv, String ruc) {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            CompanyModel company = companyProvider.newCompany(Ids.newId());
            company.setTenantId(tenantId);
            company.setRuc(ruc);
            company.setRazonSocial("Empresa " + company.getRuc());
            company.setSunatEnv(sunatEnv);
            company.setRucVerifiedAt(Instant.now());
            companyProvider.persist(company);
            for (DefaultSeries.Entry entry : DefaultSeries.ENTRIES) {
                SeriesModel series = seriesProvider.newSeries(Ids.newId());
                series.setTenantId(tenantId);
                series.setCompanyId(company.getId());
                series.setSunatEnv(sunatEnv);
                series.setTipoComprobante(entry.tipoComprobante());
                series.setSerie(entry.serie());
                series.setDefault(true);
                seriesProvider.persist(series);
            }
            return company.getId();
        });
    }
}
