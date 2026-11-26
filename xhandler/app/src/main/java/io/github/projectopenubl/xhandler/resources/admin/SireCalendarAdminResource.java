package io.github.projectopenubl.xhandler.resources.admin;

import io.github.projectopenubl.xhandler.representations.idm.SireCalendarEntryRepresentation;
import io.github.projectopenubl.xhandler.security.PlatformAdmin;

import io.github.projectopenubl.xhandler.models.SireProvider;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.Map;

/**
 * SUNAT's calendar of monthly obligations (DESIGN.md §11.7: RS 281-2022 annex II and its yearly
 * successors), loaded by platform admins: the due date of each period by the RUC's last digit.
 */
@Path("/api/admin/v1/sire/calendar")
public class SireCalendarAdminResource {


    private final PlatformAdmin admin;
    private final SireProvider sireProvider;

    public SireCalendarAdminResource(PlatformAdmin admin, SireProvider sireProvider) {
        this.admin = admin;
        this.sireProvider = sireProvider;
    }

    @PUT
    @Transactional
    public Map<String, Object> load(List<@Valid SireCalendarEntryRepresentation> entries) {
        String who = admin.require();
        if (entries == null || entries.isEmpty()) {
            throw io.github.projectopenubl.xhandler.resources.exceptions.ApiException.invalid("invalid_request", "Envíe al menos una fecha del cronograma.", null);
        }
        for (SireCalendarEntryRepresentation e : entries) {
            sireProvider.putCalendarEntry(e.period(), e.rucLastDigit(), e.dueDate());
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s loaded %d SIRE calendar entries", who, entries.size());
        return Map.of("loaded", entries.size());
    }

    @GET
    @Transactional
    public List<Map<String, Object>> list(@QueryParam("period") String period) {
        admin.require();
        return sireProvider.getCalendar(period).stream().map(r -> Map.<String, Object>of("period", r.period(), "ruc_last_digit", r.rucLastDigit(),
                "due_date", r.dueDate())).toList();
    }
}
