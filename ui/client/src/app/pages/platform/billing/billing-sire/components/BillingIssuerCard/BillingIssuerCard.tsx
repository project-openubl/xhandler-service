import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";

import {
  Button,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Spinner,
} from "@patternfly/react-core";

import { ApiErrorText } from "@app/components/ApiErrorText";
import { useAdminBillingIssuerQuery } from "@app/queries/xhandler-admin/billing";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { BillingIssuerModal } from "../BillingIssuerModal";

/** The company that issues the platform's subscription invoices as comprobantes (§14). */
export const BillingIssuerCard: React.FC = () => {
  const { data, isLoading, error } = useAdminBillingIssuerQuery();
  const [isOpen, setIsOpen] = useState(false);

  const body = () => {
    if (isLoading)
      return <Spinner size="md" aria-label="Cargando empresa emisora" />;
    if (error) {
      return (
        <ApiErrorText
          error={getMutationErrorMessage({
            error,
            fallback: "No se pudo leer la empresa emisora",
          })}
        />
      );
    }
    if (!data?.tenant_id || !data.company_id) {
      return "Aún no se eligió la empresa emisora: las facturas de Lago no se emiten como comprobantes.";
    }
    return (
      <DescriptionList
        isHorizontal
        aria-label="Empresa emisora de la plataforma"
      >
        <DescriptionListGroup>
          <DescriptionListTerm>Organización</DescriptionListTerm>
          <DescriptionListDescription>
            <Link
              to="/plataforma/organizaciones/$tenantId"
              params={{ tenantId: data.tenant_id }}
            >
              {data.tenant_id}
            </Link>
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Empresa</DescriptionListTerm>
          <DescriptionListDescription>
            <code>{data.company_id}</code>
          </DescriptionListDescription>
        </DescriptionListGroup>
      </DescriptionList>
    );
  };

  return (
    <Card>
      <CardHeader
        actions={{
          actions: (
            <Button variant="secondary" onClick={() => setIsOpen(true)}>
              Cambiar empresa emisora
            </Button>
          ),
          hasNoOffset: false,
        }}
      >
        <CardTitle component="h2">Empresa emisora de la plataforma</CardTitle>
      </CardHeader>
      <CardBody>{body()}</CardBody>
      {isOpen ? (
        <BillingIssuerModal issuer={data} onClose={() => setIsOpen(false)} />
      ) : null}
    </Card>
  );
};
