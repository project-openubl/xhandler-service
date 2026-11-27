import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { defineListState, PAGINATION_STATE } from "@app/utils/listState";

import { CATALOG_SLUGS } from "./catalogKinds";

export const CATALOG_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({ catalogo: z.enum(CATALOG_SLUGS), q: z.string() }),
    defaultValue: { catalogo: "clientes", q: "" },
  }),
});
