import { createFileRoute, notFound } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { PlatformPartnerDetailRoute } from "@app/pages/platform/partners/partner-detail/PlatformPartnerDetailRoute";
import { adminPartnerListQueryOptions } from "@app/queries/xhandler-admin/partners";

export const Route = createFileRoute(
  "/_authenticated/plataforma/socios/$partnerId",
)({
  // The admin API has no partner by id: an unknown one is missing from the (unpaged) list.
  loader: async ({ context: { queryClient }, params: { partnerId } }) => {
    const partners = await queryClient.query({
      ...adminPartnerListQueryOptions(),
      staleTime: "static",
    });
    if (!partners.some((partner) => partner.id === partnerId)) {
      throw notFound();
    }
    return { partnerId };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando socio" />,
  errorComponent: DetailRouteError,
  component: PlatformPartnerDetailRoute,
});
