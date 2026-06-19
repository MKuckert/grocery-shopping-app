# Android Build MCP Server

Based on [Android Project MCP Server](https://github.com/ShenghaiWang/androidbuild) from Tim Wang with the following changes under MIT license:

- Uses http transport instead of stdio for better integration with AI agents not running on the same machine.
- Changed the tools to a single `gradlew` tool.

---

A Model Context Protocol server that builds Android project that enables seamless workflow working with Android projects with AI agents.

## Available Tools

- `gradlew` - Run `gradlew` commands for the Android project
  - `command` (string, required): The command to run with `gradlew`, e.g., `assembleDebug`, `clean`, etc.

## Execution using uv

Use [`uv`](https://docs.astral.sh/uv/) to directly run _mcpandroidbuild_ locally.

```bash
uv run mcpandroidbuild
```
