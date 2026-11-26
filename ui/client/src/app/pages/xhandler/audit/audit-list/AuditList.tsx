import type React from "react";

import dayjs from "dayjs";

import {
  Content,
  ContentVariants,
  List,
  ListItem,
  PageSection,
  PaginationVariant,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useAuditListQuery } from "@app/queries/xhandler/audit";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import {
  estimateItemCount,
  PAGINATION_LIST_STATE,
  toPageParams,
} from "@app/utils/listState";

import { auditDetailPairs, auditObjectTypeLabel } from "./auditPresentation";

/**
 * The organization's audit log, newest first: who did what to which object, support sessions
 * and reads of secrets and personal data included (DESIGN.md §16, §26.1). Administrators only.
 */
export const AuditList: React.FC = () => {
  const { can } = useTenantRole();
  const isAdmin = can("admin");
  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_LIST_STATE.pagination,
    storage: "url",
  });

  const { data, isLoading, error } = useAuditListQuery({
    params: toPageParams(pagination),
    options: { enabled: isAdmin },
  });
  const entries = data?.items ?? [];

  const columns: DataViewTh[] = [
    { cell: "Fecha", props: { width: 15 } },
    { cell: "Actor", props: { width: 20 } },
    { cell: "Acción", props: { width: 20 } },
    { cell: "Objeto", props: { width: 20 } },
    { cell: "Detalles", props: { width: 25 } },
  ];

  const rows = entries.map((entry, index) => {
    const details = auditDetailPairs(entry.details);
    return {
      id: `${entry.at ?? ""}-${entry.object_id ?? ""}-${index}`,
      row: [
        {
          cell: entry.at ? dayjs(entry.at).format(RENDER_DATETIME_FORMAT) : "-",
        },
        {
          cell: entry.actor ?? "-",
          props: { modifier: "truncate" as const },
        },
        { cell: <code>{entry.action ?? "-"}</code> },
        {
          cell: (
            <>
              {auditObjectTypeLabel(entry.object_type)}
              {entry.object_id ? (
                <Content component={ContentVariants.small}>
                  {entry.object_id}
                </Content>
              ) : null}
            </>
          ),
        },
        {
          cell: details.length ? (
            <List isPlain>
              {details.map(([key, value]) => (
                <ListItem key={key}>
                  {key}: {value}
                </ListItem>
              ))}
            </List>
          ) : (
            "-"
          ),
          props: { modifier: "breakWord" as const },
        },
      ],
    };
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: entries.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title={
          pagination.page > 1
            ? "No hay más registros"
            : "Aún no hay registros de auditoría"
        }
        body="Aquí aparecerán los cambios, las sesiones de soporte y las lecturas de secretos y datos personales."
      />
    ),
  });

  return (
    <>
      <DocumentTitle title="Auditoría" />
      <TenantContextSelector />
      <PageSection>
        <Content component={ContentVariants.h1}>Auditoría</Content>
        <Content component={ContentVariants.p}>
          Quién hizo qué en esta organización, del más reciente al más antiguo,
          incluidas las sesiones de soporte de la plataforma.
        </Content>
      </PageSection>
      <PageSection>
        {isAdmin ? (
          <DataView activeState={activeState}>
            <DataViewToolbar pagination={pager(PaginationVariant.top)} />
            <DataViewTable
              aria-label="Tabla de auditoría"
              columns={columns}
              rows={rows}
              bodyStates={bodyStates}
            />
            <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
          </DataView>
        ) : (
          <UnauthorizedState description="Necesitas el rol Administrador o superior para ver la auditoría de la organización." />
        )}
      </PageSection>
    </>
  );
};
