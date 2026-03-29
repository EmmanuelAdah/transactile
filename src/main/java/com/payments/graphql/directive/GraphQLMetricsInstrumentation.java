package com.payments.graphql.directive;

import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.validation.ValidationError;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Custom GraphQL instrumentation for:
 * - Request/response logging
 * - Field-level latency metrics
 * - Error tracking
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GraphQLMetricsInstrumentation extends SimplePerformantInstrumentation {

    private final MeterRegistry meterRegistry;

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(
            InstrumentationValidationParameters parameters,
            InstrumentationState state) {

        Document document = parameters.getDocument();

        OperationDefinition operation = document.getDefinitions().stream()
                .filter(def -> def instanceof OperationDefinition)
                .map(def -> (OperationDefinition) def)
                .findFirst()
                .orElse(null);

        log.debug("GraphQL operation: {}", operation != null ? operation.getName() : "unknown");

        return super.beginValidation(parameters, state);
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(
            InstrumentationFieldFetchParameters parameters,
            InstrumentationState state) {

        String fieldName = parameters.getExecutionStepInfo().getPath().toString();
        Timer.Sample sample = Timer.start(meterRegistry);

        return new InstrumentationContext<>() {

            @Override
            public void onDispatched(CompletableFuture<Object> result) {

            }

            @Override
            public void onCompleted(Object result, Throwable t) {
                sample.stop(Timer.builder("graphql.field.fetch")
                        .tag("field", fieldName)
                        .tag("error", t != null ? "true" : "false")
                        .register(meterRegistry));

                if (t != null) {
                    log.warn("Field fetch error [{}]: {}", fieldName, t.getMessage());
                }
            }
        };
    }

    @Override
    public CompletableFuture<graphql.ExecutionResult> instrumentExecutionResult(
            graphql.ExecutionResult executionResult,
            InstrumentationExecutionParameters parameters,
            InstrumentationState state) {

        if (executionResult.getErrors() != null && !executionResult.getErrors().isEmpty()) {
            log.warn("GraphQL execution completed with {} errors", executionResult.getErrors().size());
            meterRegistry.counter("graphql.errors.total",
                    "count", String.valueOf(executionResult.getErrors().size())).increment();
        }
        return super.instrumentExecutionResult(executionResult, parameters, state);
    }
}
