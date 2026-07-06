package org.boava.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;

import jakarta.validation.Path;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

class ExceptionHandlerUtilsTest {

    @Test
    void fillProblemDetailSetsProvidedValues() {
        var problem = ProblemDetail.forStatus(400);

        ExceptionHandlerUtils.fillProblemDetail(problem, "Title", "Detail", "code", "X");

        assertThat(problem.getTitle()).isEqualTo("Title");
        assertThat(problem.getDetail()).isEqualTo("Detail");
        assertThat(problem.getProperties()).containsEntry("code", "X");
    }

    @Test
    void getInstanceFromReturnsServletRequestUri() {
        var servletRequest = new MockHttpServletRequest("GET", "/api/widgets/42");

        URI instance = ExceptionHandlerUtils.getInstanceFrom(new ServletWebRequest(servletRequest));

        assertThat(instance).isEqualTo(URI.create("/api/widgets/42"));
    }

    @Test
    void getInstanceFromReturnsNullForNonServletRequest() {
        WebRequest request = new WebRequest() {
            @Override
            public String getHeader(String headerName) {
                return null;
            }

            @Override
            public String[] getHeaderValues(String headerName) {
                return new String[0];
            }

            @Override
            public java.util.Iterator<String> getHeaderNames() {
                return java.util.Collections.emptyIterator();
            }

            @Override
            public String getParameter(String paramName) {
                return null;
            }

            @Override
            public String[] getParameterValues(String paramName) {
                return new String[0];
            }

            @Override
            public java.util.Iterator<String> getParameterNames() {
                return java.util.Collections.emptyIterator();
            }

            @Override
            public java.util.Map<String, String[]> getParameterMap() {
                return java.util.Collections.emptyMap();
            }

            @Override
            public java.util.Locale getLocale() {
                return java.util.Locale.ROOT;
            }

            @Override
            public String getContextPath() {
                return "";
            }

            @Override
            public String getRemoteUser() {
                return null;
            }

            @Override
            public java.security.Principal getUserPrincipal() {
                return null;
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public boolean checkNotModified(long lastModifiedTimestamp) {
                return false;
            }

            @Override
            public boolean checkNotModified(String etag) {
                return false;
            }

            @Override
            public boolean checkNotModified(String etag, long lastModifiedTimestamp) {
                return false;
            }

            @Override
            public String getDescription(boolean includeClientInfo) {
                return "test";
            }

            @Override
            public Object getAttribute(String name, int scope) {
                return null;
            }

            @Override
            public void setAttribute(String name, Object value, int scope) {
            }

            @Override
            public void removeAttribute(String name, int scope) {
            }

            @Override
            public String[] getAttributeNames(int scope) {
                return new String[0];
            }

            @Override
            public void registerDestructionCallback(String name, Runnable callback, int scope) {
            }

            @Override
            public Object resolveReference(String key) {
                return null;
            }

            @Override
            public String getSessionId() {
                return null;
            }

            @Override
            public Object getSessionMutex() {
                return this;
            }
        };

        assertThat(ExceptionHandlerUtils.getInstanceFrom(request)).isNull();
    }

    @Test
    void getPathFromHandlesNullViolationAndNullPath() {
        assertThat(ExceptionHandlerUtils.getPathFrom(null)).isEmpty();

        assertThat(ExceptionHandlerUtils.getPathFrom(new TestConstraintViolation(null, null, null))).isEmpty();
    }

    @Test
    void getPathFromReturnsViolationPath() {
        assertThat(ExceptionHandlerUtils.getPathFrom(
            new TestConstraintViolation(path("customer.email"), null, null)))
            .isEqualTo("customer.email");
    }

    @Test
    void safeToStringHandlesBrokenToString() {
        Object broken = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("boom");
            }
        };

        assertThat(ExceptionHandlerUtils.safeToString(broken)).isEqualTo("<unprintable>");
    }

    @Test
    void createMapPreservesOrderAndIsUnmodifiable() {
        var map = ExceptionHandlerUtils.<String, Object>createMap("first", 1, "second", "two");

        assertThat(map).containsExactly(
            org.assertj.core.api.Assertions.entry("first", 1),
            org.assertj.core.api.Assertions.entry("second", "two"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> map.put("third", 3));
    }

    @Test
    void createMapRejectsOddEntryCount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> ExceptionHandlerUtils.createMap("key"))
            .withMessage("Number of arguments must be even (key-value pairs)");
    }

    @Test
    void createMapRejectsDuplicateKeys() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> ExceptionHandlerUtils.createMap("key", 1, "key", 2))
            .withMessage("Duplicate key");
    }

    static Path path(String value) {
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
