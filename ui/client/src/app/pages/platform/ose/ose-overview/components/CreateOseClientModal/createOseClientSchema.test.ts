import { describe, expect, it } from "vitest";

import { createOseClientSchema, toNewClient } from "./createOseClientSchema";

const valid = { issuerRuc: "20100066603", username: "", tenantId: "" };

describe("createOseClientSchema", () => {
  it("needs only the issuer's RUC", async () => {
    await expect(createOseClientSchema.validate(valid)).resolves.toBeTruthy();
  });

  it("refuses a short RUC, a bad username and a bad tenant id", async () => {
    await expect(
      createOseClientSchema.validate({ ...valid, issuerRuc: "2010" }),
    ).rejects.toThrow("11 dígitos");
    await expect(
      createOseClientSchema.validate({ ...valid, username: "a b" }),
    ).rejects.toThrow("De 4 a 60");
    await expect(
      createOseClientSchema.validate({ ...valid, tenantId: "x" }),
    ).rejects.toThrow("no es válido");
  });
});

describe("toNewClient", () => {
  it("leaves out empty optional fields", () => {
    expect(toNewClient(valid)).toEqual({ issuer_ruc: "20100066603" });
  });
});
