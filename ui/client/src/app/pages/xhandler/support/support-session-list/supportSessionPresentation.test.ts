import { describe, expect, it } from "vitest";

import { supportSessionActions } from "./supportSessionPresentation";

describe("supportSessionActions", () => {
  it("approves only pending sessions", () => {
    expect(supportSessionActions("pending").approve).toBeUndefined();
    for (const state of ["active", "expired", "revoked"] as const) {
      expect(supportSessionActions(state).approve).toBeDefined();
    }
  });

  it("revokes pending and active sessions, not finished ones", () => {
    expect(supportSessionActions("pending").revoke).toBeUndefined();
    expect(supportSessionActions("active").revoke).toBeUndefined();
    expect(supportSessionActions("expired").revoke).toBeDefined();
    expect(supportSessionActions("revoked").revoke).toBeDefined();
  });
});
