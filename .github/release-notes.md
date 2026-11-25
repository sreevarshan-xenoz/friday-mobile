FRIDAY Mobile is FRIDAY's phone app: your Linux or Windows computer on your Android phone (its screen, keyboard and mouse, files both ways, the clipboard, notifications and sound), and a place to answer FRIDAY's questions. It is built from [Pocketdesk](https://github.com/sreevarshan-xenoz/Pocketdesk) and goes its own way from here.

**This is a beta.** It carries everything Pocketdesk 0.9.0 does, under FRIDAY's name and look.

## Install

| Platform | File | How |
|---|---|---|
| Windows | `friday-mobile-…-windows-x86_64-setup.exe` | Run it: it installs FRIDAY Mobile, starts it at sign-in with its firewall ports open, puts `friday-mobile` on `PATH` and offers FFmpeg through winget. Uninstall it from Settings → Apps. |
| Windows, portable | `friday-mobile-…-windows-x86_64.zip` | Unzip, then run `friday-mobile.exe install` to start the server at logon and open its firewall ports. `friday-mobile-app.exe` is the desktop app. FFmpeg must be on `PATH` for the encoder. |
| Debian, Ubuntu | `friday-mobile-…-linux-amd64.deb` | `sudo apt install ./friday-mobile-…-linux-amd64.deb` installs the server, the desktop app and the tray, and starts the server at every sign-in. `sudo apt remove friday-mobile` takes it out. |
| Linux | `friday-mobile-…-linux-x86_64.tar.gz` | `curl -fsSL https://raw.githubusercontent.com/sreevarshan-xenoz/friday-mobile/main/scripts/install.sh \| bash` installs the server, the desktop app, the tray and the systemd user service. It needs PipeWire and FFmpeg (libav*). |
| Android | `friday-mobile-…-android-arm64.apk` | Install it on the phone, then pair with the QR code from the desktop app's Pair page. |

`SHA256SUMS` lists every file's checksum (`sha256sum -c SHA256SUMS`).

## What's new in 0.11.0

- **Ask FRIDAY from your phone:** share text or a link to "Ask FRIDAY", add a question if you like, and FRIDAY on your computer answers on the phone, with Copy. Turn it on for a phone on the computer's Devices page ("Ask FRIDAY", off until you do); it needs FRIDAY installed on the computer. Each ask is a new FRIDAY conversation, apart from the one at your desk. The shared text goes to FRIDAY as quoted context, so FRIDAY asks you before it does anything, and FRIDAY Mobile logs nothing you asked or got back.
- **Really separate from Pocketdesk:** the Windows installer, the browser extension's helper, the file manager's "Send to phone" and the folder variables (`FRIDAY_MOBILE_STATE_DIR`, `FRIDAY_MOBILE_CONFIG_DIR`) are FRIDAY Mobile's own, so installing or removing one app no longer touches the other. If you set up the browser extension with 0.10.0, run `friday-mobile browser-host --install` again and reload the extension from this release.
- **FRIDAY's icon on the computer too:** the desktop app's window and tray, the Windows installer and shortcuts, and the Linux menu entry show FRIDAY's aperture-F mark.

## In 0.10.0, the first FRIDAY Mobile release

- **FRIDAY's name and look:** the app is FRIDAY Mobile, with FRIDAY's aperture-F icon and its obsidian and red colours.
- **Installs beside Pocketdesk:** the Android app is `dev.friday.mobile`, signed with FRIDAY Mobile's own key, and on the computer it has its own commands (`friday-mobile`, `friday-mobile-app`, `friday-mobile-tray`), folders, service and settings. It never reads, moves or deletes Pocketdesk's. Run one of the two servers at a time: they use the same ports.
- **Works with your Pocketdesk phones and computers:** the network protocol is unchanged, so a phone or computer still on Pocketdesk pairs and talks to it as before.

A computer that had Pocketdesk starts FRIDAY Mobile with no pairings: pair your phone again from the Pair page.
