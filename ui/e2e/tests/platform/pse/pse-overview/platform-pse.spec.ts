import { expect, test } from "@support/fixtures";

test("shows the platform's PSE and validates what it imports", async ({
  page,
}) => {
  await page.goto("/plataforma/pse");
  await expect(
    page.getByRole("heading", { level: 1, name: "PSE de la plataforma" }),
  ).toBeVisible();
  await expect(
    page.getByRole("grid", { name: "Tabla de autorizaciones PSE por revisar" }),
  ).toBeVisible();
  await expect(
    page.getByRole("grid", { name: "Tabla de usuarios SOL del grupo PSE" }),
  ).toBeVisible();

  // A file that isn't a keystore: the service refuses it and the form shows why.
  await page.getByRole("button", { name: /certificado$/ }).click();
  const certificate = page.getByRole("dialog");
  await certificate.getByRole("button", { name: "Importar" }).click();
  await expect(
    certificate.getByText("Elige el archivo .pfx o .p12"),
  ).toBeVisible();
  await certificate.locator('input[type="file"]').setInputFiles({
    name: "no-es-un-certificado.pfx",
    mimeType: "application/x-pkcs12",
    buffer: Buffer.from("no es un certificado"),
  });
  await certificate.getByLabel("Contraseña").fill("x");
  await certificate.getByRole("button", { name: "Importar" }).click();
  await expect(
    certificate.getByText("No se pudo importar el certificado"),
  ).toBeVisible();
  await certificate.getByRole("button", { name: "Cancelar" }).click();

  // The SOL user form asks for the user and the password (nothing is added here).
  await page.getByRole("button", { name: "Agregar usuario SOL" }).click();
  const solUser = page.getByRole("dialog", { name: "Agregar usuario SOL" });
  await solUser.getByRole("button", { name: "Agregar" }).click();
  await expect(solUser.getByText("Ingresa el usuario SOL")).toBeVisible();
  await expect(solUser.getByText("Ingresa la contraseña SOL")).toBeVisible();
  await solUser.getByRole("button", { name: "Cancelar" }).click();
});

test("reports the rejected-CDR rate of a half-year", async ({ page }) => {
  await page.goto("/plataforma/pse");
  const period = page.getByLabel("Semestre");
  await period.fill("2026-H3");
  await expect(
    page.getByText("Usa el formato AAAA-H1 o AAAA-H2"),
  ).toBeVisible();
  await period.fill("2026-H1");
  await page.getByRole("button", { name: "Consultar" }).click();
  const rate = page.getByLabel("Tasa de CDR rechazados");
  await expect(rate.getByText("2026-H1")).toBeVisible();
  await expect(rate.getByText(/límite/)).toBeVisible();
});
