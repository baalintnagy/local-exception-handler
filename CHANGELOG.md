# 0.10.0

- Resolve library problem titles, details, and types through the application's
  Spring `MessageSource`, following Spring's exception-class message-code conventions
  and current request locale. Retain existing text as fallback defaults.
- Resolve Spring validation-entry messages through their existing message codes and
  arguments. Keep Jakarta constraint interpolation with the validation provider.
- Label `IllegalArgumentException` responses `Invalid request` with `errorType=INVALID_REQUEST`.
  This changes both the default title and the machine-readable error code; consumers
  comparing either value must update their expectations. HTTP status remains 400.
- Matching message-bundle entries now override explicitly supplied problem titles
  and details, including application-authored exception messages.
- Add repository-wide source conventions, EditorConfig, LF line-ending policy,
  Eclipse formatting, and pinned Spotless/Checkstyle checks on JDK 25 and newer.
  Retain Java 17 compatibility and the Java 17/21/25 CI matrix.
- Replace the sole Lombok-generated logger with an explicit SLF4J logger and remove
  the Lombok dependency and annotation processor. Enable compiler lint.
- Add message-bundle, locale, validation, and application-context tests and document
  customization, migration, and source-style workflows.

# 0.9.4

- Catch otherwise unhandled checked and runtime exceptions through an Exception-level MVC fallback.
- Return a generic 500 ProblemDetail with correlation metadata without exposing exception messages.
- Preserve specific application and Spring MVC handlers, with integration tests for routing and disclosure.

# 0.9.2

- Preserve Spring ErrorResponse details, deliberate statuses, headers and extensions.
- Normalize Jakarta, request-body and MVC method validation to property/code/message violations.
- Omit rejected values and full exception logging by default, with independent subclass hooks.
- Add MockMvc integration coverage for validation routing, return-value errors and 429/503/504.

This release changes the validation-entry shape and diagnostic defaults; see the README upgrade notes.
