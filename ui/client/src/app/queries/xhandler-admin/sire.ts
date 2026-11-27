import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  adminListSireCalendar,
  adminLoadSireCalendar,
  type SireCalendarEntry,
  type SireCalendarLoaded,
} from "@app/client/xhandler-admin";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const AdminSireQueryKey = "admin-sire";

/** Query-key factory mirroring `/api/admin/v1/sire/calendar`. */
export const adminSireKeys = {
  all: () => [AdminSireQueryKey] as const,
  calendar: () => [...adminSireKeys.all(), "calendar"] as const,
  calendarPeriod: (period: string) =>
    [...adminSireKeys.calendar(), period] as const,
};

/**
 * `GET /api/admin/v1/sire/calendar?period=`: SUNAT's due dates by period (`AAAAMM`) and RUC
 * last digit; empty is every period loaded. Unpaged (ten rows per period).
 */
export const adminSireCalendarQueryOptions = ({ period }: { period: string }) =>
  queryOptions<SireCalendarEntry[], XhandlerAxiosError>({
    queryKey: adminSireKeys.calendarPeriod(period),
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
        queryKey: adminSireKeys.calendar(),
      });
      await options?.onSuccess?.(data, entries);
    },
    onError: options?.onError,
  });
};
