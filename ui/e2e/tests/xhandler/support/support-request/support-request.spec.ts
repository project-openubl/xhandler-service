import { expect, test } from "@support/fixtures";

test("only platform support asks organizations for a session", async ({
  page,
}) => {
  // The e2e user is a platform admin, not platform support: no link, an unauthorized page.
  await page.goto("/soporte");
  await expect(
    page.getByRole("heading", { name: "Acceso de soporte", level: 1 }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "403: Acceso denegado" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Acceso de soporte" }),
  ).toHaveCount(0);
});
