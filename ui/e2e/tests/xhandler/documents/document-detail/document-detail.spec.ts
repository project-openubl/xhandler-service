import { expect, test } from "@support/fixtures";
import { DocumentsPage } from "@support/pom/documentDataPages";

test("holds only unsent documents and marks an accepted one as delivered", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  // Every setup run issues a fresh factura that the SUNAT fake accepts: the newest one.
  const documents = new DocumentsPage(page);
  await documents.goto(tenantId!);
  await documents.expectLoaded();
  await documents.chooseFilter("Serie", "Estado");
  await page.getByRole("button", { name: "Filtrar por estado" }).click();
  await page.getByRole("option", { name: "Aceptado", exact: true }).click();
  await documents.table.getByRole("link").first().click();
  await expect(
    page.getByRole("heading", { name: "Estado en SUNAT" }),
  ).toBeVisible();

  await page.getByRole("button", { name: "Más acciones" }).click();
  // An accepted document is final: nothing to hold or release.
  await expect(
    page.getByRole("menuitem", { name: "Retener envío" }),
  ).toHaveAttribute("aria-disabled", "true");
  await expect(
    page.getByRole("menuitem", { name: "Liberar envío" }),
  ).toHaveAttribute("aria-disabled", "true");

  const delivered = page.getByRole("menuitem", {
    name: "Marcar como entregado",
  });
  test.skip(
    (await delivered.getAttribute("aria-disabled")) === "true",
    "the newest accepted document was already delivered",
  );
  await delivered.click();
  const dialog = page.getByRole("dialog", { name: "¿Marcar como entregado?" });
  await dialog.getByRole("button", { name: "Marcar como entregado" }).click();
  await expect(
    page.getByText(/figura como entregado al comprador/),
  ).toBeVisible();
  await expect(page.getByText("(declarado por el emisor)")).toBeVisible();
});
