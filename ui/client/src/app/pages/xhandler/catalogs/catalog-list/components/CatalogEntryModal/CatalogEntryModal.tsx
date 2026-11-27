import type React from "react";
import { useId } from "react";
import { type Control, Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  FormGroup,
  FormHelperText,
  FormSelect,
  FormSelectOption,
  HelperText,
  HelperTextItem,
  InputGroup,
  InputGroupItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  TextInput,
} from "@patternfly/react-core";

import type { Entry } from "@app/client/xhandler";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useCatalogEntrySaveMutation } from "@app/queries/xhandler/catalogs";
import {
  lookupTipo,
  useIdentityLookupMutation,
} from "@app/queries/xhandler/identity";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  type ICatalogField,
  type ICatalogKind,
  TIPOS_DOCUMENTO_IDENTIDAD,
} from "../../catalogKinds";
import {
  catalogEntrySchema,
  type CatalogEntryValues,
  toEntryData,
  toFormValues,
} from "./catalogEntryForm";

interface ICatalogEntryModalProps {
  companyId: string;
  kind: ICatalogKind;
  /** The entry to edit; none creates one. */
  entry?: Entry;
  onClose: () => void;
}

const article = (kind: ICatalogKind, isNew: boolean) =>
  isNew ? (kind.isFeminine ? "Nueva" : "Nuevo") : "Editar";

/** Creates or edits one catalog entry; a customer's name can come from the RUC/DNI lookup. */
export const CatalogEntryModal: React.FC<ICatalogEntryModalProps> = ({
  companyId,
  kind,
  entry,
  onClose,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const isNew = !entry;

  const { control, handleSubmit, getValues, setValue, watch } =
    useForm<CatalogEntryValues>({
      resolver: yupResolver(catalogEntrySchema(kind)),
      defaultValues: toFormValues(kind, entry?.data),
    });

  const save = useCatalogEntrySaveMutation(companyId, {
    onSuccess: () => {
      addNotification({
        variant: "success",
        title: isNew
          ? `Se agregó ${kind.isFeminine ? "la" : "el"} ${kind.singular}`
          : `Se guardaron los cambios ${kind.isFeminine ? "de la" : "del"} ${kind.singular}`,
      });
      onClose();
    },
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: `No se pudo guardar ${kind.isFeminine ? "la" : "el"} ${kind.singular}`,
        }),
      ),
  });

  const lookup = useIdentityLookupMutation(companyId, {
    onSuccess: (identity) => {
      if (identity.nombre) {
        setValue("nombre", identity.nombre, { shouldValidate: true });
      }
      if (
        identity.direccion &&
        kind.fields.some((field) => field.name === "direccion") &&
        !getValues("direccion")?.trim()
      ) {
        setValue("direccion", identity.direccion);
      }
    },
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo consultar el documento",
        }),
      ),
  });

  const tipoLookup =
    kind.kind === "customer"
      ? lookupTipo(watch("tipo_documento_identidad"))
      : undefined;

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    save.mutate({
      kind: kind.kind,
      id: entry?.id,
      data: toEntryData(kind, values, entry?.data),
    });
  });

  const onLookup = () => {
    const numero = getValues("numero_documento_identidad")?.trim();
    if (!tipoLookup || !numero) return;
    clearSubmitError();
    lookup.mutate({ tipo: tipoLookup, numero });
  };

  return (
    <Modal
      isOpen
      onClose={onClose}
      variant="small"
      aria-labelledby="catalog-entry-title"
    >
      <ModalHeader
        title={`${article(kind, isNew)} ${kind.singular}`}
        labelId="catalog-entry-title"
      />
      <ModalBody>
        <Form id={formId} onSubmit={(event) => void onSubmit(event)}>
          <FormSubmitError error={submitError} />
          {kind.fields.map((field) =>
            field.type === "tipoDocumento" ? (
              <TipoDocumentoField
                key={field.name}
                control={control}
                field={field}
                id={`${formId}-${field.name}`}
              />
            ) : field.name === "numero_documento_identidad" &&
              kind.kind === "customer" ? (
              <NumeroDocumentoField
                key={field.name}
                control={control}
                field={field}
                id={`${formId}-${field.name}`}
                canLookUp={!!tipoLookup}
                isLookingUp={lookup.isPending}
                onLookup={onLookup}
              />
            ) : (
              <ControlledTextField
                key={field.name}
                control={control}
                name={field.name}
                label={field.label}
                isRequired={field.isRequired}
                helperText={field.helperText}
                type={field.type === "email" ? "email" : "text"}
                inputMode={field.type === "number" ? "decimal" : undefined}
              />
            ),
          )}
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={save.isPending}
          isDisabled={save.isPending}
        >
          {isNew ? "Agregar" : "Guardar"}
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};

interface IFieldProps {
  control: Control<CatalogEntryValues>;
  field: ICatalogField;
  id: string;
}

const FieldError: React.FC<{ message?: string }> = ({ message }) =>
  message ? (
    <FormHelperText>
      <HelperText>
        <HelperTextItem variant="error">{message}</HelperTextItem>
      </HelperText>
    </FormHelperText>
  ) : null;

const TipoDocumentoField: React.FC<IFieldProps> = ({ control, field, id }) => (
  <Controller
    control={control}
    name={field.name}
    render={({ field: input, fieldState }) => (
      <FormGroup label={field.label} isRequired={field.isRequired} fieldId={id}>
        <FormSelect
          id={id}
          value={input.value}
          onChange={(_event, next) => input.onChange(next)}
          onBlur={input.onBlur}
          validated={fieldState.error ? "error" : "default"}
        >
          <FormSelectOption value="" label="Elige el tipo" isPlaceholder />
          {TIPOS_DOCUMENTO_IDENTIDAD.map((tipo) => (
            <FormSelectOption
              key={tipo.value}
              value={tipo.value}
              label={tipo.label}
            />
          ))}
        </FormSelect>
        <FieldError message={fieldState.error?.message} />
      </FormGroup>
    )}
  />
);

const NumeroDocumentoField: React.FC<
  IFieldProps & {
    canLookUp: boolean;
    isLookingUp: boolean;
    onLookup: () => void;
  }
> = ({ control, field, id, canLookUp, isLookingUp, onLookup }) => (
  <Controller
    control={control}
    name={field.name}
    render={({ field: input, fieldState }) => (
      <FormGroup label={field.label} isRequired={field.isRequired} fieldId={id}>
        <InputGroup>
          <InputGroupItem isFill>
            <TextInput
              id={id}
              value={input.value}
              inputMode="numeric"
              onChange={(_event, next) => input.onChange(next)}
              onBlur={input.onBlur}
              validated={fieldState.error ? "error" : "default"}
            />
          </InputGroupItem>
          <InputGroupItem>
            <Button
              variant="control"
              onClick={onLookup}
              isDisabled={!canLookUp || isLookingUp}
              isLoading={isLookingUp}
            >
              Consultar
            </Button>
          </InputGroupItem>
        </InputGroup>
        {fieldState.error ? (
          <FieldError message={fieldState.error.message} />
        ) : (
          <FormHelperText>
            <HelperText>
              <HelperTextItem>
                Con un RUC o un DNI, Consultar trae el nombre y la dirección.
              </HelperTextItem>
            </HelperText>
          </FormHelperText>
        )}
      </FormGroup>
    )}
  />
);
