package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    void responseFieldsAreRequiredAndOnlyTheNullableOnesAcceptNull() throws Exception {
        JsonNode schemas = read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()))
                .path("components").path("schemas");

        // Response schemas: every field is always sent, so every field is required
        JsonNode expense = schemas.path("ExpenseResponse");
        assertThat(names(expense.path("required"))).containsAll(names(expense.path("properties").properties()));
        assertThat(expense.path("properties").path("groupId").path("type").toString()).contains("null");
        assertThat(expense.path("properties").path("amount").path("type").toString()).doesNotContain("null");
        assertThat(names(schemas.path("Item").path("required"))).contains("groupId", "groupName", "amount");

        // Request schemas keep only the fields the API really demands
        assertThat(names(schemas.path("CreateExpenseRequest").path("required")))
                .doesNotContain("groupId", "notes", "category");
        assertThat(names(schemas.path("ShareRequest").path("required"))).containsExactly("userId");
    }

    private static List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(node -> names.add(node.asText()));
        return names;
    }

    private static List<String> names(Set<Map.Entry<String, JsonNode>> fields) {
        return fields.stream().map(Map.Entry::getKey).toList();
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
