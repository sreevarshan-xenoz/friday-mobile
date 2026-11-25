//! "Ask FRIDAY" plugin: receives text or a link shared from a phone, asks
//! FRIDAY via `friday-ctl goal --json <request>`, and returns its answer to the phone.
//! Under the Ask FRIDAY permission (`kdeconnect.pocketdesk.friday_ask`).
//! A phone without it hears `not_allowed` at once.
//! Never logs text, question, or answer (lengths only).

use dashmap::DashSet;
use friday_mobile_core::error::Result;
use friday_mobile_core::protocol::friday_ask::{
    FRIDAY_ANSWER, FRIDAY_ASK, answer_error_body, answer_ok_body, ask_of, request_text,
};
use friday_mobile_core::protocol::kdeconnect::{DeviceSender, NetworkPacket, Plugin};
use std::path::{Path, PathBuf};
use std::sync::LazyLock;
use std::time::Duration;

/// How long to wait before killing `friday-ctl`: 130 s.
const TIMEOUT: Duration = Duration::from_secs(130);

/// Active ask requests by device id, allowing only one ask at a time per phone.
static ACTIVE_ASKS: LazyLock<DashSet<String>> = LazyLock::new(Default::default);

/// RAII guard ensuring a phone is removed from `ACTIVE_ASKS` when its turn completes.
struct AskGuard(String);

impl Drop for AskGuard {
    fn drop(&mut self) {
        ACTIVE_ASKS.remove(&self.0);
    }
}

/// Look for `friday-ctl` (or `friday-ctl.exe` on Windows) on `PATH`.
pub fn find_friday_ctl() -> Option<PathBuf> {
    let binary = if cfg!(windows) {
        "friday-ctl.exe"
    } else {
        "friday-ctl"
    };
    let path = std::env::var_os("PATH")?;
    for dir in std::env::split_paths(&path) {
        let candidate = dir.join(binary);
        if candidate.is_file() {
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                if candidate
                    .metadata()
                    .is_ok_and(|meta| meta.permissions().mode() & 0o111 != 0)
                {
                    return Some(candidate);
                }
            }
            #[cfg(not(unix))]
            {
                return Some(candidate);
            }
        }
    }
    None
}

/// Run `friday-ctl goal --json --new-conversation <request>` (args, never a
/// shell). A new conversation each time, so a phone ask neither sees nor
/// joins what was asked at the desk.
pub async fn run_goal(
    binary: &Path,
    request: &str,
) -> std::result::Result<(Option<i32>, Vec<u8>, Vec<u8>), &'static str> {
    let mut cmd = tokio::process::Command::new(binary);
    cmd.arg("goal")
        .arg("--json")
        .arg("--new-conversation")
        .arg(request)
        .env("FRIDAY_CTL_WAIT_SECS", "120")
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .kill_on_drop(true);

    #[cfg(windows)]
    {
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }

    let child = cmd.spawn().map_err(|e| {
        tracing::warn!("Could not start friday-ctl: {e}");
        "failed"
    })?;

    match tokio::time::timeout(TIMEOUT, child.wait_with_output()).await {
        Ok(Ok(output)) => {
            let code = output.status.code();
            Ok((code, output.stdout, output.stderr))
        }
        Ok(Err(e)) => {
            tracing::warn!("Failed waiting for friday-ctl: {e}");
            Err("failed")
        }
        Err(_) => {
            // Timed out after 130 s; child killed on drop
            Err("timed_out")
        }
    }
}

/// Parse the output of `friday-ctl goal --json`:
/// exit 0 -> {answer: result};
/// exit 3 -> needs_confirmation;
/// exit 4 -> timed_out;
/// exit 5 -> not_running;
/// else failed (send FRIDAY's `error` text along as `detail`, max 300 chars).
pub fn parse_friday_output(
    exit_code: Option<i32>,
    stdout: &[u8],
    _stderr: &[u8],
) -> serde_json::Value {
    // The one JSON line `--json` prints (specs/ask-json.md in FRIDAY).
    let reply = String::from_utf8_lossy(stdout)
        .lines()
        .find_map(|l| serde_json::from_str::<serde_json::Value>(l.trim()).ok());
    let field = |k: &str| {
        reply
            .as_ref()
            .and_then(|v| v.get(k))
            .and_then(|v| v.as_str())
            .map(str::to_string)
    };
    match exit_code {
        Some(0) => match field("result") {
            Some(answer) => answer_ok_body(&answer),
            None => answer_error_body("failed", None),
        },
        Some(3) => answer_error_body("needs_confirmation", None),
        Some(4) => answer_error_body("timed_out", None),
        Some(5) => answer_error_body("not_running", None),
        // FRIDAY's own words for why; never its raw output.
        _ => answer_error_body("failed", field("error").as_deref()),
    }
}

/// A phone without the Ask FRIDAY permission hears `not_allowed` at once.
pub async fn answer_denied(packet: &NetworkPacket, sender: &dyn DeviceSender) {
    if packet.packet_type == FRIDAY_ASK {
        let _ = sender
            .send_packet(
                &NetworkPacket::new(FRIDAY_ANSWER)
                    .with_body(answer_error_body("not_allowed", None)),
            )
            .await;
    }
}

/// The plugin asking FRIDAY on this computer for the phone.
pub struct FridayAskPlugin;

impl FridayAskPlugin {
    pub fn new() -> Self {
        Self
    }
}

impl Default for FridayAskPlugin {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait::async_trait]
impl Plugin for FridayAskPlugin {
    fn name(&self) -> &'static str {
        "friday_ask"
    }

    fn incoming_capabilities(&self) -> &'static [&'static str] {
        &[FRIDAY_ASK]
    }

    fn outgoing_capabilities(&self) -> &'static [&'static str] {
        &[FRIDAY_ANSWER]
    }

    async fn handle_packet(&self, packet: &NetworkPacket, sender: &dyn DeviceSender) -> Result<()> {
        if packet.packet_type != FRIDAY_ASK {
            return Ok(());
        }

        let device_id = sender.device_id().to_string();

        // One ask at a time per phone
        if !ACTIVE_ASKS.insert(device_id.clone()) {
            tracing::info!(device = %device_id, "Another ask is still running for this phone");
            let reply = NetworkPacket::new(FRIDAY_ANSWER).with_body(answer_error_body(
                "failed",
                Some("Still answering the last one"),
            ));
            sender.send_packet(&reply).await?;
            return Ok(());
        }
        let _guard = AskGuard(device_id.clone());

        // Check text
        let Some((text, question)) = ask_of(&packet.body) else {
            let reply =
                NetworkPacket::new(FRIDAY_ANSWER).with_body(answer_error_body("empty", None));
            sender.send_packet(&reply).await?;
            return Ok(());
        };

        // Find binary
        let Some(ctl_path) = find_friday_ctl() else {
            tracing::info!(device = %device_id, "friday-ctl is not installed");
            let reply = NetworkPacket::new(FRIDAY_ANSWER)
                .with_body(answer_error_body("not_installed", None));
            sender.send_packet(&reply).await?;
            return Ok(());
        };

        // Format request
        let request = request_text(&text, question.as_deref());

        // Never log text, question, or answer (lengths only)
        tracing::info!(
            device = %device_id,
            text_len = text.len(),
            question_len = question.as_ref().map(|q| q.len()).unwrap_or(0),
            "Asking FRIDAY"
        );

        let body = match run_goal(&ctl_path, &request).await {
            Ok((code, stdout, stderr)) => parse_friday_output(code, &stdout, &stderr),
            Err("timed_out") => answer_error_body("timed_out", None),
            Err(reason) => answer_error_body(reason, None),
        };

        let is_ok = body.get("answer").is_some();
        let answer_len = body
            .get("answer")
            .and_then(|v| v.as_str())
            .map(|s| s.len())
            .unwrap_or(0);
        tracing::info!(
            device = %device_id,
            success = is_ok,
            answer_len = answer_len,
            "FRIDAY replied"
        );

        sender
            .send_packet(&NetworkPacket::new(FRIDAY_ANSWER).with_body(body))
            .await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use tokio::sync::Mutex;

    struct MockSender {
        device_id: String,
        sent: Arc<Mutex<Vec<NetworkPacket>>>,
    }

    #[async_trait::async_trait]
    impl DeviceSender for MockSender {
        fn device_id(&self) -> &str {
            &self.device_id
        }

        fn connection_id(&self) -> &str {
            "test-conn"
        }

        async fn send_packet(&self, packet: &NetworkPacket) -> Result<()> {
            self.sent.lock().await.push(packet.clone());
            Ok(())
        }
    }

    #[test]
    fn parse_output_mapping() {
        // Exit 0 with JSON result
        let out_0 = parse_friday_output(Some(0), br#"{"result": "Stockholm"}"#, b"");
        assert_eq!(out_0, serde_json::json!({ "answer": "Stockholm" }));

        // Exit 3 -> needs_confirmation
        let out_3 = parse_friday_output(Some(3), b"", b"");
        assert_eq!(out_3, serde_json::json!({ "error": "needs_confirmation" }));

        // Exit 4 -> timed_out
        let out_4 = parse_friday_output(Some(4), b"", b"");
        assert_eq!(out_4, serde_json::json!({ "error": "timed_out" }));

        // Exit 5 -> not_running
        let out_5 = parse_friday_output(Some(5), b"", b"");
        assert_eq!(out_5, serde_json::json!({ "error": "not_running" }));

        // Other exit with JSON error
        let out_err_json = parse_friday_output(Some(1), br#"{"error": "API quota exceeded"}"#, b"");
        assert_eq!(
            out_err_json,
            serde_json::json!({ "error": "failed", "detail": "API quota exceeded" })
        );

        // stderr is for people at the computer: never sent to the phone.
        let out_err_stderr =
            parse_friday_output(Some(2), b"", b"Network error connecting to daemon");
        assert_eq!(out_err_stderr, serde_json::json!({ "error": "failed" }));
        // A completed ask with no result is not an answer.
        let out_no_result = parse_friday_output(Some(0), b"not json", b"");
        assert_eq!(out_no_result, serde_json::json!({ "error": "failed" }));
    }

    #[tokio::test]
    async fn permission_denied_answers_immediately() {
        let sent = Arc::new(Mutex::new(Vec::new()));
        let sender = MockSender {
            device_id: "phone-test".to_string(),
            sent: sent.clone(),
        };

        let packet = NetworkPacket::new(FRIDAY_ASK);
        answer_denied(&packet, &sender).await;

        let sent_packets = sent.lock().await;
        assert_eq!(sent_packets.len(), 1);
        assert_eq!(sent_packets[0].packet_type, FRIDAY_ANSWER);
        assert_eq!(
            sent_packets[0].body,
            serde_json::json!({ "error": "not_allowed" })
        );
    }

    #[tokio::test]
    async fn one_at_a_time_rule() {
        let plugin = FridayAskPlugin::new();
        let sent1 = Arc::new(Mutex::new(Vec::new()));
        let sender1 = MockSender {
            device_id: "phone-busy".to_string(),
            sent: sent1.clone(),
        };

        // Manually hold active ask for "phone-busy"
        ACTIVE_ASKS.insert("phone-busy".to_string());

        let packet =
            NetworkPacket::new(FRIDAY_ASK).with_body(serde_json::json!({ "text": "hello" }));
        plugin.handle_packet(&packet, &sender1).await.unwrap();

        let packets = sent1.lock().await;
        assert_eq!(packets.len(), 1);
        assert_eq!(packets[0].packet_type, FRIDAY_ANSWER);
        assert_eq!(
            packets[0].body,
            serde_json::json!({ "error": "failed", "detail": "Still answering the last one" })
        );

        ACTIVE_ASKS.remove("phone-busy");
    }

    #[cfg(unix)]
    #[tokio::test]
    async fn fake_friday_ctl_exit_code_mapping() {
        use std::os::unix::fs::PermissionsExt;

        let temp_dir = std::env::temp_dir().join(format!("fake-ctl-test-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&temp_dir);
        std::fs::create_dir_all(&temp_dir).unwrap();

        let script_path = temp_dir.join("friday-ctl");
        // The script returns different exits depending on request text:
        let script_content = r#"#!/bin/sh
if echo "$*" | grep -q "code0"; then
    echo '{"result": "Success answer"}'
    exit 0
elif echo "$*" | grep -q "code3"; then
    exit 3
elif echo "$*" | grep -q "code4"; then
    exit 4
elif echo "$*" | grep -q "code5"; then
    exit 5
else
    echo '{"error": "Custom failure"}'
    exit 1
fi
"#;
        std::fs::write(&script_path, script_content).unwrap();
        let mut perms = std::fs::metadata(&script_path).unwrap().permissions();
        perms.set_mode(0o755);
        std::fs::set_permissions(&script_path, perms).unwrap();

        // Prepend temp_dir to PATH
        let original_path = std::env::var_os("PATH").unwrap();
        let mut new_path = temp_dir.as_os_str().to_os_string();
        new_path.push(":");
        new_path.push(&original_path);
        unsafe {
            std::env::set_var("PATH", &new_path);
        }

        assert_eq!(find_friday_ctl(), Some(script_path.clone()));

        // Test exit 0
        let (code, stdout, stderr) = run_goal(&script_path, "code0").await.unwrap();
        assert_eq!(code, Some(0));
        assert_eq!(
            parse_friday_output(code, &stdout, &stderr),
            serde_json::json!({ "answer": "Success answer" })
        );

        // Test exit 3
        let (code, stdout, stderr) = run_goal(&script_path, "code3").await.unwrap();
        assert_eq!(code, Some(3));
        assert_eq!(
            parse_friday_output(code, &stdout, &stderr),
            serde_json::json!({ "error": "needs_confirmation" })
        );

        // Test exit 4
        let (code, stdout, stderr) = run_goal(&script_path, "code4").await.unwrap();
        assert_eq!(code, Some(4));
        assert_eq!(
            parse_friday_output(code, &stdout, &stderr),
            serde_json::json!({ "error": "timed_out" })
        );

        // Test exit 5
        let (code, stdout, stderr) = run_goal(&script_path, "code5").await.unwrap();
        assert_eq!(code, Some(5));
        assert_eq!(
            parse_friday_output(code, &stdout, &stderr),
            serde_json::json!({ "error": "not_running" })
        );

        // Test exit 1 with error
        let (code, stdout, stderr) = run_goal(&script_path, "code1").await.unwrap();
        assert_eq!(code, Some(1));
        assert_eq!(
            parse_friday_output(code, &stdout, &stderr),
            serde_json::json!({ "error": "failed", "detail": "Custom failure" })
        );

        // Restore PATH
        unsafe {
            std::env::set_var("PATH", original_path);
        }
        let _ = std::fs::remove_dir_all(&temp_dir);
    }
}
