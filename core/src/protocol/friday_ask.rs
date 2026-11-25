//! "Ask FRIDAY": sending text or a link from the phone to FRIDAY on the
//! computer and getting its answer back.
//! Under the Ask FRIDAY permission, off until the owner turns it on for a phone.

/// Phone → computer: `{text, question?}`.
pub const FRIDAY_ASK: &str = "kdeconnect.pocketdesk.friday_ask";

/// Computer → phone: `{answer}` or `{error: reason, detail?}`.
/// Reasons: `not_allowed`, `empty`, `not_installed`, `not_running`,
/// `needs_confirmation`, `failed`, `timed_out`.
pub const FRIDAY_ANSWER: &str = "kdeconnect.pocketdesk.friday_answer";

/// The most characters the shared text keeps.
pub const MAX_TEXT: usize = crate::protocol::note::MAX_NOTE;

/// The most characters a question keeps.
pub const MAX_QUESTION: usize = 500;

/// The most bytes an answer keeps: 16 KiB.
pub const MAX_ANSWER_BYTES: usize = 16 * 1024;

/// The most characters an error detail keeps.
pub const MAX_DETAIL: usize = 300;

/// Default prompt when no question is given.
pub const DEFAULT_QUESTION: &str = "What is this about? Answer briefly.";

/// Whether `c` turns the text around it: an embedding, override or
/// isolate, or a line or paragraph separator.
fn is_bidi_mark(c: char) -> bool {
    matches!(c, '\u{202A}'..='\u{202E}' | '\u{2066}'..='\u{2069}' | '\u{2028}' | '\u{2029}')
}

/// Clean text dropping control chars (except line ends and tabs) and bidi marks,
/// trimming blank lines at ends, capped at `limit` chars (cut with `…`).
fn clean_with_limit(text: &str, limit: usize) -> Option<String> {
    let kept: String = text
        .replace("\r\n", "\n")
        .replace('\r', "\n")
        .chars()
        .filter(|c| (!c.is_control() || *c == '\n' || *c == '\t') && !is_bidi_mark(*c))
        .collect();
    let trimmed = kept.trim();
    if trimmed.is_empty() {
        return None;
    }
    if trimmed.chars().count() <= limit {
        return Some(trimmed.to_string());
    }
    let cut: String = trimmed.chars().take(limit - 1).collect();
    Some(format!("{}…", cut.trim_end()))
}

/// Clean the context text using the note cleaning rules (max [`MAX_TEXT`] characters).
pub fn clean_text(text: &str) -> Option<String> {
    crate::protocol::note::clean_note(text)
}

/// Clean an optional question (max [`MAX_QUESTION`] characters).
pub fn clean_question(question: &str) -> Option<String> {
    clean_with_limit(question, MAX_QUESTION)
}

/// Clean detail message for error responses (max [`MAX_DETAIL`] characters).
pub fn clean_detail(detail: &str) -> String {
    let trimmed = detail.trim();
    if trimmed.chars().count() <= MAX_DETAIL {
        trimmed.to_string()
    } else {
        let cut: String = trimmed.chars().take(MAX_DETAIL - 1).collect();
        format!("{}…", cut.trim_end())
    }
}

/// Format the request text for FRIDAY.
///
/// With question:
/// ```text
/// <question>
///
/// Use this text as context:
/// """
/// <text>
/// """
/// ```
///
/// Without question:
/// ```text
/// What is this about? Answer briefly.
///
/// Use this text as context:
/// """
/// <text>
/// """
/// ```
pub fn request_text(text: &str, question: Option<&str>) -> String {
    let q = match question {
        Some(q) if !q.trim().is_empty() => q.trim(),
        _ => DEFAULT_QUESTION,
    };
    format!("{q}\n\nUse this text as context:\n\"\"\"\n{text}\n\"\"\"")
}

/// The packet body for `kdeconnect.pocketdesk.friday_ask`.
pub fn ask_body(text: &str, question: Option<&str>) -> serde_json::Value {
    let mut map = serde_json::Map::new();
    map.insert(
        "text".to_string(),
        serde_json::Value::String(text.to_string()),
    );
    if let Some(q) = question {
        map.insert(
            "question".to_string(),
            serde_json::Value::String(q.to_string()),
        );
    }
    serde_json::Value::Object(map)
}

/// Parse an incoming `kdeconnect.pocketdesk.friday_ask` packet body.
/// Returns cleaned `(text, question)`.
pub fn ask_of(body: &serde_json::Value) -> Option<(String, Option<String>)> {
    let raw_text = body.get("text")?.as_str()?;
    let text = clean_text(raw_text)?;
    let question = body
        .get("question")
        .and_then(|v| v.as_str())
        .and_then(clean_question);
    Some((text, question))
}

/// Cap an answer to [`MAX_ANSWER_BYTES`] (16 KiB) on a char boundary.
pub fn cap_answer(answer: &str) -> &str {
    if answer.len() <= MAX_ANSWER_BYTES {
        answer
    } else {
        let mut end = MAX_ANSWER_BYTES;
        while end > 0 && !answer.is_char_boundary(end) {
            end -= 1;
        }
        &answer[..end]
    }
}

/// The packet body for a successful FRIDAY answer.
pub fn answer_ok_body(answer: &str) -> serde_json::Value {
    let capped = cap_answer(answer);
    serde_json::json!({ "answer": capped })
}

/// The packet body for a FRIDAY error answer.
pub fn answer_error_body(reason: &str, detail: Option<&str>) -> serde_json::Value {
    let mut map = serde_json::Map::new();
    map.insert(
        "error".to_string(),
        serde_json::Value::String(reason.to_string()),
    );
    if let Some(d) = detail {
        let cleaned = clean_detail(d);
        if !cleaned.is_empty() {
            map.insert("detail".to_string(), serde_json::Value::String(cleaned));
        }
    }
    serde_json::Value::Object(map)
}

/// Parse an incoming `kdeconnect.pocketdesk.friday_answer` packet body.
/// Returns `Ok(answer)` on success, or `Err(reason_or_detail)` on failure.
pub fn answer_of(body: &serde_json::Value) -> Result<String, String> {
    if let Some(answer) = body.get("answer").and_then(|v| v.as_str()) {
        return Ok(answer.to_string());
    }
    if let Some(reason) = body.get("error").and_then(|v| v.as_str()) {
        let detail = body.get("detail").and_then(|v| v.as_str());
        if let (true, Some(detail)) = (reason == "failed", detail.filter(|d| !d.trim().is_empty()))
        {
            return Err(detail.to_string());
        }
        return Err(reason.to_string());
    }
    Err("failed".to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn text_and_question_cleaning() {
        assert_eq!(
            clean_text("\r\n  https://example.com  \r\n").as_deref(),
            Some("https://example.com")
        );
        assert_eq!(clean_text("   \n\t  "), None);

        assert_eq!(
            clean_question("  What is this about?  ").as_deref(),
            Some("What is this about?")
        );
        assert_eq!(clean_question(" \n\t "), None);

        let long_question = "q".repeat(MAX_QUESTION + 50);
        let cut = clean_question(&long_question).unwrap();
        assert_eq!(cut.chars().count(), MAX_QUESTION);
        assert!(cut.ends_with('…'));
    }

    #[test]
    fn request_text_formatting() {
        let text = "Here is some context";
        let with_q = request_text(text, Some("Summarize this"));
        assert_eq!(
            with_q,
            "Summarize this\n\nUse this text as context:\n\"\"\"\nHere is some context\n\"\"\""
        );

        let without_q = request_text(text, None);
        assert_eq!(
            without_q,
            "What is this about? Answer briefly.\n\nUse this text as context:\n\"\"\"\nHere is some context\n\"\"\""
        );

        let empty_q = request_text(text, Some("   "));
        assert_eq!(
            empty_q,
            "What is this about? Answer briefly.\n\nUse this text as context:\n\"\"\"\nHere is some context\n\"\"\""
        );
    }

    #[test]
    fn ask_body_and_parsing() {
        let body = ask_body("context text", Some("my question"));
        let parsed = ask_of(&body);
        assert_eq!(
            parsed,
            Some(("context text".to_string(), Some("my question".to_string())))
        );

        let body_no_q = ask_body("context text", None);
        let parsed_no_q = ask_of(&body_no_q);
        assert_eq!(parsed_no_q, Some(("context text".to_string(), None)));

        assert_eq!(ask_of(&json!({"text": "   "})), None);
        assert_eq!(ask_of(&json!({})), None);
    }

    #[test]
    fn answer_body_and_parsing() {
        let ok = answer_ok_body("42 is the answer");
        assert_eq!(answer_of(&ok), Ok("42 is the answer".to_string()));

        let err_reason = answer_error_body("not_allowed", None);
        assert_eq!(answer_of(&err_reason), Err("not_allowed".to_string()));

        let err_failed = answer_error_body("failed", Some("Still answering the last one"));
        assert_eq!(
            answer_of(&err_failed),
            Err("Still answering the last one".to_string())
        );

        let err_without_detail = answer_error_body("failed", None);
        assert_eq!(answer_of(&err_without_detail), Err("failed".to_string()));
    }

    #[test]
    fn answer_capping_at_16_kib() {
        let long_answer = "a".repeat(MAX_ANSWER_BYTES + 500);
        let capped = cap_answer(&long_answer);
        assert_eq!(capped.len(), MAX_ANSWER_BYTES);
    }

    #[test]
    fn detail_cleaning() {
        assert_eq!(clean_detail("  err  "), "err");
        let long_detail = "d".repeat(MAX_DETAIL + 50);
        let cut = clean_detail(&long_detail);
        assert_eq!(cut.chars().count(), MAX_DETAIL);
        assert!(cut.ends_with('…'));
    }
}
