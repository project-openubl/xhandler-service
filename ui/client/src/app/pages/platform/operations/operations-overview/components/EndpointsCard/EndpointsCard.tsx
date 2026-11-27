import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  Card,
  CardBody,
  CardTitle,
  Label,
  LabelGroup,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn } from "@patternfly/react-table";

import type { Endpoint } from "@app/client/xhandler-admin";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  useAdminEndpointPauseMutation,
  useAdminEndpointsQuery,
} from "@app/queries/xhandler-admin/operations";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import {
  BREAKER_STATE_LABELS,
  sunatEndpointLabel,
} from "@app/utils/sunatEndpointPresentation";

import { EndpointCapModal } from "../EndpointCapModal";

const BREAKER_COLORS: Record<string, "green" | "red" | "orange"> = {
  closed: "green",
  open: "red",
  half_open: "orange",
};

/** Breakers change on their own (DESIGN.md §24.2): the table refreshes while shown. */
const REFRESH_MS = 15_000;

/** SUNAT services per environment: breaker, cap and pause, with the platform's switches. */
export const EndpointsCard: React.FC = () => {
  const { addNotification } = useNotifications();
  const { data, isLoading, error } = useAdminEndpointsQuery({
    options: { refetchInterval: REFRESH_MS },
  });
  const [pauseTarget, setPauseTarget] = useState<Endpoint | null>(null);
  const [capTarget, setCapTarget] = useState<Endpoint | null>(null);

  const pause = useAdminEndpointPauseMutation({
    onSuccess: (data, { pause: paused }) =>
      addNotification({
        variant: "success",
        title: paused
          ? `Se pausaron los envíos a ${sunatEndpointLabel(data.id)}`
          : `Se reanudaron los envíos a ${sunatEndpointLabel(data.id)}`,
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo cambiar la pausa del servicio",
        }),
      }),
  });

  const columns: DataViewTh[] = [
    { cell: "Servicio", props: { width: 35 } },
    { cell: "Estado", props: { width: 30 } },
    { cell: "Límite", props: { width: 15 } },
    { cell: "Abierto hasta", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = (data ?? []).map((endpoint) => ({
    id: endpoint.id ?? "",
    row: [
      { cell: sunatEndpointLabel(endpoint.id) },
      {
        cell: (
          <LabelGroup>
            <Label
              isCompact
              color={BREAKER_COLORS[endpoint.breaker_state ?? ""] ?? "grey"}
            >
              {BREAKER_STATE_LABELS[endpoint.breaker_state ?? ""] ??
                endpoint.breaker_state ??
                "-"}
            </Label>
            {endpoint.paused ? (
              <Label isCompact color="orange">
                Pausado
              </Label>
            ) : null}
          </LabelGroup>
        ),
      },
      { cell: (endpoint.concurrency_cap ?? 0).toLocaleString("es-PE") },
      {
        cell: endpoint.breaker_until
          ? dayjs(endpoint.breaker_until).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
      {
        cell: (
          <ActionsColumn
            items={[
              {
                title: endpoint.paused ? "Reanudar envíos" : "Pausar envíos",
                onClick: () => setPauseTarget(endpoint),
              },
              {
                title: "Cambiar límite",
                onClick: () => setCapTarget(endpoint),
              },
            ]}
            popperProps={{ position: "right" }}
          />
        ),
        props: { isActionCell: true },
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
        title="Aún no hay servicios de SUNAT"
        body="Aparecen cuando se envía el primer comprobante a cada uno."
      />
    ),
  });

  const pauseLabel = sunatEndpointLabel(pauseTarget?.id);
  return (
    <Card>
      <CardTitle component="h2">Servicios de SUNAT</CardTitle>
      <CardBody>
        <DataView activeState={activeState}>
          <DataViewTable
            aria-label="Tabla de servicios de SUNAT"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
        </DataView>
      </CardBody>
      {pauseTarget ? (
        <ConfirmActionModal
          isOpen
          title={
            pauseTarget.paused
              ? "¿Reanudar los envíos al servicio?"
              : "¿Pausar los envíos al servicio?"
          }
          body={
            pauseTarget.paused
              ? `Se reanudan los envíos de todas las organizaciones a ${pauseLabel}.`
              : `Ninguna organización enviará a ${pauseLabel} hasta que lo reanudes; sus comprobantes esperan.`
          }
          confirmLabel={
            pauseTarget.paused ? "Reanudar envíos" : "Pausar envíos"
          }
          cancelLabel="Volver"
          confirmVariant="primary"
          titleIconVariant="warning"
          isConfirming={pause.isPending}
          onConfirm={() =>
            pause.mutate(
              { endpointId: pauseTarget.id ?? "", pause: !pauseTarget.paused },
              { onSuccess: () => setPauseTarget(null) },
            )
          }
          onCancel={() => setPauseTarget(null)}
        />
      ) : null}
      {capTarget ? (
        <EndpointCapModal
          endpoint={capTarget}
          onClose={() => setCapTarget(null)}
        />
      ) : null}
    </Card>
  );
};
