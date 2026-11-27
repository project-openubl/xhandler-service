import type React from "react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { boolean, type InferType, object, string } from "yup";

import {
  Alert,
  Button,
  Checkbox,
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

import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  useAdminImportOseAssociationMutation,
  useAdminImportOseBlockedListMutation,
} from "@app/queries/xhandler-admin/ose";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  countOf,
  isBlockedListName,
  parseAssociationList,
  parseBlockedList,
} from "../../utils/issuerLists";

/** Which of SUNAT's lists is replaced. */
export type IssuerListKind = "association" | "blocked";

interface IListImportModalProps {
  kind: IssuerListKind;
  onClose: () => void;
}

const schema = object({
  list: string().trim().default(""),
  text: string().default(""),
  confirmEmpty: boolean().default(false),
});

type IListImportValues = InferType<typeof schema>;

const TEXTS: Record<
  IssuerListKind,
  { title: string; description: string; format: string }
> = {
  association: {
    title: "Reemplazar la lista de asociados",
    description:
      "La lista de SUNAT de contribuyentes que eligieron a la plataforma como su OSE. Reemplaza por completo a la que está guardada.",
    format:
      "Una línea por contribuyente: RUC, fecha de inicio y, si la tiene, fecha de fin (AAAA-MM-DD).",
  },
  blocked: {
    title: "Reemplazar una lista de bloqueados",
    description:
      "Una de las listas de SUNAT de emisores bloqueados (DL 1532). Reemplaza por completo a la lista con ese nombre.",
    format:
      "Una línea por emisor: RUC y, si la tiene, la fecha en que se publicó (AAAA-MM-DD).",
  },
};

/** Pastes or loads one of SUNAT's issuer lists, checks every line, then replaces the stored one. */
export const ListImportModal: React.FC<IListImportModalProps> = ({
  kind,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const texts = TEXTS[kind];
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const [lineErrors, setLineErrors] = useState<string[]>([]);
  const [filename, setFilename] = useState("");

  const onError = (error: unknown) =>
    setSubmitError(
      getMutationErrorMessage({
        error,
        fallback: "No se pudo reemplazar la lista",
      }),
    );
  const association = useAdminImportOseAssociationMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `La lista de asociados tiene ahora ${countOf({ count: data.imported, one: "contribuyente", many: "contribuyentes" })}`,
      }),
    onError,
  });
  const blocked = useAdminImportOseBlockedListMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `La lista de bloqueados ${data.list ?? ""} tiene ahora ${countOf({ count: data.imported, one: "emisor", many: "emisores" })}`,
      }),
    onError,
  });
  const isSaving = association.isPending || blocked.isPending;

  const { control, handleSubmit, watch, setError } = useForm<IListImportValues>(
    {
      resolver: yupResolver(schema),
      defaultValues: { list: "", text: "", confirmEmpty: false },
    },
  );
  const text = watch("text");
  const parsed =
    kind === "association"
      ? parseAssociationList(text)
      : parseBlockedList(text);
  const isEmpty = parsed.entries.length === 0 && parsed.errors.length === 0;

  const onSubmit = (values: IListImportValues) => {
    clearSubmitError();
    if (kind === "blocked") {
      const { entries, errors } = parseBlockedList(values.text);
      setLineErrors(errors);
      if (errors.length > 0) return;
      if (!isBlockedListName(values.list)) {
        setError("list", {
          message: "De 2 a 40 minúsculas, dígitos, guion o guion bajo",
        });
        return;
      }
      blocked.mutate({ list: values.list, entries }, { onSuccess: onClose });
      return;
    }
    const { entries, errors } = parseAssociationList(values.text);
    setLineErrors(errors);
    if (errors.length > 0) return;
    if (entries.length === 0 && !values.confirmEmpty) {
      setError("confirmEmpty", {
        message: "Confirma que quieres vaciar la lista",
      });
      return;
    }
    association.mutate(entries, { onSuccess: onClose });
  };

  return (
    <Modal isOpen variant="medium" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title={texts.title}
        labelId={titleId}
        description={texts.description}
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
          {kind === "blocked" ? (
            <ControlledTextField
              control={control}
              name="list"
              label="Nombre de la lista"
              isRequired
              helperText="Por ejemplo no_habidos; minúsculas, dígitos, guion o guion bajo."
            />
          ) : null}
          <Controller
            control={control}
            name="text"
            render={({ field }) => (
              <FormGroup label="Contenido" fieldId={`${formId}-text`}>
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
                    <HelperTextItem>
                      {texts.format} Se leen{" "}
                      {parsed.entries.length.toLocaleString("es-PE")}{" "}
                      {kind === "association" ? "contribuyentes" : "emisores"}.
                    </HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
            )}
          />
          {kind === "association" && isEmpty ? (
            <Controller
              control={control}
              name="confirmEmpty"
              render={({ field, fieldState }) => (
                <FormGroup fieldId={`${formId}-confirm-empty`}>
                  <Checkbox
                    id={`${formId}-confirm-empty`}
                    label="Vaciar la lista: el OSE rechazará a todos los emisores"
                    isChecked={field.value}
                    onChange={(_event, checked) => field.onChange(checked)}
                  />
                  {fieldState.error ? (
                    <FormHelperText>
                      <HelperText>
                        <HelperTextItem variant="error">
                          {fieldState.error.message}
                        </HelperTextItem>
                      </HelperText>
                    </FormHelperText>
                  ) : null}
                </FormGroup>
              )}
            />
          ) : null}
          <Alert
            isInline
            isPlain
            variant="warning"
            title="La lista guardada se reemplaza por completo con lo que envíes."
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={isSaving}
          isDisabled={isSaving}
        >
          Reemplazar lista
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
