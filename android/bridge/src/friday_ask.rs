//! "Ask FRIDAY": sending text and an optional question to FRIDAY on the computer.

use std::time::Duration;

use friday_mobile_core::protocol::friday_ask::{
    FRIDAY_ANSWER, FRIDAY_ASK, answer_of, ask_body, clean_question, clean_text,
};
use friday_mobile_core::protocol::kdeconnect::NetworkPacket;

/// How long to wait for FRIDAY's answer: 140 s (to accommodate 130 s execution timeout).
const ANSWER_WITHIN: Duration = Duration::from_secs(140);

/// Construct the packet, or `Err("empty")` if text cleans to empty.
fn ask_packet(text: &str, question: Option<&str>) -> Result<NetworkPacket, String> {
    let text = clean_text(text).ok_or_else(|| "empty".to_string())?;
    let question = question.and_then(clean_question);
    Ok(NetworkPacket::new(FRIDAY_ASK).with_body(ask_body(&text, question.as_deref())))
}

/// Ask FRIDAY on the computer at `address`:`port`. Returns `Ok(answer)` or `Err(reason)`.
pub async fn send_ask(
    address: String,
    port: u16,
    text: String,
    question: Option<String>,
) -> Result<String, String> {
    let packet = ask_packet(&text, question.as_deref())?;
    let answer = crate::api::query_peer(&address, port, &packet, FRIDAY_ANSWER, ANSWER_WITHIN)
        .await
        .map_err(|e| {
            if e.to_lowercase().contains("timeout") || e.to_lowercase().contains("timed out") {
                "timed_out".to_string()
            } else {
                e
            }
        })?;
    answer_of(&answer)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ask_packet_cleans_inputs_and_rejects_empty() {
        let packet = ask_packet("  hello world \n", Some(" what is this? ")).unwrap();
        assert_eq!(packet.packet_type, FRIDAY_ASK);
        assert_eq!(packet.body["text"], "hello world");
        assert_eq!(packet.body["question"], "what is this?");

        assert_eq!(ask_packet(" \t\n ", None).err().as_deref(), Some("empty"));
    }
}
