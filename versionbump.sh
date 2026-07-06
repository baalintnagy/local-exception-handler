#!/bin/bash
set -euo pipefail

if [ $# -eq 0 ]; then
  echo "Error: Version argument is required"
  echo "Usage: ./versionbump.sh v1.0.0"
  exit 1
fi

VERSION="$1"

if [[ ! $VERSION =~ ^v[0-9]+\.[0-9]+\.[0-9]+([a-z0-9.-]+)?$ ]]; then
  echo "Error: Version must start with 'v' and follow semver-ish format, for example v1.0.0 or v1.0.0-alpha.1"
  exit 1
fi

VERSION_NO_PREFIX="${VERSION#v}"

echo "Bumping project version to ${VERSION_NO_PREFIX}..."
mvn -q versions:set -DnewVersion="${VERSION_NO_PREFIX}" -DgenerateBackupPoms=false

echo "Updating README dependency snippets..."
sed -i.bak "s|<version>[^<]*</version>|<version>${VERSION_NO_PREFIX}</version>|g" README.md
rm -f README.md.bak

sed -i.bak -E \
  "s|(implementation[[:space:]]+'io\.github\.baalintnagy:local-exception-handler:)[^']*(')|\1${VERSION_NO_PREFIX}\2|g" \
  README.md
rm -f README.md.bak

echo "Committing and tagging..."
git add pom.xml README.md
git commit -m "Bump version to ${VERSION}"
git tag "${VERSION}"

echo "Done. Push with: git push origin main --tags"
