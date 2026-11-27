import { expect, test } from "@support/fixtures";

/** A RUC no test reuses: 20 followed by nine digits of the clock. */
const freshRuc = () => `20${String(Date.now()).slice(-9)}`;

test("creates an OSE user, shows its password once, resets and suspends it", async ({
  page,
}) => {
  await page.goto("/plataforma/ose");
  await expect(
    page.getByRole("heading", { level: 1, name: "OSE de la plataforma" }),
  ).toBeVisible();

  const ruc = freshRuc();
  await page.getByRole("button", { name: "Nuevo usuario OSE" }).click();
  const create = page.getByRole("dialog", { name: "Nuevo usuario OSE" });
  await create.getByLabel("RUC del emisor").fill("123");
  await create.getByRole("button", { name: "Crear" }).click();
  await expect(create.getByText("El RUC tiene 11 dígitos")).toBeVisible();
  await create.getByLabel("RUC del emisor").fill(ruc);
  await create.getByRole("button", { name: "Crear" }).click();

  const shown = page.getByRole("dialog", { name: "Se creó el usuario OSE" });
  await expect(shown.getByText("no se volverá a mostrar")).toBeVisible();
  await expect(
    shown.getByRole("textbox", { name: "Contraseña" }),
  ).not.toHaveValue("");
  await shown.getByRole("button", { name: "Ya la copié" }).click();

  const row = page
    .getByRole("grid", { name: "Tabla de usuarios OSE" })
    .getByRole("row", { name: new RegExp(ruc) })
    .first();
  await expect(row.getByText("Activo")).toBeVisible();

  await row.getByRole("button", { name: "Kebab toggle" }).click();
  await page
    .getByRole("menuitem", { name: "Generar contraseña nueva" })
    .click();
  await page
    .getByRole("dialog", { name: "¿Generar una contraseña nueva?" })
    .getByRole("button", { name: "Generar contraseña" })
    .click();
  const reset = page.getByRole("dialog", {
    name: "Contraseña nueva del usuario OSE",
  });
  await expect(
    reset.getByRole("textbox", { name: "Contraseña" }),
  ).not.toHaveValue("");
  await reset.getByRole("button", { name: "Ya la copié" }).click();

  await row.getByRole("button", { name: "Kebab toggle" }).click();
  await page.getByRole("menuitem", { name: "Suspender" }).click();
  await page
    .getByRole("dialog", { name: "¿Suspender el usuario OSE?" })
    .getByRole("button", { name: "Suspender" })
    .click();
  await expect(row.getByText("Suspendido")).toBeVisible();
});

test("checks SUNAT's lists before replacing them", async ({ page }) => {
  await page.goto("/plataforma/ose");

  // The association list is shared by the whole stack: only its checks run here.
  await page
    .getByRole("button", { name: "Reemplazar lista de asociados" })
    .click();
  const association = page.getByRole("dialog", {
    name: "Reemplazar la lista de asociados",
  });
  await association.locator("textarea").fill("ruc,desde\n2010,2026-01-01");
  await association.getByRole("button", { name: "Reemplazar lista" }).click();
  await expect(
    association.getByText("Línea 2: el RUC debe tener 11 dígitos"),
  ).toBeVisible();
  await association.locator("textarea").fill("");
  await association.getByRole("button", { name: "Reemplazar lista" }).click();
  await expect(
    association.getByText("Confirma que quieres vaciar la lista"),
  ).toBeVisible();
  await association.getByRole("button", { name: "Cancelar" }).click();

  // A blocked list of the tests' own.
  await page
    .getByRole("button", { name: "Reemplazar lista de bloqueados" })
    .click();
  const blocked = page.getByRole("dialog", {
    name: "Reemplazar una lista de bloqueados",
  });
  await blocked.getByLabel("Nombre de la lista").fill("e2e_bloqueados");
  await blocked.locator("textarea").fill(`${freshRuc()},2026-01-01`);
  await blocked.getByRole("button", { name: "Reemplazar lista" }).click();
  await expect(
    page.getByText(
      "La lista de bloqueados e2e_bloqueados tiene ahora 1 emisor",
    ),
  ).toBeVisible();
});

test("shows forwarding, usage and the CDR certificate", async ({ page }) => {
  await page.goto("/plataforma/ose");
  await expect(page.getByLabel("Cola de reenvío a SUNAT")).toBeVisible();
  await expect(
    page.getByRole("grid", { name: "Tabla de validaciones por usuario OSE" }),
  ).toBeVisible();
  await page.getByLabel("Mes").fill("2026-13");
  await expect(page.getByText("Usa el formato AAAA-MM")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Certificado de los CDR-OSE" }),
  ).toBeVisible();
});
