import type React from "react";
import { useState } from "react";

import {
  Button,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  Label,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn } from "@patternfly/react-table";

import type { PlatformCredential } from "@app/client/xhandler-admin";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { SolUserFormModal } from "@app/components/SolUserFormModal";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  useAdminAddPseSolUserMutation,
  useAdminPseSolUserActiveMutation,
  useAdminPseSolUsersQuery,
} from "@app/queries/xhandler-admin/pse";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

/** The pool of platform SOL users PSE documents are spread over (DESIGN.md P7). */
export const PseSolUsersCard: React.FC = () => {
  const { addNotification } = useNotifications();
  const { data, isLoading, error } = useAdminPseSolUsersQuery();
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [deactivateTarget, setDeactivateTarget] =
    useState<PlatformCredential | null>(null);
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();

  const add = useAdminAddPseSolUserMutation({
    onSuccess: (credential) =>
      addNotification({
        variant: "success",
        title: `Se agregó ${credential.label ?? "el usuario SOL"} al grupo PSE`,
      }),
    onError: (err) =>
      setSubmitError(
        getMutationErrorMessage({
          error: err,
          fallback: "No se pudo agregar el usuario SOL",
        }),
      ),
  });
  const setActive = useAdminPseSolUserActiveMutation({
    onSuccess: (_data, { active }) =>
      addNotification({
        variant: "success",
        title: active
          ? "El usuario SOL vuelve a usarse"
          : "El usuario SOL deja de usarse",
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo cambiar el usuario SOL",
        }),
      }),
  });

  const columns: DataViewTh[] = [
    { cell: "Etiqueta", props: { width: 70 } },
    { cell: "Estado", props: { width: 30 } },
    { cell: "" },
  ];
  const rows = (data ?? []).map((credential) => ({
    id: credential.id ?? "",
    row: [
      {
        cell: credential.label ?? "-",
        props: { modifier: "truncate" as const },
      },
      {
        cell: (
          <Label isCompact color={credential.active ? "green" : "grey"}>
            {credential.active ? "En uso" : "Sin usar"}
          </Label>
        ),
      },
      {
        cell: (
          <ActionsColumn
            items={[
              credential.active
                ? {
                    title: "Dejar de usar",
                    onClick: () => setDeactivateTarget(credential),
                  }
                : {
                    title: "Volver a usar",
                    onClick: () =>
                      setActive.mutate({
                        id: credential.id ?? "",
                        active: true,
                      }),
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
      <TableEmptyState title="El grupo PSE aún no tiene usuarios SOL" />
    ),
  });

  return (
    <Card>
      <CardHeader
        actions={{
          actions: (
            <Button variant="secondary" onClick={() => setIsAddOpen(true)}>
              Agregar usuario SOL
            </Button>
          ),
          hasNoOffset: false,
        }}
      >
        <CardTitle component="h2">Usuarios SOL del grupo PSE</CardTitle>
      </CardHeader>
      <CardBody>
        <DataView activeState={activeState}>
          <DataViewTable
            aria-label="Tabla de usuarios SOL del grupo PSE"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
        </DataView>
      </CardBody>
      {isAddOpen ? (
        <SolUserFormModal
          title="Agregar usuario SOL"
          description="Un usuario SOL de la plataforma más para repartir los envíos en modo PSE."
          submitLabel="Agregar"
          isSaving={add.isPending}
          submitError={submitError}
          onSubmit={(body) => {
            clearSubmitError();
            add.mutate(body, { onSuccess: () => setIsAddOpen(false) });
          }}
          onClose={() => {
            clearSubmitError();
            setIsAddOpen(false);
          }}
        />
      ) : null}
      {deactivateTarget ? (
        <ConfirmActionModal
          isOpen
          title="¿Dejar de usar el usuario SOL?"
          body={`Los envíos en modo PSE ya no usarán ${deactivateTarget.label ?? "este usuario"}; puedes volver a usarlo después.`}
          confirmLabel="Dejar de usar"
          cancelLabel="Volver"
          confirmVariant="primary"
          titleIconVariant="warning"
          isConfirming={setActive.isPending}
          onConfirm={() =>
            setActive.mutate(
              { id: deactivateTarget.id ?? "", active: false },
              { onSuccess: () => setDeactivateTarget(null) },
            )
          }
          onCancel={() => setDeactivateTarget(null)}
        />
      ) : null}
    </Card>
  );
};
