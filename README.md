# Local Exception Handler

[![CI](https://github.com/baalintnagy/local-exception-handler/actions/workflows/ci.yml/badge.svg)](https://github.com/baalintnagy/local-exception-handler/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.baalintnagy/local-exception-handler.svg)](https://search.maven.org/search?q=g:io.github.baalintnagy%20AND%20a:local-exception-handler)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A drop-in Spring MVC `@ControllerAdvice` that turns unhandled exceptions into RFC 7807 `ProblemDetail` responses.

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
    <version>0.10.0</version>
</dependency>
```

```groovy
implementation 'io.github.baalintnagy:local-exception-handler:0.10.0'
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
| `IllegalArgumentException` | `400 Bad Request` | `title`, `detail`, `errorType=INVALID_REQUEST`, `id`, `timestamp`, `instance` |
| `NoSuchElementException` | `404 Not Found` | `title`, `detail`, `errorType=RESOURCE_NOT_FOUND`, `id`, `timestamp`, `instance` |
| `ConstraintViolationException` | `400 Bad Request` | `title`, `detail`, `violations`, `id`, `timestamp`, `instance` |
| Spring MVC validation exceptions handled by `ResponseEntityExceptionHandler` | matching Spring status | enriched with `id`, `timestamp`, `instance` |
| Any other `Exception` (including checked exceptions and runtime exceptions) | `500 Internal Server Error` | generic `title` and `detail`, `id`, `timestamp`, `instance` |

The `Exception.class` fallback covers exceptions reaching Spring MVC without a more
specific handler; applications do not need to register each exception type. Existing
specific mappings take precedence. Unexpected exception messages are never exposed
in the fallback response. JVM `Error` types and failures outside MVC are outside this
fallback's scope.

`IllegalArgumentException` uses the default title `Invalid request` and the
machine-readable `errorType` value `INVALID_REQUEST`. Its detail comes from the
exception message unless a message bundle overrides it. Consumers upgrading from
the previous label must update comparisons against the title or `errorType`.

## Validation and disclosure policy

`ConstraintViolationException`, MVC request-body validation and MVC method validation
all use the same `violations` entries:

```json
{
    "property": "name",
    "code": "Size",
    "message": "size must be between 0 and 3"
}
```

Body fields use their binding paths; method parameters use their parameter names
(or an `argN` fallback); Jakarta violations use their property paths. Global and
cross-parameter errors use an empty property. Constraint codes use the short name
from Spring's message codes or the Jakarta constraint annotation.

Submitted values are omitted by default. The handler logs exception class, status,
correlation id and request path, without exception messages or stack traces.
Applications can subclass the advice and override `includeRejectedValues()` or
`includeExceptionDetailsInLogs()` to opt in independently. Register the subclass
instead of also importing `LocalExceptionHandler`. Override `logHandledException`
for a custom logging policy.

Applications still control constraint messages and the public messages supplied to
`IllegalArgumentException` and `NoSuchElementException`; keep those messages free
of secrets. Spring or other application loggers configured at DEBUG/TRACE can log
request data independently of this handler.

Spring's existing `ProblemDetail` bodies, status codes, headers, details, types,
extensions and explicit instances are preserved. Metadata is added after Spring
resolves `ErrorResponse` exceptions. MVC return-value validation retains Spring's
500 status. Authentication/filter errors outside MVC retain their existing handlers.

### Customizing messages

The advice uses the application's Spring `MessageSource` and the current request
locale. In Spring Boot, put overrides in `src/main/resources/messages.properties`
(include this base file even when using locale-specific bundles such as
`messages_fr.properties`). In plain Spring MVC, register a `MessageSource` bean
named `messageSource` with the desired bundle basename. The library does not
register its own message source or bundle.

Problem fields follow Spring's exception-class message-code conventions:

```properties
problemDetail.title.java.lang.IllegalArgumentException=Invalid request
problemDetail.java.lang.IllegalArgumentException=The operation is not allowed.
problemDetail.title.java.util.NoSuchElementException=Resource unavailable
problemDetail.java.util.NoSuchElementException=The requested resource does not exist.
problemDetail.java.lang.IllegalStateException=Please try again later.
problemDetail.title.org.springframework.web.bind.MethodArgumentNotValidException=Check your input
problemDetail.org.springframework.web.bind.MethodArgumentNotValidException=Correct the invalid fields.
problemDetail.title.org.springframework.web.method.annotation.HandlerMethodValidationException=Check your input
problemDetail.org.springframework.web.method.annotation.HandlerMethodValidationException=Correct the invalid parameters.
problemDetail.title.jakarta.validation.ConstraintViolationException=Check your input
problemDetail.jakarta.validation.ConstraintViolationException=Correct the invalid values.
localExceptionHandler.validation.fallback=Invalid value.
```

Use the **actual exception class**, including for unexpected exceptions and
subclasses; parent-class keys are not inherited. `ErrorResponse` exceptions retain
their own message codes and detail arguments. A `problemDetail.type.<exception class>`
entry can also override the problem type with a URI.

Spring validation entries resolve their existing message codes and arguments
through the same source (for example, `Size.request.name=Name is too long.`).
Jakarta constraint violation messages remain interpolated by the validation
provider; use constraint message templates and its bundle configuration for those.

Without matching entries, current titles, details, and constraint messages remain
the defaults. Matching entries now override even explicitly supplied titles/details,
including application-authored exception messages. Status codes, headers,
correlation metadata, and the response structure are unchanged.

### Upgrading from 0.9.4

`IllegalArgumentException` now returns the default title `Invalid request` and
`errorType=INVALID_REQUEST`. Update consumers and tests that compare the previous
title or error code. The HTTP status remains 400, and exception details remain
application-authored unless overridden through the message source.

Existing matching message-bundle entries now take precedence over library defaults
and explicitly supplied problem titles/details. Spring validation entries also
resolve their message codes and arguments through the application's message source.
Without matching entries, existing messages remain the defaults except for the
renamed illegal-argument title/code. Message keys and the response schema are unchanged.

The library still targets Java 17. Maintaining sources with the new style tools
requires JDK 25; consumers do not need those tools or a newer Java runtime.

### Upgrading from 0.9.1b

Validation entries now consistently use `property`, `code`, and `message`, rather
than the previous mixture of `field`, `rejectedValue`, and `invalidValue`. Rejected
values require explicit opt-in. Request-body errors now include `violations`, and
all three validation paths use the title `Validation failed`. Expected client
failures no longer log a full exception by default.

## Development

```bash
mvn clean verify
```

The build runs unit tests, integration tests, and a JaCoCo coverage gate requiring
at least 90% instruction coverage. On JDK 25 and newer, `verify` also runs Spotless
and Checkstyle. Use JDK 25 and `mvn spotless:apply` for Java formatting; see
[the source-style guide](docs/source-style.md).

## Release

Releases are tag-driven:

```bash
./versionbump.sh v0.10.0
git push origin main --tags
```

The release workflow uploads signed source, javadoc, binary, and POM artifacts to
Maven Central's Central Portal, then creates the GitHub Release. Publication is
manual: after validation succeeds, open
https://central.sonatype.com/publishing/deployments and select Publish.

Commit all intended source and build changes before creating the release tag.
The workflow builds the tagged commit, so rerunning an old tag does not pick up
later fixes. An existing tag may be replaced only for a failed, unpublished
release; never move a tag for an already published version. Push a replacement
tag explicitly, using a force-with-lease restricted to that tag.

The publishing plugin is pinned to 0.12.0, which excludes Maven 3.10 staging
metadata from the release bundle. CI also builds an unsigned bundle without
credentials and checks its layout before a release is tagged.
To verify the release lifecycle and create a
bundle locally without uploading or using a signing key:

```bash
mvn -B -Prelease -Dcentral.skipUpload=true -Dgpg.skip=true clean deploy
```

This runs tests and coverage checks and writes
`target/central-publishing/central-bundle.zip`. The dry-run bundle is unsigned;
the GitHub release workflow still signs artifacts using the configured GPG
secrets. Never use the dry-run switches when performing the real release.

Confirm both the Release workflow and Central Portal publication before building
consumers on clean machines.

Dependency Check runs on main/develop pushes, main pull requests, and manually.
Release uploads wait for the same scan to complete successfully. Configure the
NVD_API_KEY repository secret before pushing a release tag. The NVD database is
cached by plugin version; the first scan can still take a long time. No explicit
workflow timeout is configured. Pull requests from forks cannot access this secret.
