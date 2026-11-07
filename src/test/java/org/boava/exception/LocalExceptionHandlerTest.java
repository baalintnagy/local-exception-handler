package org.boava.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

class LocalExceptionHandlerTest {

    private final TestableLocalExceptionHandler handler = new TestableLocalExceptionHandler();

    @Test
    void messageBundlesCustomizeProblemFieldsAndRespectLocale() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("handler-messages");
        source.setFallbackToSystemLocale(false);
        handler.setMessageSource(source);
        try {
            LocaleContextHolder.setLocale(Locale.FRENCH);
            var response = handler.handleNoSuchElementException(
                new NoSuchElementException("original"), request("/missing"));
            var problem = (ProblemDetail) response.getBody();
            assertThat(problem.getTitle()).isEqualTo("Introuvable");
            assertThat(problem.getDetail()).isEqualTo("Resource unavailable.");
            assertThat(problem.getType()).hasToString("https://example.org/problems/not-found");
            assertThat(problem.getStatus()).isEqualTo(404);
            assertThat(problem.getProperties()).containsKeys("id", "timestamp", "errorType");

            var error = new FieldError("request", "name", null, false,
                new String[] { "Size.custom.name", "Size" }, new Object[] { 3 }, "original");
            assertThat(handler.convertResolvableToMap(error))
                .containsEntry("message", "Maximum 3 characters.");
            var missing = new DefaultMessageSourceResolvable(
                new String[] { "unknown" }, null, null);
            assertThat(handler.convertResolvableToMap(missing))
                .containsEntry("message", "Please correct this value.");

            var fallback = (ProblemDetail) handler.handleUnhandledException(
                new UnsupportedOperationException("secret"), request("/unexpected"))
                .getBody();
            assertThat(fallback.getDetail()).isEqualTo("An unexpected error occurred.");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void convertResolvableToMapIncludesFieldErrorDetails() {
        var error = new FieldError("request", "name", "bad", false, null, null,
            "must not be blank");

        var result = handler.convertResolvableToMap(error);

        assertThat(result).containsEntry("property", "name")
            .containsEntry("message", "must not be blank")
            .containsEntry("code", "Invalid")
            .doesNotContainKeys("rejectedValue", "invalidValue");
    }

    @Test
    void convertResolvableToMapHandlesObjectError() {
        var error = new ObjectError("request", "invalid request");

        assertThat(handler.convertResolvableToMap(error))
            .containsEntry("property", "")
            .containsEntry("message", "invalid request");
    }

    @Test
    void convertResolvableToMapHandlesGenericResolvable() {
        var error = new DefaultMessageSourceResolvable(
            new String[] { "plain" }, null, "plain");

        assertThat(handler.convertResolvableToMap(error))
            .containsEntry("property", "")
            .containsEntry("message", "plain");
    }

    @Test
    void handledExceptionsReturnExpectedStatuses() {
        assertThat(HttpStatus.BAD_REQUEST.value()).isEqualTo(400);
        assertThat(HttpStatus.NOT_FOUND.value()).isEqualTo(404);
    }

    @Test
    void handleExceptionInternalCreatesProblemDetailForPlainBody() {
        var response = handler.handle(
            new IllegalStateException("boom"),
            "plain body",
            HttpStatus.INTERNAL_SERVER_ERROR,
            request("/server-error"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
            .isInstanceOfSatisfying(ProblemDetail.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(500);
                assertThat(problem.getProperties()).containsKeys("id", "timestamp");
                assertThat(problem.getInstance()
                    .toString()).isEqualTo("/server-error");
            });
    }

    @Test
    void handleExceptionInternalEnrichesHandlerMethodValidationException()
        throws NoSuchMethodException {
        Method method = SampleValidatedController.class.getDeclaredMethod("submit", String.class);
        var validationResult = MethodValidationResult.create(
            new SampleValidatedController(),
            method,
            List.of(),
            List.of(new ObjectError("request", "request is invalid")));

        var response = handler.handle(
            new HandlerMethodValidationException(validationResult),
            null,
            HttpStatus.BAD_REQUEST,
            request("/validated"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
            .isInstanceOfSatisfying(ProblemDetail.class, problem -> {
                assertThat(problem.getTitle()).isEqualTo("Validation failed");
                assertThat(problem.getDetail()).isEqualTo("One or more fields are invalid.");
                assertThat(problem.getProperties()).containsKeys("violations", "id", "timestamp");
                assertThat(problem.getInstance()
                    .toString()).isEqualTo("/validated");
            });
    }

    @Test
    void preservesCommittedResponses() {
        var response = new MockHttpServletResponse();
        response.setCommitted(true);
        var request = new ServletWebRequest(new MockHttpServletRequest(), response);
        assertThat(handler.handle(new IllegalStateException("private"), null,
            HttpStatus.INTERNAL_SERVER_ERROR, request)).isNull();
    }

    @Test
    void nullMessagesAndEmptyCodesHaveStableFallbacks() {
        var error = new DefaultMessageSourceResolvable(
            new String[0], null, null);
        assertThat(handler.convertResolvableToMap(error)).containsEntry("code", "Invalid")
            .containsEntry("message", "Invalid value.");
    }

    @Test
    void exceptionDetailsCanBeExplicitlyEnabledForDiagnostics() {
        var logger = (Logger) LoggerFactory.getLogger(LocalExceptionHandler.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            var diagnostic = new LocalExceptionHandler() {
                @Override
                protected boolean includeExceptionDetailsInLogs() {
                    return true;
                }
            };
            diagnostic.handleIllegalArgumentException(new IllegalArgumentException("diagnostic"),
                request("/diagnostic"));
            assertThat(logs.list).hasSize(1);
            assertThat(logs.list.get(0)
                .getThrowableProxy()).isNotNull();
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    private ServletWebRequest request(String uri) {
        return new ServletWebRequest(new MockHttpServletRequest("GET", uri));
    }

    private static final class TestableLocalExceptionHandler extends LocalExceptionHandler {

        ResponseEntity<Object> handle(Exception ex, Object body, HttpStatus status,
            WebRequest request) {
            return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
        }
    }

    private static final class SampleValidatedController {

        @SuppressWarnings("unused")
        void submit(String input) {
        }
    }
}
