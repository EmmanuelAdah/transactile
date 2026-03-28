package com.payments.config;

import com.payments.graphql.scalar.CustomScalars;
import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
public class GraphQLConfig {

    /**
     * Registers custom and extended scalars (BigDecimal, UUID, DateTime).
     * These must match the scalar declarations in schema.graphqls.
     */
    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.UUID)
                .scalar(ExtendedScalars.DateTime)
                .scalar(CustomScalars.CURRENCY_SCALAR);
    }
}
