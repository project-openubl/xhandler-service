package io.github.projectopenubl.xhandler.models.utils;

import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.SeriesModel;
import io.github.projectopenubl.xhandler.models.TenantMemberModel;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.models.WebhookSubscriptionModel;
import io.github.projectopenubl.xhandler.representations.idm.AddMemberRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CreateCompanyRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CreateSeriesRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CreateTenantRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CreateWebhookRepresentation;
import jakarta.enterprise.context.ApplicationScoped;

/** API requests applied to models (values trimmed and defaulted as the API documents). */
@ApplicationScoped
public class RepresentationToModel {

    public void updateTenant(CreateTenantRepresentation rep, TenantModel tenant) {
        tenant.setName(rep.name().strip());
    }

    public void updateCompany(CreateCompanyRepresentation rep, CompanyModel company) {
        company.setRuc(rep.ruc());
        company.setRazonSocial(rep.razonSocial().strip());
        company.setNombreComercial(rep.nombreComercial() == null ? null : rep.nombreComercial().strip());
    }

    /** A new series starts at 1 unless it continues another provider's numbering (DESIGN.md §9.2.1). */
    public void updateSeries(CreateSeriesRepresentation rep, SeriesModel series) {
        series.setTipoComprobante(rep.tipoComprobante());
        series.setSerie(rep.serie());
        series.setNextNumber(rep.nextNumber() == null ? 1 : rep.nextNumber());
    }

    public void updateTenantMember(AddMemberRepresentation rep, TenantMemberModel member) {
        member.setKind(rep.kind());
        member.setSubject(rep.subject().strip());
        member.setRole(rep.role());
    }

    public void updateWebhookSubscription(CreateWebhookRepresentation rep, WebhookSubscriptionModel subscription) {
        subscription.setUrl(rep.url());
        subscription.setEvents(rep.events().toArray(String[]::new));
    }
}
