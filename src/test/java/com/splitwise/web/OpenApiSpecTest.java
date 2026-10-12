package com.splitwise.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keeps {@code frontend/openapi.json} (the source of the frontend's TypeScript API types) in step with the API.
 *
 * <p>Fails when the committed spec differs from what the application serves, so an API change cannot be merged
 * without the frontend seeing it. To refresh the file: {@code ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true},
 * then {@code npm run api:types} in {@code frontend/}.
 */
class OpenApiSpecTest extends ApiTestSupport {

    private static final Path SPEC = Path.of("frontend", "openapi.json");

    @Test
    void committedSpecMatchesTheApi() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String spec = normalise(body);

        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(SPEC, spec, StandardCharsets.UTF_8);
            return;
        }
        assertThat(SPEC).as("missing %s; run with -Dopenapi.update=true", SPEC).exists();
        assertThat(Files.readString(SPEC, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("%s is out of date; run with -Dopenapi.update=true and regenerate the frontend types", SPEC)
                .isEqualTo(spec);
    }

    /** Stable text: keys sorted, two-space indent, LF line endings, no environment-specific server URL. */
    private static String normalise(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        ObjectNode tree = (ObjectNode) mapper.readTree(json);
        tree.remove("servers");
        Object sorted = mapper.treeToValue(tree, Object.class);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted).replace("\r\n", "\n") + "\n";
    }
}
