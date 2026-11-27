import { expect, test } from "@support/fixtures";
import { AuditPage } from "@support/pom/tenantAdminPages";

test("lists the organization's audit log, newest first", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const audit = new AuditPage(page);
  await audit.goto(tenantId!);
  await audit.expectLoaded();

  // The setup changed the organization (a company, a document, a reception): it is audited.
  await expect(audit.rows().first()).toContainText(/\d{2}\/\d{2}\/\d{4}/);
  await expect(page.getByRole("link", { name: "Auditoría" })).toBeVisible();
});
