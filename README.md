# Household Inventory & Collaborative Android Shopping App

A local-first, real-time synchronized grocery and inventory tracking application designed for multi-device household collaboration. Built to survive cellular dead zones in supermarket basements and the chaotic reality of simultaneous mutations by multiple family members.

## Core Pillars

- **Local-First, Cloud-Synced**: Powered by **Supabase** and **PowerSync** for zero-latency UI rendering and automatic background replication.
- **Stateless UI Matrix**: Screens are derived dynamically via reactive SQLite queries, eliminating volatile in-memory states and synchronization race conditions.
- **Deterministic Convergence**: Offline conflicts and mathematical decrements resolve predictably across all participating devices without requiring a central coordinator.

## Technical Specifications

- [Database & Backend Architecture](docs/DATABASE.md) — Relational schemas, Postgres check constraints, soft-deletion resurrection triggers, multi-tenant Row Level Security (RLS), and local SQLite indexing.
- [User Interface & State Architecture](docs/UI.md) — Jetpack Compose view architectures, reactive query matrix filters, lifecycle state overlays, and hardware scanner integration boundaries.

## Local Setup

To build and run the app locally, configure the following secrets in `local.properties` (in the project root):

```properties
# Supabase API Configuration
supabase.url=<your-supabase-project-url>
supabase.anon.key=<your-supabase-public-anon-key>

# PowerSync Service Configuration
powersync.url=<your-powersync-service-url>
```

**Note:** `local.properties` is not tracked in version control. Each developer must provide their own configuration values.

## Dependencies

Uses a lot of stuff to test different AI tooling.

Installation on macos:

```
brew bundle
go install github.com/mkuckert/mcp-commands@latest

# stolen from agent-harness project
gh repo clone mkuckert/agent-harness ../agent-harness
../agent-harness/bin/build-docker-mcp.sh
docker mcp feature enable profiles
docker mcp feature disable dynamic-tools
echo "export DOCKER_MCP_IN_CONTAINER=1" >> ~/.bash_profile
docker mcp catalog pull mcp/docker-mcp-catalog:latest
../agent-harness/profiles/build.sh
```

### my agent harness

Uses the agents and commands from [my agent harness](https://github.com/mkuckert/agent-harness).

### opencode

Uses [opencode](https://opencode.ai/) as agent harness. Agents, commands and skills are located in `.opencode`.

### nono

Uses [nono](https://nono.sh/) to encapsulate the agent harness in a OS sandbox.

You probably have to adjust paths in `.nono/profile.json` to your setup.

### manifest

Uses [manifest](https://manifest.build) as a model router. [See my env project](https://github.com/MKuckert/env/tree/main/manifest) for start scripts.

I have it configured with the following routes, accessing models via GitHub Copilot & Anthropic Subscription:
- small: GPT-5-mini
- medium: Claude Haiku 4.5
- complex: Claude Sonnet 4.6
- ultra: Claude Opus 4.6

You can change those models in the agents (`.opencode/agents/*.md`) and remove the `manifest` provider from `opencode.jsonc`.

### docker mcp

Uses [docker mcp](https://github.com/docker/mcp-gateway) to encapsulate websearch and context7 MCPs for the Librarian agent.

Compile and install as described [in my agent harness](https://github.com/mkuckert/agent-harness).

### mcp-commands

Uses [mcp-commands](https://github.com/MKuckert/mcp-commands) to integrate `gradlew` as a MCP tool.

### my backgrounded script

Uses [my backgrounded script](https://github.com/MKuckert/backgrounded) to run the MCPs in the background. Either clone this repo anywhere and add it to your `PATH`, or adjust and run `.nono/hooks/before.sh` and `.nono/hooks/after.sh` to start and stop the MCPs in any other way (e.g. without `backgrounded.sh` and manually).

### Android SDK

Obviously.

## Implementation

The app is implemented by heavily relying on AI. Google Gemini and I wrote docs/UI.md and docs/DATABASE.md, Claude Sonnet drafted the plan and broke it into tasks. Claude Sonnet did the majority of the coding, while I (the human) provided the initial project structure, guidance, and oversight. Claude Opus did code reviews. The AI was responsible for generating the majority of the codebase, including database schemas, UI components, and synchronization logic. Claude Haiku supported as Explorer, Librarian and Committer.

Rough breakdown:

| model             | tokens   |
| ----------------- | -------- |
| claude-sonnet-4.6 | ~12M     |
| claude-opus-4.6   | ~5.5M    |
| claude-haiku-4.5  | ~3.5M    |
| **total**         | **~21M** |
