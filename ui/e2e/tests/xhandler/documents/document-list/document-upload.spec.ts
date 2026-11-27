import { expect, test } from "@support/fixtures";
import { DocumentsPage } from "@support/pom/documentDataPages";

test("uploads an xbuilder JSON and opens the document it became", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const documents = new DocumentsPage(page);
  await documents.goto(tenantId!);
  await documents.expectLoaded();

  await page.getByRole("button", { name: "Subir comprobante" }).click();
  const dialog = page.getByRole("dialog", { name: "Subir comprobante" });
  const company = dialog.getByLabel("Empresa");
  // The setup gave the organization a company: wait for the picker to fill.
  await expect(company.locator("option").first()).not.toHaveText(
    /Cargando empresas|No hay empresas/,
  );
  const ruc = (await company.locator("option:checked").innerText()).match(
    /RUC (\d{11})/,
  )?.[1];
  await dialog.getByLabel("Formato").selectOption("json");

  const json = dialog.locator("textarea");
  await json.fill('{"kind": "Invoice"}');
  await dialog.getByRole("button", { name: "Subir" }).click();
  await expect(
    dialog.getByText(/debe ser un documento de xbuilder/),
  ).toBeVisible();

  await json.fill(
    JSON.stringify({
      serie: "F001",
      document: {
        kind: "Invoice",
        spec: {
          cliente: {
            tipo_documento_identidad: "6",
            numero_documento_identidad: ruc,
            nombre: "Cliente Subida E2E SAC",
          },
          detalles: [{ descripcion: "Subida E2E", cantidad: 1, precio: 10 }],
        },
      },
    }),
  );
  await dialog.getByRole("button", { name: "Subir" }).click();
  await expect(
    page.getByRole("heading", { name: "Estado en SUNAT" }),
  ).toBeVisible();
  await expect(page.getByText(/Se recibió F001-\d+/)).toBeVisible();
});

test("shows why the service refused an upload", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const documents = new DocumentsPage(page);
  await documents.goto(tenantId!);
  await documents.expectLoaded();
  await page.getByRole("button", { name: "Subir comprobante" }).click();
  const dialog = page.getByRole("dialog", { name: "Subir comprobante" });
  await expect(
    dialog.getByLabel("Empresa").locator("option").first(),
  ).not.toHaveText(/Cargando empresas|No hay empresas/);

  // A well-formed name for another RUC: the service refuses the file.
  await dialog.getByLabel("Formato").selectOption("sfs");
  const files = dialog.locator("input[type=file]");
  const line =
    "NIU|1|P001|-|PRODUCTO|100|18.00|1000|18.00|100.00|IGV|VAT|10|18.00";
  await files.nth(0).setInputFiles({
    name: "20999999999-01-F001-1.CAB",
    mimeType: "text/plain",
    buffer: Buffer.from(
      "0101|2026-01-01|10:30:00|-|0000|6|20111111111|CLIENTE SAC|PEN|18.00|100.00|118.00|0.00|0.00|0.00|118.00|2.1|2.0",
    ),
  });
  await files.nth(1).setInputFiles({
    name: "20999999999-01-F001-1.DET",
    mimeType: "text/plain",
    buffer: Buffer.from(line),
  });
  await dialog.getByRole("button", { name: "Subir" }).click();
  await expect(
    dialog.getByText("No se pudo subir el comprobante"),
  ).toBeVisible();
});
