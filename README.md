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

## MCP commands

Uses [mcp-commands](https://github.com/MKuckert/mcp-commands) to integrate `gradlew` as a MCP tool.

## Implementation

The app is implemented by heavily relying on AI. Google Gemini and I wrote docs/UI.md and docs/DATABASE.md, Claude Sonnet drafted the plan and broke it into tasks. Claude Sonnet did the majority of the coding, while I (the human) provided the initial project structure, guidance, and oversight. Claude Opus did code reviews. The AI was responsible for generating the majority of the codebase, including database schemas, UI components, and synchronization logic. Claude Haiku supported as Explorer, Librarian and Committer.

Rough breakdown:

| model             | tokens   |
| ----------------- | -------- |
| claude-sonnet-4.6 | ~12M     |
| claude-opus-4.6   | ~5.5M    |
| claude-haiku-4.5  | ~3.5M    |
| **total**         | **~21M** |
