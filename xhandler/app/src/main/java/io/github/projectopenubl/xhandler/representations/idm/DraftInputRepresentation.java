package io.github.projectopenubl.xhandler.representations.idm;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** What a draft holds: the xbuilder document (kind and spec, no number), the series and labels. */
@Schema(name = "DraftInput")
public record DraftInputRepresentation(
        @NotBlank String kind,
        @Schema(description = "El documento de xbuilder sin número (`serie_numero` no se acepta: se numera al emitir).")
        @jakarta.validation.constraints.NotNull ObjectNode spec,
        @Pattern(regexp = "[A-Z0-9]{4}", message = "debe tener 4 caracteres en mayúsculas o dígitos") String serie,
        Map<String, @Size(max = 200) String> labels,
        @Schema(description = "Al editar: la versión que se editó; si el borrador cambió desde entonces, 409 (draft_changed).")
        Integer version) {}
