import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { CatalogList } from "./CatalogList";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/catalogos",
);

export const CatalogListRoute: React.FC = () => {
  const { companyId } = routeApi.useLoaderData();
  return <CatalogList companyId={companyId} />;
};
