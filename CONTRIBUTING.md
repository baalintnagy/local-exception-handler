# Contributing to Local Exception Handler

## Prerequisites

- Java 17 or newer
- Maven 3.9 or newer
- Git

## Development

```bash
mvn clean verify
```

The full build includes unit tests, integration tests, and JaCoCo coverage verification. Keep coverage at or above 90%.

## Guidelines

- Keep the library small and platform-friendly.
- Prefer Spring MVC and Jakarta APIs that are already part of a normal Spring web application.
- Avoid adding runtime dependencies unless they provide clear value.
- Add or update tests for behavior changes.
- Document any new exception mapping in `README.md`.

## Release Process

1. Run `./versionbump.sh vX.Y.Z`.
2. Push the generated commit and tag with `git push origin main --tags`.
3. The GitHub release workflow builds, signs, and deploys the artifact to Maven Central.
