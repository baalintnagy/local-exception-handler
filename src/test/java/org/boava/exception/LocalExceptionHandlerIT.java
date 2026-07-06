package org.boava.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class LocalExceptionHandlerIT {

    private final LocalExceptionHandler handler = new LocalExceptionHandler();

    @Test
    void illegalArgumentExceptionBecomesBadRequestProblemDetail() {
        var response = handler.handleIllegalArgumentException(
            new IllegalArgumentException("Invalid customer state"), request("/illegal-argument"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class, problem -> {
            assertThat(problem.getTitle()).isEqualTo("Business rule violation");
            assertThat(problem.getDetail()).isEqualTo("Invalid customer state");
            assertThat(problem.getProperties()).containsEntry("errorType", "BUSINESS_RULE_VIOLATION");
            assertCommonProblemFields(problem, "/illegal-argument");
        });
    }

    @Test
    void noSuchElementExceptionBecomesNotFoundProblemDetail() {
        var response = handler.handleNoSuchElementException(
            new NoSuchElementException("Customer was not found"), request("/missing"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class, problem -> {
            assertThat(problem.getTitle()).isEqualTo("Resource not found");
            assertThat(problem.getDetail()).isEqualTo("Customer was not found");
            assertThat(problem.getProperties()).containsEntry("errorType", "RESOURCE_NOT_FOUND");
            assertCommonProblemFields(problem, "/missing");
        });
    }

    @Test
    void constraintViolationExceptionBecomesBadRequestProblemDetail() {
        var exception = new ConstraintViolationException(Set.of(new TestConstraintViolation(
            path("customer.email"),
            "not-an-email",
            "must be a well-formed email address")));

        var response = handler.handleConstraintViolation(exception, request("/constraint-violation"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class, problem -> {
            assertThat(problem.getTitle()).isEqualTo("Constraint violation");
            assertThat(problem.getDetail()).isEqualTo("One or more constraints were violated.");
            assertThat(problem.getProperties()).containsKey("violations");
            assertCommonProblemFields(problem, "/constraint-violation");
        });
    }

    private ServletWebRequest request(String uri) {
        return new ServletWebRequest(new MockHttpServletRequest("GET", uri));
    }

    private void assertCommonProblemFields(ProblemDetail problem, String instance) {
        assertThat(problem.getInstance().toString()).isEqualTo(instance);
        assertThat(problem.getProperties()).containsKeys("id", "timestamp");
    }

    private static Path path(String value) {
        return new Path() {
            @Override
            public Iterator<Node> iterator() {
                return Collections.emptyIterator();
            }

            @Override
            public String toString() {
                return value;
            }
        };
    }

    private record TestConstraintViolation(
        Path propertyPath,
        Object invalidValue,
        String message) implements ConstraintViolation<Object> {

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public String getMessageTemplate() {
            return message;
        }

        @Override
        public Object getRootBean() {
            return null;
        }

        @Override
        public Class<Object> getRootBeanClass() {
            return Object.class;
        }

        @Override
        public Object getLeafBean() {
            return null;
        }

        @Override
        public Object[] getExecutableParameters() {
            return new Object[0];
        }

        @Override
        public Object getExecutableReturnValue() {
            return null;
        }

        @Override
        public Path getPropertyPath() {
            return propertyPath;
        }

        @Override
        public Object getInvalidValue() {
            return invalidValue;
        }

        @Override
        public ConstraintDescriptor<?> getConstraintDescriptor() {
            return null;
        }

        @Override
        public <U> U unwrap(Class<U> type) {
            throw new UnsupportedOperationException("unwrap is not supported in tests");
        }
    }
}
