import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { defineListState, PAGINATION_STATE } from "@app/utils/listState";

export const PLATFORM_TENANT_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    // The API searches by name only (`q`, contains).
    schema: z.object({ nombre: z.string() }),
    defaultValue: { nombre: "" },
  }),
});
