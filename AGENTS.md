# AI Agent Engineering Policy

This document defines how AI agents must work in this repository.

The agent must follow a deliberate engineering workflow. It must not jump directly from a request to code changes unless explicitly instructed by an approved plan.

## Core Principles

- Preserve repository integrity.
- Prefer verified facts over assumptions.
- Keep changes scoped to the approved task.
- Do not modify unrelated files.
- Do not invent business rules.
- Make uncertainty explicit.
- Treat existing user or team changes as authoritative unless told otherwise.

## Required Workflow

### 1. Understand the Request

The agent must restate the task in practical terms before changing code.

It must identify:

- the requested outcome;
- the expected scope;
- files or systems likely involved;
- any missing information or ambiguity.

If the request is unclear, the agent must ask for clarification before planning implementation.

### 2. Perform Impact Analysis

The agent must analyze the possible effects of the requested change.

Impact analysis should cover:

- affected modules;
- API or data contract changes;
- database or persistence effects;
- frontend/backend interaction effects;
- security and authorization implications;
- build, test, and deployment implications;
- risk of regressions.

The agent must explicitly state when an area appears unaffected.

### 3. Review Architecture

Before proposing implementation, the agent must review the current architecture relevant to the task.

This includes:

- existing patterns;
- ownership boundaries;
- framework conventions;
- relevant services, components, models, configuration, and build files;
- similar existing implementations.

The agent must align with existing architecture unless it explains why a different approach is necessary.

### 4. Produce an Implementation Plan

The agent must produce a concise implementation plan before making source changes.

The plan must include:

- intended file changes;
- sequence of work;
- validation steps;
- documentation updates;
- known risks or open questions.

The plan must be specific enough for review.

### 5. Wait for Approval

The agent must wait for explicit approval before implementation.

Approval is required before:

- editing application source code;
- changing configuration;
- modifying dependencies;
- altering build scripts;
- changing generated or migration files;
- deleting or moving files.

If the approved scope changes during implementation, the agent must stop and request renewed approval.

### 6. Implement

After approval, the agent may implement the plan.

Implementation rules:

- keep changes minimal and focused;
- follow existing coding conventions;
- avoid unrelated refactoring;
- avoid speculative abstractions;
- preserve public contracts unless contract changes were approved;
- keep comments useful and concise.

If implementation reveals new risks or architectural conflicts, the agent must pause and report them.

### 7. Run Build

After implementation, the agent must run the appropriate build or verification command for the affected parts of the repository.

If the build cannot be run, the agent must explain:

- which command was not run;
- why it was not run;
- what risk remains.

Build failures must be reported with the relevant error summary and next recommended action.

### 8. Run Regression Analysis

The agent must analyze whether the change could break existing behavior.

Regression analysis should include:

- changed execution paths;
- affected API contracts;
- affected UI flows;
- persistence or migration effects;
- security and permission effects;
- test coverage added or missing.

The agent must distinguish verified results from reasoned analysis.

### 9. Update Documentation

The agent must update documentation when the change affects:

- setup or build instructions;
- architecture;
- APIs;
- operational procedures;
- configuration;
- development workflows;
- AI knowledge, playbooks, checklists, ADRs, RFCs, history, or prompts.

Documentation updates must be factual and scoped to the implemented change.


## Final Response Requirements

At completion, the agent must report:

- what changed;
- what validation was run;
- regression findings;
- documentation updates;
- any remaining risks or unresolved questions.

The final response must be concise, factual, and actionable.
