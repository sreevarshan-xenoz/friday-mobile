#!/bin/sh
# Build the Debian/Ubuntu package from release binaries (BH2):
#
#   packaging/linux/build-deb.sh <release-name> <binaries-dir> <output-dir>
#
# e.g. `packaging/linux/build-deb.sh v0.7.0 target/release release` writes
# release/pocketdesk-v0.7.0-linux-amd64.deb. Needs dpkg-deb and dpkg-shlibdeps
# (dpkg-dev), so it runs on the Debian or Ubuntu machine that built the
# binaries: the package's Depends are the libraries they were linked to.
#
# The package installs the server, the desktop app and the tray to /usr/bin,
# the PAM helper for "Approve with my phone" to /usr/lib/pocketdesk (unused
# until the owner adds its line to /etc/pam.d themselves; nothing here does),
# the systemd user unit (enabled for every user, so the server starts at
# each sign-in, as the Windows installer's logon task does; `systemctl
# --user disable pocketdesk` turns it off for one user), the app's menu
# entry and icon, the man page and the docs. Removing it stops the servers
# that are running and disables the unit; nothing in anyone's home folder
# (pairings, keys, settings) is touched.
set -eu

name=${1:?release name, e.g. v0.7.0}
bins=${2:?folder with friday-mobile, friday-mobile-app, friday-mobile-tray and friday-mobile-pam-ask}
out=${3:?output folder}
root=$(cd "$(dirname "$0")/../.." && pwd)

# v0.7.0 -> 0.7.0; v0.7.0-beta.1 -> 0.7.0~beta.1, which dpkg sorts before
# 0.7.0 as a pre-release should.
version=$(printf '%s' "${name#v}" | sed 's/-/~/')
arch=$(dpkg --print-architecture)

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
pkg="$stage/pkg"

for bin in friday-mobile friday-mobile-app friday-mobile-tray; do
  install -Dm755 "$bins/$bin" "$pkg/usr/bin/$bin"
done
# Root's, as PAM runs it as root: never in a folder a user can write to.
install -Dm755 "$bins/friday-mobile-pam-ask" "$pkg/usr/lib/friday-mobile/friday-mobile-pam-ask"
# The user unit, pointed at /usr/bin rather than ~/.local/bin.
mkdir -p "$pkg/usr/lib/systemd/user"
sed 's|^ExecStart=.*|ExecStart=/usr/bin/friday-mobile start|' "$root/friday-mobile.service" \
  > "$pkg/usr/lib/systemd/user/friday-mobile.service"
chmod 644 "$pkg/usr/lib/systemd/user/friday-mobile.service"
install -Dm644 "$root/packaging/linux/dev.friday.Mobile.desktop" \
  "$pkg/usr/share/applications/dev.friday.Mobile.desktop"
install -Dm644 "$root/assets/brand/pocketdesk-icon-512.png" \
  "$pkg/usr/share/icons/hicolor/512x512/apps/pocketdesk.png"
install -Dm644 "$root/assets/brand/pocketdesk-icon.svg" \
  "$pkg/usr/share/icons/hicolor/scalable/apps/pocketdesk.svg"
doc="$pkg/usr/share/doc/friday-mobile"
for file in README.md SECURITY.md config.toml.example; do
  install -Dm644 "$root/$file" "$doc/$file"
done
{
  printf 'FRIDAY Mobile is licensed under the MIT license or the Apache License 2.0, at your option.\n\n'
  cat "$root/LICENSE-MIT"
  printf '\n\n'
  cat "$root/LICENSE-APACHE"
} > "$doc/copyright"
chmod 644 "$doc/copyright"
mkdir -p "$pkg/usr/share/man/man1"
for page in "$root"/man/*.1; do
  gzip -9n -c "$page" > "$pkg/usr/share/man/man1/$(basename "$page").gz"
done

# The libraries the binaries link to, as Debian package names.
mkdir -p "$stage/debian"
printf 'Source: friday-mobile\n\nPackage: friday-mobile\nArchitecture: any\n' > "$stage/debian/control"
depends=$(cd "$stage" && dpkg-shlibdeps -O \
  -epkg/usr/bin/friday-mobile -epkg/usr/bin/friday-mobile-app -epkg/usr/bin/friday-mobile-tray \
  -epkg/usr/lib/friday-mobile/friday-mobile-pam-ask \
  | sed -n 's/^shlibs:Depends=//p')
rm -rf "$stage/debian"

mkdir -p "$pkg/DEBIAN"
cat > "$pkg/DEBIAN/control" <<EOF
Package: friday-mobile
Version: $version
Architecture: $arch
Maintainer: FRIDAY Mobile <https://github.com/sreevarshan-xenoz/friday-mobile/issues>
Installed-Size: $(du -sk "$pkg" | cut -f1)
Depends: $depends
Recommends: ffmpeg, pipewire, xdg-desktop-portal, wl-clipboard | xclip
Suggests: tailscale, playerctl
Section: net
Priority: optional
Homepage: https://github.com/sreevarshan-xenoz/friday-mobile
Description: your computer on your Android phone
 FRIDAY Mobile shows this computer's screen on an Android phone at low
 latency and lets the phone drive it, with its sound, and moves the
 clipboard, files and notifications both ways: on the same network,
 through Tailscale, or over the internet with no port forward and no
 account. This package holds the server, the desktop app and the tray;
 the phone app is on the project's releases page.
EOF

cat > "$pkg/DEBIAN/postinst" <<'EOF'
#!/bin/sh
set -e
if [ "$1" = configure ]; then
  # Every user's server starts at their sign-in; each can turn it off with
  # `systemctl --user disable friday-mobile`.
  if command -v systemctl >/dev/null 2>&1; then
    systemctl --global enable friday-mobile.service >/dev/null 2>&1 || true
  fi
  if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database -q /usr/share/applications || true
  fi
  if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -q -t /usr/share/icons/hicolor || true
  fi
  if [ -n "${2:-}" ]; then
    # An upgrade: start again the servers prerm stopped, for each signed-in
    # user who has it on.
    if command -v loginctl >/dev/null 2>&1 && command -v systemctl >/dev/null 2>&1; then
      for user in $(loginctl list-users --no-legend 2>/dev/null | awk '{print $2}'); do
        if systemctl --user -M "$user@" is-enabled friday-mobile.service >/dev/null 2>&1; then
          systemctl --user -M "$user@" start friday-mobile.service >/dev/null 2>&1 || true
        fi
      done
    fi
  else
    echo "FRIDAY Mobile starts at your next sign-in. To start it now: systemctl --user start friday-mobile"
    echo "Then open FRIDAY Mobile from the app menu and scan its Pair page with the phone."
  fi
fi
EOF

cat > "$pkg/DEBIAN/prerm" <<'EOF'
#!/bin/sh
set -e
if [ "$1" = remove ] || [ "$1" = upgrade ]; then
  # Stop the servers running in signed-in users' sessions, so none keeps
  # running from deleted files; on an upgrade they start again below.
  if command -v loginctl >/dev/null 2>&1 && command -v systemctl >/dev/null 2>&1; then
    for user in $(loginctl list-users --no-legend 2>/dev/null | awk '{print $2}'); do
      systemctl --user -M "$user@" stop friday-mobile.service >/dev/null 2>&1 || true
    done
  fi
fi
if [ "$1" = remove ] && command -v systemctl >/dev/null 2>&1; then
  systemctl --global disable friday-mobile.service >/dev/null 2>&1 || true
fi
EOF

cat > "$pkg/DEBIAN/postrm" <<'EOF'
#!/bin/sh
set -e
if [ "$1" = remove ] || [ "$1" = purge ]; then
  if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database -q /usr/share/applications || true
  fi
fi
EOF
chmod 755 "$pkg/DEBIAN/postinst" "$pkg/DEBIAN/prerm" "$pkg/DEBIAN/postrm"

mkdir -p "$out"
# Named as the release's other files are, so its globs and checksums take it.
deb="$out/friday-mobile-${name}-linux-${arch}.deb"
dpkg-deb --root-owner-group -Zxz --build "$pkg" "$deb" >/dev/null
echo "$deb"
