#!/usr/bin/env bash
set -euo pipefail

installer="$(realpath "${1:?Pass the DEB installer}")"
mkdir -p "${2:?Pass the evidence directory}"
evidence="$(cd "$2" && pwd)"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
elevate=()
if [[ "$EUID" -ne 0 ]]; then elevate=(sudo); fi

"${elevate[@]}" apt-get update
"${elevate[@]}" apt-get install -y --no-install-recommends \
    xvfb xauth xdotool imagemagick libwebkit2gtk-4.1-0 libsecret-1-0 dbus-x11 gnome-keyring fonts-noto-cjk
"${elevate[@]}" install -d -m 0755 /usr/share/desktop-directories
"${elevate[@]}" apt-get install -y "$installer"
package_name="$(dpkg-deb -f "$installer" Package)"
launcher="$(dpkg -L "$package_name" | grep -E '/bin/Komelia$' | head -n 1)"
test -x "$launcher"
app_lib="$(dirname "$launcher")/../lib/app"
for mode in system-webview bundled; do
    java "-Dcompose.application.resources.dir=$app_lib/resources" -cp "$app_lib/*" \
        "$script_dir/DesktopNativeSmoke.java" "$mode" > "$evidence/native-$mode.log" 2>&1
done
dbus-run-session -- bash -euo pipefail -c '
    printf "%s" "komelia-ci" | gnome-keyring-daemon --unlock --components=secrets
    SKIKO_RENDER_API=SOFTWARE xvfb-run -a --server-args="-screen 0 1440x1000x24" \
        bash "$1/desktop-smoke-linux.sh" "$2" "$3"
' bash "$script_dir" "$launcher" "$evidence"
