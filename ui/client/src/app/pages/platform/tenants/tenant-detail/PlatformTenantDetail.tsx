import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  ActionList,
  ActionListGroup,
  ActionListItem,
  Button,
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Flex,
  FlexItem,
  Label,
  LabelGroup,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { PlatformAdminGate } from "@app/components/PlatformAdminGate";
import { RENDER_DATE_FORMAT, RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  useAdminCancelTenantExitMutation,
  useAdminSetArchivePlanMutation,
  useAdminSetAuditHoldMutation,
  useAdminTenantCompaniesQuery,
  useAdminTenantSendingMutation,
  useSuspenseAdminTenantDetailQuery,
} from "@app/queries/xhandler-admin/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";
import { tenantStatusLabel } from "@app/utils/tenantPresentation";

import { BulkRetryModal } from "./components/BulkRetryModal";
import { ChangePlanModal } from "./components/ChangePlanModal";
import { RegisterDataSubjectRequestModal } from "./components/RegisterDataSubjectRequestModal";
import { SuppressEmailModal } from "./components/SuppressEmailModal";
import { TenantCompaniesPanel } from "./components/TenantCompaniesPanel";
import {
  cancelExitUnavailable,
  PLAN_SOURCE_LABELS,
  type TenantAction,
  tenantActionConfirmation,
} from "./tenantActions";

interface IPlatformTenantDetailProps {
  tenantId: string;
}

type OpenModal = "plan" | "retry" | "suppress" | "data-subject" | null;

const formatDate = (value: string | undefined, format: string) =>
  value ? dayjs(value).format(format) : "-";

/** One tenant as the platform sees it, with the platform's switches (ADR 0012). */
export const PlatformTenantDetail: React.FC<IPlatformTenantDetailProps> = ({
  tenantId,
}) => (
  <PlatformAdminGate>
    <PlatformTenantDetailContent tenantId={tenantId} />
  </PlatformAdminGate>
);

const PlatformTenantDetailContent: React.FC<IPlatformTenantDetailProps> = ({
  tenantId,
}) => {
  const { addNotification } = useNotifications();
  const { data: tenant } = useSuspenseAdminTenantDetailQuery({ tenantId });
  const tenantName = tenant.name ?? "la organización";
  const [openModal, setOpenModal] = useState<OpenModal>(null);
  const [confirmAction, setConfirmAction] = useState<TenantAction | null>(null);
  const [isCompaniesShown, setIsCompaniesShown] = useState(false);
  const companies = useAdminTenantCompaniesQuery({
    tenantId,
    options: { enabled: isCompaniesShown },
  });

  const errorToast = (fallback: string) => (error: unknown) =>
    addNotification({
      variant: "danger",
      ...getMutationErrorMessage({ error, fallback }),
    });
  const closeConfirm = { onSuccess: () => setConfirmAction(null) };

  const sending = useAdminTenantSendingMutation(tenantId, {
    onSuccess: (_data, hold) =>
      addNotification({
        variant: "success",
        title: hold
          ? `Se pausaron los envíos de ${tenantName}`
          : `Se reanudaron los envíos de ${tenantName}`,
      }),
    onError: errorToast("No se pudieron cambiar los envíos de la organización"),
  });
  const auditHold = useAdminSetAuditHoldMutation(tenantId, {
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: data.audit_hold
          ? `Los datos de ${tenantName} quedan retenidos por auditoría`
          : `Se quitó la retención por auditoría de ${tenantName}`,
      }),
    onError: errorToast("No se pudo cambiar la retención por auditoría"),
  });
  const archivePlan = useAdminSetArchivePlanMutation(tenantId, {
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: data.archive_plan
          ? `${tenantName} tiene ahora el plan de archivo`
          : `Se quitó el plan de archivo de ${tenantName}`,
      }),
    onError: errorToast("No se pudo cambiar el plan de archivo"),
  });
  const cancelExit = useAdminCancelTenantExitMutation(tenantId, {
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: `Se canceló la salida de ${tenantName}`,
      }),
    onError: errorToast("No se pudo cancelar la salida"),
  });

  const runConfirmed = (action: TenantAction) => {
    switch (action) {
      case "hold":
      case "release":
        sending.mutate(action === "hold", closeConfirm);
        break;
      case "audit-hold-on":
      case "audit-hold-off":
        auditHold.mutate(action === "audit-hold-on", closeConfirm);
        break;
      case "archive-plan-on":
      case "archive-plan-off":
        archivePlan.mutate(action === "archive-plan-on", closeConfirm);
        break;
      case "cancel-exit":
        cancelExit.mutate(undefined, closeConfirm);
        break;
    }
  };

  const exitBlocked = cancelExitUnavailable(tenant);
  const confirmation = confirmAction
    ? tenantActionConfirmation({ action: confirmAction, tenantName })
    : null;

  const fields: { label: string; value: React.ReactNode }[] = [
    { label: "Identificador", value: <code>{tenant.id}</code> },
    {
      label: "Plan",
      value: `${tenant.plan ?? "-"} (${PLAN_SOURCE_LABELS[tenant.plan_source ?? ""] ?? tenant.plan_source ?? "-"})`,
    },
    { label: "Estado", value: tenantStatusLabel(tenant.status) },
    {
      label: "Socio",
      value:
        tenant.partner_id && tenant.partner_name ? (
          <Link
            to="/plataforma/socios/$partnerId"
            params={{ partnerId: tenant.partner_id }}
          >
            {tenant.partner_name}
          </Link>
        ) : (
          "Ninguno"
        ),
    },
    {
      label: "Empresas",
      value: (tenant.companies ?? 0).toLocaleString("es-PE"),
    },
    {
      label: "Creada",
      value: formatDate(tenant.created_at, RENDER_DATETIME_FORMAT),
    },
    { label: "Creada por", value: tenant.created_by ?? "-" },
    {
      label: "Envíos a SUNAT",
      value: tenant.sending_paused ? "Pausados" : "Activos",
    },
  ];
  const dataProtectionFields: { label: string; value: React.ReactNode }[] = [
    {
      label: "Retención por auditoría",
      value: tenant.audit_hold ? "Sí" : "No",
    },
    { label: "Plan de archivo", value: tenant.archive_plan ? "Sí" : "No" },
    {
      label: "Se eliminan sus datos",
      value: tenant.delete_after
        ? formatDate(tenant.delete_after, RENDER_DATE_FORMAT)
        : "Sin fecha",
    },
  ];

  return (
    <>
      <DocumentTitle title={tenant.name} />
      <PageSection>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsFlexStart" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>{tenant.name}</Content>
            <LabelGroup>
              <Label isCompact>{tenantStatusLabel(tenant.status)}</Label>
              {tenant.sending_paused ? (
                <Label isCompact color="orange">
                  Envíos pausados
                </Label>
              ) : null}
              {tenant.audit_hold ? (
                <Label isCompact color="purple">
                  Retenida por auditoría
                </Label>
              ) : null}
            </LabelGroup>
          </FlexItem>
          <FlexItem>
            <ActionList>
              <ActionListGroup>
                <ActionListItem>
                  <Button
                    variant="secondary"
                    onClick={() => setOpenModal("plan")}
                  >
                    Cambiar plan
                  </Button>
                </ActionListItem>
                <ActionListItem>
                  <Button
                    variant="secondary"
                    onClick={() =>
                      setConfirmAction(
                        tenant.sending_paused ? "release" : "hold",
                      )
                    }
                  >
                    {tenant.sending_paused
                      ? "Reanudar envíos"
                      : "Pausar envíos"}
                  </Button>
                </ActionListItem>
                <ActionListItem>
                  <Button
                    variant="secondary"
                    onClick={() => setOpenModal("retry")}
                  >
                    Reintentar envíos
                  </Button>
                </ActionListItem>
                <ActionListItem>
                  <Button
                    variant="secondary"
                    onClick={() => setOpenModal("suppress")}
                  >
                    Suprimir correo
                  </Button>
                </ActionListItem>
              </ActionListGroup>
            </ActionList>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          <StackItem>
            <Card>
              <CardTitle component="h2">Datos</CardTitle>
              <CardBody>
                <DescriptionList
                  isHorizontal
                  columnModifier={{ default: "2Col" }}
                  aria-label="Datos de la organización"
                >
                  {fields.map(({ label, value }) => (
                    <DescriptionListGroup key={label}>
                      <DescriptionListTerm>{label}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {value}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  ))}
                </DescriptionList>
              </CardBody>
            </Card>
          </StackItem>
          <StackItem>
            <Card>
              <CardTitle component="h2">Protección de datos</CardTitle>
              <CardBody>
                <Stack hasGutter>
                  <StackItem>
                    <DescriptionList
                      isHorizontal
                      aria-label="Protección de datos de la organización"
                    >
                      {dataProtectionFields.map(({ label, value }) => (
                        <DescriptionListGroup key={label}>
                          <DescriptionListTerm>{label}</DescriptionListTerm>
                          <DescriptionListDescription>
                            {value}
                          </DescriptionListDescription>
                        </DescriptionListGroup>
                      ))}
                    </DescriptionList>
                  </StackItem>
                  <StackItem>
                    <ActionList>
                      <ActionListGroup>
                        <ActionListItem>
                          <Button
                            variant="secondary"
                            onClick={() =>
                              setConfirmAction(
                                tenant.audit_hold
                                  ? "audit-hold-off"
                                  : "audit-hold-on",
                              )
                            }
                          >
                            {tenant.audit_hold
                              ? "Quitar retención por auditoría"
                              : "Retener por auditoría"}
                          </Button>
                        </ActionListItem>
                        <ActionListItem>
                          <Button
                            variant="secondary"
                            onClick={() =>
                              setConfirmAction(
                                tenant.archive_plan
                                  ? "archive-plan-off"
                                  : "archive-plan-on",
                              )
                            }
                          >
                            {tenant.archive_plan
                              ? "Quitar plan de archivo"
                              : "Activar plan de archivo"}
                          </Button>
                        </ActionListItem>
                        <ActionListItem>
                          <GatedButton
                            variant="secondary"
                            onClick={() => setConfirmAction("cancel-exit")}
                            isAriaDisabled={!!exitBlocked}
                            tooltipProps={
                              exitBlocked ? { content: exitBlocked } : undefined
                            }
                          >
                            Cancelar salida
                          </GatedButton>
                        </ActionListItem>
                        <ActionListItem>
                          <Button
                            variant="secondary"
                            onClick={() => setOpenModal("data-subject")}
                          >
                            Registrar solicitud de datos personales
                          </Button>
                        </ActionListItem>
                      </ActionListGroup>
                    </ActionList>
                  </StackItem>
                </Stack>
              </CardBody>
            </Card>
          </StackItem>
          <StackItem>
            <TenantCompaniesPanel
              tenantId={tenantId}
              tenantName={tenantName}
              isShown={isCompaniesShown}
              onShow={() => setIsCompaniesShown(true)}
              companies={companies.data}
              isLoading={companies.isLoading}
              error={companies.error}
            />
          </StackItem>
        </Stack>
      </PageSection>
      {openModal === "plan" ? (
        <ChangePlanModal tenant={tenant} onClose={() => setOpenModal(null)} />
      ) : null}
      {openModal === "retry" ? (
        <BulkRetryModal
          tenant={tenant}
          companies={companies.data}
          onClose={() => setOpenModal(null)}
        />
      ) : null}
      {openModal === "suppress" ? (
        <SuppressEmailModal
          tenant={tenant}
          onClose={() => setOpenModal(null)}
        />
      ) : null}
      {openModal === "data-subject" ? (
        <RegisterDataSubjectRequestModal
          tenant={tenant}
          onClose={() => setOpenModal(null)}
        />
      ) : null}
      {confirmAction && confirmation ? (
        <ConfirmActionModal
          isOpen
          title={confirmation.title}
          body={confirmation.body}
          confirmLabel={confirmation.confirmLabel}
          cancelLabel="Volver"
          confirmVariant="primary"
          titleIconVariant="warning"
          isConfirming={
            sending.isPending ||
            auditHold.isPending ||
            archivePlan.isPending ||
            cancelExit.isPending
          }
          onConfirm={() => runConfirmed(confirmAction)}
          onCancel={() => setConfirmAction(null)}
        />
      ) : null}
    </>
  );
};
