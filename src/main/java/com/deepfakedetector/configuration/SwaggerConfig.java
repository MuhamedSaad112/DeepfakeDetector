package com.deepfakedetector.configuration;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi deepfakeApi() {
        return GroupedOpenApi.builder()
                .group("DeepfakeDetector API")
                .packagesToScan("com.deepfakedetector.controller")
                .addOperationCustomizer(authorizationHeader())
                .build();
    }

    @Bean
    public OperationCustomizer authorizationHeader() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            Parameter headerParameter = new Parameter()
                    .in(ParameterIn.HEADER.toString())
                    .required(true)
                    .schema(new StringSchema()._default("Bearer your-jwt-token"))
                    .name("Authorization")
                    .description("JWT token header");
            operation.addParametersItem(headerParameter);
            return operation;
        };
    }
}
