import { expect, test } from "@support/fixtures";
import { SupportSessionsPage } from "@support/pom/tenantAdminPages";

test("lists the support sessions asked of the organization", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const sessions = new SupportSessionsPage(page);
  await sessions.goto(tenantId!);
  await sessions.expectLoaded();
  await expect(
    sessions.firstRowOrEmpty("No hay sesiones de soporte"),
  ).toBeVisible();
  await expect(
    page.getByText(/dura 4 horas, es de solo lectura/),
  ).toBeVisible();
});
