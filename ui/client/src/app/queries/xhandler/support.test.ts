import { describe, expect, it } from "vitest";

import { supportSessionState } from "./support";

const now = new Date("2026-11-26T12:00:00Z");

describe("supportSessionState", () => {
  it("is pending until approved, active until it expires", () => {
    expect(supportSessionState({ id: "s" }, now)).toBe("pending");
    expect(
      supportSessionState({ id: "s", expires_at: "2026-11-26T14:00:00Z" }, now),
    ).toBe("active");
    expect(
      supportSessionState({ id: "s", expires_at: "2026-11-26T10:00:00Z" }, now),
    ).toBe("expired");
  });

  it("is revoked once revoked, approved or not", () => {
    expect(
      supportSessionState(
        {
          id: "s",
          expires_at: "2026-11-26T14:00:00Z",
          revoked_at: "2026-11-26T11:00:00Z",
        },
        now,
      ),
    ).toBe("revoked");
    expect(
      supportSessionState({ id: "s", revoked_at: "2026-11-26T11:00:00Z" }, now),
    ).toBe("revoked");
  });
});
