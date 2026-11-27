import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
  useSuspenseQuery,
} from "@tanstack/react-query";

import {
  adminCancelTenantExit,
  type AdminCompany,
  adminGetTenant,
  adminHoldCompanySending,
  adminHoldTenantSending,
  adminListTenantCompanies,
  adminListTenants,
  type AdminListTenantsData,
  adminRegisterDataSubjectRequest,
  adminReleaseCompanySending,
  adminReleaseTenantSending,
  adminRetryTenantDocuments,
  adminSetTenantArchivePlan,
  adminSetTenantAuditHold,
  adminSuppressEmail,
  type AdminTenant,
  type BulkRetry,
  type CompanySending,
  type Count,
  type DataSubjectRequestForwarded,
  type EmailSuppression,
  type NewRequest,
  type PageAdminTenant,
  type TenantArchivePlan,
  type TenantAuditHold,
  type TenantSending,
  type TenantStatus,
} from "@app/client/xhandler-admin";

import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const AdminTenantsQueryKey = "admin-tenants";

export type IAdminTenantListParams = ListParams<AdminListTenantsData>;

/** Query-key factory mirroring `/api/admin/v1/tenants`; a prefix invalidates every variant. */
export const adminTenantKeys = {
  all: () => [AdminTenantsQueryKey] as const,
  list: () => [...adminTenantKeys.all(), "list"] as const,
  listQuery: (params: IAdminTenantListParams) =>
    [...adminTenantKeys.list(), params] as const,
  detail: (tenantId: string) =>
    [...adminTenantKeys.all(), "detail", tenantId] as const,
  companies: (tenantId: string) =>
    [...adminTenantKeys.detail(tenantId), "companies"] as const,
};

type AdminTenantListOptions = Omit<
  UseQueryOptions<PageAdminTenant, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET /api/admin/v1/tenants` (platform-admin): every tenant as the platform sees it, newest
 * first, searchable by name (`q`), paged by `limit`/`offset` without a total.
 */
export const adminTenantListQueryOptions = ({
  params,
  options,
}: {
  params: IAdminTenantListParams;
  options?: AdminTenantListOptions;
}) =>
  queryOptions<PageAdminTenant, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: adminTenantKeys.listQuery(params),
    queryFn: async () =>
      (await adminListTenants({ query: params, throwOnError: true })).data,
  });

export const useAdminTenantListQuery = ({
  params,
  options,
}: {
  params: IAdminTenantListParams;
  options?: AdminTenantListOptions;
}) => useQuery(adminTenantListQueryOptions({ params, options }));

type AdminTenantOptions = Omit<
  UseQueryOptions<AdminTenant, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET /api/admin/v1/tenants/{id}`: one tenant, its holds and number of companies. */
export const adminTenantDetailQueryOptions = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: AdminTenantOptions;
}) =>
  queryOptions<AdminTenant, XhandlerAxiosError>({
    ...options,
    queryKey: adminTenantKeys.detail(tenantId),
    queryFn: async () =>
      (await adminGetTenant({ path: { tenantId }, throwOnError: true })).data,
  });

export const useAdminTenantDetailQuery = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: AdminTenantOptions;
}) =>
  useQuery({
    ...adminTenantDetailQueryOptions({ tenantId, options }),
    enabled: !!tenantId && (options?.enabled ?? true),
  });

export const useSuspenseAdminTenantDetailQuery = ({
  tenantId,
}: {
  tenantId: string;
}) => useSuspenseQuery(adminTenantDetailQueryOptions({ tenantId }));

type AdminCompaniesOptions = Omit<
  UseQueryOptions<AdminCompany[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET /api/admin/v1/tenants/{id}/companies`: the tenant's companies, unpaged. Each read is
 * recorded in the tenant's audit log (`tenant.companies_listed`), so it runs on demand only.
 */
export const adminTenantCompaniesQueryOptions = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: AdminCompaniesOptions;
}) =>
  queryOptions<AdminCompany[], XhandlerAxiosError>({
    ...options,
    queryKey: adminTenantKeys.companies(tenantId),
    queryFn: async () =>
      (
        await adminListTenantCompanies({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
  });

export const useAdminTenantCompaniesQuery = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: AdminCompaniesOptions;
}) => useQuery(adminTenantCompaniesQueryOptions({ tenantId, options }));

/** Re-reads the tenant (detail and lists) after a write that changes what the platform sees. */
const useInvalidateAdminTenant = () => {
  const queryClient = useQueryClient();
  return async (tenantId: string) => {
    await queryClient.invalidateQueries({
      queryKey: adminTenantKeys.detail(tenantId),
    });
    await queryClient.invalidateQueries({ queryKey: adminTenantKeys.list() });
  };
};

/** `POST …/tenants/{id}/hold` or `…/release`: pauses or resumes every send of the tenant. */
export const useAdminTenantSendingMutation = (
  tenantId: string,
  options?: IMutationCallbacks<TenantSending, boolean, XhandlerAxiosError>,
) => {
  const invalidate = useInvalidateAdminTenant();
  return useMutation<TenantSending, XhandlerAxiosError, boolean>({
    mutationFn: async (hold) =>
      (
        await (hold ? adminHoldTenantSending : adminReleaseTenantSending)({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, hold) => {
      await invalidate(tenantId);
      await options?.onSuccess?.(data, hold);
    },
    onError: options?.onError,
  });
};

export interface IAdminCompanyHoldVariables {
  companyId: string;
  hold: boolean;
}

/** `POST …/companies/{id}/hold` or `…/release`: pauses or resumes one company's sends. */
export const useAdminCompanySendingMutation = (
  tenantId: string,
  options?: IMutationCallbacks<
    CompanySending,
    IAdminCompanyHoldVariables,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<
    CompanySending,
    XhandlerAxiosError,
    IAdminCompanyHoldVariables
  >({
    mutationFn: async ({ companyId, hold }) =>
      (
        await (hold ? adminHoldCompanySending : adminReleaseCompanySending)({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: adminTenantKeys.companies(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/**
 * `POST …/tenants/{id}/retry`: makes due now the tenant's documents still being sent (by
 * company, status and issue dates, all optional); answers how many.
 */
export const useAdminRetryTenantDocumentsMutation = (
  tenantId: string,
  options?: IMutationCallbacks<Count, BulkRetry, XhandlerAxiosError>,
) =>
  useMutation<Count, XhandlerAxiosError, BulkRetry>({
    mutationFn: async (body) =>
      (
        await adminRetryTenantDocuments({
          path: { tenantId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });

/** `POST …/tenants/{id}/email-suppressions`: stops emails to an address (bounce, complaint). */
export const useAdminSuppressEmailMutation = (
  tenantId: string,
  options?: IMutationCallbacks<
    EmailSuppression,
    EmailSuppression,
    XhandlerAxiosError
  >,
) =>
  useMutation<EmailSuppression, XhandlerAxiosError, EmailSuppression>({
    mutationFn: async (body) =>
      (
        await adminSuppressEmail({
          path: { tenantId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });

/** `PUT /data-protection/tenants/{id}/audit-hold`: keeps the tenant's data past its deletion date. */
export const useAdminSetAuditHoldMutation = (
  tenantId: string,
  options?: IMutationCallbacks<TenantAuditHold, boolean, XhandlerAxiosError>,
) => {
  const invalidate = useInvalidateAdminTenant();
  return useMutation<TenantAuditHold, XhandlerAxiosError, boolean>({
    mutationFn: async (auditHold) =>
      (
        await adminSetTenantAuditHold({
          path: { tenantId },
          body: { audit_hold: auditHold },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, auditHold) => {
      await invalidate(tenantId);
      await options?.onSuccess?.(data, auditHold);
    },
    onError: options?.onError,
  });
};

/** `PUT /data-protection/tenants/{id}/archive-plan`: an exited tenant kept with no deletion date. */
export const useAdminSetArchivePlanMutation = (
  tenantId: string,
  options?: IMutationCallbacks<TenantArchivePlan, boolean, XhandlerAxiosError>,
) => {
  const invalidate = useInvalidateAdminTenant();
  return useMutation<TenantArchivePlan, XhandlerAxiosError, boolean>({
    mutationFn: async (archivePlan) =>
      (
        await adminSetTenantArchivePlan({
          path: { tenantId },
          body: { archive_plan: archivePlan },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, archivePlan) => {
      await invalidate(tenantId);
      await options?.onSuccess?.(data, archivePlan);
    },
    onError: options?.onError,
  });
};

/** `POST /data-protection/tenants/{id}/exit/cancel`: an exited tenant becomes active again. */
export const useAdminCancelTenantExitMutation = (
  tenantId: string,
  options?: IMutationCallbacks<TenantStatus, void, XhandlerAxiosError>,
) => {
  const invalidate = useInvalidateAdminTenant();
  return useMutation<TenantStatus, XhandlerAxiosError, void>({
    mutationFn: async () =>
      (
        await adminCancelTenantExit({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data) => {
      await invalidate(tenantId);
      await options?.onSuccess?.(data, undefined);
    },
    onError: options?.onError,
  });
};

/**
 * `POST /data-protection/tenants/{id}/data-subject-requests`: a request that reached the
 * platform, forwarded to the tenant at once (DESIGN.md §16).
 */
export const useAdminRegisterDataSubjectRequestMutation = (
  tenantId: string,
  options?: IMutationCallbacks<
    DataSubjectRequestForwarded,
    NewRequest,
    XhandlerAxiosError
  >,
) =>
  useMutation<DataSubjectRequestForwarded, XhandlerAxiosError, NewRequest>({
    mutationFn: async (body) =>
      (
        await adminRegisterDataSubjectRequest({
          path: { tenantId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
