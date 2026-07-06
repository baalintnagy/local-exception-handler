package org.boava.exception;

import static java.util.stream.Collectors.toMap;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

import jakarta.validation.ConstraintViolation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Utility methods used by {@link LocalExceptionHandler} to build RFC 7807 problem responses.
 */
public final class ExceptionHandlerUtils {

    private ExceptionHandlerUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Applies the common title, detail, and custom property values to a problem response.
     */
    public static void fillProblemDetail(ProblemDetail problem,
        String title, String detail, String propertyName, Object propertyValue) {

        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setProperty(propertyName, propertyValue);
    }

    /**
     * Extracts the request URI as the RFC 7807 {@code instance} value when the request is servlet-backed.
     */
    public static @Nullable URI getInstanceFrom(@NonNull WebRequest request) {
        if (!(request instanceof ServletWebRequest swr)) {
            return null;
        }
        var uri = swr.getRequest().getRequestURI();
        if (uri == null || uri.isBlank()) {
            return null;
        }
        return URI.create(uri);
    }

    /**
     * Returns the property path of a constraint violation, or an empty string when it is unavailable.
     */
    public static String getPathFrom(ConstraintViolation<?> cv) {
        if (cv == null || cv.getPropertyPath() == null) {
            return "";
        }
        return cv.getPropertyPath().toString();
    }

    /**
     * Converts a value to text while protecting error handling from broken {@code toString()} methods.
     */
    public static String safeToString(Object v) {
        try {
            return String.valueOf(v);
        } catch (Exception ignore) {
            return "<unprintable>";
        }
    }

    /**
     * Creates an insertion-ordered, unmodifiable map from alternating key and value arguments.
     */
    public static <K, V> Map<K, V> createMap(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments must be even (key-value pairs)");
        }

        // Create map using stream API with LinkedHashMap to preserve order
        Map<K, V> map = IntStream.iterate(0, i -> i < entries.length, i -> i + 2)
            .boxed()
            .collect(toMap(
                i -> valueAt(entries, i), // key extractor
                i -> valueAt(entries, i + 1), // value extractor
                (v1, v2) -> {
                    throw new IllegalStateException("Duplicate key");
                },
                () -> new LinkedHashMap<>(entries.length / 2, 1.0f)));

        return Collections.unmodifiableMap(map);
    }

    private static <T> T valueAt(Object[] entries, int i) {
        @SuppressWarnings("unchecked")
        T val = (T) entries[i];
        return val;
    }
}
