package org.boava.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URI;
import java.util.List;

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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;

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
    void bodyValidationHasStructuredViolationsWithoutSubmittedValues() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + PRIVATE_VALUE + "\"}"))
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
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{"))
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
            .setValidator(validator).setControllerAdvice(diagnostic).build();
        diagnosticMvc.perform(get("/parameter").param("name", PRIVATE_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations[0].invalidValue").value(PRIVATE_VALUE));
        diagnosticMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + PRIVATE_VALUE + "\"}"))
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
            failure.getHeaders().set("Retry-After", "3");
            failure.getBody().setTitle("Please retry");
            failure.getBody().setDetail("Service is busy");
            failure.getBody().setType(URI.create("urn:example:busy"));
            failure.getBody().setInstance(URI.create("/original"));
            failure.getBody().setProperty("retryable", true);
            throw failure;
        }

        @NotBlank
        @GetMapping("/return-value")
        String invalidReturnValue() {
            return "";
        }
    }
}
