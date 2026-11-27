import { describe, expect, it } from "vitest";

import { partnerMemberSchema } from "./partnerMemberSchema";

describe("partnerMemberSchema", () => {
  it("accepts a user or a machine client as admin or viewer", async () => {
    await expect(
      partnerMemberSchema.validate({
        kind: "user",
        subject: "abc",
        role: "admin",
      }),
    ).resolves.toBeTruthy();
    await expect(
      partnerMemberSchema.validate({
        kind: "client",
        subject: "acme-erp",
        role: "viewer",
      }),
    ).resolves.toBeTruthy();
  });

  it("refuses another kind or role and an empty subject", async () => {
    await expect(
      partnerMemberSchema.validate({
        kind: "group",
        subject: "x",
        role: "admin",
      }),
    ).rejects.toThrow("Elige el tipo de miembro");
    await expect(
      partnerMemberSchema.validate({
        kind: "user",
        subject: "x",
        role: "owner",
      }),
    ).rejects.toThrow("Elige el rol");
    await expect(
      partnerMemberSchema.validate({
        kind: "user",
        subject: " ",
        role: "admin",
      }),
    ).rejects.toThrow("Ingresa el identificador");
  });
});
