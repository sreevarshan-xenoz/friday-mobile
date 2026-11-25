# FRIDAY Mobile

FRIDAY Mobile is the phone side of [FRIDAY](https://github.com/sreevarshan-xenoz/project-friday):
your FRIDAY computer on your Android phone, and a place to answer and ask
FRIDAY. It starts from [Pocketdesk](https://github.com/sreevarshan-xenoz/Pocketdesk)
(same author, MIT OR Apache-2.0) at commit `4824f4a3` and goes its own way from
there. Pocketdesk stays its own project: changes made here are not sent back
by default.

## Plan

In order; each step is its own PR with its tests passing.

1. **Name and look.** App name "FRIDAY Mobile", Android application id
   `dev.friday.mobile` (installs beside Pocketdesk), FRIDAY's aperture-F icon
   and Core palette (obsidian `#111318`, red `#D42D51`), README and docs.
2. **Commands and paths.** Binaries `friday-mobile`, `friday-mobile-app`,
   `friday-mobile-tray`; config and state under `friday-mobile/`; the user
   unit `friday-mobile.service`; crates renamed to match.
3. **Releases.** Tagged releases build the Linux tarball, the Windows build
   and the APK, the way Pocketdesk's `release.yml` does.
4. **FRIDAY uses it.** project-friday's package and `friday phone` switch from
   `pocketdesk` to `friday-mobile`.
5. **FRIDAY on the phone.** An "Ask FRIDAY" share entry (ECO-8), FRIDAY's
   questions and confirmations answered on the phone, the answer pill on the
   phone, then the FRIDAY items in project-friday's roadmap that need a phone.
