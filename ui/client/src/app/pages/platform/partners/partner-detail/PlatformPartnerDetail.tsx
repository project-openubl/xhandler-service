import type React from "react";
import { useState } from "react";

import { useSuspenseQuery } from "@tanstack/react-query";
import dayjs from "dayjs";

import {
  Button,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn } from "@patternfly/react-table";

import type { PartnerMember } from "@app/client/xhandler-admin";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { PlatformAdminGate } from "@app/components/PlatformAdminGate";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATE_FORMAT, RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  isPartnerRole,
  PARTNER_ROLE_LABELS,
} from "@app/queries/xhandler/partners";
import {
  adminPartnerListQueryOptions,
  isPartnerMemberKind,
  useAdminPartnerMembersQuery,
  useAdminRemovePartnerMemberMutation,
} from "@app/queries/xhandler-admin/partners";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import {
  PARTNER_MEMBER_KIND_LABELS,
  partnerStatusLabel,
} from "@app/utils/partnerPresentation";

import { PartnerMemberModal } from "./components/PartnerMemberModal";

interface IPlatformPartnerDetailProps {
  partnerId: string;
}

/** One partner and its members (ADR 0012); its tenants are in the platform's organizations. */
export const PlatformPartnerDetail: React.FC<IPlatformPartnerDetailProps> = ({
  partnerId,
}) => (
  <PlatformAdminGate>
    <PlatformPartnerDetailContent partnerId={partnerId} />
  </PlatformAdminGate>
);

const PlatformPartnerDetailContent: React.FC<IPlatformPartnerDetailProps> = ({
  partnerId,
}) => {
  const { addNotification } = useNotifications();
  // The admin API has no partner by id: the loader checked it is in the (unpaged) list.
  const { data: partners } = useSuspenseQuery(adminPartnerListQueryOptions());
  const partner = partners.find((p) => p.id === partnerId);
  const partnerName = partner?.name ?? "el socio";
  const members = useAdminPartnerMembersQuery({ partnerId });
  const [memberModal, setMemberModal] = useState<{
    member?: PartnerMember;
  } | null>(null);
  const [removeTarget, setRemoveTarget] = useState<PartnerMember | null>(null);

  const remove = useAdminRemovePartnerMemberMutation(partnerId, {
    onSuccess: (_data, member) =>
      addNotification({
        variant: "success",
        title: `${member.subject ?? "El miembro"} ya no es miembro de ${partnerName}`,
      }),
    onError: (error) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error,
          fallback: "No se pudo quitar al miembro",
        }),
      }),
  });

  const columns: DataViewTh[] = [
    { cell: "Identificador", props: { width: 40 } },
    { cell: "Tipo", props: { width: 20 } },
    { cell: "Rol", props: { width: 20 } },
    { cell: "Desde", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = (members.data ?? []).map((member) => ({
    id: `${member.kind ?? ""}:${member.subject ?? ""}`,
    row: [
      {
        cell: member.subject ?? "-",
        props: { modifier: "truncate" as const },
      },
      {
        cell:
          member.kind && isPartnerMemberKind(member.kind)
            ? PARTNER_MEMBER_KIND_LABELS[member.kind]
            : (member.kind ?? "-"),
      },
      {
        cell:
          member.role && isPartnerRole(member.role)
            ? PARTNER_ROLE_LABELS[member.role]
            : (member.role ?? "-"),
      },
      {
        cell: member.created_at
          ? dayjs(member.created_at).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
      {
        cell: (
          <ActionsColumn
            items={[
              {
                title: "Cambiar rol",
                onClick: () => setMemberModal({ member }),
              },
              { title: "Quitar", onClick: () => setRemoveTarget(member) },
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
    isLoading: members.isLoading,
    error: members.error,
    isEmpty: rows.length === 0,
    emptyState: <TableEmptyState title="El socio aún no tiene miembros" />,
  });

  const fields: { label: string; value: React.ReactNode }[] = [
    { label: "Identificador", value: <code>{partnerId}</code> },
    { label: "Estado", value: partnerStatusLabel(partner?.status) },
    {
      label: "Organizaciones",
      value: (partner?.tenants ?? 0).toLocaleString("es-PE"),
    },
    {
      label: "Creado",
      value: partner?.created_at
        ? dayjs(partner.created_at).format(RENDER_DATE_FORMAT)
        : "-",
    },
  ];

  return (
    <>
      <DocumentTitle title={partner?.name} />
      <PageSection>
        <Content component={ContentVariants.h1}>{partner?.name}</Content>
        <Content component={ContentVariants.p}>
          Socio de la plataforma. Sus administradores crean y administran las
          organizaciones de sus clientes desde la consola de socios.
        </Content>
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
                  aria-label="Datos del socio"
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
              <CardHeader
                actions={{
                  actions: (
                    <Button
                      variant="secondary"
                      onClick={() => setMemberModal({})}
                    >
                      Agregar miembro
                    </Button>
                  ),
                  hasNoOffset: false,
                }}
              >
                <CardTitle component="h2">Miembros</CardTitle>
              </CardHeader>
              <CardBody>
                <DataView activeState={activeState}>
                  <DataViewTable
                    aria-label="Tabla de miembros del socio"
                    columns={columns}
                    rows={rows}
                    bodyStates={bodyStates}
                  />
                </DataView>
              </CardBody>
            </Card>
          </StackItem>
        </Stack>
      </PageSection>
      {memberModal ? (
        <PartnerMemberModal
          partnerId={partnerId}
          partnerName={partnerName}
          member={memberModal.member}
          onClose={() => setMemberModal(null)}
        />
      ) : null}
      <ConfirmActionModal
        isOpen={!!removeTarget}
        title="¿Quitar al miembro?"
        body={`${removeTarget?.subject ?? "El miembro"} dejará de administrar las organizaciones de ${partnerName}.`}
        confirmLabel="Quitar"
        cancelLabel="Volver"
        confirmVariant="primary"
        titleIconVariant="warning"
        isConfirming={remove.isPending}
        onConfirm={() => {
          if (removeTarget) {
            remove.mutate(removeTarget, {
              onSuccess: () => setRemoveTarget(null),
            });
          }
        }}
        onCancel={() => setRemoveTarget(null)}
      />
    </>
  );
};
