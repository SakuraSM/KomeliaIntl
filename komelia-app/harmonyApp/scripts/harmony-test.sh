#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
deveco_home="${DEVECO_HOME:-/Applications/DevEco-Studio.app/Contents}"
hvigorw="${HVIGORW:-${deveco_home}/tools/hvigor/bin/hvigorw}"
test_result="${project_dir}/entry/.test/default/intermediates/test/coverage_data/test_result.txt"

if [[ ! -x "${hvigorw}" ]]; then
  echo "DevEco Hvigor was not found at ${hvigorw}" >&2
  exit 1
fi

cd "${project_dir}"
node --test "${project_dir}/scripts/check-home-group-alignment.test.mjs"
node --test "${project_dir}/scripts/harmony-real-device-preflight.test.mjs"
node --test "${project_dir}/scripts/local-content-schema.test.mjs"
bash "${project_dir}/scripts/check-page-top-alignment.sh"
bash "${project_dir}/scripts/check-theme-parity.sh"
bash "${project_dir}/scripts/check-navigation-parity.sh"
bash "${project_dir}/scripts/check-image-continuity.sh"
bash "${project_dir}/scripts/check-version-parity.sh"

DEVECO_SDK_HOME="${DEVECO_SDK_HOME:-${deveco_home}/sdk}" \
  "${hvigorw}" test -p module=entry -p coverage=false --no-daemon

if [[ ! -f "${test_result}" ]]; then
  echo "HarmonyOS test result was not generated: ${test_result}" >&2
  exit 1
fi

summary="$(grep 'Tests run:' "${test_result}" | tail -1 || true)"
echo "${summary}"
if [[ "${summary}" != *"Failure: 0, Error: 0"* ]]; then
  echo "HarmonyOS tests contain failures even though Hvigor returned success." >&2
  exit 1
fi
