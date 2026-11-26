package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.BillingProvider;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Quotas from the tenant's plan, enforced at emit (DESIGN.md §11.1, §14, §26.8). Production documents
 * count per month; beta and the sandbox per day (Lima). The entitlement is cached from Lago; when
 * Lago hasn't been reached for 24 hours, quotas fall back to the free plan (fail closed). Usage is
 * counted from the documents themselves, so there is no hot counter row.
 */
@ApplicationScoped
public class EntitlementManager implements EmitQuota {

    /** A plan's limits. Null limits are unlimited. */
    public record Plan(String code, String name, Integer productionPerMonth, Integer betaPerDay, Integer sandboxPerDay,
            boolean customSmtp, boolean webhooks) {}

    /** What a tenant may do now, and what it used. */
    public record Usage(Plan plan, String source, Instant syncedAt, boolean stale, long productionThisMonth, long betaToday,
            long sandboxToday) {}

    static final Duration STALE_AFTER = Duration.ofHours(24);

    private final BillingProvider billingProvider;
    private final NotificationManager notifications;
    private final io.github.projectopenubl.xhandler.security.TenantAccess access;
    private final String defaultPlan;

    public EntitlementManager(BillingProvider billingProvider, NotificationManager notifications, io.github.projectopenubl.xhandler.security.TenantAccess access,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "xhandler.billing.default-plan", defaultValue = "free") String defaultPlan) {
        this.defaultPlan = defaultPlan;
        this.billingProvider = billingProvider;
        this.notifications = notifications;
        this.access = access;
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void checkEmit(UUID tenantId, String sunatEnv) {
        Usage usage = usage(tenantId);
        Plan plan = usage.plan();
        switch (sunatEnv) {
            case "production" -> {
                Integer limit = plan.productionPerMonth();
                if (limit != null && usage.productionThisMonth() >= limit) {
                    // The emit is refused (its transaction rolls back): the notice commits on its own
                    io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                        access.enterAsPlatform(tenantId);
                        notifyQuota(tenantId, usage, 100);
                    });
                    throw new ApiException(402, "quota_exceeded",
                            "Alcanzó el límite de " + limit + " documentos del mes en su plan " + plan.name() + ".");
                }
                if (limit != null && usage.productionThisMonth() + 1 >= Math.ceil(limit * 0.8)) {
                    notifyQuota(tenantId, usage, 80);
                }
            }
            case "beta" -> check(plan.betaPerDay(), usage.betaToday(), "SUNAT beta");
            default -> check(plan.sandboxPerDay(), usage.sandboxToday(), "el ambiente de pruebas");
        }
    }

    private static void check(Integer limit, long used, String where) {
        if (limit != null && used >= limit) {
            throw new ApiException(429, "quota_exceeded", "Alcanzó el límite diario de " + limit + " documentos en " + where + ".");
        }
    }

    /** With the tenant open. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Usage usage(UUID tenantId) {
        java.util.Optional<BillingProvider.EntitlementRow> row = billingProvider.getEntitlement(tenantId);
        String code = row.isEmpty() ? defaultPlan : row.get().planCode();
        String source = row.isEmpty() ? "default" : row.get().source();
        Instant syncedAt = row.isEmpty() ? null : row.get().syncedAt();
        boolean stale = "lago".equals(source) && syncedAt != null && syncedAt.isBefore(Instant.now().minus(STALE_AFTER));
        // Lago unreachable for too long: fail closed to the free plan's quotas
        Plan plan = plan(stale ? "free" : code);
        LocalDate today = io.github.projectopenubl.xhandler.utils.Deadlines.todayInLima();
        Instant monthStart = YearMonth.from(today).atDay(1).atStartOfDay(io.github.projectopenubl.xhandler.utils.Deadlines.LIMA).toInstant();
        Instant dayStart = today.atStartOfDay(io.github.projectopenubl.xhandler.utils.Deadlines.LIMA).toInstant();
        BillingProvider.UsageCounts counts = billingProvider.countApiDocuments(monthStart, dayStart);
        return new Usage(plan, source, syncedAt, stale, counts.productionThisMonth(), counts.betaToday(), counts.sandboxToday());
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public Plan plan(String code) {
        BillingProvider.PlanRow r = billingProvider.getPlan(code).orElseGet(billingProvider::getFreePlan);
        return new Plan(r.code(), r.name(), r.productionPerMonth(), r.betaPerDay(), r.sandboxPerDay(), r.customSmtp(), r.webhooks());
    }

    /** Whether the service knows this plan code. */
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean known(String code) {
        return billingProvider.isKnownPlan(code);
    }

    /** Sets a tenant's plan (from Lago, or by a platform admin). Unknown codes are refused, never mapped to free. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void setPlan(UUID tenantId, String code, String source) {
        if (!known(code)) {
            throw ApiException.invalid("unknown_plan", "Plan desconocido: " + code + ".", null);
        }
        billingProvider.upsertEntitlement(tenantId, plan(code).code(), source);
    }

    /** The plan allows a tenant SMTP server (free tenants send through the platform, §26.8). */
    @Transactional(Transactional.TxType.MANDATORY)
    public void requireCustomSmtp(CompanyModel company) {
        if (!usage(company.getTenantId()).plan().customSmtp()) {
            throw new ApiException(402, "plan_required", "Su plan no permite un servidor de correo propio.");
        }
    }

    /** 80% and 100% of the monthly quota, notified once per month each (§26.6). */
    private void notifyQuota(UUID tenantId, Usage usage, int percent) {
        String period = YearMonth.now(io.github.projectopenubl.xhandler.utils.Deadlines.LIMA).toString();
        billingProvider.ensureEntitlement(tenantId);
        int updated = billingProvider.markQuotaNotified(tenantId, percent, period);
        if (updated == 1) {
            notifications.publish(tenantId, NotificationManager.QUOTA, "Uso del plan: " + percent + "%",
                    "Su empresa usó el " + percent + "% de los " + usage.plan().productionPerMonth() + " documentos del mes de su plan "
                            + usage.plan().name() + ".", null);
        }
    }
}
