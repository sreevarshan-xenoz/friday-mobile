//! A clipboard push, as a packet line (`server/src/clipboard_push.rs`).
#![no_main]

use libfuzzer_sys::fuzz_target;
use friday_mobile_core::protocol::kdeconnect::NetworkPacket;

fuzz_target!(|data: &[u8]| {
    let Ok(line) = std::str::from_utf8(data) else {
        return;
    };
    if let Ok(packet) = NetworkPacket::from_wire(line) {
        friday_mobile_server::fuzzing::clipboard_body(&packet.body);
    }
});
