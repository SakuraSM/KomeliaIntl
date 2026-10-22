#!/usr/bin/env bash
set -euo pipefail

launcher="${1:?Pass the installed Komelia launcher}"
evidence="${2:?Pass the evidence directory}"
mkdir -p "$evidence"

"$launcher" >"$evidence/stdout.log" 2>"$evidence/stderr.log" &
app_pid=$!
cleanup() {
  kill "$app_pid" 2>/dev/null || true
  wait "$app_pid" 2>/dev/null || true
}
trap cleanup EXIT

window_id=""
for attempt in $(seq 1 60); do
  if ! kill -0 "$app_pid" 2>/dev/null; then
    cat "$evidence/stderr.log" >&2
    echo 'The installed desktop application exited before opening its window.' >&2
    exit 1
  fi
  window_id="$(xdotool search --onlyvisible --pid "$app_pid" --name '^Komelia$' 2>/dev/null | head -n 1 || true)"
  if [[ -n "$window_id" ]] && grep -q 'Application dependencies initialized' "$evidence/stdout.log"; then break; fi
  sleep 1
done
test -n "$window_id"
xdotool windowsize "$window_id" 1280 800
sleep 5
kill -0 "$app_pid"
import -window "$window_id" "$evidence/desktop-startup.png"
xdotool key --window "$window_id" Tab
sleep 1
kill -0 "$app_pid"
import -window "$window_id" "$evidence/desktop-keyboard-focus.png"
if ! grep -q 'Application dependencies initialized' "$evidence/stdout.log"; then
  cat "$evidence/stdout.log" "$evidence/stderr.log" >&2
  echo 'The desktop window opened without completing application initialization.' >&2
  exit 1
fi
printf '{"processAlive":true,"windowPresent":true,"applicationInitialized":true,"screenshotsCaptured":2}\n' >"$evidence/result.json"
