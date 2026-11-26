import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { CatalogListRoute } from "@app/pages/xhandler/catalogs/catalog-list/CatalogListRoute";
import { CATALOG_LIST_STATE } from "@app/pages/xhandler/catalogs/catalog-list/catalogListState";
import { companyDetailQueryOptions } from "@app/queries/xhandler/companies";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(CATALOG_LIST_STATE);

// `$companyId_`: under the company's address without nesting in its detail page.
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/catalogos",
)({
  validateSearch,
  search,
  loader: async ({
    context: { queryClient },
    params: { tenantId, companyId },
  }) => {
    await queryClient.query({
      ...companyDetailQueryOptions({ tenantId, companyId }),
      staleTime: "static",
    });
    return { companyId };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando empresa" />,
  errorComponent: DetailRouteError,
  component: CatalogListRoute,
});
