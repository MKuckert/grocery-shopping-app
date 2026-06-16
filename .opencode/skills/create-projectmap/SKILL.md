---
name: create-projectmap
description: Used for a high-level "cognitive map" analysis of a the project. It is intended for scenarios where the user wants to understand what the project does, how modules are divided, how key functions and workflows connect, where the risk areas lie, and where to start when taking over the project. It avoids line-by-line file explanations or the generation of formal specification documents. The output language defaults to the language of the current conversation.
---

# Project Map

This skill transforms a code project into a "project map" that allows a human to quickly build a mental model. The goal is to explain the project's overall purpose, module division, core business loops, key workflows, high-risk boundaries, and the path for taking over the project, rather than simply recounting code file by file.

## Usage Scope

Use this skill when:

- The user asks to "analyze the entire project," "explain what the project does," "map out the modules," "provide a handover guide," "outline key workflows," "show the architectural overview," or "describe the project backbone."
- The user wants a human-readable explanation and does not want to get bogged down in the details of individual files.
- The user needs to build a holistic understanding of a large repository before deciding which module to investigate further.
- The existing `PROJECT_MAP.md` is missing, outdated, or insufficient for understanding the current state of the project.

## Core Principles

- Explain the project's purpose before the code structure.
- Explain business or product concepts before providing file references.
- Explain module relationships and the main workflow before deciding whether to dive into specific details.
- Use file paths only for navigation and as evidence, not as the main body of the explanation.
- Default the output language to english.
- Prioritize natural language; avoid simply listing class names, method names, and line numbers.
- Do not exhaustively list every directory, class, or method for the sake of completeness.
- Do not pass off a "directory structure list" as a true understanding of the project.
- Do not default to a specific delivery format; unless the user has explicitly specified the output style, ask them to choose first.

## Structured Deep Report

Generates a Markdown file `PROJECT_MAP.md` — broken down by overview, modules, processes, risks, and file paths.

Direct Execution Rules:

## Analysis Process

1. Read project rules and entry-point materials first:

- Prioritize `AGENTS.md`, `README*`, `docs/`, project configurations, and package management files.
- If the project has clear architecture or module documentation, skim the index first, but do not let outdated documentation constrain your analysis.

2. Establish entry points and the execution backbone:

- Identify application entry points, lifecycles, routing/state machines/main loops, and dependency injection or module registration points.
- First, understand "how the project starts" and "how the core business loop executes."

3. Create a module map:

- Identify modules based on directory structure, naming conventions, registration points, messaging/events, configurations, and data models.
- Describe modules in terms of "what they are responsible for, why they are important, what they interact with, and their key workflows."

4. Identify key workflows:

- Select workflows that best represent the project's business logic.
- Describe the starting point, process, outcome, and risks of each workflow in plain language; include minimal code anchors where necessary.

5. Highlight risk areas and the handover sequence:

- Mark infrastructure, highly reusable modules, areas involving state consistency, external dependencies, and paths that are difficult to roll back.
- Provide recommendations for a new developer or AI taking over the project: which concepts and files to review first versus later details.

## Output Structure

Use the following structure by default, adjusting it based on the project's size:

```md
# Project Map

## 1. One-Sentence Overview

Explain the main problem the project solves in 1–3 sentences.

## 2. Main Execution Flow

Explain, in plain language, the general operation of the project from the entry point to the completion of the core business loop.

## 3. Module Map

### <Module Name>

- Responsibilities:
- Importance:
- Interactions:
- Key Processes:
- Code Anchors:

## 4. Key Processes

### <Process Name>

Explain the process's starting point, key stages, outcomes, and risks in plain language; provide specific file anchors where necessary.

## 5. High-Risk Areas

Identify areas requiring caution during modification and explain the associated risks.
```

## Depth Control

- When analyzing a large-scale project for the first time, prioritize a layered map over attempting to cover every detail at once.
- For large codebases, focus on capturing 60–80% of the high-level conceptual value first, then list topics for deeper exploration later.
- Establish a directory plan before analyzing and writing in batches; do not simply stitch together raw sub-agent outputs into the final document.
- If a conflict arises between documentation and code, explicitly highlight the discrepancy; do not ignore the reality of the code just to preserve outdated documentation.
