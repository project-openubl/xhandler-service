import { describe, expect, it } from "vitest";

import { dataSubjectRequestSchema } from "./dataSubjectRequestSchema";

const valid = {
  subjectDocType: "1",
  subjectDocNumber: "12345678",
  subjectName: "Juana Pérez",
  kind: "access",
  details: "",
};

describe("dataSubjectRequestSchema", () => {
  it("accepts a DNI holder's access request", async () => {
    await expect(
      dataSubjectRequestSchema.validate(valid),
    ).resolves.toBeTruthy();
  });

  it("checks DNI and RUC lengths", async () => {
    await expect(
      dataSubjectRequestSchema.validate({
        ...valid,
        subjectDocNumber: "123",
      }),
    ).rejects.toThrow("El DNI tiene 8 dígitos.");
    await expect(
      dataSubjectRequestSchema.validate({
        ...valid,
        subjectDocType: "6",
        subjectDocNumber: "12345678",
      }),
    ).rejects.toThrow("El RUC tiene 11 dígitos.");
  });

  it("refuses a right the API doesn't know", async () => {
    await expect(
      dataSubjectRequestSchema.validate({ ...valid, kind: "deletion" }),
    ).rejects.toThrow("Elige el derecho que se ejerce.");
  });
});
