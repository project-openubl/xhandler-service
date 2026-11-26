package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.managers.RegisterManager;
import io.github.projectopenubl.xhandler.utils.RegisterFormats;

import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.ReportProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a report (DESIGN.md §11.9): rows read in one transaction, the file rendered and stored
 * outside it (§10.8), the result recorded in another.
 */
@ApplicationScoped
public class ReportJob implements JobHandler {

    private final CompanyProvider companyProvider;

    public static final String NAME = "report.generate";

    private final TenantAccess access;
    private final RegisterManager registers;
    private final FileStore files;
    private final ReportProvider reportProvider;

    public ReportJob(TenantAccess access, RegisterManager registers, FileStore files, ReportProvider reportProvider, CompanyProvider companyProvider) {
        this.companyProvider = companyProvider;
        this.access = access;
        this.registers = registers;
        this.files = files;
        this.reportProvider = reportProvider;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        UUID reportId = context.uuidArg("report_id");
        try {
            return generate(context, reportId);
        } catch (RuntimeException e) {
            // Whatever failed, the report must not stay pending
            org.jboss.logging.Logger.getLogger(ReportJob.class).errorf(e, "report %s failed", reportId);
            finish(context.tenantId(), reportId, "failed", null, null, null, "No se pudo generar el reporte.");
            return Map.of("status", "failed");
        }
    }

    private Map<String, Object> generate(JobContext context, UUID reportId) {
        record Input(String register, String format, String period, String ruc, String razonSocial, List<RegisterManager.Row> rows) {}
        Input input = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(context.tenantId());
            ReportProvider.ReportRequestRow r = reportProvider.getReportRequest(reportId);
            CompanyModel company = companyProvider.getCompany(r.companyId()).orElse(null);
            String register = r.register();
            List<RegisterManager.Row> rows = "sales".equals(register)
                    ? registers.sales(company.getId(), r.sunatEnv(), r.period())
                    : registers.purchases(company.getId(), r.period());
            return new Input(register, r.format(), r.period(), company.getRuc(), company.getRazonSocial(), rows);
        });
        long missing = input.rows().stream().filter(RegisterManager.Row::amountsMissing).count();
        if (missing > 0) {
            // Never zeros instead of the real bases: the backfill job completes them (§11.9)
            finish(context.tenantId(), reportId, "failed", null, null, null,
                    missing + " comprobantes aún no tienen el desglose de impuestos; se completa en unos minutos. Vuelva a generar el reporte.");
            return Map.of("status", "failed", "missing_amounts", missing);
        }
        RegisterFormats.File file;
        try {
            file = switch (input.format()) {
                case "csv" -> RegisterFormats.csv(input.register(), input.ruc(), input.period(), input.rows());
                case "xlsx" -> RegisterFormats.xlsx(input.register(), input.ruc(), input.period(), input.rows());
                case "ple" -> RegisterFormats.ple(input.register(), input.ruc(), input.period(), input.rows());
                default -> RegisterFormats.sire("sales".equals(input.register()) ? "rvie" : "rce", input.ruc(), input.razonSocial(),
                        input.period(), input.rows());
            };
        } catch (IllegalStateException e) {
            finish(context.tenantId(), reportId, "failed", null, null, null,
                    "Falta el tipo de cambio de algún comprobante en moneda extranjera.");
            return Map.of("status", "failed");
        }
        FileStore.Stored stored = files.put(context.tenantId(), file.content());
        finish(context.tenantId(), reportId, "done", stored.key(), file.name(), input.rows().size(), null);
        return Map.of("status", "done", "rows", input.rows().size());
    }

    private void finish(UUID tenantId, UUID reportId, String status, String ref, String name, Integer rows, String detail) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            reportProvider.finishReport(reportId, status, ref, name, rows, detail);
        });
    }
}
