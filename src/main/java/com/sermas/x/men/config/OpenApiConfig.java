package com.sermas.x.men.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for Swagger UI.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "X-Men Mutation Service API",
            version = "v0.0.1",
            description =
                "Endpoints for generating mutation variants of SPTHY models, "
                    + "including skip, replace, forget, and neglect mutations, "
                    + "plus optional derivation tree analysis.",
            contact = @Contact(name = "X-Men Team"),
            license = @License(name = "Internal")),
    tags = {
      @Tag(
          name = "Mutations",
          description = "Combined mutation generation with header-controlled options"),
      @Tag(name = "Add Mutations", description = "Add-rule mutation generation"),
      @Tag(name = "Forget Mutations", description = "Forget mutation generation and controls"),
      @Tag(name = "Neglect Mutations", description = "Neglect mutation generation"),
      @Tag(name = "Replace Mutations", description = "Replace sub-messages or types"),
      @Tag(name = "Skip Mutations", description = "Skip send/receive mutations"),
      @Tag(name = "Derivation", description = "Haskell derivation analysis")
    })
public class OpenApiConfig {}

