package io.github.projectopenubl.xhandler.managers;


/**
 * The platform's own subscription invoices are real comprobantes issued through the service
 * (DESIGN.md §14): Lago's invoices are not. Lago's {@code invoice.created} webhook (HMAC-signed)
 * becomes a factura (or a boleta, without a billing RUC) from the platform's issuing company to the
 * tenant, emailed to its billing address. Idempotent per Lago invoice.
 */
public final class SelfInvoicingManager {

    public static final String ISSUER_TENANT = "billing.issuer_tenant_id";
    public static final String ISSUER_COMPANY = "billing.issuer_company_id";

    private SelfInvoicingManager() {}


}
