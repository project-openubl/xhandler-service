import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  adminGetBillingIssuer,
  adminListSireCalendar,
  adminLoadSireCalendar,
  adminSetBillingIssuer,
  type BillingIssuer,
  type SireCalendarEntry,
  type SireCalendarLoaded,
} from "@app/client/xhandler-admin";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const AdminBillingQueryKey = "admin-billing";

/** Query-key factory mirroring `/api/admin/v1/billing` and `/sire/calendar`. */
export const adminBillingKeys = {
  all: () => [AdminBillingQueryKey] as const,
  issuer: () => [...adminBillingKeys.all(), "issuer"] as const,
  sireCalendar: () => [...adminBillingKeys.all(), "sire-calendar"] as const,
  sireCalendarPeriod: (period: string) =>
    [...adminBillingKeys.sireCalendar(), period] as const,
};

/**
 * `GET /api/admin/v1/billing/issuer`: the company that issues the platform's own invoices as
 * comprobantes (DESIGN.md §14); both ids empty while it isn't set.
 */
export const adminBillingIssuerQueryOptions = queryOptions<
  BillingIssuer,
  XhandlerAxiosError
>({
  queryKey: adminBillingKeys.issuer(),
  queryFn: async () =>
    (await adminGetBillingIssuer({ throwOnError: true })).data,
});

export const useAdminBillingIssuerQuery = () =>
  useQuery(adminBillingIssuerQueryOptions);

/** `PUT …/billing/issuer`: sets the issuing tenant and company (404 when the company is unknown). */
export const useAdminSetBillingIssuerMutation = (
  options?: IMutationCallbacks<
    BillingIssuer,
    BillingIssuer,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<BillingIssuer, XhandlerAxiosError, BillingIssuer>({
    mutationFn: async (body) =>
      (await adminSetBillingIssuer({ body, throwOnError: true })).data,
    onSuccess: async (data, body) => {
      queryClient.setQueryData(adminBillingKeys.issuer(), data);
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
};

/**
 * `GET /api/admin/v1/sire/calendar?period=`: SUNAT's due dates by period (`AAAAMM`) and RUC
 * last digit; empty is every period loaded. Unpaged (ten rows per period).
 */
export const adminSireCalendarQueryOptions = ({ period }: { period: string }) =>
  queryOptions<SireCalendarEntry[], XhandlerAxiosError>({
    queryKey: adminBillingKeys.sireCalendarPeriod(period),
    queryFn: async () =>
      (
        await adminListSireCalendar({
          query: period ? { period } : undefined,
          throwOnError: true,
        })
      ).data,
  });

export const useAdminSireCalendarQuery = ({ period }: { period: string }) =>
  useQuery(adminSireCalendarQueryOptions({ period }));

/** `PUT …/sire/calendar`: loads due dates, replacing those of the same period and digit. */
export const useAdminLoadSireCalendarMutation = (
  options?: IMutationCallbacks<
    SireCalendarLoaded,
    SireCalendarEntry[],
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<
    SireCalendarLoaded,
    XhandlerAxiosError,
    SireCalendarEntry[]
  >({
    mutationFn: async (entries) =>
      (await adminLoadSireCalendar({ body: entries, throwOnError: true })).data,
    onSuccess: async (data, entries) => {
      await queryClient.invalidateQueries({
        queryKey: adminBillingKeys.sireCalendar(),
      });
      await options?.onSuccess?.(data, entries);
    },
    onError: options?.onError,
  });
};
