# 0.9.2

- Preserve Spring ErrorResponse details, deliberate statuses, headers and extensions.
- Normalize Jakarta, request-body and MVC method validation to property/code/message violations.
- Omit rejected values and full exception logging by default, with independent subclass hooks.
- Add MockMvc integration coverage for validation routing, return-value errors and 429/503/504.

This release changes the validation-entry shape and diagnostic defaults; see the README upgrade notes.
