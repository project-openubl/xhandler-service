import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";

import {
  ActionGroup,
  Button,
  Card,
  CardBody,
  CardTitle,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Stack,
  StackItem,
  TextInput,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { useAdminOseUsageQuery } from "@app/queries/xhandler-admin/ose";
import type { DataViewTh } from "@app/utils/dataViewTypes";

const isMonth = (value: string) => /^\d{4}-(0[1-9]|1[0-2])$/.test(value.trim());

/** Validations per OSE user in a month: the OSE's own billing line (RS 242-2018 art. 7.16). */
export const OseUsageCard: React.FC = () => {
  const [monthInput, setMonthInput] = useState("");
  const [month, setMonth] = useState("");
  // Unpaged by the API (one row per OSE user).
  const { data, isLoading, error } = useAdminOseUsageQuery({ month });
  const isInvalid = monthInput.trim() !== "" && !isMonth(monthInput);

  const columns: DataViewTh[] = [
    { cell: "Usuario", props: { width: 30 } },
    { cell: "RUC del emisor", props: { width: 25 } },
    { cell: "Organización", props: { width: 25 } },
    { cell: "Validaciones", props: { width: 20 } },
  ];
  const rows = (data ?? []).map((usage) => ({
    id: usage.ose_client_id ?? "",
    row: [
      { cell: usage.username ?? "-", props: { modifier: "truncate" as const } },
      { cell: usage.issuer_ruc ?? "-" },
      {
        cell: usage.tenant_id ? (
          <Link
            to="/plataforma/organizaciones/$tenantId"
            params={{ tenantId: usage.tenant_id }}
          >
            Ver organización
          </Link>
        ) : (
          "-"
        ),
      },
      { cell: (usage.validations ?? 0).toLocaleString("es-PE") },
    ],
  }));
  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: <TableEmptyState title="No hubo validaciones en el mes" />,
  });

  return (
    <Card>
      <CardTitle component="h2">Validaciones por usuario</CardTitle>
      <CardBody>
        <Stack hasGutter>
          <StackItem>
            <Form
              onSubmit={(event) => {
                event.preventDefault();
                if (!isInvalid) setMonth(monthInput.trim());
              }}
            >
              <FormGroup label="Mes" fieldId="admin-ose-month">
                <TextInput
                  id="admin-ose-month"
                  value={monthInput}
                  placeholder="AAAA-MM"
                  onChange={(_event, value) => setMonthInput(value)}
                  validated={isInvalid ? "error" : "default"}
                />
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem variant={isInvalid ? "error" : "default"}>
                      {isInvalid
                        ? "Usa el formato AAAA-MM"
                        : "Vacío es el mes actual."}
                    </HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
              <ActionGroup>
                <Button
                  type="submit"
                  variant="secondary"
                  isDisabled={isInvalid}
                >
                  Consultar
                </Button>
              </ActionGroup>
            </Form>
          </StackItem>
          <StackItem>
            <DataView activeState={activeState}>
              <DataViewTable
                aria-label="Tabla de validaciones por usuario OSE"
                columns={columns}
                rows={rows}
                bodyStates={bodyStates}
              />
            </DataView>
          </StackItem>
        </Stack>
      </CardBody>
    </Card>
  );
};
