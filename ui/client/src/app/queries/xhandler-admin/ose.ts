import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  adminActivateOseClient,
  adminCreateOseClient,
  adminGetOseCdrCertificate,
  adminGetOseForwarding,
  adminGetOseUsage,
  adminImportOseAssociation,
  adminImportOseBlockedList,
  adminImportOseCdrCertificate,
  adminListOseClients,
  adminReforwardOseSubmission,
  adminResetOseClientPassword,
  adminSetOseSunatCredentials,
  adminSuspendOseClient,
  type Association,
  type Blocked,
  type BlockedListImported,
  type Imported,
  type NewClient,
  type OseClient,
  type OseClientAction,
  type OseClientCreated,
  type OseForwarding,
  type OseReforward,
  type OseRejectedForward,
  type OseUsage,
  type PlatformCertificate,
  type PlatformCredential,
  type SolUserRequest,
} from "@app/client/xhandler-admin";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";
import type { IAdminCertificateImportVariables } from "./pse";

export const AdminOseQueryKey = "admin-ose";

/** `ose_client.status` (migration V21). */
export const OSE_CLIENT_STATUSES = ["active", "suspended"] as const;

/** Query-key factory mirroring `/api/admin/v1/ose`. */
export const adminOseKeys = {
  all: () => [AdminOseQueryKey] as const,
  clients: () => [...adminOseKeys.all(), "clients"] as const,
  cdrCertificate: () => [...adminOseKeys.all(), "cdr-certificate"] as const,
  forwarding: () => [...adminOseKeys.all(), "forwarding"] as const,
  usage: (month: string) => [...adminOseKeys.all(), "usage", month] as const,
};

/** `GET /api/admin/v1/ose/clients`: every OSE user; unpaged (one per issuer RUC). */
export const adminOseClientsQueryOptions = queryOptions<
  OseClient[],
  XhandlerAxiosError
>({
  queryKey: adminOseKeys.clients(),
  queryFn: async () => (await adminListOseClients({ throwOnError: true })).data,
});

export const useAdminOseClientsQuery = () =>
  useQuery(adminOseClientsQueryOptions);

/** `GET …/ose/cdr-certificate`: the certificate that signs CDR-OSE; 404 when there is none. */
export const adminOseCdrCertificateQueryOptions = queryOptions<
  PlatformCertificate,
  XhandlerAxiosError
>({
  queryKey: adminOseKeys.cdrCertificate(),
  queryFn: async () =>
    (await adminGetOseCdrCertificate({ throwOnError: true })).data,
  // A 404 is an answer (no certificate yet), not a failure worth retrying.
  retry: false,
});

export const useAdminOseCdrCertificateQuery = () =>
  useQuery(adminOseCdrCertificateQueryOptions);

type OseForwardingOptions = Omit<
  UseQueryOptions<OseForwarding, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET …/ose/forwarding`: the queue of documents to forward to SUNAT against the 1-hour limit. */
export const adminOseForwardingQueryOptions = ({
  options,
}: {
  options?: OseForwardingOptions;
} = {}) =>
  queryOptions<OseForwarding, XhandlerAxiosError>({
    ...options,
    queryKey: adminOseKeys.forwarding(),
    queryFn: async () =>
      (await adminGetOseForwarding({ throwOnError: true })).data,
  });

export const useAdminOseForwardingQuery = ({
  options,
}: {
  options?: OseForwardingOptions;
} = {}) => useQuery(adminOseForwardingQueryOptions({ options }));

/**
 * `GET …/ose/usage?month=`: validations per OSE user in a month (`AAAA-MM`; empty is the
 * current one). Unpaged (one row per OSE user).
 */
export const adminOseUsageQueryOptions = ({ month }: { month: string }) =>
  queryOptions<OseUsage[], XhandlerAxiosError>({
    queryKey: adminOseKeys.usage(month),
    queryFn: async () =>
      (
        await adminGetOseUsage({
          query: month ? { month } : undefined,
          throwOnError: true,
        })
      ).data,
  });

export const useAdminOseUsageQuery = ({ month }: { month: string }) =>
  useQuery(adminOseUsageQueryOptions({ month }));

/** `POST …/ose/clients`: a new OSE user; its password comes back once. */
export const useAdminCreateOseClientMutation = (
  options?: IMutationCallbacks<OseClientCreated, NewClient, XhandlerAxiosError>,
) => {
  const queryClient = useQueryClient();
  return useMutation<OseClientCreated, XhandlerAxiosError, NewClient>({
    mutationFn: async (body) =>
      (await adminCreateOseClient({ body, throwOnError: true })).data,
    onSuccess: async (data, body) => {
      await queryClient.invalidateQueries({ queryKey: adminOseKeys.clients() });
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
};

/** What the platform can do to an OSE user. */
export const OSE_CLIENT_ACTIONS = [
  "suspend",
  "activate",
  "reset-password",
] as const;

export type OseClientActionKind = (typeof OSE_CLIENT_ACTIONS)[number];

export interface IAdminOseClientActionVariables {
  client: OseClient;
  action: OseClientActionKind;
}

const OSE_CLIENT_ACTION_CALLS = {
  suspend: adminSuspendOseClient,
  activate: adminActivateOseClient,
  "reset-password": adminResetOseClientPassword,
} satisfies Record<OseClientActionKind, unknown>;

/**
 * `POST …/ose/clients/{id}/suspend|activate|reset-password`; a reset answers the new password
 * once.
 */
export const useAdminOseClientActionMutation = (
  options?: IMutationCallbacks<
    OseClientAction,
    IAdminOseClientActionVariables,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<
    OseClientAction,
    XhandlerAxiosError,
    IAdminOseClientActionVariables
  >({
    mutationFn: async ({ client, action }) =>
      (
        await OSE_CLIENT_ACTION_CALLS[action]({
          path: { id: client.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({ queryKey: adminOseKeys.clients() });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/**
 * `PUT …/ose/association`: replaces SUNAT's list of issuers associated with the platform as
 * OSE. An empty list refuses every issuer, so it needs `confirm_empty`.
 */
export const useAdminImportOseAssociationMutation = (
  options?: IMutationCallbacks<Imported, Association[], XhandlerAxiosError>,
) =>
  useMutation<Imported, XhandlerAxiosError, Association[]>({
    mutationFn: async (entries) =>
      (
        await adminImportOseAssociation({
          body: entries,
          query: entries.length === 0 ? { confirm_empty: true } : undefined,
          throwOnError: true,
        })
      ).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });

export interface IAdminOseBlockedListVariables {
  list: string;
  entries: Blocked[];
}

/** `PUT …/ose/blocked/{list}`: replaces one of SUNAT's blocked-issuer lists (DL 1532). */
export const useAdminImportOseBlockedListMutation = (
  options?: IMutationCallbacks<
    BlockedListImported,
    IAdminOseBlockedListVariables,
    XhandlerAxiosError
  >,
) =>
  useMutation<
    BlockedListImported,
    XhandlerAxiosError,
    IAdminOseBlockedListVariables
  >({
    mutationFn: async ({ list, entries }) =>
      (
        await adminImportOseBlockedList({
          path: { list },
          body: entries,
          throwOnError: true,
        })
      ).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });

/** `POST …/ose/cdr-certificate` (multipart): the certificate that signs CDR-OSE. */
export const useAdminImportOseCdrCertificateMutation = (
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
        await adminImportOseCdrCertificate({
          body: { file, password },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      queryClient.setQueryData(adminOseKeys.cdrCertificate(), data);
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `POST …/ose/sunat-credentials`: the OSE's own SOL user, to forward documents to SUNAT. */
export const useAdminSetOseSunatCredentialsMutation = (
  options?: IMutationCallbacks<
    PlatformCredential,
    SolUserRequest,
    XhandlerAxiosError
  >,
) =>
  useMutation<PlatformCredential, XhandlerAxiosError, SolUserRequest>({
    mutationFn: async (body) =>
      (await adminSetOseSunatCredentials({ body, throwOnError: true })).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });

/** `POST …/ose/submissions/{id}/reforward`: sends a rejected forward to SUNAT again. */
export const useAdminReforwardOseSubmissionMutation = (
  options?: IMutationCallbacks<
    OseReforward,
    OseRejectedForward,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<OseReforward, XhandlerAxiosError, OseRejectedForward>({
    mutationFn: async (submission) =>
      (
        await adminReforwardOseSubmission({
          path: { id: submission.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, submission) => {
      await queryClient.invalidateQueries({
        queryKey: adminOseKeys.forwarding(),
      });
      await options?.onSuccess?.(data, submission);
    },
    onError: options?.onError,
  });
};
