package io.github.projectopenubl.xhandler.bootstrap;

import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import jakarta.inject.Singleton;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the JSON trees of the Jackson 2 era: {@code 118.00} becomes {@code 118}, as before Jackson
 * 3. Request hashes (Idempotency-Key, DESIGN.md §11.1) are computed over these trees, so a retry
 * keeps matching across the upgrade. The request-reading defaults Jackson 3 changed are kept in
 * {@code application.properties} ({@code quarkus.jackson.*}).
 */
@Singleton
public class JacksonCompatibility implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.enable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
    }
}
