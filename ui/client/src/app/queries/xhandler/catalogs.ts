import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  createCatalogEntry,
  deleteCatalogEntry,
  type Entry,
  listCatalogEntries,
  type ListCatalogEntriesData,
  type PageEntry,
  updateCatalogEntry,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const CatalogsQueryKey = "catalogs";

export type ICatalogListParams = ListParams<ListCatalogEntriesData>;

/** The `kind`s `…/catalogs/{kind}` accepts (DESIGN.md §17). */
export const CATALOG_KIND_VALUES = [
  "customer",
  "product",
  "vehicle",
  "driver",
  "address",
] as const;

export type CatalogKind = (typeof CATALOG_KIND_VALUES)[number];

export const isCatalogKind = (value: string): value is CatalogKind =>
  (CATALOG_KIND_VALUES as readonly string[]).includes(value);

export const catalogKeys = {
  all: () => [CatalogsQueryKey] as const,
  list: (tenantId: string, companyId: string, kind: CatalogKind) =>
    [...catalogKeys.all(), tenantId, companyId, kind, "list"] as const,
  listQuery: (
    tenantId: string,
    companyId: string,
    kind: CatalogKind,
    params: ICatalogListParams,
  ) => [...catalogKeys.list(tenantId, companyId, kind), params] as const,
};

type CatalogListOptions = Omit<
  UseQueryOptions<PageEntry, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET …/companies/{id}/catalogs/{kind}` (viewer): one page of a company's entries of one
 * kind, by key; `q` matches key, name, description, document, plate or address.
 */
export const catalogEntriesListQueryOptions = ({
  tenantId,
  companyId,
  kind,
  params,
  options,
}: {
  tenantId: string;
  companyId: string;
  kind: CatalogKind;
  params: ICatalogListParams;
  options?: CatalogListOptions;
}) =>
  queryOptions<PageEntry, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: catalogKeys.listQuery(tenantId, companyId, kind, params),
    queryFn: async () =>
      (
        await listCatalogEntries({
          path: { tenantId, companyId, kind },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useCatalogEntriesListQuery = ({
  companyId,
  kind,
  params,
  options,
}: {
  companyId: string;
  kind: CatalogKind;
  params: ICatalogListParams;
  options?: CatalogListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    catalogEntriesListQueryOptions({
      tenantId,
      companyId,
      kind,
      params,
      options,
    }),
  );
};

export interface ICatalogEntrySave {
  kind: CatalogKind;
  /** The entry being edited; none creates one. */
  id?: string;
  data: Record<string, unknown>;
}

/**
 * `POST …/catalogs/{kind}` (`201`) or `PUT …/catalogs/{kind}/{id}` (operator): the whole entry's
 * data; `422` for a missing required field, `409` (`catalog_entry_exists`) for a taken key.
 */
export const useCatalogEntrySaveMutation = (
  companyId: string,
  options?: IMutationCallbacks<Entry, ICatalogEntrySave, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<Entry, XhandlerAxiosError, ICatalogEntrySave>({
    mutationFn: async ({ kind, id, data }) =>
      id
        ? (
            await updateCatalogEntry({
              path: { tenantId, companyId, kind, id },
              body: data,
              throwOnError: true,
            })
          ).data
        : (
            await createCatalogEntry({
              path: { tenantId, companyId, kind },
              body: data,
              throwOnError: true,
            })
          ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: catalogKeys.list(tenantId, companyId, variables.kind),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

export interface ICatalogEntryDelete {
  kind: CatalogKind;
  entry: Entry;
}

/** `DELETE …/catalogs/{kind}/{id}` (operator, `204`): soft-deletes the entry. */
export const useCatalogEntryDeleteMutation = (
  companyId: string,
  options?: IMutationCallbacks<void, ICatalogEntryDelete, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<void, XhandlerAxiosError, ICatalogEntryDelete>({
    mutationFn: async ({ kind, entry }) => {
      await deleteCatalogEntry({
        path: { tenantId, companyId, kind, id: entry.id ?? "" },
        throwOnError: true,
      });
    },
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: catalogKeys.list(tenantId, companyId, variables.kind),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
