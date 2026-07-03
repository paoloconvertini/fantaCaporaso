# Impact Analysis Playbook

Use this playbook before modifying code for any feature request.

The goal is to understand the change, identify affected areas, and expose risks before implementation begins.

## Objectives

- Clarify the requested behavior.
- Identify the technical scope.
- Locate affected systems and ownership boundaries.
- Detect contract, data, security, and operational impacts.
- Surface risks and unknowns early.
- Produce enough context to create a safe implementation plan.

## Files to Inspect

Inspect only files relevant to the requested change.

Common file categories:

- application entry points;
- routing and controller files;
- service or domain logic;
- data models, schemas, entities, and DTOs;
- API clients and generated contracts;
- frontend components, views, state, and forms;
- configuration and environment files;
- dependency manifests;
- build and test configuration;
- existing tests near the affected behavior;
- documentation related to setup, APIs, or workflows.

Record files inspected and why they matter.

## Dependencies

Identify dependencies that may affect or be affected by the change.

Check:

- internal modules or packages;
- shared libraries;
- framework integrations;
- generated clients or schemas;
- third-party services;
- authentication and authorization providers;
- messaging, queueing, or event systems;
- database drivers and migration tools;
- build, test, and deployment tooling.

Note whether dependency changes are required. If none are required, state that explicitly.

## REST Endpoints

Determine whether the request affects HTTP APIs.

Inspect:

- route definitions;
- request and response DTOs;
- validation logic;
- status codes and error handling;
- authentication and authorization annotations or middleware;
- API clients used by frontend or other services;
- tests covering the endpoint contract.

Document any endpoint additions, removals, behavior changes, or contract changes. If no REST endpoint is affected, state that explicitly.

## Frontend Impact

Determine whether the request affects user-facing behavior.

Inspect:

- routes and navigation;
- components and templates;
- forms and validation;
- state management;
- service/API client methods;
- error and loading states;
- permissions-based UI behavior;
- responsive or accessibility-sensitive views;
- frontend tests.

Identify changed user flows and any required UI states. If no frontend behavior is affected, state that explicitly.

## Database Impact

Determine whether the request affects persisted data.

Inspect:

- entities, models, and schemas;
- migrations;
- repositories and query logic;
- transaction boundaries;
- seed data and fixtures;
- indexes and constraints;
- data retention or history tables;
- import/export jobs.

Document whether a migration is required, whether existing data needs backfill or cleanup, and whether rollback is possible. If no database impact exists, state that explicitly.

## WebSocket Impact

Determine whether the request affects real-time behavior.

Inspect:

- socket endpoints;
- event names and payloads;
- connection lifecycle handling;
- authorization for socket connections;
- broadcaster or publisher code;
- frontend socket consumers;
- reconnection and error handling;
- tests or manual verification steps.

Document any event contract changes and affected subscribers. If no WebSocket impact exists, state that explicitly.

## Permissions

Determine whether the request changes access control.

Inspect:

- role or permission checks;
- authentication configuration;
- route guards or middleware;
- frontend permission-based rendering;
- service-level authorization;
- audit or logging requirements;
- tests for allowed and denied access.

Document who can perform the new or changed behavior. If permissions are unchanged, state that explicitly.

## Risks

List implementation and operational risks.

Common risks:

- unclear requirements;
- contract drift between clients and servers;
- incomplete validation;
- partial data updates;
- concurrency or timing issues;
- performance regressions;
- security exposure;
- missing observability;
- migration or rollback complexity;
- untested edge cases.

Each risk should include a mitigation or a reason it is accepted.

## Regression Risks

Identify existing behavior that could break.

Check:

- shared code paths;
- existing API consumers;
- existing UI flows;
- persisted data assumptions;
- background jobs or scheduled tasks;
- real-time event consumers;
- authorization paths;
- build and deployment pipelines.

Classify regression risk as low, medium, or high, and explain the reason.

## Output Format

The impact analysis should produce:

- summary of the request;
- inspected files and findings;
- affected areas;
- unaffected areas;
- risks and mitigations;
- regression risks;
- open questions;
- recommendation for the implementation plan.

Do not begin implementation until the impact analysis has been reviewed and the implementation plan has been approved.
