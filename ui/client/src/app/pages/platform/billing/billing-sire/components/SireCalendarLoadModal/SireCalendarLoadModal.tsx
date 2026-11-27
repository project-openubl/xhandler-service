import type React from "react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { type InferType, object, string } from "yup";

import {
  Alert,
  Button,
  FileUpload,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  List,
  ListItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAdminLoadSireCalendarMutation } from "@app/queries/xhandler-admin/billing";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { parseSireCalendar } from "../../utils/sireCalendar";

interface ISireCalendarLoadModalProps {
  onClose: () => void;
}

const schema = object({
  text: string().default(""),
});

type ISireCalendarLoadValues = InferType<typeof schema>;

/** Loads SUNAT's schedule: dates of the same period and digit are replaced, others kept. */
export const SireCalendarLoadModal: React.FC<ISireCalendarLoadModalProps> = ({
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const [filename, setFilename] = useState("");
  const [lineErrors, setLineErrors] = useState<string[]>([]);
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const load = useAdminLoadSireCalendarMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `Se cargaron ${(data.loaded ?? 0).toLocaleString("es-PE")} fechas del cronograma`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cargar el cronograma",
        }),
      ),
  });

  const { control, handleSubmit, watch, setError } =
    useForm<ISireCalendarLoadValues>({
      resolver: yupResolver(schema),
      defaultValues: { text: "" },
    });
  const parsedCount = parseSireCalendar(watch("text")).entries.length;

  const onSubmit = (values: ISireCalendarLoadValues) => {
    clearSubmitError();
    const { entries, errors } = parseSireCalendar(values.text);
    setLineErrors(errors);
    if (errors.length > 0) return;
    if (entries.length === 0) {
      setError("text", { message: "Pega al menos una fecha del cronograma" });
      return;
    }
    load.mutate(entries, { onSuccess: onClose });
  };

  return (
    <Modal isOpen variant="medium" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Cargar cronograma"
        labelId={titleId}
        description="Las fechas de vencimiento de SUNAT por periodo y último dígito del RUC. Una fecha del mismo periodo y dígito reemplaza a la guardada."
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          {lineErrors.length > 0 ? (
            <Alert
              isInline
              variant="danger"
              title={`Corrige ${lineErrors.length === 1 ? "la línea" : `las ${lineErrors.length} líneas`} con errores`}
            >
              <List>
                {lineErrors.slice(0, 10).map((message) => (
                  <ListItem key={message}>{message}</ListItem>
                ))}
              </List>
            </Alert>
          ) : null}
          <Controller
            control={control}
            name="text"
            render={({ field, fieldState }) => (
              <FormGroup label="Fechas" fieldId={`${formId}-text`}>
                <FileUpload
                  id={`${formId}-text`}
                  type="text"
                  value={field.value}
                  filename={filename}
                  filenamePlaceholder="Arrastra un archivo .csv o .txt, o pega el contenido"
                  browseButtonText="Elegir"
                  clearButtonText="Quitar"
                  allowEditingUploadedText
                  accept=".csv,.txt,text/csv,text/plain"
                  onFileInputChange={(_event, file) => setFilename(file.name)}
                  onDataChange={(_event, data) => field.onChange(data)}
                  onTextChange={(_event, value) => field.onChange(value)}
                  onClearClick={() => {
                    setFilename("");
                    field.onChange("");
                  }}
                />
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem
                      variant={fieldState.error ? "error" : "default"}
                    >
                      {fieldState.error?.message ??
                        `Una línea por fecha: periodo (AAAAMM), último dígito del RUC (0-9) y vencimiento (AAAA-MM-DD). Se leen ${parsedCount.toLocaleString("es-PE")} fechas.`}
                    </HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
            )}
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={load.isPending}
          isDisabled={load.isPending}
        >
          Cargar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
