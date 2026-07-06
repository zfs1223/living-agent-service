---
allowed-tools: Bash(gh issue view:*), Bash(gh search:*), Bash(gh issue list:*), Bash(gh pr comment:*), Bash(gh pr diff:*), Bash(gh pr view:*), Bash(gh pr list:*)
description: Code review a pull request
---

Provide a code review for the given pull request.

**Agent assumptions (applies to all agents and subagents):**
- All tools are functional and will work without error. Do not test tools or make exploratory calls.
- Only call a tool if it is required to complete the task. Every tool call should have a clear purpose.

To do this, follow these steps precisely:

1. Launch a haiku agent to check if any of the following are true:
   - The pull request is closed
   - The pull request is a draft
   - The pull request does not need code review
   - Claude has already commented on this PR

   If any condition is true, stop and do not proceed.

2. Launch a sonnet agent to view the pull request and return a summary of the changes.

3. Launch 4 agents in parallel to independently review the changes:
   - Agents 1 + 2: CLAUDE.md compliance sonnet agents
   - Agent 3: Opus bug agent (scan for obvious bugs in the diff)
   - Agent 4: Opus bug agent (look for problems in introduced code)

4. For each issue found, launch parallel subagents to validate the issue.

5. Filter out unvalidated issues.

6. Output a summary of the review findings.

7. If `--comment` argument IS provided, post inline comments for each validated issue.
