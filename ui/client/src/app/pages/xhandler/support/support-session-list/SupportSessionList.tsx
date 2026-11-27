import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  Content,
  ContentVariants,
  Label,
  PageSection,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { SessionView } from "@app/client/xhandler";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  supportSessionState,
  useSupportSessionApproveMutation,
  useSupportSessionRevokeMutation,
  useSupportSessionsQuery,
} from "@app/queries/xhandler/support";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import {
  SUPPORT_SESSION_STATE_PRESENTATION,
  supportSessionActions,
} from "./supportSessionPresentation";

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

interface IPendingAction {
  kind: "approve" | "revoke";
  session: SessionView;
}

/**
 * Support sessions the platform's operators asked of the organization (DESIGN.md §26.1): an
 * administrator approves one for 4 hours of read-only access, or revokes it; everything the
 * operator does is in Auditoría. Administrators only.
 */
export const SupportSessionList: React.FC = () => {
  const { can } = useTenantRole();
  const isAdmin = can("admin");
  const { addNotification } = useNotifications();
  const [pending, setPending] = useState<IPendingAction | null>(null);

  const { data, isLoading, error } = useSupportSessionsQuery({
    options: { enabled: isAdmin },
  });
  const sessions = [...(data ?? [])].sort((a, b) =>
    (b.requested_at ?? "").localeCompare(a.requested_at ?? ""),
  );

  const errorToast = (fallback: string) => (err: unknown) =>
    addNotification({
      variant: "danger",
      ...getMutationErrorMessage({ error: err, fallback }),
    });
  const approve = useSupportSessionApproveMutation({
    onSuccess: (session) =>
      addNotification({
        variant: "success",
        title: `Se aprobó la sesión de ${session.operator ?? "soporte"}`,
        description: `Puede ver la organización, sin cambiar nada, hasta ${formatTime(session.expires_at)}.`,
      }),
    onError: errorToast("No se pudo aprobar la sesión de soporte"),
  });
  const revoke = useSupportSessionRevokeMutation({
    onSuccess: (session) =>
      addNotification({
        variant: "success",
        title: `Se revocó la sesión de ${session.operator ?? "soporte"}`,
      }),
    onError: errorToast("No se pudo revocar la sesión de soporte"),
  });

  const actionsFor = (session: SessionView): IAction[] => {
    const { approve: approveReason, revoke: revokeReason } =
      supportSessionActions(supportSessionState(session));
    const gate = (reason: string | undefined) =>
      reason ? { isAriaDisabled: true, tooltipProps: { content: reason } } : {};
    return [
      {
        title: "Aprobar",
        onClick: () => setPending({ kind: "approve", session }),
        ...gate(approveReason),
      },
      {
        title:
          supportSessionState(session) === "pending" ? "Rechazar" : "Revocar",
        onClick: () => setPending({ kind: "revoke", session }),
        ...gate(revokeReason),
      },
    ];
  };

  const columns: DataViewTh[] = [
    { cell: "Operador", props: { width: 20 } },
    { cell: "Motivo", props: { width: 30 } },
    { cell: "Solicitada", props: { width: 15 } },
    { cell: "Estado", props: { width: 10 } },
    { cell: "Aprobada por", props: { width: 15 } },
    { cell: "Vence", props: { width: 10 } },
    { cell: "" },
  ];

  const rows = sessions.map((session) => {
    const { label, color } =
      SUPPORT_SESSION_STATE_PRESENTATION[supportSessionState(session)];
    return {
      id: session.id ?? "",
      row: [
        {
          cell: session.operator ?? "-",
          props: { modifier: "truncate" as const },
        },
        {
          cell: session.reason ?? "-",
          props: { modifier: "breakWord" as const },
        },
        { cell: formatTime(session.requested_at) },
        {
          cell: (
            <Label color={color} isCompact>
              {label}
            </Label>
          ),
        },
        {
          cell: session.approved_by ?? "-",
          props: { modifier: "truncate" as const },
        },
        { cell: formatTime(session.expires_at) },
        {
          cell: (
            <ActionsColumn
              items={actionsFor(session)}
              popperProps={{ position: "right" }}
            />
          ),
          props: { isActionCell: true },
        },
      ],
    };
  });

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="No hay sesiones de soporte"
        body="Cuando el soporte de la plataforma necesite ver tu organización, su solicitud aparecerá aquí para que la apruebes."
      />
    ),
  });

  const operator = pending?.session.operator ?? "el operador";

  return (
    <>
      <DocumentTitle title="Soporte" />
      <TenantContextSelector />
      <PageSection>
        <Content component={ContentVariants.h1}>Soporte</Content>
        <Content component={ContentVariants.p}>
          El soporte de la plataforma no ve los datos de tu organización salvo
          que apruebes una sesión: dura 4 horas, es de solo lectura y queda en
          la auditoría.
        </Content>
      </PageSection>
      <PageSection>
        {isAdmin ? (
          <DataView activeState={activeState}>
            <DataViewTable
              aria-label="Tabla de sesiones de soporte"
              columns={columns}
              rows={rows}
              bodyStates={bodyStates}
            />
          </DataView>
        ) : (
          <UnauthorizedState description="Necesitas el rol Administrador o superior para gestionar las sesiones de soporte." />
        )}
      </PageSection>
      <ConfirmActionModal
        isOpen={pending?.kind === "approve"}
        title="¿Aprobar la sesión de soporte?"
        body={`${operator} podrá ver la organización durante 4 horas, sin cambiar nada. Puedes revocarla antes.`}
        confirmLabel="Aprobar"
        cancelLabel="Cancelar"
        confirmVariant="primary"
        isConfirming={approve.isPending}
        onConfirm={() =>
          pending &&
          approve.mutate(pending.session, {
            onSuccess: () => setPending(null),
          })
        }
        onCancel={() => setPending(null)}
      />
      <ConfirmActionModal
        isOpen={pending?.kind === "revoke"}
        title="¿Terminar la sesión de soporte?"
        body={`${operator} deja de ver la organización ahora.`}
        confirmLabel="Terminar sesión"
        cancelLabel="Cancelar"
        confirmVariant="danger"
        titleIconVariant="warning"
        isConfirming={revoke.isPending}
        onConfirm={() =>
          pending &&
          revoke.mutate(pending.session, {
            onSuccess: () => setPending(null),
          })
        }
        onCancel={() => setPending(null)}
      />
    </>
  );
};
