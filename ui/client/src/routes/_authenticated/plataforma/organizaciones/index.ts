import { createFileRoute } from "@tanstack/react-router";

import { PlatformTenantList } from "@app/pages/platform/tenants/tenant-list/PlatformTenantList";
import { PLATFORM_TENANT_LIST_STATE } from "@app/pages/platform/tenants/tenant-list/platformTenantListState";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(PLATFORM_TENANT_LIST_STATE);

export const Route = createFileRoute(
  "/_authenticated/plataforma/organizaciones/",
)({
  validateSearch,
  search,
  component: PlatformTenantList,
});
