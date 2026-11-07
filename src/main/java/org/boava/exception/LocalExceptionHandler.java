package org.boava.exception;

import static java.util.UUID.randomUUID;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.context.i18n.LocaleContextHolder.getLocale;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.ProblemDetail.forStatus;
import static org.springframework.http.ProblemDetail.forStatusAndDetail;
import static org.springframework.web.ErrorResponse.getDefaultDetailMessageCode;
import static org.springframework.web.ErrorResponse.getDefaultTitleMessageCode;
import static org.springframework.web.ErrorResponse.getDefaultTypeMessageCode;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Spring MVC advice with consistent, payload-free validation responses and diagnostics. */
@ControllerAdvice
public class LocalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = getLogger(LocalExceptionHandler.class);
    private static final String LOG_FORMAT =
        "event=http_exception exceptionType={} status={} id={} instance={}";

    /** Handles any MVC exception without a more specific handler. */
    @ExceptionHandler(Exception.class)
    public @Nullable ResponseEntity<Object> handleUnhandledException(
        @NonNull Exception ex, @NonNull WebRequest request) {
        ProblemDetail problem = forStatusAndDetail(INTERNAL_SERVER_ERROR,
            "An unexpected error occurred.");
        return handleExceptionInternal(ex, problem,
            new HttpHeaders(), INTERNAL_SERVER_ERROR,
            request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
        Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode status,
        WebRequest request) {

        if (ex instanceof MethodArgumentNotValidException validation) {
            body = validationProblem(status,
                validation.getBindingResult()
                    .getAllErrors()
                    .stream()
                    .map(this::convertResolvableToMap)
                    .toList());
        } else if (ex instanceof HandlerMethodValidationException validation) {
            List<Map<String, Object>> violations = new ArrayList<>();
            validation.getParameterValidationResults()
                .forEach(result -> {
                    String name = result.getMethodParameter()
                        .getParameterName();
                    String property = name == null
                        ? "arg%d".formatted(
                            result.getMethodParameter()
                                .getParameterIndex())
                        : name;
                    result.getResolvableErrors()
                        .forEach(error -> violations.add(
                            violation(error, property,
                                result.getArgument())));
                });
            validation.getCrossParameterValidationResults()
                .stream()
                .map(this::convertResolvableToMap)
                .forEach(violations::add);
            body = validationProblem(status, violations);
        }

        if (body instanceof ProblemDetail problem) {
            resolveProblemMessages(ex, problem);
        }

        // Let Spring resolve ErrorResponse bodies and honor already committed responses first.
        ResponseEntity<Object> response = super.handleExceptionInternal(
            ex, body, headers, status, request);
        if (response != null
            && response.getBody() instanceof ProblemDetail problem) {
            logHandledException(ex, problem, status);
        }
        return response;
    }

    private void resolveProblemMessages(Exception ex, ProblemDetail problem) {
        ErrorResponse error = ex instanceof ErrorResponse response ? response : null;
        String titleCode = error == null
            ? getDefaultTitleMessageCode(ex.getClass())
            : error.getTitleMessageCode();
        problem.setTitle(
            resolveMessage(titleCode,
                problem.getTitle()));
        MessageSource source = getMessageSource();
        Object[] arguments = error == null
            || source == null ? null
                : error.getDetailMessageArguments(source,
                    getLocale());
        String detailCode = error == null
            ? getDefaultDetailMessageCode(ex.getClass(), null)
            : error.getDetailMessageCode();
        problem.setDetail(
            resolveMessage(detailCode, arguments,
                problem.getDetail()));
        String typeCode = error == null
            ? getDefaultTypeMessageCode(ex.getClass())
            : error.getTypeMessageCode();
        String type = resolveMessage(typeCode, null);
        if (type != null) {
            problem.setType(URI.create(type));
        }
    }

    private @Nullable String resolveMessage(String code, @Nullable String fallback) {
        return resolveMessage(code, null, fallback);
    }

    private @Nullable String resolveMessage(String code, @Nullable Object[] arguments,
        @Nullable String fallback) {
        MessageSource source = getMessageSource();
        return source == null ? fallback
            : source.getMessage(code, arguments, fallback,
                getLocale());
    }

    private @Nullable String resolveValidationMessage(MessageSourceResolvable error) {
        MessageSource source = getMessageSource();
        if (source != null) {
            try {
                return source.getMessage(error,
                    getLocale());
            } catch (NoSuchMessageException ignored) {
                // A missing bundle entry must not turn validation into a server error.
            }
        }
        return error.getDefaultMessage();
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
        @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail problem = body instanceof ProblemDetail existing
            ? existing
            : forStatus(status);
        problem.setProperty("id",
            randomUUID().toString());
        problem.setProperty("timestamp",
            Instant.now()
                .toEpochMilli());
        if (problem.getInstance() == null) {
            problem.setInstance(ExceptionHandlerUtils.getInstanceFrom(request));
        }
        return super.createResponseEntity(problem, headers, status, request);
    }

    private ProblemDetail validationProblem(HttpStatusCode status,
        List<Map<String, Object>> violations) {
        ProblemDetail problem = forStatus(status);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty("violations", violations);
        return problem;
    }

    /** Applications may explicitly opt in to submitted values in validation responses. */
    protected boolean includeRejectedValues() {
        return false;
    }

    /** Applications may explicitly opt in to exception messages and stack traces in logs. */
    protected boolean includeExceptionDetailsInLogs() {
        return false;
    }

    /** Override to integrate application-specific structured logging or correlation. */
    protected void logHandledException(Exception ex, ProblemDetail problem, HttpStatusCode status) {
        Object id = problem.getProperties() == null ? null
            : problem.getProperties()
                .get("id");
        Object[] arguments = { ex.getClass()
            .getSimpleName(), status.value(), id,
                problem.getInstance() };
        if (includeExceptionDetailsInLogs()) {
            arguments = new Object[] { arguments[0], arguments[1], arguments[2], arguments[3], ex };
        }
        if (status.is4xxClientError()) {
            log.warn(LOG_FORMAT, arguments);
        } else {
            log.error(LOG_FORMAT, arguments);
        }
    }

    /** Shared property/code/message representation for Spring validation errors. */
    protected Map<String, Object> convertResolvableToMap(MessageSourceResolvable error) {
        return violation(error, "", null);
    }

    private Map<String, Object> violation(MessageSourceResolvable error, String property,
        @Nullable Object invalidValue) {
        if (error instanceof FieldError field) {
            property = field.getField();
            invalidValue = field.getRejectedValue();
        }
        String[] codes = error.getCodes();
        String code = codes == null
            || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
        int separator = code.indexOf('.');
        if (separator >= 0) {
            code = code.substring(0, separator);
        }
        return violation(property, code,
            resolveValidationMessage(error), invalidValue);
    }

    private Map<String, Object> violation(String property, String code, @Nullable String message,
        @Nullable Object invalidValue) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("property", property);
        result.put("code", code);
        result.put("message", message == null
            ? resolveMessage("localExceptionHandler.validation.fallback", "Invalid value.")
            : message);
        if (includeRejectedValues()) {
            result.put("invalidValue",
                ExceptionHandlerUtils.safeToString(invalidValue));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Handles Jakarta method and programmatic validation failures. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
        @NonNull ConstraintViolationException ex, @NonNull WebRequest request) {
        List<Map<String, Object>> violations = ex.getConstraintViolations()
            .stream()
            .map(cv -> violation(
                ExceptionHandlerUtils.getPathFrom(cv),
                cv.getConstraintDescriptor() == null ? "Invalid"
                    : cv.getConstraintDescriptor()
                        .getAnnotation()
                        .annotationType()
                        .getSimpleName(),
                cv.getMessage(),
                cv.getInvalidValue()))
            .toList();
        return handleExceptionInternal(ex,
            validationProblem(BAD_REQUEST, violations),
            new HttpHeaders(), BAD_REQUEST, request);
    }

    /** Illegal argument messages are application-authored public error details. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(
        @NonNull IllegalArgumentException ex, @NonNull WebRequest request) {
        ProblemDetail problem = forStatus(BAD_REQUEST);
        ExceptionHandlerUtils.fillProblemDetail(problem, "Invalid request",
            ex.getMessage(),
            "errorType", "INVALID_REQUEST");
        return handleExceptionInternal(ex, problem,
            new HttpHeaders(), BAD_REQUEST, request);
    }

    /** Handles explicitly missing application resources. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleNoSuchElementException(
        @NonNull NoSuchElementException ex, @NonNull WebRequest request) {
        ProblemDetail problem = forStatus(NOT_FOUND);
        ExceptionHandlerUtils.fillProblemDetail(problem, "Resource not found",
            ex.getMessage(),
            "errorType", "RESOURCE_NOT_FOUND");
        return handleExceptionInternal(ex, problem,
            new HttpHeaders(), NOT_FOUND, request);
    }
}
