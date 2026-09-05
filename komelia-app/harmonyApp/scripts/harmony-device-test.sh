#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
deveco_home="${DEVECO_HOME:-/Applications/DevEco-Studio.app/Contents}"
hvigorw="${HVIGORW:-${deveco_home}/tools/hvigor/bin/hvigorw}"
hdc="${HDC:-${deveco_home}/sdk/default/openharmony/toolchains/hdc}"
hdc_server="${HDC_SERVER:-}"
hdc_target="${HDC_TARGET:-}"
main_output_dir="${project_dir}/entry/build/default/outputs/default"
test_output_dir="${project_dir}/entry/build/default/outputs/ohosTest"
main_hap="${main_output_dir}/entry-default-unsigned.hap"
test_hap="${test_output_dir}/entry-ohosTest-unsigned.hap"
test_output="$(mktemp -t komelia-harmony-device-test.XXXXXX)"

if [[ ! -x "${hvigorw}" || ! -x "${hdc}" ]]; then
  echo "DevEco Hvigor or hdc is unavailable under ${deveco_home}" >&2
  exit 1
fi

hdc_command=("${hdc}")
if [[ -n "${hdc_server}" ]]; then
  hdc_command+=("-s" "${hdc_server}")
fi
if [[ -z "${hdc_target}" ]]; then
  hdc_target="$("${hdc_command[@]}" list targets | head -1)"
fi
if [[ -z "${hdc_target}" || "${hdc_target}" == "[Empty]" ]]; then
  echo "No HarmonyOS device or emulator is connected." >&2
  exit 1
fi

if [[ "${hdc_target}" == 127.0.0.1:* ]]; then
  # TCP emulator registrations belong to the current HDC server. A Gradle run
  # may start a fresh server that has not inherited a connection established by
  # DevEco Studio or a previous shell, so make the test command self-contained.
  "${hdc_command[@]}" tconn "${hdc_target}" >/dev/null
  if ! "${hdc_command[@]}" list targets | grep -Fxq "${hdc_target}"; then
    echo "HarmonyOS emulator ${hdc_target} did not register with HDC." >&2
    exit 1
  fi
fi
hdc_command+=("-t" "${hdc_target}")

restore_screen_timeout=false
restore_emulator_timeout() {
  if [[ "${restore_screen_timeout}" == "true" ]]; then
    "${hdc_command[@]}" shell power-shell timeout -r >/dev/null 2>&1 || true
  fi
}
trap restore_emulator_timeout EXIT

if [[ "${hdc_target}" == 127.0.0.1:* ]]; then
  # A freshly cold-booted HarmonyOS 6.1 emulator can report itself through HDC
  # while its keyguard still prevents aa test from launching the application UI.
  "${hdc_command[@]}" shell power-shell wakeup >/dev/null 2>&1 || true
  if "${hdc_command[@]}" shell power-shell timeout -o 120000 >/dev/null 2>&1; then
    restore_screen_timeout=true
  fi
  "${hdc_command[@]}" shell uitest uiInput swipe 660 2500 660 500 1200 >/dev/null 2>&1 || true
fi

cd "${project_dir}"
bash "${project_dir}/scripts/check-page-top-alignment.sh"
bash "${project_dir}/scripts/check-theme-parity.sh"
bash "${project_dir}/scripts/check-navigation-parity.sh"
bash "${project_dir}/scripts/check-image-continuity.sh"
DEVECO_SDK_HOME="${DEVECO_SDK_HOME:-${deveco_home}/sdk}" \
  "${hvigorw}" assembleHap --mode module -p product=default -p module=entry@default \
  -p buildMode=debug --no-daemon
DEVECO_SDK_HOME="${DEVECO_SDK_HOME:-${deveco_home}/sdk}" \
  "${hvigorw}" assembleHap --mode module -p product=default -p module=entry@ohosTest \
  -p buildMode=debug --no-daemon

if [[ -f "${main_output_dir}/entry-default-signed.hap" &&
      ( ! -f "${main_hap}" || "${main_output_dir}/entry-default-signed.hap" -nt "${main_hap}" ) ]]; then
  main_hap="${main_output_dir}/entry-default-signed.hap"
fi
if [[ -f "${test_output_dir}/entry-ohosTest-signed.hap" &&
      ( ! -f "${test_hap}" || "${test_output_dir}/entry-ohosTest-signed.hap" -nt "${test_hap}" ) ]]; then
  test_hap="${test_output_dir}/entry-ohosTest-signed.hap"
fi
if [[ ! -f "${main_hap}" || ! -f "${test_hap}" ]]; then
  echo "HarmonyOS main or test HAP was not generated." >&2
  exit 1
fi

"${hdc_command[@]}" install -r "${main_hap}"
"${hdc_command[@]}" install -r "${test_hap}"
"${hdc_command[@]}" shell aa test -b io.github.sakurasm.komeliaintl \
  -m entry_test -s unittest OpenHarmonyTestRunner -s timeout 180000 -w 240 | tee "${test_output}"

if ! grep -q 'OHOS_REPORT_CODE: 0' "${test_output}" || \
  ! grep -q 'Failure: 0, Error: 0' "${test_output}"; then
  echo "HarmonyOS device tests failed. Output retained at ${test_output}" >&2
  exit 1
fi
echo "HarmonyOS device tests passed on ${hdc_target}."
