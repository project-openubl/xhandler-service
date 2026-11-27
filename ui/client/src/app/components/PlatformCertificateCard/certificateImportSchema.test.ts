import { describe, expect, it } from "vitest";

import { certificateImportSchema } from "./certificateImportSchema";

describe("certificateImportSchema", () => {
  it("takes a .pfx or .p12 with its password", async () => {
    await expect(
      certificateImportSchema.validate({
        file: new File(["x"], "pse.pfx"),
        password: "secreto",
      }),
    ).resolves.toBeTruthy();
  });

  it("refuses another file type and a missing password", async () => {
    await expect(
      certificateImportSchema.validate({
        file: new File(["x"], "pse.pem"),
        password: "secreto",
      }),
    ).rejects.toThrow(".pfx o .p12");
    await expect(
      certificateImportSchema.validate({ file: new File(["x"], "pse.p12") }),
    ).rejects.toThrow("Ingresa la contraseña");
  });
});
