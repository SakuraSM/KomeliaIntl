#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
deveco_home="${DEVECO_HOME:-/Applications/DevEco-Studio.app/Contents}"
hdc="${HDC:-${deveco_home}/sdk/default/openharmony/toolchains/hdc}"
sign_tool="${HAP_SIGN_TOOL:-${deveco_home}/sdk/default/openharmony/toolchains/lib/hap-sign-tool.jar}"
target="${HDC_TARGET:-}"
hap_path="${HAP_PATH:-}"
timestamp="$(date '+%Y%m%d-%H%M%S')"
report_dir="${VALIDATION_OUTPUT_DIR:-/private/tmp/komelia-harmony-real-device-${timestamp}}"

if [[ ! -x "${hdc}" || ! -f "${sign_tool}" ]]; then
  echo "HarmonyOS HDC or HAP signing verifier is unavailable under ${deveco_home}." >&2
  exit 1
fi
if [[ -z "${target}" ]]; then
  target="$("${hdc}" list targets | head -1 | tr -d '\r')"
fi
if [[ -z "${target}" || "${target}" == "[Empty]" ]]; then
  echo "No HarmonyOS target is connected." >&2
  exit 1
fi
if [[ "${target}" == 127.0.0.1:* || "${target}" == localhost:* ]]; then
  echo "Real-device validation rejects emulator target ${target}. Connect a HarmonyOS 6 device." >&2
  exit 1
fi

hdc_command=("${hdc}" "-t" "${target}")
read_param() {
  "${hdc_command[@]}" shell param get "$1" 2>/dev/null | tr -d '\r' | tail -1
}

product_model="$(read_param const.product.model)"
api_version="$(read_param const.ohos.apiversion)"
os_full_name="$(read_param const.ohos.fullname)"
product_model_lower="$(printf '%s' "${product_model}" | LC_ALL=C tr '[:upper:]' '[:lower:]')"
if [[ "${product_model_lower}" == *emulator* ]]; then
  echo "Real-device validation rejects product model ${product_model}." >&2
  exit 1
fi
if [[ ! "${api_version}" =~ ^[0-9]+$ || "${api_version}" -lt 20 ]]; then
  echo "HarmonyOS API 20 or newer is required; device reported ${api_version:-unknown}." >&2
  exit 1
fi
if [[ -z "${hap_path}" || ! -f "${hap_path}" ]]; then
  echo "Set HAP_PATH to the signed HarmonyOS HAP produced by the local DevEco signing profile." >&2
  exit 1
fi

mkdir -p "${report_dir}"
verify_dir="$(mktemp -d "${TMPDIR:-/tmp}/komelia-hap-verify.XXXXXX")"
cleanup_verify_dir() {
  rm -rf "${verify_dir}"
}
trap cleanup_verify_dir EXIT

java -jar "${sign_tool}" verify-app \
  -inFile "${hap_path}" \
  -outCertChain "${verify_dir}/certificate-chain.cer" \
  -outProfile "${verify_dir}/profile.p7b" >/dev/null

hap_sha256="$(shasum -a 256 "${hap_path}" | awk '{print $1}')"
HDC="${hdc}" HDC_TARGET="${target}" bash "${project_dir}/scripts/harmony-device-test.sh"
"${hdc_command[@]}" install -r "${hap_path}"
"${hdc_command[@]}" shell aa start -a EntryAbility -b io.github.sakurasm.komeliaintl >/dev/null

report_path="${report_dir}/validation-report.md"
{
  echo "# Komelia HarmonyOS real-device validation"
  echo
  echo "- Generated: ${timestamp}"
  echo "- Target: ${target}"
  echo "- Product model: ${product_model:-unknown}"
  echo "- OS: ${os_full_name:-unknown}"
  echo "- API: ${api_version}"
  echo "- HAP: $(basename "${hap_path}")"
  echo "- HAP SHA-256: ${hap_sha256}"
  echo "- Signature verification: passed"
  echo "- Automated device tests: passed"
  echo
  echo "## Manual gates"
  echo
  echo "Complete every item on this same build and device. Do not include credentials, server URLs, or private book titles in this report."
  echo
  echo "- [ ] HTTPS login, server switching, library, search, series and book details"
  echo "- [ ] Image, PDF and RAR/CBR online reading with progress writeback"
  echo "- [ ] Complete download, app restart, network-off offline reading, reconnect and progress upload"
  echo "- [ ] Reader Kit EPUB table of contents, reflow, rotation and position restoration"
  echo "- [ ] Speech Kit Chinese/English read-aloud, lock screen, headset/call interruption and chapter transition"
  echo "- [ ] Continuous 30-minute screen-off read-aloud without termination"
  echo "- [ ] MindSpore fixed public sample: SSIM >= 0.98 and page time <= Android baseline x 1.25"
  echo "- [ ] Continuous 30-minute image/EPUB reading without crash, black screen or sustained memory growth"
  echo "- [ ] Light, Dark, OLED and system/English/Simplified Chinese"
  echo "- [ ] Phone/tablet adaptive navigation, safe areas, keyboard focus and system back"
} > "${report_path}"

echo "Signed HAP installed and automated device tests passed on ${product_model}."
echo "Manual validation report: ${report_path}"
