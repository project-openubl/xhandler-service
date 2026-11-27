import type React from "react";

import { Alert } from "@patternfly/react-core";

import { ApiErrorText } from "@app/components/ApiErrorText";
import type { IMutationErrorMessage } from "@app/utils/apiError";

interface ILookupErrorProps {
  error: IMutationErrorMessage;
}

/** A failed lookup by id: "No se pudo …" with the server's message and detail (ADR 0006). */
export const LookupError: React.FC<ILookupErrorProps> = ({ error }) => (
  <Alert isInline variant="danger" title={error.title}>
    <ApiErrorText error={error} />
  </Alert>
);
