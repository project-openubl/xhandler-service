import { describe, expect, it } from "vitest";

import { solUserSchema } from "./solUserSchema";

describe("solUserSchema", () => {
  it("takes the RUC followed by the user, a password and an optional label", async () => {
    await expect(
      solUserSchema.validate({
        username: "20100066603MODDATOS",
        password: "moddatos",
        label: "",
      }),
    ).resolves.toBeTruthy();
  });

  it("refuses a missing user or password", async () => {
    await expect(
      solUserSchema.validate({ username: " ", password: "x", label: "" }),
    ).rejects.toThrow("Ingresa el usuario SOL");
    await expect(
      solUserSchema.validate({ username: "20100066603MODDATOS", label: "" }),
    ).rejects.toThrow("Ingresa la contraseña SOL");
  });
});
