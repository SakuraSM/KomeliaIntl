#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 2 ]]; then
  echo "Usage: $0 <installer.dmg> <evidence-directory>" >&2
  exit 2
fi
installer=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
mkdir -p "$2"
evidence=$(cd "$2" && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/komelia-macos-smoke.XXXXXX")
mount="$work/mount"
mkdir -p "$mount" "$work/install" "$work/home" "$work/tmp"
pid=''
mounted=false
cleanup() {
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  fi
  if [[ "$mounted" == true ]] && ! hdiutil detach "$mount" >/dev/null; then
    echo "Could not detach the temporary installer at $mount" >&2
    return
  fi
  rm -rf -- "$work"
}
trap cleanup EXIT
hdiutil attach -nobrowse -readonly -mountpoint "$mount" "$installer" > "$evidence/mount.log"
mounted=true
cp -R "$mount/Komelia.app" "$work/install/"
app="$work/install/Komelia.app"
JAVA_TOOL_OPTIONS="-Duser.home=$work/home -Djava.io.tmpdir=$work/tmp" \
  "$app/Contents/MacOS/Komelia" > "$evidence/app.log" 2>&1 &
pid=$!
for ((i = 0; i < 60; i++)); do
  kill -0 "$pid" 2>/dev/null || { echo 'Application exited before initialization.' >&2; exit 1; }
  if grep -q 'Application dependencies initialized' "$evidence/app.log"; then break; fi
  sleep 1
done
grep -q 'Application dependencies initialized' "$evidence/app.log" || { echo 'Application initialization timed out.' >&2; exit 1; }
window=$(swift "$(dirname "$0")/find-window.swift" "$pid") || { echo 'No visible application window.' >&2; exit 1; }
screencapture -x -l "$window" "$evidence/window.png" || { echo 'Could not capture the application window.' >&2; exit 1; }
test -s "$evidence/window.png"
shasum -a 256 "$installer" > "$evidence/sha256.txt"
swift - "$pid" <<'SWIFT'
import AppKit
guard CommandLine.arguments.count == 2,
      let pid = Int32(CommandLine.arguments[1]),
      let app = NSRunningApplication(processIdentifier: pid),
      app.terminate() else { exit(1) }
SWIFT
for ((i = 0; i < 30; i++)); do
  if ! kill -0 "$pid" 2>/dev/null; then break; fi
  sleep 1
done
if kill -0 "$pid" 2>/dev/null; then
  echo 'Application did not exit after the quit request.' >&2
  exit 1
fi
set +e
wait "$pid"
app_exit=$?
set -e
pid=''
printf '%s\n' "$app_exit" > "$evidence/exit-code.txt"
if [[ "$app_exit" != 0 ]]; then
  echo "Application exited with status $app_exit." >&2
  exit 1
fi
echo 'macOS installer initialized, displayed a window and exited cleanly.'
