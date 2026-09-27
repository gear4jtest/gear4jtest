#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_directory}/.." && pwd)"

if [[ ! -x "${repository_root}/gradlew" ]]; then
  echo "Gradle wrapper is missing or not executable: ${repository_root}/gradlew" >&2
  exit 1
fi

echo "Bootstrapping root-build lock state and SHA-256 dependency metadata."
"${repository_root}/gradlew" --no-daemon \
  --write-locks \
  --write-verification-metadata sha256 \
  resolveAllLockableConfigurations

for isolated_build in build-logic release-tools; do
  echo "Bootstrapping ${isolated_build} dependency trust state."
  "${repository_root}/gradlew" --no-daemon \
    -p "${repository_root}/${isolated_build}" \
    --write-locks \
    --write-verification-metadata sha256 \
    resolveLockableConfigurations
done

echo
echo "Bootstrap complete. Treat every generated checksum as untrusted input until it has been reviewed."
echo "Review dependency diffs and corroborate new artifacts from authoritative publisher sources before commit."
