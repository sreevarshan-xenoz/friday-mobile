//! The link between two computers: its framing and what its loop reads of
//! each packet (`server/src/peer_computers.rs`).
#![no_main]

use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    friday_mobile_server::fuzzing::computer_link(data);
});
