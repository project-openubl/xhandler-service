import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  Button,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  Stack,
  StackItem,
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
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATE_FORMAT } from "@app/constants";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useAdminSireCalendarQuery } from "@app/queries/xhandler-admin/billing";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import { isSirePeriod } from "../../utils/sireCalendar";
import { SireCalendarLoadModal } from "../SireCalendarLoadModal";
import { SIRE_CALENDAR_LIST_STATE } from "./sireCalendarState";

/** SUNAT's due dates for SIRE, which the daily deadline notices read (DESIGN.md §11.7). */
export const SireCalendarCard: React.FC = () => {
  // A card's list, not the page's main one: its state isn't kept in the URL (ADR 0008).
  const [filters, setFilters] = usePersistentState({
    ...SIRE_CALENDAR_LIST_STATE.filters,
    storage: "state",
  });
  const [isLoadOpen, setIsLoadOpen] = useState(false);
  const typed = filters.periodo.trim();
  // The API filters by a whole period only: ask once it is complete.
  const period = isSirePeriod(typed) ? typed : "";
  // Unpaged by the API (ten rows per period).
  const { data, isLoading, error } = useAdminSireCalendarQuery({ period });

  const columns: DataViewTh[] = [
    { cell: "Periodo", props: { width: 30 } },
    { cell: "Último dígito del RUC", props: { width: 30 } },
    { cell: "Vence", props: { width: 40 } },
  ];
  const rows = (data ?? []).map((entry) => ({
    id: `${entry.period}-${entry.ruc_last_digit}`,
    row: [
      { cell: entry.period },
      { cell: String(entry.ruc_last_digit ?? "-") },
      {
        cell: entry.due_date
          ? dayjs(entry.due_date).format(RENDER_DATE_FORMAT)
          : "-",
      },
    ],
  }));
  const clearFilters = () =>
    setFilters(SIRE_CALENDAR_LIST_STATE.filters.defaultValue);
  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="Aún no se cargó el cronograma"
        isFiltered={!!period}
        onClearFilters={clearFilters}
      />
    ),
  });

  return (
    <Card>
      <CardHeader
        actions={{
          actions: (
            <Button variant="secondary" onClick={() => setIsLoadOpen(true)}>
              Cargar cronograma
            </Button>
          ),
          hasNoOffset: false,
        }}
      >
        <CardTitle component="h2">Cronograma de SIRE</CardTitle>
      </CardHeader>
      <CardBody>
        <Stack hasGutter>
          <StackItem>
            <DataView activeState={activeState}>
              <DataViewToolbar
                clearAllFilters={clearFilters}
                customLabelGroupContent={
                  <ClearFiltersButton onClick={clearFilters} />
                }
                filters={
                  <DataViewFilters
                    values={filters}
                    onChange={(_key, next) =>
                      setFilters((prev) => ({ ...prev, ...next }))
                    }
                  >
                    <DataViewTextFilter
                      filterId="periodo"
                      title="Periodo"
                      placeholder="Filtrar por periodo (AAAAMM)"
                    />
                  </DataViewFilters>
                }
              />
              <DataViewTable
                aria-label="Tabla del cronograma de SIRE"
                columns={columns}
                rows={rows}
                bodyStates={bodyStates}
              />
            </DataView>
          </StackItem>
        </Stack>
      </CardBody>
      {isLoadOpen ? (
        <SireCalendarLoadModal onClose={() => setIsLoadOpen(false)} />
      ) : null}
    </Card>
  );
};
