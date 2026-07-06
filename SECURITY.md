# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.x     | Yes       |

## Reporting a Vulnerability

Please report vulnerabilities privately with a GitHub security advisory:

https://github.com/baalintnagy/local-exception-handler/security/advisories/new

Include:

- Affected version
- Steps to reproduce
- Expected and actual behavior
- Potential impact
- Suggested fix, if known

## Security Notes

This library is intentionally small. It does not authenticate, authorize, persist, or transport data by itself. Applications should still review which exception messages are safe to expose to clients before using generic exception mappings in public APIs.
