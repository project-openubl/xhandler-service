import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";

import { PaginationVariant } from "@patternfly/react-core";
import {
  DataView,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { usePartnerTenantsQuery } from "@app/queries/xhandler/partners";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { PAGINATION_LIST_STATE } from "@app/utils/listState";

import { CreatePartnerTenantModal } from "../CreatePartnerTenantModal";

interface IPartnerTenantsTabProps {
  partnerId: string;
  /** Disabled when the user isn't an admin of the partner. */
  gateProps: { isAriaDisabled?: boolean; tooltipProps?: { content: string } };
}

const TENANT_STATUS_LABELS: Record<string, string> = {
  active: "Activa",
  suspended: "Suspendida",
  exited: "Dada de baja",
};

/** The partner's tenants (DESIGN.md §14). */
export const PartnerTenantsTab: React.FC<IPartnerTenantsTabProps> = ({
  partnerId,
  gateProps,
}) => {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_LIST_STATE.pagination,
    storage: "url",
  });

  // GET …/partners/{id}/tenants is unpaged (one row per tenant of the partner): read whole,
  // paged on screen.
  const { data, isLoading, error } = usePartnerTenantsQuery({ partnerId });
  const tenants = data ?? [];
  const start = (pagination.page - 1) * pagination.perPage;
  const pageTenants = tenants.slice(start, start + pagination.perPage);

  const columns: DataViewTh[] = [
    { cell: "Organización", props: { width: 70 } },
    { cell: "Estado", props: { width: 30 } },
  ];

  const rows = pageTenants.map((tenant) => ({
    id: tenant.id ?? "",
    row: [
      {
        cell: tenant.id ? (
          <Link
            to="/organizaciones/$tenantId/empresas"
            params={{ tenantId: tenant.id }}
          >
            {tenant.name}
          </Link>
        ) : (
          tenant.name
        ),
        props: { modifier: "truncate" as const },
      },
      {
        cell: TENANT_STATUS_LABELS[tenant.status ?? ""] ?? tenant.status ?? "-",
      },
    ],
  }));

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="El socio aún no tiene organizaciones"
        body="Las organizaciones que crees para tus clientes aparecerán aquí."
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={tenants.length}
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DataView activeState={activeState}>
        <DataViewToolbar
          actions={
            <GatedButton
              variant="primary"
              onClick={() => setIsCreateOpen(true)}
              {...gateProps}
            >
              Nueva organización
            </GatedButton>
          }
          pagination={pager(PaginationVariant.top)}
        />
        <DataViewTable
          aria-label="Tabla de organizaciones del socio"
          columns={columns}
          rows={rows}
          bodyStates={bodyStates}
        />
        <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
      </DataView>
      <CreatePartnerTenantModal
        partnerId={partnerId}
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
      />
    </>
  );
};
