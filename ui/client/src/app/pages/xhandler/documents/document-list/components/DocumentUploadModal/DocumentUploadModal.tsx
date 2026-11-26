import type React from "react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { useNavigate } from "@tanstack/react-router";

import {
  Button,
  FileUpload,
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
} from "@patternfly/react-core";

import { FormSubmitError } from "@app/components/FormSubmitError";
import { DROPDOWN_PAGE_SIZE } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useCompaniesListQuery } from "@app/queries/xhandler/companies";
import {
  type IDocumentUploadVariables,
  useDocumentUploadMutation,
} from "@app/queries/xhandler/documents";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  isUploadFormat,
  parseEmitJson,
  sfsFileName,
  UPLOAD_FORMATS,
  type UploadFormat,
} from "./documentUpload";
import {
  documentUploadSchema,
  type IDocumentUploadValues,
} from "./documentUploadSchema";

const FORMAT_HELP: Record<UploadFormat, string> = {
  ubl: "Un comprobante en XML UBL 2.1, firmado o sin firmar (se firma con el certificado de la empresa). Conserva su número.",
  sfs: "Los archivos del facturador SUNAT: la cabecera (.CAB, o .NOT en notas) y el detalle (.DET), con nombre RUC-TIPO-SERIE-NUMERO. Conserva su número.",
  json: "Un documento de xbuilder (kind y spec) o una solicitud de emisión. El servicio le da el siguiente número de la serie.",
};

interface IDocumentUploadModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/**
 * Uploads a document built outside the console (an ERP, the SFS, xbuilder) for one company: it
 * is validated, signed and sent like any emit. Writing one by hand is a draft (ADR 0009).
 */
export const DocumentUploadModal: React.FC<IDocumentUploadModalProps> = ({
  isOpen,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="medium"
    aria-labelledby="document-upload-title"
  >
    {/* Mounted only while open: each opening is a new intent with its own Idempotency-Key. */}
    {isOpen ? <DocumentUploadForm onClose={onClose} /> : null}
  </Modal>
);

const FileField: React.FC<{
  id: string;
  label: string;
  accept: string;
  value: File | undefined;
  error: string | undefined;
  onChange: (file: File | undefined) => void;
}> = ({ id, label, accept, value, error, onChange }) => (
  <FormGroup label={label} isRequired fieldId={id}>
    <FileUpload
      id={id}
      filename={value?.name ?? ""}
      filenamePlaceholder="Arrastra el archivo o elígelo"
      browseButtonText="Elegir"
      clearButtonText="Quitar"
      hideDefaultPreview
      accept={accept}
      onFileInputChange={(_event, file) => onChange(file)}
      onClearClick={() => onChange(undefined)}
      validated={error ? "error" : "default"}
    />
    {error ? (
      <FormHelperText>
        <HelperText>
          <HelperTextItem variant="error">{error}</HelperTextItem>
        </HelperText>
      </FormHelperText>
    ) : null}
  </FormGroup>
);

const DocumentUploadForm: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const formId = useId();
  const tenantId = useSelectedTenantId();
  const navigate = useNavigate();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [jsonFilename, setJsonFilename] = useState("");

  // Few companies per tenant: the first page fills the picker (no name filter in the API).
  const { data: companies, isLoading } = useCompaniesListQuery({
    params: { limit: DROPDOWN_PAGE_SIZE, offset: 0 },
  });
  const firstCompanyId = companies?.items?.[0]?.id ?? "";

  const { control, handleSubmit, watch } = useForm<IDocumentUploadValues>({
    resolver: yupResolver(documentUploadSchema),
    values: { companyId: firstCompanyId, format: "ubl", jsonText: "" },
    resetOptions: { keepDirtyValues: true },
  });
  const format = watch("format") as UploadFormat;

  const upload = useDocumentUploadMutation({
    onSuccess: (document) => {
      addNotification({
        variant: "success",
        title: `Se recibió ${document.serie_numero ?? "el comprobante"}`,
        description: "Se enviará a SUNAT; su estado se actualiza solo.",
      });
      onClose();
      if (document.id) {
        void navigate({
          to: "/organizaciones/$tenantId/documentos/$documentId",
          params: { tenantId, documentId: document.id },
        });
      }
    },
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo subir el comprobante",
        }),
      ),
  });

  const onSubmit = handleSubmit(async (values) => {
    clearSubmitError();
    let variables: IDocumentUploadVariables;
    if (values.format === "ubl" && values.ublFile) {
      variables = {
        companyId: values.companyId,
        format: "ubl",
        file: values.ublFile,
      };
    } else if (values.format === "sfs" && values.cabFile && values.detFile) {
      const named = sfsFileName(values.cabFile.name, values.detFile.name);
      if ("error" in named) return;
      variables = {
        companyId: values.companyId,
        format: "sfs",
        idempotencyKey,
        body: {
          file_name: named.fileName,
          cab: await values.cabFile.text(),
          det: await values.detFile.text(),
        },
      };
    } else {
      const parsed = parseEmitJson(values.jsonText);
      if ("error" in parsed) return;
      variables = {
        companyId: values.companyId,
        format: "json",
        idempotencyKey,
        body: parsed.request,
      };
    }
    upload.mutate(variables);
  });

  return (
    <>
      <ModalHeader
        title="Subir comprobante"
        labelId="document-upload-title"
        description="Para comprobantes generados en otro sistema: se validan, firman y envían a SUNAT como cualquier emisión. Para escribir uno a mano, crea un borrador en Emisión manual."
      />
      <ModalBody>
        <Form id={formId} onSubmit={(event) => void onSubmit(event)}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="companyId"
            render={({ field, fieldState }) => (
              <FormGroup
                label="Empresa"
                isRequired
                fieldId={`${formId}-company`}
              >
                <FormSelect
                  id={`${formId}-company`}
                  value={field.value}
                  onChange={(_event, next) => field.onChange(next)}
                  onBlur={field.onBlur}
                  isDisabled={isLoading}
                  validated={fieldState.error ? "error" : "default"}
                >
                  {(companies?.items ?? []).length === 0 ? (
                    <FormSelectOption
                      value=""
                      label={
                        isLoading ? "Cargando empresas" : "No hay empresas"
                      }
                      isPlaceholder
                    />
                  ) : null}
                  {(companies?.items ?? []).map((company) => (
                    <FormSelectOption
                      key={company.id}
                      value={company.id}
                      label={`${company.razon_social ?? ""} (RUC ${company.ruc ?? ""})`}
                    />
                  ))}
                </FormSelect>
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
          <Controller
            control={control}
            name="format"
            render={({ field }) => (
              <FormGroup
                label="Formato"
                isRequired
                fieldId={`${formId}-format`}
              >
                <FormSelect
                  id={`${formId}-format`}
                  value={field.value}
                  onChange={(_event, next) => field.onChange(next)}
                  onBlur={field.onBlur}
                >
                  {UPLOAD_FORMATS.map((item) => (
                    <FormSelectOption
                      key={item.value}
                      value={item.value}
                      label={item.label}
                    />
                  ))}
                </FormSelect>
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem>
                      {isUploadFormat(format) ? FORMAT_HELP[format] : null}
                    </HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
            )}
          />
          {format === "ubl" ? (
            <Controller
              control={control}
              name="ublFile"
              render={({ field, fieldState }) => (
                <FileField
                  id={`${formId}-ubl`}
                  label="Archivo XML"
                  accept=".xml,application/xml,text/xml"
                  value={field.value}
                  error={fieldState.error?.message}
                  onChange={field.onChange}
                />
              )}
            />
          ) : null}
          {format === "sfs" ? (
            <>
              <Controller
                control={control}
                name="cabFile"
                render={({ field, fieldState }) => (
                  <FileField
                    id={`${formId}-cab`}
                    label="Cabecera (.CAB o .NOT)"
                    accept=".cab,.not,.CAB,.NOT"
                    value={field.value}
                    error={fieldState.error?.message}
                    onChange={field.onChange}
                  />
                )}
              />
              <Controller
                control={control}
                name="detFile"
                render={({ field, fieldState }) => (
                  <FileField
                    id={`${formId}-det`}
                    label="Detalle (.DET)"
                    accept=".det,.DET"
                    value={field.value}
                    error={fieldState.error?.message}
                    onChange={field.onChange}
                  />
                )}
              />
            </>
          ) : null}
          {format === "json" ? (
            <Controller
              control={control}
              name="jsonText"
              render={({ field, fieldState }) => (
                <FormGroup
                  label="Documento JSON"
                  isRequired
                  fieldId={`${formId}-json`}
                >
                  <FileUpload
                    id={`${formId}-json`}
                    type="text"
                    value={field.value}
                    filename={jsonFilename}
                    filenamePlaceholder="Arrastra un archivo .json, elígelo o pega el JSON"
                    browseButtonText="Elegir"
                    clearButtonText="Quitar"
                    accept=".json,application/json"
                    allowEditingUploadedText
                    onFileInputChange={(_event, file) =>
                      setJsonFilename(file.name)
                    }
                    onDataChange={(_event, data) => field.onChange(data)}
                    onTextChange={(_event, text) => field.onChange(text)}
                    onClearClick={() => {
                      setJsonFilename("");
                      field.onChange("");
                    }}
                    validated={fieldState.error ? "error" : "default"}
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
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={upload.isPending}
          isDisabled={upload.isPending}
        >
          Subir
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
