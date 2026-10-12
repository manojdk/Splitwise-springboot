package com.splitwise.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Splitwise API", version = "v1",
                description = "Shared-expense tracking: groups, expense splitting, balances and debt simplification"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    /**
     * Response records always serialise every component (a missing value is sent as {@code null}), but the spec
     * would otherwise list every response field as optional. This marks all fields of response-only schemas as
     * required; fields that can be null say so with {@code @Schema(nullable = true)} on the record component.
     * Schemas reachable from a request body are left alone, since their optional fields really are optional.
     */
    @Bean
    OpenApiCustomizer responseFieldsArePresent() {
        return openApi -> {
            @SuppressWarnings("rawtypes")
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            Set<String> requestSchemas = new HashSet<>();
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                    operation.getRequestBody().getContent().values()
                            .forEach(media -> collectReferenced(media.getSchema(), schemas, requestSchemas));
                }
            }));
            schemas.forEach((name, schema) -> {
                if (!requestSchemas.contains(name) && schema.getProperties() != null) {
                    @SuppressWarnings("unchecked")
                    Set<String> properties = schema.getProperties().keySet();
                    schema.setRequired(new ArrayList<>(new TreeSet<>(properties)));
                }
            });
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collectReferenced(Schema<?> schema, Map<String, Schema> all, Set<String> found) {
        if (schema == null) {
            return;
        }
        if (schema.get$ref() != null) {
            String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
            if (found.add(name)) {
                collectReferenced(all.get(name), all, found);
            }
            return;
        }
        collectReferenced(schema.getItems(), all, found);
        if (schema.getProperties() != null) {
            ((Map<String, Schema<?>>) (Map) schema.getProperties()).values()
                    .forEach(property -> collectReferenced(property, all, found));
        }
    }
}
