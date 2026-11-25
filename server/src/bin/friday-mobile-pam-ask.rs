//! `pocketdesk-pam-ask`: approve sudo, polkit and the lock screen on the
//! owner's phone, run by PAM's `pam_exec` as `auth sufficient`. Installed
//! to `/usr/lib/pocketdesk/` by the packages; see `friday_mobile_server::pam_ask`.

#[cfg(target_os = "linux")]
fn main() {
    std::process::exit(friday_mobile_server::pam_ask::main())
}

#[cfg(not(target_os = "linux"))]
fn main() {
    eprintln!("friday-mobile-pam-ask is for Linux's PAM.");
    std::process::exit(2)
}
