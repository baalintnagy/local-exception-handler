# Local Exception Handler

[![CI](https://github.com/baalintnagy/local-exception-handler/actions/workflows/ci.yml/badge.svg)](https://github.com/baalintnagy/local-exception-handler/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.baalintnagy/local-exception-handler.svg)](https://search.maven.org/search?q=g:io.github.baalintnagy%20AND%20a:local-exception-handler)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A drop-in Spring MVC `@ControllerAdvice` that turns common application exceptions into RFC 7807 `ProblemDetail` responses.

## Requirements

- Java 17 or newer
- Spring Framework 7.x / Spring MVC
- Jakarta Servlet 6.1+
- Jakarta Validation 3.1+

Spring, Servlet, and Validation APIs are declared as `provided` dependencies so the consuming application keeps control of its platform versions.

## Installation

```xml
<dependency>
    <groupId>io.github.baalintnagy</groupId>
    <artifactId>local-exception-handler</artifactId>
    <version>0.9.1b</version>
</dependency>
```

```groovy
implementation 'io.github.baalintnagy:local-exception-handler:0.9.1b'
```

## Usage

Register `org.boava.exception.LocalExceptionHandler` through component scanning or import it directly from your Spring MVC application.

```java
@Import(LocalExceptionHandler.class)
@SpringBootApplication
class Application {
}
```

The handler currently maps:

| Exception | HTTP status | Problem fields |
|-----------|-------------|----------------|
| `IllegalArgumentException` | `400 Bad Request` | `title`, `detail`, `errorType=BUSINESS_RULE_VIOLATION`, `id`, `timestamp`, `instance` |
| `NoSuchElementException` | `404 Not Found` | `title`, `detail`, `errorType=RESOURCE_NOT_FOUND`, `id`, `timestamp`, `instance` |
| `ConstraintViolationException` | `400 Bad Request` | `title`, `detail`, `violations`, `id`, `timestamp`, `instance` |
| Spring MVC validation exceptions handled by `ResponseEntityExceptionHandler` | matching Spring status | enriched with `id`, `timestamp`, `instance` |

## Development

```bash
mvn clean verify
```

The build runs unit tests, integration tests, and a JaCoCo coverage gate requiring at least 90% instruction coverage.

## Release

Releases are tag-driven:

```bash
./versionbump.sh v0.1.0
git push origin main --tags
```

The release workflow publishes signed source, javadoc, and binary artifacts through Maven Central's Central Portal.
