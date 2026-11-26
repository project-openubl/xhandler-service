package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.DefaultSeries;
import io.github.projectopenubl.xhandler.models.jpa.entities.CompanyEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.SeriesEntity;

import io.github.projectopenubl.xhandler.Api;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;

/**
 * Verified companies for tests. They don't count toward the unverified-company limit, so tests can
 * create them on every run against a database that outlives the run.
 */
@ApplicationScoped
public class TestCompanies {

    private final TenantAccess access;

    public TestCompanies(TenantAccess access) {
        this.access = access;
    }

    public UUID verified(UUID tenantId, String sunatEnv) {
        return verified(tenantId, sunatEnv, Api.randomRuc());
    }

    public UUID verified(UUID tenantId, String sunatEnv, String ruc) {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            CompanyEntity company = new CompanyEntity();
            company.setId(Ids.newId());
            company.setTenantId(tenantId);
            company.setRuc(ruc);
            company.setRazonSocial("Empresa " + company.getRuc());
            company.setSunatEnv(sunatEnv);
            company.setRucVerifiedAt(Instant.now());
            company.persist();
            for (DefaultSeries.Entry entry : DefaultSeries.ENTRIES) {
                SeriesEntity series = new SeriesEntity();
                series.setId(Ids.newId());
                series.setTenantId(tenantId);
                series.setCompanyId(company.getId());
                series.setSunatEnv(sunatEnv);
                series.setTipoComprobante(entry.tipoComprobante());
                series.setSerie(entry.serie());
                series.setDefault(true);
                series.persist();
            }
            return company.getId();
        });
    }
}
