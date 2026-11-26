package io.github.projectopenubl.xhandler.resources.admin;

import io.github.projectopenubl.xhandler.models.SireProvider;
import io.github.projectopenubl.xhandler.representations.idm.SireCalendarEntryRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.SireCalendarLoadedRepresentation;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.security.PlatformAdmin;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.time.LocalDate;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;

/**
 * SUNAT's calendar of monthly obligations (DESIGN.md §11.7: RS 281-2022 annex II and its yearly
 * successors), loaded by platform admins: the due date of each period by the RUC's last digit.
 */
@Extension(name = AdminOpenApi.PROFILE_EXTENSION, value = "")
@Path("/api/admin/v1/sire/calendar")
public class SireCalendarAdminResource {

    private final PlatformAdmin admin;
    private final SireProvider sireProvider;

    public SireCalendarAdminResource(PlatformAdmin admin, SireProvider sireProvider) {
        this.admin = admin;
        this.sireProvider = sireProvider;
    }

    @Operation(operationId = "adminLoadSireCalendar", summary = "Carga o reemplaza fechas del cronograma de obligaciones de SUNAT")
    @PUT
    @Transactional
    public SireCalendarLoadedRepresentation load(List<@Valid SireCalendarEntryRepresentation> entries) {
        String who = admin.require();
        if (entries == null || entries.isEmpty()) {
            throw ApiException.invalid("invalid_request", "Envíe al menos una fecha del cronograma.", null);
        }
        for (SireCalendarEntryRepresentation e : entries) {
            sireProvider.putCalendarEntry(e.period(), e.rucLastDigit(), e.dueDate());
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s loaded %d SIRE calendar entries", who, entries.size());
        return new SireCalendarLoadedRepresentation(entries.size());
    }

    @Operation(operationId = "adminListSireCalendar", summary = "Cronograma de obligaciones (de un periodo AAAAMM, o todo)")
    @GET
    @Transactional
    public List<SireCalendarEntryRepresentation> list(@QueryParam("period") String period) {
        admin.require();
        return sireProvider.getCalendar(period).stream()
                .map(r -> new SireCalendarEntryRepresentation(r.period(), r.rucLastDigit(), LocalDate.parse(r.dueDate())))
                .toList();
    }
}
