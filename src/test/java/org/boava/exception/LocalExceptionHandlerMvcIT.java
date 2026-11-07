package org.boava.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.Locale;
import java.util.NoSuchElementException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class LocalExceptionHandlerMvcIT {
    private static final String PRIVATE_VALUE = "private-submitted-value";
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LocalExceptionHandler.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        validator.setMessageInterpolator(new ParameterMessageInterpolator());
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new Controller(validator))
            .setValidator(validator)
            .setControllerAdvice(new LocalExceptionHandler())
            .build();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void close() {
        logger.detachAppender(logs);
        logs.stop();
        validator.close();
    }

    @Test
    void checkedExceptionsUseTheBaseExceptionFallback() throws Exception {
        assertFallback("/checked");
    }

    @Test
    void applicationMessageSourceCustomizesResponsesForRequestLocale() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(MessagesConfig.class);
            context.refresh();
            var localizedMvc = MockMvcBuilders.webAppContextSetup(context)
                .build();
            localizedMvc.perform(get("/missing").locale(Locale.FRENCH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Introuvable"))
                .andExpect(jsonPath("$.detail").value("Resource unavailable."));
            localizedMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"too long\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Check your input"))
                .andExpect(jsonPath("$.detail").value("Correct the invalid fields."))
                .andExpect(jsonPath("$.violations[0].message").value("Name is too long."));
            localizedMvc.perform(get("/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Try again later."));
        }
    }

    @Configuration
    @EnableWebMvc
    static class MessagesConfig implements WebMvcConfigurer {
        @Override
        public Validator getValidator() {
            return applicationValidator();
        }

        @Bean
        MessageSource messageSource() {
            var source = new ResourceBundleMessageSource();
            source.setBasename("handler-messages");
            return source;
        }

        @Bean
        LocalExceptionHandler advice() {
            return new LocalExceptionHandler();
        }

        @Bean
        Controller controller(
            @Qualifier("applicationValidator") LocalValidatorFactoryBean validator) {
            return new Controller(validator);
        }

        @Bean
        LocalValidatorFactoryBean applicationValidator() {
            var validator = new LocalValidatorFactoryBean();
            validator.setMessageInterpolator(new ParameterMessageInterpolator());
            return validator;
        }
    }

    @Test
    void runtimeExceptionsUseTheBaseExceptionFallback() throws Exception {
        assertFallback("/runtime");
    }

    private void assertFallback(String path) throws Exception {
        mvc.perform(get(path))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
            .andExpect(jsonPath("$.id").isString())
            .andExpect(jsonPath("$.timestamp").isNumber())
            .andExpect(jsonPath("$.instance").value(path));
        assertSafeLogs();
        assertThat(logs.list)
            .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("status=500"));
    }

    @Test
    void specificApplicationHandlersTakePrecedenceOverFallback() throws Exception {
        mvc.perform(get("/illegal"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.errorType").value("INVALID_REQUEST"));
        mvc.perform(get("/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorType").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void bodyValidationHasStructuredViolationsWithoutSubmittedValues() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"%s\"}".formatted(PRIVATE_VALUE)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.violations[0].property").value("name"))
            .andExpect(jsonPath("$.violations[0].code").value("Size"))
            .andExpect(jsonPath("$.violations[0].message").isString())
            .andExpect(jsonPath("$.violations[0].invalidValue").doesNotExist())
            .andExpect(jsonPath("$.id").isString())
            .andExpect(jsonPath("$.timestamp").isNumber())
            .andExpect(jsonPath("$.instance").value("/body"));
        assertSafeLogs();
    }

    @Test
    void mvcMethodValidationUsesTheSameRepresentation() throws Exception {
        mvc.perform(get("/parameter").param("name", PRIVATE_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations[0].property").value("name"))
            .andExpect(jsonPath("$.violations[0].code").value("Pattern"))
            .andExpect(jsonPath("$.violations[0].invalidValue").doesNotExist());
        assertSafeLogs();
    }

    @Test
    void jakartaValidationUsesTheSameRepresentation() throws Exception {
        mvc.perform(get("/constraint"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.violations[0].property").value("name"))
            .andExpect(jsonPath("$.violations[0].code").value("Size"))
            .andExpect(jsonPath("$.violations[0].invalidValue").doesNotExist());
        assertSafeLogs();
    }

    @ParameterizedTest
    @ValueSource(ints = { 429, 503, 504 })
    void preservesErrorResponseStatusDetailHeadersAndExtensions(int expected) throws Exception {
        mvc.perform(get("/unavailable").param("status", Integer.toString(expected)))
            .andExpect(status().is(expected))
            .andExpect(header().string("Retry-After", "3"))
            .andExpect(jsonPath("$.title").value("Please retry"))
            .andExpect(jsonPath("$.detail").value("Service is busy"))
            .andExpect(jsonPath("$.type").value("urn:example:busy"))
            .andExpect(jsonPath("$.instance").value("/original"))
            .andExpect(jsonPath("$.retryable").value(true))
            .andExpect(jsonPath("$.id").isString());
    }

    @Test
    void serverReturnValueFailuresRemainServerErrors() throws Exception {
        mvc.perform(get("/return-value"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.violations[0].code").value("NotBlank"));
    }

    @Test
    void preservesSpringDetailsForMissingParametersAndUnreadableBodies() throws Exception {
        mvc.perform(get("/parameter"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isString());
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON)
            .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void explicitOptInIncludesRejectedValues() throws Exception {
        LocalExceptionHandler diagnostic = new LocalExceptionHandler() {
            @Override
            protected boolean includeRejectedValues() {
                return true;
            }
        };
        MockMvc diagnosticMvc = MockMvcBuilders.standaloneSetup(new Controller(validator))
            .setValidator(validator)
            .setControllerAdvice(diagnostic)
            .build();
        diagnosticMvc.perform(get("/parameter").param("name", PRIVATE_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations[0].invalidValue").value(PRIVATE_VALUE));
        diagnosticMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"%s\"}".formatted(PRIVATE_VALUE)))
            .andExpect(jsonPath("$.violations[0].invalidValue").value(PRIVATE_VALUE));
    }

    private void assertSafeLogs() {
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(PRIVATE_VALUE);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    public record Request(@Size(max = 3) String name) {
    }

    @RestController
    static class Controller {
        private final LocalValidatorFactoryBean validator;

        Controller(LocalValidatorFactoryBean validator) {
            this.validator = validator;
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody Request body) {
            return "ok";
        }

        @GetMapping("/checked")
        String checked() throws Exception {
            throw new Exception(PRIVATE_VALUE);
        }

        @GetMapping("/runtime")
        String runtime() {
            throw new IllegalStateException(PRIVATE_VALUE);
        }

        @GetMapping("/illegal")
        String illegal() {
            throw new IllegalArgumentException("Invalid state");
        }

        @GetMapping("/missing")
        String missing() {
            throw new NoSuchElementException("Missing resource");
        }

        @GetMapping("/parameter")
        String parameter(@RequestParam @Pattern(regexp = "approved") String name) {
            return "ok";
        }

        @GetMapping("/constraint")
        String constraint() {
            throw new ConstraintViolationException(validator.validate(new Request(PRIVATE_VALUE)));
        }

        @GetMapping("/unavailable")
        String unavailable(@RequestParam int status) {
            ErrorResponseException failure = new ErrorResponseException(
                HttpStatusCode.valueOf(status));
            failure.getHeaders()
                .set("Retry-After", "3");
            failure.getBody()
                .setTitle("Please retry");
            failure.getBody()
                .setDetail("Service is busy");
            failure.getBody()
                .setType(URI.create("urn:example:busy"));
            failure.getBody()
                .setInstance(URI.create("/original"));
            failure.getBody()
                .setProperty("retryable", true);
            throw failure;
        }

        @NotBlank
        @GetMapping("/return-value")
        String invalidReturnValue() {
            return "";
        }
    }
}
