import type React from "react";
import { useId } from "react";

import {
  Alert,
  Button,
  ClipboardCopy,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Stack,
  StackItem,
} from "@patternfly/react-core";

interface IOneTimePasswordModalProps {
  title: string;
  username: string;
  password: string;
  onClose: () => void;
}

/** A password the API shows once (ADR 0012): copy it now, it can't be seen again. */
export const OneTimePasswordModal: React.FC<IOneTimePasswordModalProps> = ({
  title,
  username,
  password,
  onClose,
}) => {
  const titleId = useId();
  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader title={title} labelId={titleId} />
      <ModalBody>
        <Stack hasGutter>
          <StackItem>
            <Alert
              isInline
              variant="warning"
              title="Copia la contraseña ahora: no se volverá a mostrar."
            >
              Entrégala al emisor por un canal seguro. Si se pierde, genera una
              nueva.
            </Alert>
          </StackItem>
          <StackItem>
            <DescriptionList
              isHorizontal
              aria-label="Credenciales del usuario OSE"
            >
              <DescriptionListGroup>
                <DescriptionListTerm>Usuario</DescriptionListTerm>
                <DescriptionListDescription>
                  <ClipboardCopy
                    isReadOnly
                    hoverTip="Copiar"
                    clickTip="Copiado"
                    textAriaLabel="Usuario"
                    toggleAriaLabel="Mostrar usuario"
                  >
                    {username}
                  </ClipboardCopy>
                </DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>Contraseña</DescriptionListTerm>
                <DescriptionListDescription>
                  <ClipboardCopy
                    isReadOnly
                    hoverTip="Copiar"
                    clickTip="Copiado"
                    textAriaLabel="Contraseña"
                    toggleAriaLabel="Mostrar contraseña"
                  >
                    {password}
                  </ClipboardCopy>
                </DescriptionListDescription>
              </DescriptionListGroup>
            </DescriptionList>
          </StackItem>
        </Stack>
      </ModalBody>
      <ModalFooter>
        <Button variant="primary" onClick={onClose}>
          Ya la copié
        </Button>
      </ModalFooter>
    </Modal>
  );
};
