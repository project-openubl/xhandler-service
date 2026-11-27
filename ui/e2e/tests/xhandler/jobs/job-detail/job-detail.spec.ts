import { expect, test } from "@support/fixtures";
import { JobsPage } from "@support/pom/pages";

test("opens a job from Trabajos with its state, times and resources", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  // The setup issued a document: at least its send attempt ran as a job.
  const jobs = new JobsPage(page);
  await jobs.goto(tenantId!);
  await jobs.expectLoaded();
  const first = jobs.table.getByRole("link").first();
  const name = await first.innerText();
  await first.click();

  await expect(page.getByRole("heading", { name, level: 1 })).toBeVisible();
  const details = page
    .getByRole("group", { name: "Detalle del trabajo" })
    .or(page.getByLabel("Detalle del trabajo"));
  await expect(details.getByText("Estado")).toBeVisible();
  await expect(details.getByText("Recursos reservados")).toBeVisible();

  await page.getByRole("link", { name: "Trabajos" }).first().click();
  await jobs.expectLoaded();
});

test("an unknown job is a 404 page", async ({ page, tenantsPage }) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(
    `/organizaciones/${tenantId}/trabajos/00000000-0000-0000-0000-000000000000`,
  );
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});
