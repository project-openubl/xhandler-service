import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  adminActivatePseSolUser,
  adminAddPseSolUser,
  adminApprovePseAuthorization,
  adminDeactivatePseSolUser,
  adminDownloadPseAuthorizationEvidence,
  adminGetPseCertificate,
  adminGetPseRejectedRate,
  adminImportPseCertificate,
  adminListPendingPseAuthorizations,
  adminListPseSolUsers,
  type PendingPseAuthorization,
  type PlatformCertificate,
  type PlatformCredential,
  type PlatformCredentialState,
  type PseAuthorizationApproved,
  type PseRejectedRate,
  type SolUserRequest,
} from "@app/client/xhandler-admin";

import { downloadFile } from "../utils/fileDownload";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const AdminPseQueryKey = "admin-pse";

/** Query-key factory mirroring `/api/admin/v1/pse`. */
export const adminPseKeys = {
  all: () => [AdminPseQueryKey] as const,
  certificate: () => [...adminPseKeys.all(), "certificate"] as const,
  solUsers: () => [...adminPseKeys.all(), "sol-users"] as const,
  pending: () => [...adminPseKeys.all(), "authorizations", "pending"] as const,
  rejectedRate: (period: string) =>
    [...adminPseKeys.all(), "rejected-rate", period] as const,
};

/** `GET /api/admin/v1/pse/certificate`: the platform's PSE certificate; 404 when there is none. */
export const adminPseCertificateQueryOptions = queryOptions<
  PlatformCertificate,
  XhandlerAxiosError
>({
  queryKey: adminPseKeys.certificate(),
  queryFn: async () =>
    (await adminGetPseCertificate({ throwOnError: true })).data,
  // A 404 is an answer (no certificate yet), not a failure worth retrying.
  retry: false,
});

export const useAdminPseCertificateQuery = () =>
  useQuery(adminPseCertificateQueryOptions);

/** `GET …/pse/sol-users`: the PSE pool's SOL users; unpaged (a handful). */
export const adminPseSolUsersQueryOptions = queryOptions<
  PlatformCredential[],
  XhandlerAxiosError
>({
  queryKey: adminPseKeys.solUsers(),
  queryFn: async () =>
    (await adminListPseSolUsers({ throwOnError: true })).data,
});

export const useAdminPseSolUsersQuery = () =>
  useQuery(adminPseSolUsersQueryOptions);

/** `GET …/pse/authorizations/pending`: authorizations waiting for approval; unpaged (a few). */
export const adminPendingPseAuthorizationsQueryOptions = queryOptions<
  PendingPseAuthorization[],
  XhandlerAxiosError
>({
  queryKey: adminPseKeys.pending(),
  queryFn: async () =>
    (await adminListPendingPseAuthorizations({ throwOnError: true })).data,
});

export const useAdminPendingPseAuthorizationsQuery = () =>
  useQuery(adminPendingPseAuthorizationsQueryOptions);

type RejectedRateOptions = Omit<
  UseQueryOptions<PseRejectedRate, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET …/pse/rejected-rate?period=`: the half-year's rejected-CDR rate against its limit
 * (`AAAA-H1`/`AAAA-H2`; empty is the current half-year in Lima).
 */
export const adminPseRejectedRateQueryOptions = ({
  period,
  options,
}: {
  period: string;
  options?: RejectedRateOptions;
}) =>
  queryOptions<PseRejectedRate, XhandlerAxiosError>({
    ...options,
    queryKey: adminPseKeys.rejectedRate(period),
    queryFn: async () =>
      (
        await adminGetPseRejectedRate({
          query: period ? { period } : undefined,
          throwOnError: true,
        })
      ).data,
  });

export const useAdminPseRejectedRateQuery = ({
  period,
  options,
}: {
  period: string;
  options?: RejectedRateOptions;
}) => useQuery(adminPseRejectedRateQueryOptions({ period, options }));

export interface IAdminCertificateImportVariables {
  file: File;
  password: string;
}

/** `POST …/pse/certificate` (multipart): imports or replaces the platform's PSE certificate. */
export const useAdminImportPseCertificateMutation = (
  options?: IMutationCallbacks<
    PlatformCertificate,
    IAdminCertificateImportVariables,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<
    PlatformCertificate,
    XhandlerAxiosError,
    IAdminCertificateImportVariables
  >({
    mutationFn: async ({ file, password }) =>
      (
        await adminImportPseCertificate({
          body: { file, password },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      queryClient.setQueryData(adminPseKeys.certificate(), data);
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `POST …/pse/sol-users`: adds a SOL user to the PSE pool (sealed; reads are logged). */
export const useAdminAddPseSolUserMutation = (
  options?: IMutationCallbacks<
    PlatformCredential,
    SolUserRequest,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<PlatformCredential, XhandlerAxiosError, SolUserRequest>({
    mutationFn: async (body) =>
      (await adminAddPseSolUser({ body, throwOnError: true })).data,
    onSuccess: async (data, body) => {
      await queryClient.invalidateQueries({
        queryKey: adminPseKeys.solUsers(),
      });
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
};

export interface IAdminSolUserActiveVariables {
  id: string;
  active: boolean;
}

/** `POST …/pse/sol-users/{id}/activate` or `…/deactivate`: uses it again, or stops using it. */
export const useAdminPseSolUserActiveMutation = (
  options?: IMutationCallbacks<
    PlatformCredentialState,
    IAdminSolUserActiveVariables,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<
    PlatformCredentialState,
    XhandlerAxiosError,
    IAdminSolUserActiveVariables
  >({
    mutationFn: async ({ id, active }) =>
      (
        await (active ? adminActivatePseSolUser : adminDeactivatePseSolUser)({
          path: { id },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: adminPseKeys.solUsers(),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `POST …/pse/authorizations/{id}/approve`: the company may now sign in PSE mode. */
export const useAdminApprovePseAuthorizationMutation = (
  options?: IMutationCallbacks<
    PseAuthorizationApproved,
    PendingPseAuthorization,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<
    PseAuthorizationApproved,
    XhandlerAxiosError,
    PendingPseAuthorization
  >({
    mutationFn: async (authorization) =>
      (
        await adminApprovePseAuthorization({
          path: { id: authorization.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, authorization) => {
      await queryClient.invalidateQueries({ queryKey: adminPseKeys.pending() });
      await options?.onSuccess?.(data, authorization);
    },
    onError: options?.onError,
  });
};

/** `GET …/pse/authorizations/{id}/evidence`: saves the evidence the tenant uploaded. */
export const useAdminDownloadPseEvidenceMutation = (
  options?: IMutationCallbacks<
    void,
    PendingPseAuthorization,
    XhandlerAxiosError
  >,
) =>
  useMutation<void, XhandlerAxiosError, PendingPseAuthorization>({
    mutationFn: (authorization) =>
      downloadFile({
        request: () =>
          adminDownloadPseAuthorizationEvidence({
            path: { id: authorization.id ?? "" },
            responseType: "blob",
            throwOnError: true,
          }),
        fallbackName: `autorizacion-pse-${authorization.ruc ?? authorization.id ?? ""}`,
      }),
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
