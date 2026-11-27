import type React from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Content,
  ContentVariants,
  Label,
  LabelGroup,
  PageSection,
  PaginationVariant,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewFilters,
  DataViewTable,
  DataViewTextFilter,
  DataViewToolbar,
} from "@patternfly/react-data-view";

import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { ListPagination } from "@app/components/ListPagination";
import { PlatformAdminGate } from "@app/components/PlatformAdminGate";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATE_FORMAT } from "@app/constants";
import { useDebouncedValue } from "@app/hooks/useDebouncedValue";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useAdminTenantListQuery } from "@app/queries/xhandler-admin/tenants";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { estimateItemCount, toPageParams } from "@app/utils/listState";
import { tenantStatusLabel } from "@app/utils/tenantPresentation";

import { PLATFORM_TENANT_LIST_STATE } from "./platformTenantListState";

/** Every tenant of the platform, searchable by name (ADR 0012). Platform admins only. */
export const PlatformTenantList: React.FC = () => (
  <PlatformAdminGate>
    <PlatformTenantListContent />
  </PlatformAdminGate>
);

const PlatformTenantListContent: React.FC = () => {
  const [pagination, setPagination] = usePersistentState({
    ...PLATFORM_TENANT_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...PLATFORM_TENANT_LIST_STATE.filters,
    storage: "url",
  });
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(PLATFORM_TENANT_LIST_STATE.filters.defaultValue);
    toFirstPage();
  };
  const debouncedNombre = useDebouncedValue({ value: filters.nombre.trim() });

  const { data, isLoading, error } = useAdminTenantListQuery({
    params: { ...toPageParams(pagination), q: debouncedNombre || undefined },
  });
  const tenants = data?.items ?? [];

  const columns: DataViewTh[] = [
    { cell: "Organización", props: { width: 30 } },
    { cell: "Plan", props: { width: 15 } },
    { cell: "Estado", props: { width: 15 } },
    { cell: "Socio", props: { width: 15 } },
    { cell: "Empresas", props: { width: 10 } },
    { cell: "Creada", props: { width: 15 } },
  ];

  const rows = tenants.map((tenant) => ({
    id: tenant.id ?? "",
    row: [
      {
        cell: tenant.id ? (
          <Link
            to="/plataforma/organizaciones/$tenantId"
            params={{ tenantId: tenant.id }}
          >
            {tenant.name}
          </Link>
        ) : (
          tenant.name
        ),
        props: { modifier: "truncate" as const },
      },
      { cell: tenant.plan ?? "-" },
      {
        cell: (
          <LabelGroup>
            <Label isCompact>{tenantStatusLabel(tenant.status)}</Label>
            {tenant.sending_paused ? (
              <Label isCompact color="orange">
                Envíos pausados
              </Label>
            ) : null}
          </LabelGroup>
        ),
      },
      {
        cell:
          tenant.partner_id && tenant.partner_name ? (
            <Link
              to="/plataforma/socios/$partnerId"
              params={{ partnerId: tenant.partner_id }}
            >
              {tenant.partner_name}
            </Link>
          ) : (
            "-"
          ),
      },
      { cell: String(tenant.companies ?? 0) },
      {
        cell: tenant.created_at
          ? dayjs(tenant.created_at).format(RENDER_DATE_FORMAT)
          : "-",
      },
    ],
  }));

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: tenants.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  const isFiltered = !!filters.nombre.trim();
  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title={
          pagination.page > 1
            ? "No hay más organizaciones"
            : "La plataforma aún no tiene organizaciones"
        }
        isFiltered={isFiltered}
        onClearFilters={clearAllFilters}
      />
    ),
  });

  return (
    <>
      <DocumentTitle title="Organizaciones de la plataforma" />
      <PageSection>
        <Content component={ContentVariants.h1}>
          Organizaciones de la plataforma
        </Content>
        <Content component={ContentVariants.p}>
          Todas las organizaciones, con su plan, su estado y su socio. Desde
          aquí no se ven sus comprobantes.
        </Content>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewToolbar
            clearAllFilters={clearAllFilters}
            customLabelGroupContent={
              <ClearFiltersButton onClick={clearAllFilters} />
            }
            filters={
              <DataViewFilters
                values={filters}
                onChange={(_key, next) => {
                  setFilters((prev) => ({ ...prev, ...next }));
                  toFirstPage();
                }}
              >
                <DataViewTextFilter
                  filterId="nombre"
                  title="Nombre"
                  placeholder="Filtrar por nombre"
                />
              </DataViewFilters>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de organizaciones de la plataforma"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
          <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
        </DataView>
      </PageSection>
    </>
  );
};
