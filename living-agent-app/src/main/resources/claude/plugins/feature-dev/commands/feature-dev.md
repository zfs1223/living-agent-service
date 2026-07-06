---
description: Guided feature development with codebase understanding and architecture focus
argument-hint: Optional feature description
---

# Feature Development

You are helping a developer implement a new feature. Follow a systematic approach: understand the codebase deeply, identify and ask about all underspecified details, design elegant architectures, then implement.

## Core Principles

- **Ask clarifying questions**: Identify all ambiguities, edge cases, and underspecified behaviors. Ask specific, concrete questions rather than making assumptions. Wait for user answers before proceeding with implementation.
- **Understand before acting**: Read and comprehend existing code patterns first
- **Simple and elegant**: Prioritize readable, maintainable, architecturally sound code

---

## Phase 1: Discovery

**Goal**: Understand what needs to be built

Initial request: $ARGUMENTS

**Actions**:
1. If feature unclear, ask user for clarification
2. Summarize understanding and confirm with user

---

## Phase 2: Codebase Exploration

**Goal**: Understand relevant existing code and patterns

**Actions**:
1. Launch 2-3 code-explorer agents in parallel to trace through the codebase
2. Read all files identified by agents to build deep understanding
3. Present comprehensive summary of findings

---

## Phase 3: Clarifying Questions

**Goal**: Fill in gaps and resolve all ambiguities before designing

**CRITICAL**: DO NOT SKIP. Present all questions to the user and wait for answers.

---

## Phase 4: Architecture Design

**Goal**: Design multiple implementation approaches with different trade-offs

**Actions**:
1. Launch 2-3 code-architect agents in parallel
2. Present approaches with trade-offs comparison and your recommendation
3. Ask user which approach they prefer

---

## Phase 5: Implementation

**Goal**: Build the feature

**DO NOT START WITHOUT USER APPROVAL**

---

## Phase 6: Quality Review

**Goal**: Ensure code quality

**Actions**:
1. Launch 3 code-reviewer agents in parallel
2. Present findings to user
3. Address issues based on user decision

---

## Phase 7: Summary

**Goal**: Document what was accomplished

1. Summarize what was built, key decisions, files modified
