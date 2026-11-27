import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  ActionList,
  ActionListGroup,
  ActionListItem,
  Button,
  Card,
  CardBody,
  CardTitle,
  ExpandableSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";

import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  useAdminMigrationsQuery,
  useAdminRepairPartitionsMutation,
  useAdminRequeueStuckMutation,
  useAdminRotateKeysMutation,
} from "@app/queries/xhandler-admin/operations";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

type MaintenanceAction = "partitions" | "requeue" | "rotate";

const CONFIRMATIONS: Record<
  MaintenanceAction,
  { title: string; body: string; confirmLabel: string }
> = {
  partitions: {
    title: "¿Crear las particiones que falten?",
    body: "Crea ahora las particiones de comprobantes y eventos que la tarea diaria aún no creó.",
    confirmLabel: "Crear particiones",
  },
  requeue: {
    title: "¿Reencolar los comprobantes atascados?",
    body: "Todo comprobante sin respuesta definitiva que ningún intento automático tomaría, de todas las organizaciones, se intenta ahora.",
    confirmLabel: "Reencolar",
  },
  rotate: {
    title: "¿Rotar las llaves?",
    body: "Se vuelven a cifrar con la llave maestra actual todos los secretos y llaves de todas las organizaciones y de la plataforma. Puede tardar.",
    confirmLabel: "Rotar llaves",
  },
};

/** Platform maintenance (DESIGN.md §15): repairs, key rotation and the applied migrations. */
export const MaintenanceCard: React.FC = () => {
  const { addNotification } = useNotifications();
  const [confirm, setConfirm] = useState<MaintenanceAction | null>(null);
  const [isMigrationsOpen, setIsMigrationsOpen] = useState(false);
  const migrations = useAdminMigrationsQuery();
  const errorToast = (fallback: string) => (error: unknown) =>
    addNotification({
      variant: "danger",
      ...getMutationErrorMessage({ error, fallback }),
    });

  const partitions = useAdminRepairPartitionsMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: "Se crearon las particiones que faltaban",
        description: data.day ? `Hasta el ${data.day}.` : undefined,
      }),
    onError: errorToast("No se pudieron crear las particiones"),
  });
  const requeue = useAdminRequeueStuckMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `Se reencolaron ${(data.count ?? 0).toLocaleString("es-PE")} comprobantes`,
      }),
    onError: errorToast("No se pudieron reencolar los comprobantes"),
  });
  const rotate = useAdminRotateKeysMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: "Se rotaron las llaves",
        description: `Organizaciones: ${(data.tenants ?? 0).toLocaleString("es-PE")}; credenciales: ${(data.credentials ?? 0).toLocaleString("es-PE")}; llaves de firma: ${(data.signing_keys ?? 0).toLocaleString("es-PE")}.`,
      }),
    onError: errorToast("No se pudieron rotar las llaves"),
  });

  const close = { onSuccess: () => setConfirm(null) };
  const run = (action: MaintenanceAction) => {
    if (action === "partitions") partitions.mutate(undefined, close);
    if (action === "requeue") requeue.mutate(undefined, close);
    if (action === "rotate") rotate.mutate(undefined, close);
  };

  const columns: DataViewTh[] = [
    { cell: "Versión", props: { width: 15 } },
    { cell: "Descripción", props: { width: 45 } },
    { cell: "Estado", props: { width: 15 } },
    { cell: "Aplicada", props: { width: 25 } },
  ];
  const rows = (migrations.data ?? []).map((migration) => ({
    id: migration.version ?? "",
    row: [
      { cell: migration.version ?? "-" },
      {
        cell: migration.description ?? "-",
        props: { modifier: "truncate" as const },
      },
      { cell: migration.state ?? "-" },
      {
        cell: migration.installed_on
          ? dayjs(migration.installed_on).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
    ],
  }));
  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading: migrations.isLoading,
    error: migrations.error,
    isEmpty: rows.length === 0,
    emptyState: <TableEmptyState title="No hay migraciones aplicadas" />,
  });

  const confirmation = confirm ? CONFIRMATIONS[confirm] : null;
  return (
    <Card>
      <CardTitle component="h2">Mantenimiento</CardTitle>
      <CardBody>
        <Stack hasGutter>
          <StackItem>
            <ActionList>
              <ActionListGroup>
                <ActionListItem>
                  <Button
                    variant="secondary"
                    onClick={() => setConfirm("partitions")}
                  >
                    Crear particiones
                  </Button>
                </ActionListItem>
                <ActionListItem>
                  <Button
                    variant="secondary"
                    onClick={() => setConfirm("requeue")}
                  >
                    Reencolar comprobantes atascados
                  </Button>
                </ActionListItem>
                <ActionListItem>
                  <Button
                    variant="secondary"
                    onClick={() => setConfirm("rotate")}
                  >
                    Rotar llaves
                  </Button>
                </ActionListItem>
              </ActionListGroup>
            </ActionList>
          </StackItem>
          <StackItem>
            <ExpandableSection
              toggleText={`Migraciones aplicadas (${(migrations.data?.length ?? 0).toLocaleString("es-PE")})`}
              isExpanded={isMigrationsOpen}
              onToggle={(_event, isExpanded) => setIsMigrationsOpen(isExpanded)}
            >
              <DataView activeState={activeState}>
                <DataViewTable
                  aria-label="Tabla de migraciones"
                  columns={columns}
                  rows={rows}
                  bodyStates={bodyStates}
                />
              </DataView>
            </ExpandableSection>
          </StackItem>
        </Stack>
      </CardBody>
      {confirm && confirmation ? (
        <ConfirmActionModal
          isOpen
          title={confirmation.title}
          body={confirmation.body}
          confirmLabel={confirmation.confirmLabel}
          cancelLabel="Volver"
          confirmVariant="primary"
          titleIconVariant="warning"
          isConfirming={
            partitions.isPending || requeue.isPending || rotate.isPending
          }
          onConfirm={() => run(confirm)}
          onCancel={() => setConfirm(null)}
        />
      ) : null}
    </Card>
  );
};
