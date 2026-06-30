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

## Credits

- beep_success.mp3: Positive Blip Effect by CogFireStudios -- https://freesound.org/s/531512/ -- License: Creative Commons 0
- beep_failure.mp3: sfx_wrong_generic.wav by Mihacappy -- https://freesound.org/s/844147/ -- License: Creative Commons 0
