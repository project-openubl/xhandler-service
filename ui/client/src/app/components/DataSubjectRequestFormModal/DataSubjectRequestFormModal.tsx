import type React from "react";
import { useId } from "react";
import { Controller, type FieldError, useForm } from "react-hook-form";

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
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  TextArea,
  TextInput,
} from "@patternfly/react-core";

import type { NewRequest } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { DATA_SUBJECT_REQUEST_KINDS } from "@app/queries/xhandler/data-subject-requests";
import type { IMutationErrorMessage } from "@app/utils/apiError";
import {
  DATA_SUBJECT_REQUEST_KIND_LABELS,
  SUBJECT_DOC_TYPES,
} from "@app/utils/dataSubjectRequestPresentation";

import {
  dataSubjectRequestSchema,
  type IDataSubjectRequestValues,
} from "./dataSubjectRequestSchema";

interface IDataSubjectRequestFormModalProps {
  title: string;
  description: string;
  /** The caller's request is running. */
  isSaving: boolean;
  /** The caller's last failure, shown in the form. */
  submitError: IMutationErrorMessage | null;
  /** The request body (`NewRequest`, the same in the tenant and the admin API). */
  onSubmit: (body: NewRequest) => void;
  onClose: () => void;
}

const fieldError = (error: FieldError | undefined) =>
  error ? (
    <FormHelperText>
      <HelperText>
        <HelperTextItem variant="error">{error.message}</HelperTextItem>
      </HelperText>
    </FormHelperText>
  ) : null;

/**
 * The form of a personal-data request (Ley 29733), controlled: the caller runs the request
 * (the tenant's, or the platform's that forwards it) and owns its errors and toasts.
 */
export const DataSubjectRequestFormModal: React.FC<
  IDataSubjectRequestFormModalProps
> = ({ title, description, isSaving, submitError, onSubmit, onClose }) => {
  const formId = useId();
  const titleId = useId();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<IDataSubjectRequestValues>({
    resolver: yupResolver(dataSubjectRequestSchema),
    defaultValues: {
      subjectDocType: "1",
      subjectDocNumber: "",
      subjectName: "",
      kind: "access",
      details: "",
    },
  });

  const submit = (values: IDataSubjectRequestValues) =>
    onSubmit({
      subject_doc_type: values.subjectDocType,
      subject_doc_number: values.subjectDocNumber,
      subject_name: values.subjectName || undefined,
      kind: values.kind,
      details: values.details || undefined,
    });

  return (
    <Modal isOpen variant="medium" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader title={title} labelId={titleId} description={description} />
      <ModalBody>
        <Form id={formId} onSubmit={handleSubmit(submit)}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="subjectDocType"
            render={({ field }) => (
              <FormGroup
                label="Tipo de documento"
                isRequired
                fieldId={`${formId}-doc-type`}
              >
                <FormSelect
                  id={`${formId}-doc-type`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {SUBJECT_DOC_TYPES.map((type) => (
                    <FormSelectOption
                      key={type.value}
                      value={type.value}
                      label={type.label}
                    />
                  ))}
                </FormSelect>
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="subjectDocNumber"
            render={({ field }) => (
              <FormGroup
                label="Número de documento"
                isRequired
                fieldId={`${formId}-doc-number`}
              >
                <TextInput
                  id={`${formId}-doc-number`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.subjectDocNumber ? "error" : "default"}
                />
                {fieldError(errors.subjectDocNumber)}
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="subjectName"
            render={({ field }) => (
              <FormGroup label="Nombre del titular" fieldId={`${formId}-name`}>
                <TextInput
                  id={`${formId}-name`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.subjectName ? "error" : "default"}
                />
                {fieldError(errors.subjectName)}
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="kind"
            render={({ field }) => (
              <FormGroup label="Derecho" isRequired fieldId={`${formId}-kind`}>
                <FormSelect
                  id={`${formId}-kind`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {DATA_SUBJECT_REQUEST_KINDS.map((kind) => (
                    <FormSelectOption
                      key={kind}
                      value={kind}
                      label={DATA_SUBJECT_REQUEST_KIND_LABELS[kind]}
                    />
                  ))}
                </FormSelect>
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="details"
            render={({ field }) => (
              <FormGroup label="Detalle" fieldId={`${formId}-details`}>
                <TextArea
                  id={`${formId}-details`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.details ? "error" : "default"}
                  resizeOrientation="vertical"
                />
                {fieldError(errors.details)}
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
          isLoading={isSaving}
          isDisabled={isSaving}
        >
          Registrar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
