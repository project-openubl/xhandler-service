import { describe, expect, it } from "vitest";

import {
  cancelExitUnavailable,
  type TenantAction,
  tenantActionConfirmation,
} from "./tenantActions";

const ACTIONS: TenantAction[] = [
  "hold",
  "release",
  "audit-hold-on",
  "audit-hold-off",
  "archive-plan-on",
  "archive-plan-off",
  "cancel-exit",
];

describe("tenantActionConfirmation", () => {
  it("names the tenant in every confirmation", () => {
    for (const action of ACTIONS) {
      expect(
        tenantActionConfirmation({ action, tenantName: "Bodega SAC" }).body,
      ).toContain("Bodega SAC");
    }
  });
});

describe("cancelExitUnavailable", () => {
  it("cancels only an exited tenant's exit", () => {
    expect(cancelExitUnavailable({ status: "exited" })).toBeUndefined();
    expect(cancelExitUnavailable({ status: "active" })).toBeDefined();
  });
});
