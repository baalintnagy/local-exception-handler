# TODO

- Reenable automatic OWASP Dependency Check in CI after successful manual scans confirm that
  the NVD API key and cached vulnerability database keep runtime acceptable. It is temporarily
  manual because fresh database downloads took hours. Run the Dependency Check workflow from
  GitHub Actions to warm the cache and measure subsequent scans. Configure the NVD_API_KEY
  repository secret first. The database cache is isolated by plugin version and updated after
  successful runs; initial downloads can still be slow.
