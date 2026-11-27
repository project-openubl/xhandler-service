import type React from "react";
import { useState } from "react";

import {
  ActionList,
  ActionListGroup,
  ActionListItem,
  Button,
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { type IssuerListKind, ListImportModal } from "../ListImportModal";

/** SUNAT's lists that decide which issuers the OSE validates for (DESIGN.md §11.4). */
export const OseListsCard: React.FC = () => {
  const [openList, setOpenList] = useState<IssuerListKind | null>(null);
  return (
    <Card>
      <CardTitle component="h2">Listas de SUNAT</CardTitle>
      <CardBody>
        <Stack hasGutter>
          <StackItem>
            <Content component={ContentVariants.p}>
              El OSE solo valida comprobantes de contribuyentes asociados a la
              plataforma y rechaza a los emisores bloqueados. Cada importación
              reemplaza la lista guardada.
            </Content>
          </StackItem>
          <StackItem>
            <ActionList>
              <ActionListGroup>
                <ActionListItem>
                  <Button
                    variant="secondary"
                    onClick={() => setOpenList("association")}
                  >
                    Reemplazar lista de asociados
                  </Button>
                </ActionListItem>
                <ActionListItem>
                  <Button
                    variant="secondary"
                    onClick={() => setOpenList("blocked")}
                  >
                    Reemplazar lista de bloqueados
                  </Button>
                </ActionListItem>
              </ActionListGroup>
            </ActionList>
          </StackItem>
        </Stack>
      </CardBody>
      {openList ? (
        <ListImportModal kind={openList} onClose={() => setOpenList(null)} />
      ) : null}
    </Card>
  );
};
