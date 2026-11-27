import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  Card,
  CardBody,
  CardTitle,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Label,
  Spinner,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn } from "@patternfly/react-table";

import type { OseRejectedForward } from "@app/client/xhandler-admin";
import { ApiErrorText } from "@app/components/ApiErrorText";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  useAdminOseForwardingQuery,
  useAdminReforwardOseSubmissionMutation,
} from "@app/queries/xhandler-admin/ose";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

/** The queue moves on its own: refreshed while shown. */
const REFRESH_MS = 30_000;
/** RS 117-2017: every validated document reaches SUNAT within 1 hour. */
const FORWARD_LIMIT_SECONDS = 3600;

/** Forwarding of validated documents to SUNAT, against the 1-hour limit (DESIGN.md §11.4). */
export const OseForwardingCard: React.FC = () => {
  const { addNotification } = useNotifications();
  const { data, isLoading, error } = useAdminOseForwardingQuery({
    options: { refetchInterval: REFRESH_MS },
  });
  const [target, setTarget] = useState<OseRejectedForward | null>(null);
  const reforward = useAdminReforwardOseSubmissionMutation({
    onSuccess: (_data, submission) =>
      addNotification({
        variant: "info",
        title: `Se reenviará ${submission.name ?? "el comprobante"} a SUNAT`,
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo reenviar a SUNAT",
        }),
      }),
  });

  const oldest = data?.oldest_pending_seconds;
  const columns: DataViewTh[] = [
    { cell: "Comprobante", props: { width: 30 } },
    { cell: "Error", props: { width: 45 } },
    { cell: "CDR emitido", props: { width: 25 } },
    { cell: "" },
  ];
  const rows = (data?.rejected_items ?? []).map((item) => ({
    id: item.id ?? "",
    row: [
      { cell: item.name ?? "-", props: { modifier: "truncate" as const } },
      { cell: item.error ?? "-", props: { modifier: "truncate" as const } },
      {
        cell: item.cdr_issued_at
          ? dayjs(item.cdr_issued_at).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
      {
        cell: (
          <ActionsColumn
            items={[
              { title: "Reenviar a SUNAT", onClick: () => setTarget(item) },
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
    emptyState: <TableEmptyState title="No hay reenvíos rechazados" />,
  });

  const counts: { label: string; value: number | undefined }[] = [
    { label: "Por reenviar", value: data?.pending },
    { label: "Reenviados", value: data?.forwarded },
    { label: "Fuera de plazo", value: data?.late },
    { label: "Rechazados por SUNAT", value: data?.rejected },
    { label: "Bloqueados", value: data?.blocked },
  ];

  return (
    <Card>
      <CardTitle component="h2">Reenvío a SUNAT</CardTitle>
      <CardBody>
        <Stack hasGutter>
          <StackItem>
            {isLoading ? (
              <Spinner size="md" aria-label="Cargando reenvíos" />
            ) : error ? (
              <ApiErrorText
                error={getMutationErrorMessage({
                  error,
                  fallback: "No se pudo leer la cola de reenvío",
                })}
              />
            ) : (
              <DescriptionList
                isHorizontal
                columnModifier={{ default: "2Col" }}
                aria-label="Cola de reenvío a SUNAT"
              >
                {counts.map(({ label, value }) => (
                  <DescriptionListGroup key={label}>
                    <DescriptionListTerm>{label}</DescriptionListTerm>
                    <DescriptionListDescription>
                      {(value ?? 0).toLocaleString("es-PE")}
                    </DescriptionListDescription>
                  </DescriptionListGroup>
                ))}
                <DescriptionListGroup>
                  <DescriptionListTerm>
                    El más antiguo por reenviar
                  </DescriptionListTerm>
                  <DescriptionListDescription>
                    {oldest ? (
                      <>
                        hace {Math.round(oldest / 60).toLocaleString("es-PE")}{" "}
                        min{" "}
                        {oldest > FORWARD_LIMIT_SECONDS ? (
                          <Label isCompact color="red">
                            Supera 1 hora
                          </Label>
                        ) : null}
                      </>
                    ) : (
                      "-"
                    )}
                  </DescriptionListDescription>
                </DescriptionListGroup>
              </DescriptionList>
            )}
          </StackItem>
          <StackItem>
            <DataView activeState={activeState}>
              <DataViewTable
                aria-label="Tabla de reenvíos rechazados"
                columns={columns}
                rows={rows}
                bodyStates={bodyStates}
              />
            </DataView>
          </StackItem>
        </Stack>
      </CardBody>
      {target ? (
        <ConfirmActionModal
          isOpen
          title="¿Reenviar a SUNAT?"
          body={`${target.name ?? "El comprobante"} y su CDR se envían de nuevo a SUNAT.`}
          confirmLabel="Reenviar"
          cancelLabel="Volver"
          confirmVariant="primary"
          titleIconVariant="warning"
          isConfirming={reforward.isPending}
          onConfirm={() =>
            reforward.mutate(target, { onSuccess: () => setTarget(null) })
          }
          onCancel={() => setTarget(null)}
        />
      ) : null}
    </Card>
  );
};
