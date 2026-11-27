import type React from "react";
import { useState } from "react";

import {
  ActionGroup,
  Button,
  Card,
  CardBody,
  CardTitle,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Label,
  Spinner,
  Stack,
  StackItem,
  TextInput,
} from "@patternfly/react-core";

import { ApiErrorText } from "@app/components/ApiErrorText";
import { useAdminPseRejectedRateQuery } from "@app/queries/xhandler-admin/pse";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { formatRate, isHalfYearPeriod } from "./rejectedRate";

/** The half-year's rejected-CDR rate against SUNAT's 10 % / 5 % limit (DESIGN.md P7). */
export const RejectedRateCard: React.FC = () => {
  const [periodInput, setPeriodInput] = useState("");
  const [period, setPeriod] = useState("");
  const { data, isLoading, error } = useAdminPseRejectedRateQuery({ period });
  const isInvalid = periodInput.trim() !== "" && !isHalfYearPeriod(periodInput);

  return (
    <Card>
      <CardTitle component="h2">Tasa de CDR rechazados</CardTitle>
      <CardBody>
        <Stack hasGutter>
          <StackItem>
            <Form
              onSubmit={(event) => {
                event.preventDefault();
                if (!isInvalid) setPeriod(periodInput.trim());
              }}
            >
              <FormGroup label="Semestre" fieldId="admin-pse-period">
                <TextInput
                  id="admin-pse-period"
                  value={periodInput}
                  placeholder="AAAA-H1 o AAAA-H2"
                  onChange={(_event, value) => setPeriodInput(value)}
                  validated={isInvalid ? "error" : "default"}
                />
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem variant={isInvalid ? "error" : "default"}>
                      {isInvalid
                        ? "Usa el formato AAAA-H1 o AAAA-H2"
                        : "Vacío es el semestre actual (hora de Lima)."}
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
            {isLoading ? (
              <Spinner size="md" aria-label="Cargando tasa de rechazos" />
            ) : error ? (
              <ApiErrorText
                error={getMutationErrorMessage({
                  error,
                  fallback: "No se pudo leer la tasa de rechazos",
                })}
              />
            ) : data ? (
              <DescriptionList isHorizontal aria-label="Tasa de CDR rechazados">
                <DescriptionListGroup>
                  <DescriptionListTerm>Semestre</DescriptionListTerm>
                  <DescriptionListDescription>
                    {data.period}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Aceptados</DescriptionListTerm>
                  <DescriptionListDescription>
                    {(data.accepted ?? 0).toLocaleString("es-PE")}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Rechazados</DescriptionListTerm>
                  <DescriptionListDescription>
                    {(data.rejected ?? 0).toLocaleString("es-PE")}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Tasa</DescriptionListTerm>
                  <DescriptionListDescription>
                    {formatRate(data.rate)} (límite {formatRate(data.limit)}){" "}
                    {data.breached ? (
                      <Label isCompact color="red">
                        Supera el límite
                      </Label>
                    ) : (
                      <Label isCompact color="green">
                        Dentro del límite
                      </Label>
                    )}
                  </DescriptionListDescription>
                </DescriptionListGroup>
              </DescriptionList>
            ) : null}
          </StackItem>
        </Stack>
      </CardBody>
    </Card>
  );
};
