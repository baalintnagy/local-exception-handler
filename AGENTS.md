# Repository conventions

Use source styling and general engineering rules similar to the sibling
`../permaformula` and `../permaformula-internal` repositories. This file records
the applicable rules locally so this repository remains usable independently.
When available, consult their source-style guidance for context; their product,
hosting, database, branding, and deployment policies do not apply here.

## Source style

- Use four spaces and a target width of 100 characters; YAML uses two spaces.
- Use explicit Java imports, including static imports for recurring static calls.
  Avoid fully qualified names, wildcard imports, and unused imports.
- Use explicit local types in production Java. Tests and loop declarations may use `var`.
- Use modern `instanceof` pattern matching when using the matched value.
- Use `.formatted(...)` for runtime string values, not string concatenation.
  Prefer ordinary strings for short single-line values and text blocks for multiline
  values or necessary wrapping. Preserve exact string contents and trailing newlines.
- Put text-block delimiters on their own lines at matching indentation. Continuation
  text blocks receive one additional four-space indentation level.
- Put member/type annotations on their own lines; parameter annotations remain inline.
- Always use braces around conditional and loop bodies.
- Break compound call-based conditions before boolean operators. Put arguments
  containing calls, constructors, or lambdas on separate continuation lines.
- Put every method invocation after the first in a call chain on a separate line,
  using leading dots and one continuation indentation level.
- Prefer streams or collection operations when no additional mutable state is needed.
- Use descriptive names for caught exceptions that are referenced. For unused catches,
  use `ignored`: unlike the sibling projects, this library targets Java 17, where `_`
  is unavailable. Do not raise the Java minimum solely to adopt that syntax.
- Apply these rules to maintained sources, not generated output or vendored code.
- Fix straightforward compiler warnings; report remaining warnings and practical
  options. Do not globally hide warnings or disable checks to get a clean build.

## Tooling and verification

- Use JDK 25 for `mvn spotless:apply` and
  `mvn spotless:check checkstyle:check`. Do not write independent formatters.
- The `source-style` Maven profile activates automatically on JDK 25 and newer;
  its Spotless and Checkstyle checks also run during `mvn verify`.
- Keep Java 17 bytecode/API compatibility and the existing CI runtime matrix.
- Run `mvn clean verify` for behavioral/build changes; retain the 90% coverage gate.
- Maintain guidance, formatter/check configurations, and `docs/source-style.md`
  together. Document rules that require review rather than automated enforcement.
- Prefer existing Spring/Jakarta facilities and avoid unnecessary runtime dependencies.
- Document behavior and compatibility changes. Do not invent compatibility needs for
  hypothetical consumers, but assess the library's actual public API and Java baseline.
- Preserve existing user changes. Do not publish, deploy, or move release tags without
  authorization.
