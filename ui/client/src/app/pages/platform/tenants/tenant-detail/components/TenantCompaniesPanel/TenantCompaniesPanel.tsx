import type React from "react";
import { useState } from "react";

import {
  Alert,
  Button,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  Label,
  LabelGroup,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { AdminCompany } from "@app/client/xhandler-admin";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { useNotifications } from "@app/hooks/useNotifications";
import type { XhandlerAxiosError } from "@app/queries/utils/openapiErrorTypes";
import { useAdminCompanySendingMutation } from "@app/queries/xhandler-admin/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import {
  type CompanyAction,
  companyActionConfirmation,
  EMISSION_MODE_LABELS,
} from "./companyActions";

interface ITenantCompaniesPanelProps {
  tenantId: string;
  tenantName: string;
  /** Whether the user asked to list them: each listing is audited in the tenant's log. */
  isShown: boolean;
  onShow: () => void;
  companies: AdminCompany[] | undefined;
  isLoading: boolean;
  error: XhandlerAxiosError | null;
}

interface ICompanyActionTarget {
  company: AdminCompany;
  action: CompanyAction;
}

/** The tenant's companies, listed on demand, with the platform's per-company switches. */
export const TenantCompaniesPanel: React.FC<ITenantCompaniesPanelProps> = ({
  tenantId,
  tenantName,
  isShown,
  onShow,
  companies,
  isLoading,
  error,
}) => {
  const { addNotification } = useNotifications();
  const [target, setTarget] = useState<ICompanyActionTarget | null>(null);
  const nameOf = (companyId: string) => {
    const company = companies?.find((c) => c.id === companyId);
    return company
      ? `${company.ruc ?? ""} ${company.razon_social ?? ""}`.trim()
      : "la empresa";
  };

  const sending = useAdminCompanySendingMutation(tenantId, {
    onSuccess: (_data, { companyId, hold }) =>
      addNotification({
        variant: "success",
        title: hold
          ? `Se pausaron los envíos de ${nameOf(companyId)}`
          : `Se reanudaron los envíos de ${nameOf(companyId)}`,
      }),
    onError: (err, { hold }) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: hold
            ? "No se pudieron pausar los envíos"
            : "No se pudieron reanudar los envíos",
        }),
      }),
  });
  const run = ({ company, action }: ICompanyActionTarget) => {
    const companyId = company.id ?? "";
    const close = { onSuccess: () => setTarget(null) };
    sending.mutate({ companyId, hold: action === "hold" }, close);
  };

  const actionsFor = (company: AdminCompany): IAction[] => [
    company.sending_paused
      ? {
          title: "Reanudar envíos",
          onClick: () => setTarget({ company, action: "release" }),
        }
      : {
          title: "Pausar envíos",
          onClick: () => setTarget({ company, action: "hold" }),
        },
  ];

  const columns: DataViewTh[] = [
    { cell: "RUC", props: { width: 15 } },
    { cell: "Razón social", props: { width: 30 } },
    { cell: "Ambiente", props: { width: 10 } },
    { cell: "Modo", props: { width: 10 } },
    { cell: "Estado", props: { width: 35 } },
    { cell: "" },
  ];

  const rows = (companies ?? []).map((company) => ({
    id: company.id ?? "",
    row: [
      { cell: company.ruc ?? "-" },
      {
        cell: company.razon_social ?? "-",
        props: { modifier: "truncate" as const },
      },
      { cell: <SunatEnvLabel sunatEnv={company.sunat_env} /> },
      {
        cell:
          EMISSION_MODE_LABELS[company.emission_mode ?? ""] ??
          company.emission_mode ??
          "-",
      },
      {
        cell: (
          <LabelGroup>
            <Label isCompact color={company.ruc_verified_at ? "green" : "grey"}>
              {company.ruc_verified_at ? "RUC verificado" : "RUC sin verificar"}
            </Label>
            {company.sending_paused ? (
              <Label isCompact color="orange">
                Envíos pausados
              </Label>
            ) : null}
          </LabelGroup>
        ),
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(company)}
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
    emptyState: <TableEmptyState title="La organización no tiene empresas" />,
  });

  const confirmation = target
    ? companyActionConfirmation({
        action: target.action,
        companyName: nameOf(target.company.id ?? ""),
        tenantName,
      })
    : null;

  return (
    <Card>
      <CardHeader
        actions={
          isShown
            ? undefined
            : {
                actions: (
                  <Button variant="secondary" onClick={onShow}>
                    Ver empresas
                  </Button>
                ),
                hasNoOffset: false,
              }
        }
      >
        <CardTitle component="h2">Empresas</CardTitle>
      </CardHeader>
      <CardBody>
        <Alert
          isInline
          isPlain
          variant="info"
          title="Cada consulta de las empresas queda registrada en la auditoría de la plataforma."
        />
        {isShown ? (
          <DataView activeState={activeState}>
            <DataViewTable
              aria-label="Tabla de empresas de la organización"
              columns={columns}
              rows={rows}
              bodyStates={bodyStates}
            />
          </DataView>
        ) : null}
      </CardBody>
      {target && confirmation ? (
        <ConfirmActionModal
          isOpen
          title={confirmation.title}
          body={confirmation.body}
          confirmLabel={confirmation.confirmLabel}
          cancelLabel="Volver"
          confirmVariant="primary"
          titleIconVariant="warning"
          isConfirming={sending.isPending}
          onConfirm={() => run(target)}
          onCancel={() => setTarget(null)}
        />
      ) : null}
    </Card>
  );
};
