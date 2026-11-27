import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { PartnerMember } from "@app/client/xhandler-admin";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  isPartnerRole,
  PARTNER_ROLE_LABELS,
  PARTNER_ROLES,
} from "@app/queries/xhandler/partners";
import {
  PARTNER_MEMBER_KINDS,
  useAdminSetPartnerMemberMutation,
} from "@app/queries/xhandler-admin/partners";
import { getMutationErrorMessage } from "@app/utils/apiError";
import { PARTNER_MEMBER_KIND_LABELS } from "@app/utils/partnerPresentation";

import {
  type IPartnerMemberValues,
  partnerMemberSchema,
} from "./partnerMemberSchema";

interface IPartnerMemberModalProps {
  partnerId: string;
  partnerName: string;
  /** The member whose role changes; without it, a new member is added. */
  member?: PartnerMember;
  onClose: () => void;
}

const roleLabel = (role: string | undefined) =>
  role && isPartnerRole(role) ? PARTNER_ROLE_LABELS[role] : (role ?? "");

/** Adds a member to the partner, or changes an existing member's role. */
export const PartnerMemberModal: React.FC<IPartnerMemberModalProps> = ({
  partnerId,
  partnerName,
  member,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const setMember = useAdminSetPartnerMemberMutation(partnerId, {
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `${data.subject ?? "El miembro"} es ${roleLabel(data.role).toLowerCase()} de ${partnerName}`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: member
            ? "No se pudo cambiar el rol del miembro"
            : "No se pudo agregar al miembro",
        }),
      ),
  });

  const { control, handleSubmit } = useForm<IPartnerMemberValues>({
    resolver: yupResolver(partnerMemberSchema),
    defaultValues: {
      kind: member?.kind ?? "user",
      subject: member?.subject ?? "",
      role: member?.role ?? "admin",
    },
  });

  const onSubmit = (values: IPartnerMemberValues) => {
    clearSubmitError();
    setMember.mutate(
      { kind: values.kind, subject: values.subject.trim(), role: values.role },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title={member ? "Cambiar rol" : "Agregar miembro"}
        labelId={titleId}
        description={
          member
            ? `El nuevo rol de ${member.subject ?? "este miembro"} en ${partnerName}.`
            : `Los administradores de ${partnerName} crean y administran las organizaciones de sus clientes; los lectores solo las ven.`
        }
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <FormGroup label="Tipo" isRequired fieldId={`${formId}-kind`}>
            <Controller
              name="kind"
              control={control}
              render={({ field }) => (
                <FormSelect
                  id={`${formId}-kind`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  isDisabled={!!member}
                >
                  {PARTNER_MEMBER_KINDS.map((kind) => (
                    <FormSelectOption
                      key={kind}
                      value={kind}
                      label={PARTNER_MEMBER_KIND_LABELS[kind]}
                    />
                  ))}
                </FormSelect>
              )}
            />
          </FormGroup>
          <ControlledTextField
            control={control}
            name="subject"
            label="Identificador"
            isRequired
            isDisabled={!!member}
            helperText="Para una persona, su identificador de usuario en Keycloak (sub); para un cliente de máquina, su client_id."
          />
          <FormGroup label="Rol" isRequired fieldId={`${formId}-role`}>
            <Controller
              name="role"
              control={control}
              render={({ field }) => (
                <FormSelect
                  id={`${formId}-role`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {PARTNER_ROLES.map((role) => (
                    <FormSelectOption
                      key={role}
                      value={role}
                      label={PARTNER_ROLE_LABELS[role]}
                    />
                  ))}
                </FormSelect>
              )}
            />
          </FormGroup>
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={setMember.isPending}
          isDisabled={setMember.isPending}
        >
          {member ? "Cambiar rol" : "Agregar"}
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
