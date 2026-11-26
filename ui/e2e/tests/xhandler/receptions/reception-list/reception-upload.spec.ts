import { readFile } from "node:fs/promises";

import { expect, test } from "@support/fixtures";
import { readFixtures } from "@support/helpers/tenants";
import { ReceptionsPage } from "@support/pom/documentDataPages";

test("asks for the XML and shows why a reception is refused", async ({
  page,
}) => {
  // The setup's company received the factura it issued to itself.
  const fixtures = readFixtures();
  test.skip(!fixtures, "the setup prepared no company");

  const receptions = new ReceptionsPage(page);
  await receptions.goto({
    tenantId: fixtures!.tenantId,
    companyId: fixtures!.companyId,
  });
  await receptions.expectLoaded();

  // The XML of a reception the company already holds, through its download.
  const firstRow = receptions.firstRowOrEmpty(/No hay comprobantes recibidos/);
  await firstRow
    .getByRole("button", { name: /acciones|Kebab toggle/i })
    .click();
  const download = page.waitForEvent("download");
  await page.getByRole("menuitem", { name: "Descargar XML" }).click();
  const xmlPath = await (await download).path();

  await page.getByRole("button", { name: "Recibir comprobante" }).click();
  const dialog = page.getByRole("dialog", { name: "Recibir comprobante" });
  await dialog.getByRole("button", { name: "Recibir" }).click();
  await expect(dialog.getByText("Elige el XML del comprobante")).toBeVisible();

  await dialog
    .locator("input[type=file]")
    .first()
    .setInputFiles({
      name: "factura.xml",
      mimeType: "application/xml",
      buffer: await readFile(xmlPath),
    });
  await dialog.getByRole("button", { name: "Recibir" }).click();
  await expect(
    dialog.getByText("No se pudo recibir el comprobante"),
  ).toBeVisible();
  await expect(
    dialog.getByText("Ese comprobante ya fue recibido."),
  ).toBeVisible();
});
