#!/usr/bin/env python3
"""
Security Reminder Hook for Claude Code
This hook checks for security patterns in file edits and warns about potential vulnerabilities.
"""

import json
import os
import random
import sys
from datetime import datetime

DEBUG_LOG_FILE = "/tmp/security-warnings-log.txt"


def debug_log(message):
    try:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        with open(DEBUG_LOG_FILE, "a") as f:
            f.write(f"[{timestamp}] {message}\n")
    except Exception:
        pass


SECURITY_PATTERNS = [
    {
        "ruleName": "github_actions_workflow",
        "path_check": lambda path: ".github/workflows/" in path
        and (path.endswith(".yml") or path.endswith(".yaml")),
        "reminder": """You are editing a GitHub Actions workflow file. Be aware of these security risks:

1. **Command Injection**: Never use untrusted input directly in run: commands without proper escaping
2. **Use environment variables**: Instead of ${{ github.event.issue.title }}, use env: with proper quoting

Example of UNSAFE pattern: run: echo "${{ github.event.issue.title }}"
Example of SAFE pattern: env: TITLE: ${{ github.event.issue.title }}; run: echo "$TITLE"
""",
    },
    {
        "ruleName": "child_process_exec",
        "substrings": ["child_process.exec", "exec(", "execSync("],
        "reminder": """Security Warning: Using child_process.exec() can lead to command injection vulnerabilities.
Use execFile instead of exec (prevents shell injection).""",
    },
    {
        "ruleName": "eval_injection",
        "substrings": ["eval("],
        "reminder": "Security Warning: eval() executes arbitrary code and is a major security risk. Consider using JSON.parse() or alternative patterns.",
    },
    {
        "ruleName": "react_dangerously_set_html",
        "substrings": ["dangerouslySetInnerHTML"],
        "reminder": "Security Warning: dangerouslySetInnerHTML can lead to XSS vulnerabilities if used with untrusted content. Ensure all content is properly sanitized.",
    },
    {
        "ruleName": "document_write_xss",
        "substrings": ["document.write"],
        "reminder": "Security Warning: document.write() can be exploited for XSS attacks. Use DOM manipulation methods instead.",
    },
    {
        "ruleName": "innerHTML_xss",
        "substrings": [".innerHTML =", ".innerHTML="],
        "reminder": "Security Warning: Setting innerHTML with untrusted content can lead to XSS vulnerabilities. Use textContent or safe DOM methods.",
    },
    {
        "ruleName": "pickle_deserialization",
        "substrings": ["pickle"],
        "reminder": "Security Warning: Using pickle with untrusted content can lead to arbitrary code execution. Consider using JSON instead.",
    },
    {
        "ruleName": "os_system_injection",
        "substrings": ["os.system", "from os import system"],
        "reminder": "Security Warning: os.system should only be used with static arguments, never with user-controlled input.",
    },
]


def get_state_file(session_id):
    return os.path.expanduser(f"~/.claude/security_warnings_state_{session_id}.json")


def cleanup_old_state_files():
    try:
        state_dir = os.path.expanduser("~/.claude")
        if not os.path.exists(state_dir):
            return
        current_time = datetime.now().timestamp()
        thirty_days_ago = current_time - (30 * 24 * 60 * 60)
        for filename in os.listdir(state_dir):
            if filename.startswith("security_warnings_state_") and filename.endswith(".json"):
                file_path = os.path.join(state_dir, filename)
                try:
                    if os.path.getmtime(file_path) < thirty_days_ago:
                        os.remove(file_path)
                except (OSError, IOError):
                    pass
    except Exception:
        pass


def load_state(session_id):
    state_file = get_state_file(session_id)
    if os.path.exists(state_file):
        try:
            with open(state_file, "r") as f:
                return set(json.load(f))
        except (json.JSONDecodeError, IOError):
            return set()
    return set()


def save_state(session_id, shown_warnings):
    state_file = get_state_file(session_id)
    try:
        os.makedirs(os.path.dirname(state_file), exist_ok=True)
        with open(state_file, "w") as f:
            json.dump(list(shown_warnings), f)
    except IOError:
        pass


def check_patterns(file_path, content):
    normalized_path = file_path.lstrip("/")
    for pattern in SECURITY_PATTERNS:
        if "path_check" in pattern and pattern["path_check"](normalized_path):
            return pattern["ruleName"], pattern["reminder"]
        if "substrings" in pattern and content:
            for substring in pattern["substrings"]:
                if substring in content:
                    return pattern["ruleName"], pattern["reminder"]
    return None, None


def extract_content_from_input(tool_name, tool_input):
    if tool_name == "Write":
        return tool_input.get("content", "")
    elif tool_name == "Edit":
        return tool_input.get("new_string", "")
    elif tool_name == "MultiEdit":
        edits = tool_input.get("edits", [])
        if edits:
            return " ".join(edit.get("new_string", "") for edit in edits)
        return ""
    return ""


def main():
    security_reminder_enabled = os.environ.get("ENABLE_SECURITY_REMINDER", "1")
    if security_reminder_enabled == "0":
        sys.exit(0)

    if random.random() < 0.1:
        cleanup_old_state_files()

    try:
        raw_input = sys.stdin.read()
        input_data = json.loads(raw_input)
    except json.JSONDecodeError:
        sys.exit(0)

    session_id = input_data.get("session_id", "default")
    tool_name = input_data.get("tool_name", "")
    tool_input = input_data.get("tool_input", {})

    if tool_name not in ["Edit", "Write", "MultiEdit"]:
        sys.exit(0)

    file_path = tool_input.get("file_path", "")
    if not file_path:
        sys.exit(0)

    content = extract_content_from_input(tool_name, tool_input)
    rule_name, reminder = check_patterns(file_path, content)

    if rule_name and reminder:
        warning_key = f"{file_path}-{rule_name}"
        shown_warnings = load_state(session_id)
        if warning_key not in shown_warnings:
            shown_warnings.add(warning_key)
            save_state(session_id, shown_warnings)
            print(reminder, file=sys.stderr)
            sys.exit(2)

    sys.exit(0)


if __name__ == "__main__":
    main()
