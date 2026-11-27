import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { PlatformTenantDetail } from "./PlatformTenantDetail";

const routeApi = getRouteApi(
  "/_authenticated/plataforma/organizaciones/$tenantId",
);

export const PlatformTenantDetailRoute: React.FC = () => {
  const { tenantId } = routeApi.useLoaderData();
  return <PlatformTenantDetail tenantId={tenantId} />;
};
