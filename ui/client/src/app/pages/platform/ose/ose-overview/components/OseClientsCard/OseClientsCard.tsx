import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

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

import type { OseClient } from "@app/client/xhandler-admin";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  type OseClientActionKind,
  useAdminOseClientActionMutation,
  useAdminOseClientsQuery,
} from "@app/queries/xhandler-admin/ose";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import { CreateOseClientModal } from "../CreateOseClientModal";
import { OneTimePasswordModal } from "../OneTimePasswordModal";

const STATUS_LABELS: Record<string, string> = {
  active: "Activo",
  suspended: "Suspendido",
};

const CONFIRMATIONS: Record<
  OseClientActionKind,
  { title: string; body: (name: string) => string; confirmLabel: string }
> = {
  suspend: {
    title: "¿Suspender el usuario OSE?",
    body: (name) =>
      `${name} no podrá enviar comprobantes al OSE hasta que lo reactives.`,
    confirmLabel: "Suspender",
  },
  activate: {
    title: "¿Reactivar el usuario OSE?",
    body: (name) => `${name} podrá volver a enviar comprobantes al OSE.`,
    confirmLabel: "Reactivar",
  },
  "reset-password": {
    title: "¿Generar una contraseña nueva?",
    body: (name) =>
      `La contraseña actual de ${name} deja de funcionar ahora; la nueva se muestra una sola vez.`,
    confirmLabel: "Generar contraseña",
  },
};

interface IPasswordShown {
  title: string;
  username: string;
  password: string;
}

const clientName = (client: OseClient) =>
  client.username ?? client.issuer_ruc ?? "el usuario";

/** The OSE's users, one per issuer RUC (DESIGN.md §11.4). */
export const OseClientsCard: React.FC = () => {
  const { addNotification } = useNotifications();
  const { data, isLoading, error } = useAdminOseClientsQuery();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [target, setTarget] = useState<{
    client: OseClient;
    action: OseClientActionKind;
  } | null>(null);
  const [passwordShown, setPasswordShown] = useState<IPasswordShown | null>(
    null,
  );

  const act = useAdminOseClientActionMutation({
    onSuccess: (_data, { client, action }) => {
      if (action !== "reset-password") {
        addNotification({
          variant: "success",
          title:
            action === "suspend"
              ? `Se suspendió ${clientName(client)}`
              : `Se reactivó ${clientName(client)}`,
        });
      }
    },
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo cambiar el usuario OSE",
        }),
      }),
  });

  const columns: DataViewTh[] = [
    { cell: "Usuario", props: { width: 25 } },
    { cell: "RUC del emisor", props: { width: 20 } },
    { cell: "Estado", props: { width: 15 } },
    { cell: "Organización", props: { width: 15 } },
    { cell: "Último ingreso", props: { width: 25 } },
    { cell: "" },
  ];

  const rows = (data ?? []).map((client) => ({
    id: client.id ?? "",
    row: [
      {
        cell: client.username ?? "-",
        props: { modifier: "truncate" as const },
      },
      { cell: client.issuer_ruc ?? "-" },
      {
        cell: (
          <Label
            isCompact
            color={client.status === "active" ? "green" : "orange"}
          >
            {STATUS_LABELS[client.status ?? ""] ?? client.status ?? "-"}
          </Label>
        ),
      },
      {
        cell: client.tenant_id ? (
          <Link
            to="/plataforma/organizaciones/$tenantId"
            params={{ tenantId: client.tenant_id }}
          >
            Ver organización
          </Link>
        ) : (
          "-"
        ),
      },
      {
        cell: client.last_login_at
          ? dayjs(client.last_login_at).format(RENDER_DATETIME_FORMAT)
          : "Nunca",
      },
      {
        cell: (
          <ActionsColumn
            items={[
              client.status === "suspended"
                ? {
                    title: "Reactivar",
                    onClick: () => setTarget({ client, action: "activate" }),
                  }
                : {
                    title: "Suspender",
                    onClick: () => setTarget({ client, action: "suspend" }),
                  },
              {
                title: "Generar contraseña nueva",
                onClick: () => setTarget({ client, action: "reset-password" }),
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
    emptyState: <TableEmptyState title="Aún no hay usuarios OSE" />,
  });

  const confirmation = target ? CONFIRMATIONS[target.action] : null;
  return (
    <Card>
      <CardHeader
        actions={{
          actions: (
            <Button variant="secondary" onClick={() => setIsCreateOpen(true)}>
              Nuevo usuario OSE
            </Button>
          ),
          hasNoOffset: false,
        }}
      >
        <CardTitle component="h2">Usuarios OSE</CardTitle>
      </CardHeader>
      <CardBody>
        <DataView activeState={activeState}>
          <DataViewTable
            aria-label="Tabla de usuarios OSE"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
        </DataView>
      </CardBody>
      {isCreateOpen ? (
        <CreateOseClientModal
          onCreated={(created) => {
            setIsCreateOpen(false);
            setPasswordShown({
              title: "Se creó el usuario OSE",
              username: created.username ?? "",
              password: created.password ?? "",
            });
          }}
          onClose={() => setIsCreateOpen(false)}
        />
      ) : null}
      {target && confirmation ? (
        <ConfirmActionModal
          isOpen
          title={confirmation.title}
          body={confirmation.body(clientName(target.client))}
          confirmLabel={confirmation.confirmLabel}
          cancelLabel="Volver"
          confirmVariant="primary"
          titleIconVariant="warning"
          isConfirming={act.isPending}
          onConfirm={() =>
            act.mutate(target, {
              onSuccess: (result) => {
                setTarget(null);
                if (target.action === "reset-password" && result.password) {
                  setPasswordShown({
                    title: "Contraseña nueva del usuario OSE",
                    username: clientName(target.client),
                    password: result.password,
                  });
                }
              },
            })
          }
          onCancel={() => setTarget(null)}
        />
      ) : null}
      {passwordShown ? (
        <OneTimePasswordModal
          title={passwordShown.title}
          username={passwordShown.username}
          password={passwordShown.password}
          onClose={() => setPasswordShown(null)}
        />
      ) : null}
    </Card>
  );
};
