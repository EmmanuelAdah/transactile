package com.payments.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        log.error("GraphQL error on field [{}]: {}", env.getField().getName(), ex.getMessage(), ex);

        return switch (ex) {
            case PaymentException pe -> GraphqlErrorBuilder.newError(env)
                    .message(pe.getMessage())
                    .errorType(mapErrorType(pe))
                    .extensions(pe.getExtensions())
                    .build();

            case ConstraintViolationException cve -> null; // handled by resolveToMultipleErrors

            case AccessDeniedException ignored -> GraphqlErrorBuilder.newError(env)
                    .message("Access denied: insufficient permissions")
                    .errorType(ErrorType.FORBIDDEN)
                    .extensions(Map.of("code", "ACCESS_DENIED"))
                    .build();

            case AuthenticationException ignored -> GraphqlErrorBuilder.newError(env)
                    .message("Authentication required")
                    .errorType(ErrorType.UNAUTHORIZED)
                    .extensions(Map.of("code", "UNAUTHENTICATED"))
                    .build();

            case IllegalArgumentException iae -> GraphqlErrorBuilder.newError(env)
                    .message(iae.getMessage())
                    .errorType(ErrorType.BAD_REQUEST)
                    .extensions(Map.of("code", "INVALID_ARGUMENT"))
                    .build();

            case IllegalStateException ise -> GraphqlErrorBuilder.newError(env)
                    .message(ise.getMessage())
                    .errorType(ErrorType.BAD_REQUEST)
                    .extensions(Map.of("code", "INVALID_STATE"))
                    .build();

            default -> {
                log.error("Unhandled GraphQL exception", ex);
                yield GraphqlErrorBuilder.newError(env)
                        .message("An internal error occurred. Please try again later.")
                        .errorType(ErrorType.INTERNAL_ERROR)
                        .extensions(Map.of("code", "INTERNAL_ERROR"))
                        .build();
            }
        };
    }

    @Override
    protected List<GraphQLError> resolveToMultipleErrors(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ConstraintViolationException cve) {
            return cve.getConstraintViolations().stream()
                    .map(cv -> GraphqlErrorBuilder.newError(env)
                            .message(cv.getPropertyPath() + ": " + cv.getMessage())
                            .errorType(ErrorType.BAD_REQUEST)
                            .extensions(Map.of(
                                    "code", "VALIDATION_ERROR",
                                    "field", cv.getPropertyPath().toString(),
                                    "invalidValue", cv.getInvalidValue() != null ? cv.getInvalidValue().toString() : "null"
                            ))
                            .build())
                    .toList();
        }
        return super.resolveToMultipleErrors(ex, env);
    }

    private ErrorType mapErrorType(PaymentException ex) {
        return switch (ex.getHttpStatus().value()) {
            case 400 -> ErrorType.BAD_REQUEST;
            case 401 -> ErrorType.UNAUTHORIZED;
            case 403 -> ErrorType.FORBIDDEN;
            case 404 -> ErrorType.NOT_FOUND;
            default -> ErrorType.INTERNAL_ERROR;
        };
    }
}
