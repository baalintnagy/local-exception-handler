# Source style

This repository follows the applicable source conventions of the sibling
PermaFormula repositories, recorded locally in [AGENTS.md](../AGENTS.md).
The library continues to target Java 17; tooling runs separately on JDK 25.

With JDK 25 selected, run:

```bash
mvn spotless:apply
mvn spotless:check checkstyle:check
mvn clean verify
```

The `source-style` profile activates automatically on JDK 25 and newer. It pins
the same Spotless, Eclipse formatter, and Checkstyle versions as the sibling
project. Both checks run during `verify` on those JDKs, including the JDK 25 CI
job. Java 17 and 21 builds continue to compile and test without loading those
newer tooling dependencies. Do not explicitly activate this profile on older JDKs.

Spotless formats all maintained Java files under `src/main/java` and
`src/test/java`, orders imports, removes unused imports, rejects wildcards, and
applies the Eclipse profile in [eclipse/java-style.properties](../eclipse/java-style.properties).
Generated sources and build output are excluded. `.editorconfig` and
`.gitattributes` define indentation and LF line endings for repository text.

Checkstyle enforces the 100-column Java line limit (excluding package/import
declarations), spaces instead of tabs, explicit/used imports, braces, member/type
annotation placement, and text-block delimiter layout. The sibling's conservative
simple-string check and corresponding Spotless replacement are retained together.

Review is still needed for explicit production local types, `.formatted()` rather
than string concatenation, appropriate static imports, pattern matching, collection
operations, and argument layout for calls/constructors/lambdas. Eclipse wraps
arguments based on width rather than their expression type. Its forced selector
wrapping handles fluent call chains; review nested and unqualified call expressions
as well. Text blocks must preserve exact whitespace and trailing newlines.

Compiler lint is enabled; annotation-processor claiming warnings are excluded
because Spring/Jakarta annotations require no processor. The library no longer
uses Lombok: its single generated logger was replaced with an explicit SLF4J logger.
Do not suppress JVM or compiler warnings globally. Spotless currently emits a JDK 25
`sun.misc.Unsafe` warning from its own implementation; a compatible upstream fix
is preferable to disabling diagnostics.

Keep these instructions, `AGENTS.md`, Maven tooling, Checkstyle, and the Eclipse
profile synchronized when changing rules. Use the existing formatter/check
frameworks rather than writing independent formatters. For tool changes, verify a
representative violation is rejected and that applying fixes produces stable output.
