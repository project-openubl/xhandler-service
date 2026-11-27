import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";

import { Card, CardBody, CardTitle } from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn } from "@patternfly/react-table";

import type { PendingPseAuthorization } from "@app/client/xhandler-admin";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  useAdminApprovePseAuthorizationMutation,
  useAdminDownloadPseEvidenceMutation,
  useAdminPendingPseAuthorizationsQuery,
} from "@app/queries/xhandler-admin/pse";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

const companyName = (authorization: PendingPseAuthorization) =>
  `${authorization.ruc ?? ""} ${authorization.razon_social ?? ""}`.trim() ||
  "la empresa";

/** PSE authorizations tenants registered, waiting for a platform admin (DESIGN.md P7). */
export const PendingAuthorizationsCard: React.FC = () => {
  const { addNotification } = useNotifications();
  const { data, isLoading, error } = useAdminPendingPseAuthorizationsQuery();
  const [approveTarget, setApproveTarget] =
    useState<PendingPseAuthorization | null>(null);

  const approve = useAdminApprovePseAuthorizationMutation({
    onSuccess: (_data, authorization) =>
      addNotification({
        variant: "success",
        title: `Se aprobó la autorización PSE de ${companyName(authorization)}`,
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo aprobar la autorización",
        }),
      }),
  });
  const evidence = useAdminDownloadPseEvidenceMutation({
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo descargar la evidencia",
        }),
      }),
  });

  const columns: DataViewTh[] = [
    { cell: "Empresa", props: { width: 45 } },
    { cell: "Autorizada en SOL", props: { width: 20 } },
    { cell: "Evidencia", props: { width: 15 } },
    { cell: "Organización", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = (data ?? []).map((authorization) => ({
    id: authorization.id ?? "",
    row: [
      {
        cell: companyName(authorization),
        props: { modifier: "truncate" as const },
      },
      { cell: authorization.authorized_at ?? "-" },
      { cell: authorization.evidence ? "Sí" : "No" },
      {
        cell: authorization.tenant_id ? (
          <Link
            to="/plataforma/organizaciones/$tenantId"
            params={{ tenantId: authorization.tenant_id }}
          >
            Ver organización
          </Link>
        ) : (
          "-"
        ),
      },
      {
        cell: (
          <ActionsColumn
            items={[
              {
                title: "Descargar evidencia",
                onClick: () => evidence.mutate(authorization),
                isAriaDisabled: !authorization.evidence,
                tooltipProps: authorization.evidence
                  ? undefined
                  : { content: "La organización no subió evidencia." },
              },
              {
                title: "Aprobar",
                onClick: () => setApproveTarget(authorization),
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
      <TableEmptyState title="No hay autorizaciones PSE por revisar" />
    ),
  });

  return (
    <Card>
      <CardTitle component="h2">Autorizaciones por revisar</CardTitle>
      <CardBody>
        <DataView activeState={activeState}>
          <DataViewTable
            aria-label="Tabla de autorizaciones PSE por revisar"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
        </DataView>
      </CardBody>
      {approveTarget ? (
        <ConfirmActionModal
          isOpen
          title="¿Aprobar la autorización PSE?"
          body={`Revisa antes la evidencia. Desde ahora ${companyName(approveTarget)} podrá emitir en modo PSE, firmando con el certificado de la plataforma.`}
          confirmLabel="Aprobar"
          cancelLabel="Volver"
          confirmVariant="primary"
          titleIconVariant="warning"
          isConfirming={approve.isPending}
          onConfirm={() =>
            approve.mutate(approveTarget, {
              onSuccess: () => setApproveTarget(null),
            })
          }
          onCancel={() => setApproveTarget(null)}
        />
      ) : null}
    </Card>
  );
};
