use regex::Regex;
use serde::Deserialize;
use std::collections::HashSet;

#[derive(Debug, Deserialize)]
pub struct CompactMessage {
    pub role: Option<String>,
    pub content: Option<String>,
}

pub fn summarize_messages_json(messages_json: &str, max_lines: usize) -> String {
    let parsed: Vec<CompactMessage> = serde_json::from_str(messages_json).unwrap_or_default();
    if parsed.is_empty() {
        return "<summary>no parsable messages</summary>".to_string();
    }

    let mut user_count = 0;
    let mut assistant_count = 0;
    let mut tool_count = 0;

    let mut recent_user: Vec<String> = Vec::new();
    let mut key_files: HashSet<String> = HashSet::new();
    let mut pending: Vec<String> = Vec::new();

    let file_re = Regex::new(r"([\\w./-]+\\.(?:java|rs|py|ts|js|json|yml|yaml|md|toml|xml|sql|sh))").unwrap();
    let pending_re = Regex::new(r"(?i)(todo|next|pending|remaining|still need|follow.?up|继续|待办|下一步)").unwrap();

    for m in &parsed {
        let role = m.role.as_deref().unwrap_or("");
        let content = m.content.as_deref().unwrap_or("").trim();

        match role {
            "user" => {
                user_count += 1;
                if !content.is_empty() {
                    recent_user.push(truncate(content, 180));
                }
            }
            "assistant" => assistant_count += 1,
            "tool" => tool_count += 1,
            _ => {}
        }

        if !content.is_empty() {
            for cap in file_re.captures_iter(content) {
                if let Some(m0) = cap.get(1) {
                    key_files.insert(m0.as_str().to_string());
                }
            }
            if pending_re.is_match(content) {
                pending.push(truncate(content, 120));
            }
        }
    }

    let mut out = String::new();
    out.push_str("<summary>\n");
    out.push_str(&format!("- messages={} (user={}, assistant={}, tool={})\n", parsed.len(), user_count, assistant_count, tool_count));

    if !recent_user.is_empty() {
        out.push_str("- recent user requests:\n");
        let start = recent_user.len().saturating_sub(max_lines.min(5));
        for item in recent_user.iter().skip(start) {
            out.push_str(&format!("  - {}\n", item));
        }
    }

    if !pending.is_empty() {
        out.push_str("- pending hints:\n");
        for item in pending.iter().take(5) {
            out.push_str(&format!("  - {}\n", item));
        }
    }

    if !key_files.is_empty() {
        let mut files: Vec<String> = key_files.into_iter().collect();
        files.sort();
        out.push_str("- key files: ");
        out.push_str(&files.into_iter().take(10).collect::<Vec<_>>().join(", "));
        out.push('\n');
    }

    out.push_str("</summary>");
    out
}

pub fn estimate_token_count_text(text: &str) -> i32 {
    if text.is_empty() {
        return 0;
    }
    (text.chars().count() / 4) as i32
}

fn truncate(s: &str, n: usize) -> String {
    if s.chars().count() <= n {
        return s.to_string();
    }
    s.chars().take(n).collect::<String>() + "..."
}
