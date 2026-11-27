import { describe, expect, it } from "vitest";

import { bulkRetrySchema, toBulkRetry } from "./bulkRetrySchema";

const empty = { companyId: "", status: "", from: "", to: "" };

describe("bulkRetrySchema", () => {
  it("accepts no filter at all", async () => {
    await expect(bulkRetrySchema.validate(empty)).resolves.toBeTruthy();
  });

  it("refuses a status a retry never picks up and a reversed range", async () => {
    await expect(
      bulkRetrySchema.validate({ ...empty, status: "accepted" }),
    ).rejects.toThrow("Elige un estado");
    await expect(
      bulkRetrySchema.validate({
        ...empty,
        from: "2026-02-01",
        to: "2026-01-01",
      }),
    ).rejects.toThrow("anterior a la inicial");
  });
});

describe("toBulkRetry", () => {
  it("leaves out empty fields", () => {
    expect(toBulkRetry(empty)).toEqual({});
    expect(
      toBulkRetry({ ...empty, status: "exception", from: "2026-01-01" }),
    ).toEqual({ status: "exception", from: "2026-01-01" });
  });
});
