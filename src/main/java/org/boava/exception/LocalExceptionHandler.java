package org.boava.exception;

import static java.util.UUID.randomUUID;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring MVC controller advice that maps common exceptions to RFC 7807 {@link ProblemDetail} responses.
 */
@Slf4j
@ControllerAdvice
public class LocalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String LOG_FORMAT = "Exception: [{}] status={}, type={}, id={}, timestamp={}, instance={}";
    private static final String VIOLATIONS = "violations";
    private static final String ERROR_TYPE = "errorType";
    private static final String MESSAGE = "message";
    private static final String VALIDATION_FAILED_TITLE = "Validation failed";
    private static final String VALIDATION_FAILED_DETAIL = "One or more fields are invalid.";
    private static final String CONSTRAINT_VIOLATION_TITLE = "Constraint violation";
    private static final String CONSTRAINT_VIOLATION_DETAIL = "One or more constraints were violated.";
    private static final String BUSINESS_RULE_VIOLATION_TITLE = "Business rule violation";
    private static final String BUSINESS_RULE_VIOLATION_TYPE = "BUSINESS_RULE_VIOLATION";
    private static final String RESOURCE_NOT_FOUND_TITLE = "Resource not found";
    private static final String RESOURCE_NOT_FOUND_TYPE = "RESOURCE_NOT_FOUND";
    private static final String FIELD = "field";
    private static final String REJECTED_VALUE = "rejectedValue";
    private static final String PROPERTY = "property";
    private static final String INVALID_VALUE = "invalidValue";

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
        Exception ex,
        @Nullable Object body,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        if (ex instanceof HandlerMethodValidationException validationEx
            && !(body instanceof ProblemDetail)) {
            List<Map<String, Object>> violations = validationEx.getAllErrors()
                .stream()
                .map(this::convertResolvableToMap)
                .toList();

            ProblemDetail problemDetailForStatus = ProblemDetail.forStatus(status);
            ExceptionHandlerUtils.fillProblemDetail(
                problemDetailForStatus,
                VALIDATION_FAILED_TITLE,
                VALIDATION_FAILED_DETAIL,
                VIOLATIONS, violations);
            body = problemDetailForStatus;
        }

        var id = randomUUID();
        var epochMillis = Instant.now().toEpochMilli();

        var problem = (body instanceof ProblemDetail pd)
            ? pd
            : ProblemDetail.forStatus(status);
        problem.setProperty("id", id.toString());
        problem.setProperty("timestamp", epochMillis);
        problem.setInstance(ExceptionHandlerUtils.getInstanceFrom(request));

        if (status.is4xxClientError()) {
            log.warn(LOG_FORMAT,
                ex.toString(), status.value(), problem.getType(), id, epochMillis, problem.getInstance(), ex);
        } else {
            log.error(LOG_FORMAT,
                ex.toString(), status.value(), problem.getType(), id, epochMillis, problem.getInstance(), ex);
        }

        return super.handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Converts a Spring validation error into a serializable map entry for the {@code violations} property.
     */
    protected Map<String, Object> convertResolvableToMap(MessageSourceResolvable resolvable) {
        if (resolvable instanceof FieldError fieldError) {
            return ExceptionHandlerUtils.createMap(
                FIELD, fieldError.getField(),
                MESSAGE, fieldError.getDefaultMessage(),
                REJECTED_VALUE, String.valueOf(fieldError.getRejectedValue()));
        }
        if (resolvable instanceof ObjectError objectError) {
            return ExceptionHandlerUtils.createMap(MESSAGE, objectError.getDefaultMessage());
        }
        return ExceptionHandlerUtils.createMap(MESSAGE, resolvable.getDefaultMessage());
    }

    /**
     * Handles Jakarta Bean Validation constraint violations as a {@code 400 Bad Request} problem.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
        @NonNull ConstraintViolationException ex, @NonNull WebRequest request) {

        var violations = ex.getConstraintViolations()
            .stream()
            .map(cv -> Map.of(
                PROPERTY, ExceptionHandlerUtils.getPathFrom(cv),
                INVALID_VALUE, ExceptionHandlerUtils.safeToString(cv.getInvalidValue()),
                MESSAGE, cv.getMessage()))
            .toList();

        var problem = ProblemDetail.forStatus(BAD_REQUEST);
        ExceptionHandlerUtils.fillProblemDetail(problem,
            CONSTRAINT_VIOLATION_TITLE,
            CONSTRAINT_VIOLATION_DETAIL,
            VIOLATIONS, violations);

        return handleExceptionInternal(ex, problem, new HttpHeaders(),
            BAD_REQUEST, request);
    }

    /**
     * Handles illegal arguments as business rule violations.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(
        @NonNull IllegalArgumentException ex, @NonNull WebRequest request) {

        var problem = ProblemDetail.forStatus(BAD_REQUEST);
        ExceptionHandlerUtils.fillProblemDetail(problem,
            BUSINESS_RULE_VIOLATION_TITLE,
            ex.getMessage(),
            ERROR_TYPE, BUSINESS_RULE_VIOLATION_TYPE);

        return handleExceptionInternal(ex, problem, new HttpHeaders(),
            BAD_REQUEST, request);
    }

    /**
     * Handles missing resources as {@code 404 Not Found} problems.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleNoSuchElementException(
        @NonNull NoSuchElementException ex, @NonNull WebRequest request) {

        var problem = ProblemDetail.forStatus(NOT_FOUND);
        ExceptionHandlerUtils.fillProblemDetail(
            problem,
            RESOURCE_NOT_FOUND_TITLE,
            ex.getMessage(),
            ERROR_TYPE, RESOURCE_NOT_FOUND_TYPE);

        return handleExceptionInternal(ex, problem, new HttpHeaders(),
            NOT_FOUND, request);
    }

}
