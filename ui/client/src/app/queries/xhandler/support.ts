import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  approveSupportSession,
  listSupportSessions,
  type RequestSession,
  requestSupportSession,
  revokeSupportSession,
  type SessionRequested,
  type SessionView,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const SupportSessionsQueryKey = "support-sessions";

export const supportSessionKeys = {
  all: () => [SupportSessionsQueryKey] as const,
  list: (tenantId: string) =>
    [...supportSessionKeys.all(), tenantId, "list"] as const,
};

/** Where a support session stands (DESIGN.md §26.1), derived from its timestamps. */
export type SupportSessionState = "pending" | "active" | "expired" | "revoked";

export const supportSessionState = (
  session: SessionView,
  now: Date = new Date(),
): SupportSessionState => {
  if (session.revoked_at) return "revoked";
  if (!session.expires_at) return "pending";
  return new Date(session.expires_at) > now ? "active" : "expired";
};

type SupportSessionListOptions = Omit<
  UseQueryOptions<SessionView[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET /tenants/{id}/support-sessions` (admin): every session asked of the tenant; unpaged. */
export const supportSessionsQueryOptions = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: SupportSessionListOptions;
}) =>
  queryOptions<SessionView[], XhandlerAxiosError>({
    ...options,
    queryKey: supportSessionKeys.list(tenantId),
    queryFn: async () =>
      (await listSupportSessions({ path: { tenantId }, throwOnError: true }))
        .data,
  });

export const useSupportSessionsQuery = ({
  options,
}: {
  options?: SupportSessionListOptions;
} = {}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(supportSessionsQueryOptions({ tenantId, options }));
};

const useSupportSessionAction = (
  action: typeof approveSupportSession,
  options?: IMutationCallbacks<SessionView, SessionView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<SessionView, XhandlerAxiosError, SessionView>({
    mutationFn: async (session) =>
      (
        await action({
          path: { tenantId, sessionId: session.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, session) => {
      await queryClient.invalidateQueries({
        queryKey: supportSessionKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, session);
    },
    onError: options?.onError,
  });
};

/**
 * `POST …/support-sessions/{id}/approve` (admin): opens it for 4 hours, read-only; `409` when it
 * was already approved or revoked.
 */
export const useSupportSessionApproveMutation = (
  options?: IMutationCallbacks<SessionView, SessionView, XhandlerAxiosError>,
) => useSupportSessionAction(approveSupportSession, options);

/** `POST …/support-sessions/{id}/revoke` (admin): ends it now, or refuses a pending one. */
export const useSupportSessionRevokeMutation = (
  options?: IMutationCallbacks<SessionView, SessionView, XhandlerAxiosError>,
) => useSupportSessionAction(revokeSupportSession, options);

/**
 * `POST /support/sessions` (platform support only): asks a tenant for a session; `201` with its
 * id, `404` for an unknown tenant. Nothing to invalidate: operators can't list sessions.
 */
export const useSupportSessionRequestMutation = (
  options?: IMutationCallbacks<
    SessionRequested,
    RequestSession,
    XhandlerAxiosError
  >,
) =>
  useMutation<SessionRequested, XhandlerAxiosError, RequestSession>({
    mutationFn: async (body) =>
      (await requestSupportSession({ body, throwOnError: true })).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
