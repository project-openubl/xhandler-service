import { expect, test } from "@support/fixtures";

test("shows the SUNAT services and confirms before pausing one", async ({
  page,
}) => {
  await page.goto("/plataforma/envios");
  await expect(
    page.getByRole("heading", { level: 1, name: "Envío a SUNAT" }),
  ).toBeVisible();

  // The setup sent a document to the SUNAT fake: its invoice service exists.
  const endpoints = page.getByRole("grid", {
    name: "Tabla de servicios de SUNAT",
  });
  const fake = endpoints.getByRole("row", {
    name: /Pruebas \(fake\) · Comprobantes/,
  });
  await expect(fake).toBeVisible();

  // Pausing names the service; going back changes nothing (other specs send to it).
  await fake.getByRole("button", { name: "Kebab toggle" }).click();
  const pauseItem = page.getByRole("menuitem", { name: /envíos$/ });
  await pauseItem.click();
  const confirm = page.getByRole("dialog");
  await expect(
    confirm.getByText(/Pruebas \(fake\) · Comprobantes/),
  ).toBeVisible();
  await confirm.getByRole("button", { name: "Volver" }).click();

  // A cap below 1 is refused in the form.
  await fake.getByRole("button", { name: "Kebab toggle" }).click();
  await page.getByRole("menuitem", { name: "Cambiar límite" }).click();
  const cap = page.getByRole("dialog", { name: "Cambiar límite de envíos" });
  await cap.getByLabel("Envíos simultáneos").fill("0");
  await cap.getByRole("button", { name: "Guardar" }).click();
  await expect(cap.getByText("El límite es al menos 1")).toBeVisible();
  await cap.getByRole("button", { name: "Cancelar" }).click();
});

test("looks up a job by id and reports an unknown one", async ({ page }) => {
  await page.goto("/plataforma/envios");
  const jobId = page.getByLabel("Identificador del trabajo");
  await jobId.fill("no-es-un-id");
  await expect(page.getByText("El identificador no es válido")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Buscar trabajo" }),
  ).toBeDisabled();

  await jobId.fill("00000000-0000-0000-0000-000000000000");
  await page.getByRole("button", { name: "Buscar trabajo" }).click();
  await expect(page.getByText("No se pudo leer el trabajo")).toBeVisible();
});

test("runs harmless maintenance and lists the migrations", async ({ page }) => {
  await page.goto("/plataforma/envios");

  // Purging only jobs older than ~11 years deletes nothing.
  await page.getByLabel(/Purgar trabajos terminados/).fill("100000");
  await page.getByRole("button", { name: "Purgar trabajos" }).click();
  await page
    .getByRole("dialog", { name: "¿Purgar los trabajos terminados?" })
    .getByRole("button", { name: "Purgar trabajos" })
    .click();
  await expect(
    page.getByText(/Se borraron \d+ trabajos terminados/),
  ).toBeVisible();

  await page.getByRole("button", { name: "Crear particiones" }).click();
  await page
    .getByRole("dialog", { name: "¿Crear las particiones que falten?" })
    .getByRole("button", { name: "Crear particiones" })
    .click();
  await expect(
    page.getByText("Se crearon las particiones que faltaban"),
  ).toBeVisible();

  // Key rotation only confirms here.
  await page.getByRole("button", { name: "Rotar llaves" }).click();
  await page
    .getByRole("dialog", { name: "¿Rotar las llaves?" })
    .getByRole("button", { name: "Volver" })
    .click();

  await page.getByRole("button", { name: /Migraciones aplicadas/ }).click();
  await expect(
    page
      .getByRole("grid", { name: "Tabla de migraciones" })
      .getByRole("row")
      .nth(1),
  ).toBeVisible();
});
