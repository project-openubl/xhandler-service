import type { UseQueryOptions } from "@tanstack/react-query";
import { queryOptions, useQuery } from "@tanstack/react-query";

import { getExchangeRate, type Rate } from "@app/client/xhandler";

import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const ExchangeRatesQueryKey = "exchange-rates";

export const exchangeRateKeys = {
  all: () => [ExchangeRatesQueryKey] as const,
  /** `day` empty means today in Lima. */
  day: (day: string) => [...exchangeRateKeys.all(), day] as const,
};

type ExchangeRateOptions = Omit<
  UseQueryOptions<Rate, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET /exchange-rates?date=` (any signed-in user): SUNAT's purchase and sale rate of a day
 * (`AAAA-MM-DD`, today in Lima when empty), or the latest earlier one; `404` when there is none
 * (DESIGN.md §26.7). Rates of a day don't change: cached for the session.
 */
export const exchangeRateQueryOptions = ({
  day,
  options,
}: {
  day: string;
  options?: ExchangeRateOptions;
}) =>
  queryOptions<Rate, XhandlerAxiosError>({
    staleTime: Infinity,
    retry: false,
    ...options,
    queryKey: exchangeRateKeys.day(day),
    queryFn: async () =>
      (
        await getExchangeRate({
          query: day ? { date: day } : undefined,
          throwOnError: true,
        })
      ).data,
  });

export const useExchangeRateQuery = ({
  day,
  options,
}: {
  day: string;
  options?: ExchangeRateOptions;
}) => useQuery(exchangeRateQueryOptions({ day, options }));
