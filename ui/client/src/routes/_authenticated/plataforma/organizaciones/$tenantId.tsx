import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { PlatformTenantDetailRoute } from "@app/pages/platform/tenants/tenant-detail/PlatformTenantDetailRoute";
import { adminTenantDetailQueryOptions } from "@app/queries/xhandler-admin/tenants";

export const Route = createFileRoute(
  "/_authenticated/plataforma/organizaciones/$tenantId",
)({
  // Without platform-admin the admin API answers 403, shown by DetailRouteError (ADR 0012).
  loader: async ({ context: { queryClient }, params: { tenantId } }) => {
    await queryClient.query({
      ...adminTenantDetailQueryOptions({ tenantId }),
      staleTime: "static",
    });
    return { tenantId };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando organización" />,
  errorComponent: DetailRouteError,
  component: PlatformTenantDetailRoute,
});
