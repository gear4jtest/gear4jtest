#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

version="${1:-}"
if [[ -z "${version}" ]]; then
  echo "Usage: $0 <project-version>" >&2
  exit 1
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT
first="${work_dir}/first.sha256"
second="${work_dir}/second.sha256"
preserved_evidence="${work_dir}/preserved-evidence"

preserve_evidence_file() {
  local source_file="$1"
  if [[ ! -f "${source_file}" ]]; then
    return
  fi
  mkdir -p "${preserved_evidence}/$(dirname "${source_file}")"
  cp "${source_file}" "${preserved_evidence}/${source_file}"
}

preserve_release_evidence() {
  while IFS= read -r -d '' evidence_file; do
    preserve_evidence_file "${evidence_file#./}"
  done < <(find . -type f \
    \( -path './gear4jtest-*/build/test-results/*.xml' \
       -o -path './gear4jtest-*/build/reports/tests/*' \
       -o -path './build/reports/dependency-check-report.*' \
       -o -path './build/reports/japicmp/*.xml' \
       -o -path './gear4jtest-core/build/reports/jmh/*' \) \
    -print0)
}

restore_release_evidence() {
  if [[ -d "${preserved_evidence}" ]]; then
    cp -a "${preserved_evidence}/." .
  fi
}

snapshot() {
  local output_file="$1"
  if [[ ! -d build/staging-deploy ]]; then
    echo "build/staging-deploy does not exist" >&2
    exit 1
  fi
  find build/staging-deploy -type f \
    \( -name '*.jar' -o -name '*.pom' -o -name '*.module' \) \
    -print0 \
    | sort -z \
    | while IFS= read -r -d '' artifact; do
        relative_path="${artifact#build/staging-deploy/}"
        printf '%s  %s\n' "$(sha256sum "${artifact}" | awk '{print $1}')" "${relative_path}"
      done > "${output_file}"

  if [[ ! -s "${output_file}" ]]; then
    echo "No staged JAR, POM or Gradle module metadata artifacts were found." >&2
    exit 1
  fi
}

stage() {
  ./gradlew --no-daemon \
    verifyStagedReleaseArtifacts \
    -PprojectVersion="${version}"
}

if [[ ! -d build/staging-deploy ]]; then
  stage
fi
snapshot "${first}"
preserve_release_evidence

./gradlew --no-daemon clean
stage
snapshot "${second}"
restore_release_evidence

report_dir="build/reports/reproducibility"
mkdir -p "${report_dir}"
cp "${first}" "${report_dir}/first.sha256"
cp "${second}" "${report_dir}/second.sha256"

if ! diff -u "${first}" "${second}" > "${report_dir}/diff.txt"; then
  cat "${report_dir}/diff.txt" >&2
  echo "Staged JAR/POM/module artifacts are not reproducible." >&2
  exit 1
fi

rm -f "${report_dir}/diff.txt"
echo "Reproducibility verified for $(wc -l < "${second}") staged artifacts."
