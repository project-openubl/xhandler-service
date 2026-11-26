import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useQuery,
} from "@tanstack/react-query";

import {
  listAuditEntries,
  type ListAuditEntriesData,
  type PageAuditEntry,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { ListParams } from "../utils/listParams";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const AuditQueryKey = "audit";

export type IAuditListParams = ListParams<ListAuditEntriesData>;

export const auditKeys = {
  all: () => [AuditQueryKey] as const,
  list: (tenantId: string) => [...auditKeys.all(), tenantId, "list"] as const,
  listQuery: (tenantId: string, params: IAuditListParams) =>
    [...auditKeys.list(tenantId), params] as const,
};

type AuditListOptions = Omit<
  UseQueryOptions<PageAuditEntry, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET /tenants/{id}/audit` (admin): the tenant's audit log, newest first, paged by
 * `limit`/`offset` without a total.
 */
export const auditListQueryOptions = ({
  tenantId,
  params,
  options,
}: {
  tenantId: string;
  params: IAuditListParams;
  options?: AuditListOptions;
}) =>
  queryOptions<PageAuditEntry, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: auditKeys.listQuery(tenantId, params),
    queryFn: async () =>
      (
        await listAuditEntries({
          path: { tenantId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useAuditListQuery = ({
  params,
  options,
}: {
  params: IAuditListParams;
  options?: AuditListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(auditListQueryOptions({ tenantId, params, options }));
};
