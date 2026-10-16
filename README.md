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
    <version>0.9.2</version>
</dependency>
```

```groovy
implementation 'io.github.baalintnagy:local-exception-handler:0.9.2'
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

The build runs unit tests, integration tests, and a JaCoCo coverage gate requiring at least 90% instruction coverage.

## Release

Releases are tag-driven:

```bash
./versionbump.sh v0.9.2
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
