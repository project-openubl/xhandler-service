import { useAuth } from "./useAuth";

/** xhandler's platform realm roles (`CurrentActor`, the admin API). */
export const PLATFORM_ROLES = ["platform-admin"] as const;

export type PlatformRole = (typeof PLATFORM_ROLES)[number];

/**
 * Which platform roles the signed-in user's token carries, to show the platform screens only to
 * them (ADR 0012). Never a security check: xhandler verifies the token on every call.
 */
export const usePlatformRoles = () => {
  const { user } = useAuth();
  const has = (role: PlatformRole): boolean =>
    user?.realmRoles.includes(role) ?? false;
  return { has };
};
