import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Button,
  Content,
  ContentVariants,
  Flex,
  FlexItem,
  PageSection,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { PlatformAdminGate } from "@app/components/PlatformAdminGate";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATE_FORMAT } from "@app/constants";
import { useAdminPartnerListQuery } from "@app/queries/xhandler-admin/partners";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { partnerStatusLabel } from "@app/utils/partnerPresentation";

import { CreatePartnerModal } from "./components/CreatePartnerModal";

/** The platform's partners, with their members and tenants (ADR 0012). */
export const PlatformPartnerList: React.FC = () => (
  <PlatformAdminGate>
    <PlatformPartnerListContent />
  </PlatformAdminGate>
);

const PlatformPartnerListContent: React.FC = () => {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  // Unpaged by the API (a few partners): read whole.
  const { data, isLoading, error } = useAdminPartnerListQuery();

  const columns: DataViewTh[] = [
    { cell: "Socio", props: { width: 40 } },
    { cell: "Estado", props: { width: 15 } },
    { cell: "Miembros", props: { width: 15 } },
    { cell: "Organizaciones", props: { width: 15 } },
    { cell: "Creado", props: { width: 15 } },
  ];

  const rows = (data ?? []).map((partner) => ({
    id: partner.id ?? "",
    row: [
      {
        cell: partner.id ? (
          <Link
            to="/plataforma/socios/$partnerId"
            params={{ partnerId: partner.id }}
          >
            {partner.name}
          </Link>
        ) : (
          partner.name
        ),
        props: { modifier: "truncate" as const },
      },
      { cell: partnerStatusLabel(partner.status) },
      { cell: (partner.members ?? 0).toLocaleString("es-PE") },
      { cell: (partner.tenants ?? 0).toLocaleString("es-PE") },
      {
        cell: partner.created_at
          ? dayjs(partner.created_at).format(RENDER_DATE_FORMAT)
          : "-",
      },
    ],
  }));

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: <TableEmptyState title="La plataforma aún no tiene socios" />,
  });

  return (
    <>
      <DocumentTitle title="Socios de la plataforma" />
      <PageSection>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>
              Socios de la plataforma
            </Content>
            <Content component={ContentVariants.p}>
              Los socios administran las organizaciones de sus clientes desde su
              consola.
            </Content>
          </FlexItem>
          <FlexItem>
            <Button variant="primary" onClick={() => setIsCreateOpen(true)}>
              Nuevo socio
            </Button>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewTable
            aria-label="Tabla de socios de la plataforma"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
        </DataView>
      </PageSection>
      {isCreateOpen ? (
        <CreatePartnerModal onClose={() => setIsCreateOpen(false)} />
      ) : null}
    </>
  );
};
