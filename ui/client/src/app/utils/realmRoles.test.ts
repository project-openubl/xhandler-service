import { describe, expect, it } from "vitest";

import { realmRolesOf } from "./realmRoles";

const token = (payload: unknown) =>
  [
    "e30",
    btoa(JSON.stringify(payload))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, ""),
    "sig",
  ].join(".");

describe("realmRolesOf", () => {
  it("reads realm_access.roles", () => {
    expect(
      realmRolesOf(
        token({ realm_access: { roles: ["platform-support", "search"] } }),
      ),
    ).toEqual(["platform-support", "search"]);
  });

  it("reads non-ASCII claims (base64url, UTF-8)", () => {
    expect(
      realmRolesOf(
        token({ name: "Peña ñandú?>", realm_access: { roles: ["a"] } }),
      ),
    ).toEqual(["a"]);
  });

  it("has no roles without a token, a claim or a valid payload", () => {
    expect(realmRolesOf(undefined)).toEqual([]);
    expect(realmRolesOf(token({ sub: "x" }))).toEqual([]);
    expect(realmRolesOf("a.%%%.c")).toEqual([]);
  });
});
