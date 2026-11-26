import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";

import {
  Breadcrumb,
  BreadcrumbItem,
  Content,
  ContentVariants,
  Flex,
  FlexItem,
  PageSection,
  PaginationVariant,
  Tab,
  Tabs,
  TabTitleText,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewFilters,
  DataViewTable,
  DataViewTextFilter,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { Entry } from "@app/client/xhandler";
import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { useDebouncedValue } from "@app/hooks/useDebouncedValue";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useCatalogEntriesListQuery,
  useCatalogEntryDeleteMutation,
} from "@app/queries/xhandler/catalogs";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { estimateItemCount, toPageParams } from "@app/utils/listState";

import {
  CATALOG_KINDS,
  catalogKindBySlug,
  fieldLabel,
  type ICatalogKind,
  tipoDocumentoLabel,
} from "./catalogKinds";
import { CATALOG_LIST_STATE } from "./catalogListState";
import { CatalogEntryModal } from "./components/CatalogEntryModal";

interface ICatalogListProps {
  companyId: string;
}

/** A cell's text: identity document types by name, the rest as stored. */
const cellText = (column: string, value: unknown): string => {
  if (value === undefined || value === null || value === "") return "-";
  return column === "tipo_documento_identidad"
    ? tipoDocumentoLabel(value)
    : String(value);
};

const entryName = (kind: ICatalogKind, entry: Entry) =>
  String(entry.data?.[kind.columns[kind.columns.length > 1 ? 1 : 0]] ?? "");

/**
 * A company's optional catalogs (DESIGN.md §17): customers, products, vehicles, drivers and
 * addresses, one tab each, that documents may reference instead of repeating the data.
 */
export const CatalogList: React.FC<ICatalogListProps> = ({ companyId }) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { disabledProps } = useTenantRole();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });
  // undefined: closed; null: a new entry; an entry: editing it.
  const [editing, setEditing] = useState<Entry | null | undefined>(undefined);
  const [deleting, setDeleting] = useState<Entry | null>(null);

  const [pagination, setPagination] = usePersistentState({
    ...CATALOG_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...CATALOG_LIST_STATE.filters,
    storage: "url",
  });
  const kind = catalogKindBySlug(filters.catalogo);
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters((prev) => ({ ...prev, q: "" }));
    toFirstPage();
  };
  const debouncedQ = useDebouncedValue({ value: filters.q.trim() });

  const { data, isLoading, error } = useCatalogEntriesListQuery({
    companyId,
    kind: kind.kind,
    params: { ...toPageParams(pagination), q: debouncedQ || undefined },
  });
  const entries = data?.items ?? [];

  const remove = useCatalogEntryDeleteMutation(companyId, {
    onSuccess: () => {
      addNotification({
        variant: "success",
        title: `Se eliminó ${kind.isFeminine ? "la" : "el"} ${kind.singular}`,
      });
      setDeleting(null);
    },
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: `No se pudo eliminar ${kind.isFeminine ? "la" : "el"} ${kind.singular}`,
        }),
      }),
  });

  const operatorGate = disabledProps("operator");
  const actionsFor = (entry: Entry): IAction[] => [
    {
      title: "Editar",
      onClick: () => setEditing(entry),
      ...operatorGate,
    },
    {
      title: "Eliminar",
      onClick: () => setDeleting(entry),
      ...operatorGate,
    },
  ];

  const columns: DataViewTh[] = [
    ...kind.columns.map((column) => ({ cell: fieldLabel(kind, column) })),
    { cell: "" },
  ];

  const rows = entries.map((entry) => ({
    id: entry.id ?? "",
    row: [
      ...kind.columns.map((column) => ({
        cell: cellText(column, entry.data?.[column]),
        props: { modifier: "truncate" as const },
      })),
      {
        cell: (
          <ActionsColumn
            items={actionsFor(entry)}
            popperProps={{ position: "right" }}
          />
        ),
        props: { isActionCell: true },
      },
    ],
  }));

  const isFiltered = Boolean(filters.q);
  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title={`No hay ${kind.label.toLowerCase()} en el catálogo`}
        body="Los registros del catálogo se pueden usar al emitir en lugar de repetir los datos."
        isFiltered={isFiltered}
        onClearFilters={clearAllFilters}
      />
    ),
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

  return (
    <>
      <DocumentTitle title={`Catálogos · ${company.ruc ?? ""}`} />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/organizaciones/$tenantId/empresas" params={{ tenantId }}>
              Empresas
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem>
            <Link
              to="/organizaciones/$tenantId/empresas/$companyId"
              params={{ tenantId, companyId }}
            >
              {company.ruc}
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>Catálogos</BreadcrumbItem>
        </Breadcrumb>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>Catálogos</Content>
            <Content component={ContentVariants.p}>
              Clientes, productos, vehículos, conductores y direcciones de{" "}
              {company.razon_social || company.ruc}, para no repetirlos en cada
              comprobante.
            </Content>
          </FlexItem>
          <FlexItem>
            <GatedButton
              variant="primary"
              onClick={() => setEditing(null)}
              {...operatorGate}
            >
              {kind.isFeminine ? "Nueva" : "Nuevo"} {kind.singular}
            </GatedButton>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <Tabs
          activeKey={kind.slug}
          onSelect={(_event, key) => {
            setFilters({ catalogo: String(key), q: "" });
            toFirstPage();
          }}
          aria-label="Catálogos"
        >
          {CATALOG_KINDS.map((item) => (
            <Tab
              key={item.slug}
              eventKey={item.slug}
              title={<TabTitleText>{item.label}</TabTitleText>}
            />
          ))}
        </Tabs>
        <DataView activeState={activeState}>
          <DataViewToolbar
            clearAllFilters={clearAllFilters}
            customLabelGroupContent={
              <ClearFiltersButton onClick={clearAllFilters} />
            }
            filters={
              <DataViewFilters
                values={{ q: filters.q }}
                onChange={(_key, next) => {
                  setFilters((prev) => ({ ...prev, ...next }));
                  toFirstPage();
                }}
              >
                <DataViewTextFilter
                  filterId="q"
                  title="Buscar"
                  placeholder="Buscar por código, nombre, documento o placa"
                />
              </DataViewFilters>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label={`Tabla de ${kind.label.toLowerCase()}`}
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
          <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
        </DataView>
      </PageSection>

      {editing !== undefined ? (
        <CatalogEntryModal
          companyId={companyId}
          kind={kind}
          entry={editing ?? undefined}
          onClose={() => setEditing(undefined)}
        />
      ) : null}
      <ConfirmActionModal
        isOpen={deleting !== null}
        title={`¿Eliminar ${kind.isFeminine ? "la" : "el"} ${kind.singular}?`}
        body={`${deleting ? entryName(kind, deleting) || kind.singular : ""} deja de aparecer en el catálogo. Los comprobantes ya emitidos no cambian.`}
        confirmLabel="Eliminar"
        cancelLabel="Cancelar"
        isConfirming={remove.isPending}
        onConfirm={() => {
          if (deleting) remove.mutate({ kind: kind.kind, entry: deleting });
        }}
        onCancel={() => setDeleting(null)}
      />
    </>
  );
};
