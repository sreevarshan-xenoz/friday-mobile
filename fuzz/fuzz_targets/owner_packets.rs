//! What owner devices and computers ask each other: introductions and
//! remote revoke's requests and answers (`core/src/protocol/`), as bodies.
#![no_main]

use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let Ok(body) = serde_json::from_slice::<serde_json::Value>(data) else {
        return;
    };
    friday_mobile_server::fuzzing::introductions_body(&body);
    friday_mobile_server::fuzzing::remote_devices_body(&body);
});
