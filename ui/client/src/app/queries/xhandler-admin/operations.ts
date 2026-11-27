import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  adminCancelJob,
  adminGetJob,
  adminHoldDocument,
  adminHoldEndpoint,
  adminListEndpoints,
  adminListMigrations,
  adminPurgeJobs,
  adminReleaseDocument,
  adminReleaseEndpoint,
  adminRepairPartitions,
  adminRequeueStuckDocuments,
  adminRetryDocument,
  adminRotateKeys,
  adminUpdateEndpoint,
  type Count,
  type DocumentState,
  type Endpoint,
  type JobState,
  type JobSummary,
  type KeyRotation,
  type Migration,
  type PartitionsRepaired,
} from "@app/client/xhandler-admin";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const AdminOperationsQueryKey = "admin-operations";

/** Query-key factory mirroring the admin API's operations paths. */
export const adminOperationKeys = {
  all: () => [AdminOperationsQueryKey] as const,
  endpoints: () => [...adminOperationKeys.all(), "endpoints"] as const,
  job: (jobId: string) => [...adminOperationKeys.all(), "jobs", jobId] as const,
  migrations: () => [...adminOperationKeys.all(), "migrations"] as const,
};

type AdminEndpointsOptions = Omit<
  UseQueryOptions<Endpoint[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET /api/admin/v1/endpoints`: each SUNAT service per environment with its breaker, cap and
 * pause. Unpaged (one row per environment and service).
 */
export const adminEndpointsQueryOptions = ({
  options,
}: {
  options?: AdminEndpointsOptions;
} = {}) =>
  queryOptions<Endpoint[], XhandlerAxiosError>({
    ...options,
    queryKey: adminOperationKeys.endpoints(),
    queryFn: async () =>
      (await adminListEndpoints({ throwOnError: true })).data,
  });

export const useAdminEndpointsQuery = ({
  options,
}: {
  options?: AdminEndpointsOptions;
} = {}) => useQuery(adminEndpointsQueryOptions({ options }));

type AdminJobOptions = Omit<
  UseQueryOptions<JobSummary, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET /api/admin/v1/jobs/{id}`: a job of any tenant, by id. */
export const adminJobQueryOptions = ({
  jobId,
  options,
}: {
  jobId: string;
  options?: AdminJobOptions;
}) =>
  queryOptions<JobSummary, XhandlerAxiosError>({
    ...options,
    queryKey: adminOperationKeys.job(jobId),
    queryFn: async () =>
      (await adminGetJob({ path: { id: jobId }, throwOnError: true })).data,
  });

export const useAdminJobQuery = ({
  jobId,
  options,
}: {
  jobId: string;
  options?: AdminJobOptions;
}) =>
  useQuery({
    ...adminJobQueryOptions({ jobId, options }),
    enabled: !!jobId && (options?.enabled ?? true),
  });

/** `GET /api/admin/v1/migrations`: the database migrations applied; unpaged (a few dozen). */
export const adminMigrationsQueryOptions = queryOptions<
  Migration[],
  XhandlerAxiosError
>({
  queryKey: adminOperationKeys.migrations(),
  queryFn: async () => (await adminListMigrations({ throwOnError: true })).data,
});

export const useAdminMigrationsQuery = () =>
  useQuery(adminMigrationsQueryOptions);

export interface IAdminEndpointPauseVariables {
  endpointId: string;
  pause: boolean;
}

/** `POST …/endpoints/{id}/hold` or `…/release`: pauses or resumes sends to one SUNAT service. */
export const useAdminEndpointPauseMutation = (
  options?: IMutationCallbacks<
    Endpoint,
    IAdminEndpointPauseVariables,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<
    Endpoint,
    XhandlerAxiosError,
    IAdminEndpointPauseVariables
  >({
    mutationFn: async ({ endpointId, pause }) =>
      (
        await (pause ? adminHoldEndpoint : adminReleaseEndpoint)({
          path: { id: endpointId },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: adminOperationKeys.endpoints(),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

export interface IAdminEndpointCapVariables {
  endpointId: string;
  concurrencyCap: number;
}

/** `PUT …/endpoints/{id}`: how many sends to that SUNAT service may run at once (at least 1). */
export const useAdminEndpointCapMutation = (
  options?: IMutationCallbacks<
    Endpoint,
    IAdminEndpointCapVariables,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<Endpoint, XhandlerAxiosError, IAdminEndpointCapVariables>({
    mutationFn: async ({ endpointId, concurrencyCap }) =>
      (
        await adminUpdateEndpoint({
          path: { id: endpointId },
          body: { concurrency_cap: concurrencyCap },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: adminOperationKeys.endpoints(),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `POST …/jobs/{id}/cancel`: asks any tenant's job to stop; answers its new state. */
export const useAdminCancelJobMutation = (
  options?: IMutationCallbacks<JobState, string, XhandlerAxiosError>,
) => {
  const queryClient = useQueryClient();
  return useMutation<JobState, XhandlerAxiosError, string>({
    mutationFn: async (jobId) =>
      (await adminCancelJob({ path: { id: jobId }, throwOnError: true })).data,
    onSuccess: async (data, jobId) => {
      await queryClient.invalidateQueries({
        queryKey: adminOperationKeys.job(jobId),
      });
      await options?.onSuccess?.(data, jobId);
    },
    onError: options?.onError,
  });
};

/** `POST …/jobs/purge?older_than_hours=`: deletes finished jobs older than that; answers how many. */
export const useAdminPurgeJobsMutation = (
  options?: IMutationCallbacks<Count, number, XhandlerAxiosError>,
) =>
  useMutation<Count, XhandlerAxiosError, number>({
    mutationFn: async (olderThanHours) =>
      (
        await adminPurgeJobs({
          query: { older_than_hours: olderThanHours },
          throwOnError: true,
        })
      ).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });

/** `POST …/keys/rotate`: re-wraps every secret and key with the current master key. */
export const useAdminRotateKeysMutation = (
  options?: IMutationCallbacks<KeyRotation, void, XhandlerAxiosError>,
) =>
  useMutation<KeyRotation, XhandlerAxiosError, void>({
    mutationFn: async () =>
      (await adminRotateKeys({ throwOnError: true })).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });

/** `POST …/repair/partitions`: creates now the partitions that are missing. */
export const useAdminRepairPartitionsMutation = (
  options?: IMutationCallbacks<PartitionsRepaired, void, XhandlerAxiosError>,
) =>
  useMutation<PartitionsRepaired, XhandlerAxiosError, void>({
    mutationFn: async () =>
      (await adminRepairPartitions({ throwOnError: true })).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });

/**
 * `POST …/repair/requeue-stuck`: makes due any document not yet final that no automatic attempt
 * would pick up; answers how many.
 */
export const useAdminRequeueStuckMutation = (
  options?: IMutationCallbacks<Count, void, XhandlerAxiosError>,
) =>
  useMutation<Count, XhandlerAxiosError, void>({
    mutationFn: async () =>
      (await adminRequeueStuckDocuments({ throwOnError: true })).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });

/** What the platform can do to one document of any tenant, by id. */
export const ADMIN_DOCUMENT_ACTIONS = ["retry", "hold", "release"] as const;

export type AdminDocumentAction = (typeof ADMIN_DOCUMENT_ACTIONS)[number];

export interface IAdminDocumentActionVariables {
  documentId: string;
  action: AdminDocumentAction;
}

const DOCUMENT_ACTION_CALLS = {
  retry: adminRetryDocument,
  hold: adminHoldDocument,
  release: adminReleaseDocument,
} satisfies Record<AdminDocumentAction, unknown>;

/** `POST …/documents/{id}/retry|hold|release`: answers the document's sending state. */
export const useAdminDocumentActionMutation = (
  options?: IMutationCallbacks<
    DocumentState,
    IAdminDocumentActionVariables,
    XhandlerAxiosError
  >,
) =>
  useMutation<DocumentState, XhandlerAxiosError, IAdminDocumentActionVariables>(
    {
      mutationFn: async ({ documentId, action }) =>
        (
          await DOCUMENT_ACTION_CALLS[action]({
            path: { id: documentId },
            throwOnError: true,
          })
        ).data,
      onSuccess: options?.onSuccess,
      onError: options?.onError,
    },
  );
